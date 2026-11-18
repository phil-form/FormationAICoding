# Serveur MCP helpdesk avec authentification JWT Bearer

Même serveur que `helpdesk/`, mais chaque requête doit contenir :

```
Authorization: Bearer <jeton JWT>
```

Sans jeton valide, le serveur répond **401**.

| Fichier            | Rôle                                                   |
|--------------------|--------------------------------------------------------|
| `server.py`        | le serveur MCP : vérifie le jeton puis réutilise les outils de `helpdesk/server.py` |
| `generer_token.py` | crée un jeton signé pour un utilisateur                |

URL : `http://127.0.0.1:8081/mcp`

## 1. Démarrer

```bash
export MCP_JWT_SECRET=$(openssl rand -hex 32)   # secret qui signe les jetons
docker compose up -d --build auth
```

## 2. Créer un jeton

```bash
docker compose exec auth python authentification/generer_token.py alice
```

Le jeton contient `sub` (qui), `aud` (pour quel serveur), `scope` (`helpdesk`) et
`exp` (expire après 1 heure). Le serveur vérifie la signature, l'audience,
l'expiration et la présence du scope `helpdesk`.

## 3. Tester avec curl

```bash
TOKEN=$(docker compose exec -T auth python authentification/generer_token.py alice)

# Sans jeton → 401
curl -i -X POST http://127.0.0.1:8081/mcp

# Avec jeton → 200
curl -X POST http://127.0.0.1:8081/mcp \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"curl","version":"0"}}}'
```

## 4. Connecter Claude Code

```bash
export HELPDESK_TOKEN=$(docker compose exec -T auth python authentification/generer_token.py alice)

claude mcp add --transport http --scope project helpdesk-auth http://127.0.0.1:8081/mcp \
  --header 'Authorization: Bearer ${HELPDESK_TOKEN}'
```

Les guillemets simples empêchent le shell de remplacer `${HELPDESK_TOKEN}`. La
commande écrit `.mcp.json` avec la variable, pas sa valeur : Claude Code la lit au
démarrage, donc le jeton n'apparaît jamais en clair dans un fichier versionné. Il faut toutefois lancer `claude` depuis un terminal
où `HELPDESK_TOKEN` est défini.

Vérifier : `claude mcp list` → `helpdesk-auth ... ✓ Connected`

Quand le jeton expire (1 h), il suffit d'en générer un nouveau et de relancer Claude Code.

## Limites de cette démo

- Le secret HS256 sert à la fois à **signer** et à **vérifier** les jetons : quiconque
  le possède peut fabriquer un jeton. En production, les jetons sont émis par un
  fournisseur d'identité (Keycloak, Entra ID…) avec une clé RS256, et le serveur
  ne connaît que la clé publique (voir §6.2 du README principal).
- Pas de révocation : un jeton reste valide jusqu'à son expiration.
- Changer `MCP_JWT_SECRET` invalide tous les jetons existants.
