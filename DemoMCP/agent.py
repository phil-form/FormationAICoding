"""Démo 3c — une boucle agentique minimale : Claude + les outils d'un serveur MCP.

Usage : python agent.py "Quels tickets urgents sont ouverts ?"
La clé est lue dans le fichier .env (ANTHROPIC_API_KEY=sk-ant-...) ou dans l'environnement.
"""
import asyncio
import json
import os
import sys

import anthropic
from dotenv import load_dotenv

from mcp import Client

load_dotenv()  # lit ANTHROPIC_API_KEY (et MCP_URL) depuis le fichier .env du dossier courant

MODELE = "claude-sonnet-5-5"
URL_MCP = os.environ.get("MCP_URL", "http://localhost:8080/mcp")  # docker compose up -d helpdesk-mcp
MAX_TOURS = 10                           # garde-fou : jamais de boucle infinie


def outils_mcp_vers_anthropic(outils) -> list[dict]:
    """Traduction 1:1 — un tool MCP a déjà un nom, une description et un JSON Schema."""
    return [
        {"name": o.name, "description": o.description or "", "input_schema": o.input_schema}
        for o in outils
    ]


async def executer(client: Client, appel) -> dict:
    """Exécute un tool_use de Claude sur le serveur MCP et renvoie le bloc tool_result."""
    res = await client.call_tool(appel.name, appel.input)
    texte = "\n".join(c.text for c in res.content if c.type == "text")
    print(f"  -> {appel.name}({json.dumps(appel.input, ensure_ascii=False)}) : {texte[:120]!r}")
    return {"type": "tool_result", "tool_use_id": appel.id, "content": texte, "is_error": res.is_error}


async def main(question: str, llm: anthropic.AsyncAnthropic | None = None) -> str:
    llm = llm or anthropic.AsyncAnthropic()
    async with Client(URL_MCP) as client:
        outils = outils_mcp_vers_anthropic((await client.list_tools()).tools)
        messages = [{"role": "user", "content": question}]

        for tour in range(MAX_TOURS):
            reponse = await llm.messages.create(
                model=MODELE, max_tokens=1024, tools=outils, messages=messages,
                system=client.instructions or "",
            )
            messages.append({"role": "assistant", "content": reponse.content})

            if reponse.stop_reason != "tool_use":
                return "".join(b.text for b in reponse.content if b.type == "text")

            print(f"Tour {tour + 1} : Claude demande {sum(b.type == 'tool_use' for b in reponse.content)} outil(s)")
            resultats = [await executer(client, b) for b in reponse.content if b.type == "tool_use"]
            messages.append({"role": "user", "content": resultats})

    return "Arrêt : nombre maximal de tours atteint."


if __name__ == "__main__":
    print(asyncio.run(main(" ".join(sys.argv[1:]) or "Quels tickets haute priorité sont ouverts ?")))
