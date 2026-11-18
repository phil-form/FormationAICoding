"""Serveur MCP « helpdesk » — démo 1 (stdio) et démo 2 (Streamable HTTP)."""
import os
import sys
from typing import Annotated, Literal

from pydantic import BaseModel, Field
from starlette.requests import Request
from starlette.responses import JSONResponse

from mcp.server import MCPServer
from mcp.server.mcpserver import Elicit, Resolve
from mcp.server.mcpserver.exceptions import ToolError
from mcp.server.transport_security import TransportSecuritySettings

from db import connect

mcp = MCPServer(
    "helpdesk",
    instructions="Serveur de tickets du support interne. Toujours chercher avant de créer un doublon.",
)

Priorite = Literal["basse", "moyenne", "haute"]


class Ticket(BaseModel):
    id: int
    titre: str
    description: str
    priorite: Priorite
    statut: Literal["ouvert", "fermé"]


# ---------- TOOLS : des actions que le modèle peut décider d'appeler ----------

@mcp.tool()
def rechercher_tickets(
    mot_cle: Annotated[str, Field(description="Mot à chercher dans le titre ou la description")] = "",
    statut: Literal["ouvert", "fermé", "tous"] = "ouvert",
) -> list[Ticket]:
    """Recherche des tickets par mot-clé et par statut."""
    sql = "SELECT * FROM tickets WHERE (titre LIKE ? OR description LIKE ?)"
    params: list[str] = [f"%{mot_cle}%", f"%{mot_cle}%"]
    if statut != "tous":
        sql += " AND statut = ?"
        params.append(statut)
    with connect() as conn:
        return [Ticket(**dict(row)) for row in conn.execute(sql, params)]


@mcp.tool()
def creer_ticket(titre: str, description: str, priorite: Priorite = "moyenne") -> Ticket:
    """Crée un nouveau ticket et renvoie le ticket créé."""
    with connect() as conn:
        cur = conn.execute(
            "INSERT INTO tickets (titre, description, priorite) VALUES (?,?,?)",
            (titre, description, priorite),
        )
        row = conn.execute("SELECT * FROM tickets WHERE id = ?", (cur.lastrowid,)).fetchone()
    return Ticket(**dict(row))


# ---------- Elicitation (MRTR) : demander une confirmation à l'humain ----------

class Confirmation(BaseModel):
    confirmer: bool = Field(description="Cocher pour confirmer la fermeture")
    commentaire: str = Field(default="", description="Commentaire de clôture (optionnel)")


def demander_confirmation(ticket_id: int) -> Elicit[Confirmation]:
    return Elicit(f"Fermer définitivement le ticket #{ticket_id} ?", Confirmation)


@mcp.tool()
def fermer_ticket(
    ticket_id: int,
    reponse: Annotated[Confirmation, Resolve(demander_confirmation)],
) -> str:
    """Ferme un ticket après confirmation explicite de l'utilisateur."""
    if not reponse.confirmer:
        return f"Fermeture du ticket #{ticket_id} annulée par l'utilisateur."
    with connect() as conn:
        cur = conn.execute(
            "UPDATE tickets SET statut = 'fermé' WHERE id = ? AND statut = 'ouvert'", (ticket_id,)
        )
    if cur.rowcount == 0:
        raise ToolError(f"Ticket #{ticket_id} introuvable ou déjà fermé.")
    return f"Ticket #{ticket_id} fermé. Commentaire : {reponse.commentaire or '—'}"


# ---------- RESOURCES : des données que l'application choisit d'injecter ----------

@mcp.resource("tickets://{ticket_id}", mime_type="application/json")
def lire_ticket(ticket_id: int) -> str:
    """Un ticket complet, au format JSON."""
    with connect() as conn:
        row = conn.execute("SELECT * FROM tickets WHERE id = ?", (ticket_id,)).fetchone()
    if row is None:
        raise ValueError(f"Ticket #{ticket_id} introuvable")
    return Ticket(**dict(row)).model_dump_json(indent=2)


@mcp.resource("helpdesk://procedure-escalade", mime_type="text/markdown")
def procedure_escalade() -> str:
    """La procédure d'escalade du support (document statique)."""
    return (
        "# Procédure d'escalade\n"
        "1. Priorité haute : prise en charge sous 1 h ouvrée.\n"
        "2. Sans réponse après 4 h : escalade au responsable infra.\n"
        "3. Incident de sécurité : prévenir immédiatement le RSSI.\n"
    )


# ---------- PROMPTS : des modèles de conversation que l'utilisateur déclenche ----------

@mcp.prompt()
def trier_tickets(equipe: str = "support N1") -> str:
    """Prépare une demande de tri des tickets ouverts pour une équipe."""
    return (
        f"Tu es l'assistant de l'équipe {equipe}. Liste les tickets ouverts avec l'outil "
        "rechercher_tickets, classe-les par priorité, puis propose pour chacun une première "
        "action concrète. Applique la ressource helpdesk://procedure-escalade."
    )


# ---------- Route HTTP hors protocole (healthcheck Docker / load balancer) ----------

@mcp.custom_route("/health", methods=["GET"])
async def health(request: Request) -> JSONResponse:
    return JSONResponse({"status": "ok"})


if __name__ == "__main__":
    transport = sys.argv[1] if len(sys.argv) > 1 else "stdio"
    if transport == "http":
        # Écoute sur 0.0.0.0 (conteneur) => le SDK n'active PAS la protection DNS rebinding tout seul.
        # On la configure explicitement : seuls ces en-têtes Host/Origin sont acceptés.
        hotes = os.environ.get("MCP_ALLOWED_HOSTS", "localhost:*,127.0.0.1:*").split(",")
        securite = TransportSecuritySettings(
            enable_dns_rebinding_protection=True,
            allowed_hosts=hotes,
            allowed_origins=[f"http://{h}" for h in hotes],
        )
        mcp.run("streamable-http", host="0.0.0.0", port=8000, transport_security=securite)
    else:
        mcp.run()  # stdio par défaut
