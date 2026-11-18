"""Démo 3a — un client MCP minimal qui pilote le serveur helpdesk en stdio."""
import asyncio
import json

from mcp import Client, StdioServerParameters
from mcp.client.context import ClientRequestContext
from mcp.types import ElicitRequestParams, ElicitResult

serveur = StdioServerParameters(command="python", args=["helpdesk/server.py"])


async def repondre_elicitation(ctx: ClientRequestContext, params: ElicitRequestParams) -> ElicitResult:
    """Appelé quand un outil a besoin d'une réponse humaine (MRTR)."""
    print(f"\n[ELICITATION] {params.message}")
    print("  schéma demandé :", json.dumps(params.requested_schema, ensure_ascii=False))
    # Dans une vraie appli : afficher un formulaire. Ici on accepte automatiquement.
    return ElicitResult(action="accept", content={"confirmer": True, "commentaire": "Résolu par reboot"})


async def main() -> None:
    async with Client(serveur, elicitation_callback=repondre_elicitation) as client:
        print("Protocole négocié :", client.protocol_version)
        print("Serveur :", client.server_info.name)

        outils = await client.list_tools()
        for outil in outils.tools:
            print(f"- tool {outil.name}: {outil.description}")

        res = await client.call_tool("rechercher_tickets", {"mot_cle": "VPN"})
        print("\nRésultat structuré :", res.structured_content)

        lu = await client.read_resource("tickets://2")
        print("Ressource tickets://2 :", lu.contents[0].text)

        prompt = await client.get_prompt("trier_tickets", {"equipe": "infra"})
        print("\nPrompt :", prompt.messages[0].content.text)

        res = await client.call_tool("fermer_ticket", {"ticket_id": 1})
        print("\nFermeture :", res.content[0].text)


asyncio.run(main())
