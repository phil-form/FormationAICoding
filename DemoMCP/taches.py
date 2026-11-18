"""Serveur MCP de gestion de tâches (SDK officiel MCP v2).

Les tâches sont stockées dans tasks.json, à côté de ce fichier.
Lancement (stdio) : python taches.py
"""

import json
from pathlib import Path
from typing import TypedDict

from mcp.server import MCPServer
from mcp.server.mcpserver.exceptions import ToolError

TASKS_FILE = Path(__file__).with_name("tasks.json")

mcp = MCPServer("taches")


class Task(TypedDict):
    id: int
    title: str
    done: bool


def load_tasks() -> list[Task]:
    if not TASKS_FILE.exists():
        return []
    return json.loads(TASKS_FILE.read_text(encoding="utf-8"))


def save_tasks(tasks: list[Task]) -> None:
    # Écriture atomique : fichier temporaire puis remplacement.
    tmp = TASKS_FILE.with_suffix(".json.tmp")
    tmp.write_text(json.dumps(tasks, ensure_ascii=False, indent=2), encoding="utf-8")
    tmp.replace(TASKS_FILE)


@mcp.tool()
def list_tasks(all: bool = False) -> list[Task]:
    """Liste les tâches à faire ; toutes les tâches (y compris terminées) si all=True."""
    tasks = load_tasks()
    return tasks if all else [t for t in tasks if not t["done"]]


@mcp.tool()
def add_tasks(title: str) -> int:
    """Ajoute une tâche et renvoie son numéro."""
    title = title.strip()
    if not title:
        raise ToolError("Le titre ne peut pas être vide.")
    tasks = load_tasks()
    task_id = max((t["id"] for t in tasks), default=0) + 1
    tasks.append({"id": task_id, "title": title, "done": False})
    save_tasks(tasks)
    return task_id


if __name__ == "__main__":
    mcp.run()
