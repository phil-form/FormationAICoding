"""Base SQLite du fil rouge « helpdesk » (créée au premier lancement)."""
import sqlite3
from pathlib import Path

DB_PATH = Path(__file__).with_name("helpdesk.db")

SEED = [
    ("Imprimante du 2e étage hors ligne", "L'imprimante HP du 2e ne répond plus depuis ce matin.", "haute", "ouvert"),
    ("Accès VPN refusé", "Erreur 809 en se connectant depuis la maison.", "haute", "ouvert"),
    ("Demande d'écran supplémentaire", "Un second écran 24 pouces pour la compta.", "basse", "ouvert"),
    ("Mot de passe expiré", "Impossible de changer le mot de passe Windows.", "moyenne", "fermé"),
]


def connect() -> sqlite3.Connection:
    first_run = not DB_PATH.exists()
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    if first_run:
        conn.execute(
            """CREATE TABLE tickets (
                   id INTEGER PRIMARY KEY AUTOINCREMENT,
                   titre TEXT NOT NULL,
                   description TEXT NOT NULL,
                   priorite TEXT NOT NULL CHECK (priorite IN ('basse','moyenne','haute')),
                   statut TEXT NOT NULL DEFAULT 'ouvert' CHECK (statut IN ('ouvert','fermé'))
               )"""
        )
        conn.executemany(
            "INSERT INTO tickets (titre, description, priorite, statut) VALUES (?,?,?,?)", SEED
        )
        conn.commit()
    return conn
