"""Démo 3b — tester un serveur MCP sans réseau ni sous-processus (Client in-process)."""
import sys

import pytest

sys.path.insert(0, "helpdesk")
import db  # noqa: E402
from server import mcp  # noqa: E402

from mcp import Client  # noqa: E402
from mcp.types import ElicitResult  # noqa: E402


@pytest.fixture(autouse=True)
def base_temporaire(tmp_path, monkeypatch):
    monkeypatch.setattr(db, "DB_PATH", tmp_path / "test.db")


@pytest.mark.anyio
async def test_recherche_par_mot_cle():
    async with Client(mcp) as client:
        res = await client.call_tool("rechercher_tickets", {"mot_cle": "VPN"})
    assert [t["id"] for t in res.structured_content["result"]] == [2]


@pytest.mark.anyio
async def test_priorite_invalide_refusee():
    async with Client(mcp) as client:
        res = await client.call_tool("creer_ticket", {"titre": "x", "description": "y", "priorite": "urgente"})
    assert res.is_error


@pytest.mark.anyio
async def test_fermeture_refusee_par_humain():
    async def refuser(ctx, params):
        return ElicitResult(action="accept", content={"confirmer": False})

    async with Client(mcp, elicitation_callback=refuser) as client:
        res = await client.call_tool("fermer_ticket", {"ticket_id": 1})
        ouverts = await client.call_tool("rechercher_tickets", {})
    assert "annulée" in res.content[0].text
    assert 1 in [t["id"] for t in ouverts.structured_content["result"]]
