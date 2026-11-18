"""Génère un jeton JWT pour le serveur helpdesk-auth.

Usage : python authentification/generer_token.py alice
"""
import os
import sys
import time

import jwt

utilisateur = sys.argv[1] if len(sys.argv) > 1 else "demo"
claims = {
    "sub": utilisateur,  # qui se connecte
    "aud": os.environ.get("MCP_RESOURCE_URL", "http://127.0.0.1:8081/mcp"),  # pour quel serveur
    "scope": "helpdesk",  # ce qu'il a le droit de faire
    "exp": int(time.time()) + 3600,  # valable 1 heure
}
print(jwt.encode(claims, os.environ["MCP_JWT_SECRET"], algorithm="HS256"))
