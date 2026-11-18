"""Serveur MCP « helpdesk » protégé par un jeton JWT Bearer.

Mêmes outils que helpdesk/server.py, mais chaque requête doit porter l'en-tête :
    Authorization: Bearer <jwt signé avec MCP_JWT_SECRET>
"""
import os
import sys
from pathlib import Path

import jwt

# On réutilise le code de helpdesk/server.py au lieu de le copier
sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "helpdesk"))
import server as base  # noqa: E402

from mcp.server import MCPServer  # noqa: E402
from mcp.server.auth.provider import AccessToken, TokenVerifier  # noqa: E402
from mcp.server.auth.settings import AuthSettings  # noqa: E402
from mcp.server.transport_security import TransportSecuritySettings  # noqa: E402

SECRET = os.environ.get("MCP_JWT_SECRET", "")
if len(SECRET) < 32:  # un secret vide ou court permettrait de forger des jetons
    sys.exit("MCP_JWT_SECRET manquant ou trop court : export MCP_JWT_SECRET=$(openssl rand -hex 32)")
URL = os.environ.get("MCP_RESOURCE_URL", "http://127.0.0.1:8081/mcp")  # l'« aud » attendu


class JWTVerifier(TokenVerifier):
    """Appelé à chaque requête. Renvoyer None => le client reçoit un 401."""

    async def verify_token(self, token: str) -> AccessToken | None:
        try:
            claims = jwt.decode(token, SECRET, algorithms=["HS256"], audience=URL)
        except jwt.PyJWTError:  # signature fausse, jeton expiré, mauvaise audience...
            return None
        return AccessToken(
            token=token,
            client_id=claims["sub"],
            scopes=claims.get("scope", "").split(),
            expires_at=claims.get("exp"),
            resource=URL,
            subject=claims["sub"],
        )


mcp = MCPServer(
    "helpdesk-auth",
    token_verifier=JWTVerifier(),
    auth=AuthSettings(
        issuer_url=URL,  # obligatoire pour le SDK ; ici c'est nous qui émettons les jetons
        resource_server_url=URL,
        required_scopes=["helpdesk"],
        validate_token_resource=True,
    ),
)

# Les mêmes outils, ressources et prompts que le serveur sans authentification
mcp.tool()(base.rechercher_tickets)
mcp.tool()(base.creer_ticket)
mcp.tool()(base.fermer_ticket)
mcp.resource("tickets://{ticket_id}", mime_type="application/json")(base.lire_ticket)
mcp.resource("helpdesk://procedure-escalade", mime_type="text/markdown")(base.procedure_escalade)
mcp.prompt()(base.trier_tickets)
mcp.custom_route("/health", methods=["GET"])(base.health)

if __name__ == "__main__":
    hotes = os.environ.get("MCP_ALLOWED_HOSTS", "localhost:*,127.0.0.1:*").split(",")
    securite = TransportSecuritySettings(
        enable_dns_rebinding_protection=True,
        allowed_hosts=hotes,
        allowed_origins=[f"http://{h}" for h in hotes],
    )
    mcp.run("streamable-http", host="0.0.0.0", port=8000, transport_security=securite)
