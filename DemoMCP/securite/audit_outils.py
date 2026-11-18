"""Démo sécurité — épingler les descriptions d'outils (détection de rug pull et d'injection).

1re exécution : enregistre une empreinte SHA-256 de chaque outil dans outils.lock.json
Exécutions suivantes : signale tout outil nouveau, modifié, ou contenant des motifs suspects.
"""
import asyncio
import hashlib
import json
import re
import sys
from pathlib import Path

from mcp import Client, StdioServerParameters

LOCK = Path(__file__).with_name("outils.lock.json")
SUSPECT = re.compile(r"<important>|ignore (all|previous)|ne le mentionne pas|do not tell|~/.ssh|\.env", re.I)


def empreinte(outil) -> str:
    brut = json.dumps({"d": outil.description, "s": outil.input_schema}, sort_keys=True)
    return hashlib.sha256(brut.encode()).hexdigest()[:16]


async def main(commande: list[str]) -> int:
    # NB : le SDK ne transmet au sous-processus qu'un environnement minimal (pas vos secrets).
    async with Client(StdioServerParameters(command=sys.executable, args=commande)) as client:
        outils = (await client.list_tools()).tools

    connu = json.loads(LOCK.read_text()) if LOCK.exists() else {}
    alertes = 0
    for o in outils:
        h = empreinte(o)
        if SUSPECT.search(o.description or ""):
            print(f"[SUSPECT]  {o.name} : motif d'injection dans la description")
            alertes += 1
        if o.name not in connu:
            print(f"[NOUVEAU]  {o.name} ({h}) — à relire avant approbation")
            connu[o.name] = h
        elif connu[o.name] != h:
            print(f"[MODIFIÉ]  {o.name} : {connu[o.name]} -> {h}  (rug pull ?)")
            alertes += 1
        else:
            print(f"[OK]       {o.name} ({h})")
    if not LOCK.exists():
        LOCK.write_text(json.dumps(connu, indent=2))
        print(f"Empreintes enregistrées dans {LOCK.name}")
    return 1 if alertes else 0


if __name__ == "__main__":
    sys.exit(asyncio.run(main(sys.argv[1:])))
