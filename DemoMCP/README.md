# Serveur MCP « helpdesk » — connexion distante (Streamable HTTP)

Ce dépôt contient un serveur MCP de tickets de support (`helpdesk/server.py`).
Ce guide explique comment le lancer dans Docker et comment y connecter Claude.

```
┌────────────────┐  HTTP (Streamable HTTP)  ┌──────────────────────────────┐
│ Claude Code    │ ───────────────────────▶ │ 127.0.0.1:8080  (hôte)       │
│ Claude Desktop │                          │   └─▶ conteneur :8000 /mcp   │
└────────────────┘                          └──────────────────────────────┘
```

| Élément               | Valeur                          |
|-----------------------|---------------------------------|
| Port du serveur MCP   | `8000` (dans le conteneur)      |
| Port exposé sur l'hôte| `127.0.0.1:8080`                |
| URL MCP               | `http://127.0.0.1:8080/mcp`     |
| Healthcheck           | `http://127.0.0.1:8080/health`  |

---

## 0. Méthodologie : comprendre MCP par étapes

### De quoi parle-t-on ?

**MCP** (*Model Context Protocol*) est un protocole standard qui permet à une IA (Claude Code, Claude Desktop, un
agent maison) d'utiliser les outils d'une application : ici, un helpdesk de tickets. Le serveur MCP déclare ce qu'il
sait faire ; l'IA choisit quand l'utiliser. Sous le capot, ce sont des messages **JSON-RPC** (voir
`exampleJsonRPC.json`).

Un serveur MCP expose trois types de briques :

| Brique | Qui la déclenche | Exemple dans `helpdesk/server.py` |
|---|---|---|
| **Outil** (*tool*) | Le modèle, quand il juge utile de l'appeler | `rechercher_tickets`, `creer_ticket`, `fermer_ticket` |
| **Ressource** | L'utilisateur, qui la joint au contexte (`@helpdesk:`) | `tickets://{id}`, `helpdesk://procedure-escalade` |
| **Prompt** | L'utilisateur, comme une commande (`/mcp__helpdesk__…`) | `trier_tickets` |

S'y ajoute l'**élicitation** : en cours d'exécution, un outil peut demander une confirmation à l'humain (fermer un
ticket demande « êtes-vous sûr ? »).

### Le principe : une difficulté à la fois

La démo suit un fil rouge unique (le helpdesk) et n'ajoute qu'une notion à chaque étape. Chaque fichier correspond à
une étape et peut être lu seul :

| Étape | Fichier | Ce qu'on apprend |
|---|---|---|
| Échauffement | `taches.py` | Écrire un serveur MCP minimal (2 outils, stockage JSON) **en le demandant à Claude** (voir `prompt.md`), puis l'utiliser depuis Claude Code |
| Démo 1 | `helpdesk/server.py` (stdio) | Outils typés (Pydantic), ressources, prompts, élicitation ; transport **stdio** (le client lance le serveur) |
| Démo 2 | `helpdesk/server.py http` + Docker | Transport **Streamable HTTP** : un serveur partagé, joignable à distance (§ 1 à 5 ci-dessous) |
| Démo 3a | `client_demo.py` | Le point de vue du **client** : négociation du protocole, liste et appel des outils, réponse à une élicitation |
| Démo 3b | `test_helpdesk.py` | **Tester** un serveur MCP sans réseau ni sous-processus (client en mémoire, base temporaire) |
| Démo 3c | `agent.py` | Une **boucle agentique** minimale : Claude reçoit les outils MCP, les appelle, lit les résultats, recommence (garde-fou : 10 tours max) |
| Sécurité | `securite/serveur_piege.py`, `securite/audit_outils.py` | Les risques propres à MCP : **injection** dans une description d'outil, **rug pull** (description modifiée après approbation) et leur détection par empreinte |
| Authentification | `authentification/` | Restreindre l'accès : jeton **JWT Bearer**, puis OAuth 2.1 (§ 6) |

### Ce qu'il faut retenir

- **La description d'un outil est un prompt** : le modèle la lit et lui obéit. C'est la meilleure façon de guider
  l'IA… et la porte d'entrée des attaques. Il faut relire les serveurs tiers avant de les approuver et surveiller
  leurs changements (`audit_outils.py`).
- **Commencer en local, ouvrir ensuite** : stdio puis HTTP lié à `127.0.0.1`, tunnel SSH, et seulement ensuite un
  accès réseau, toujours avec authentification.
- **Un serveur MCP se teste comme une API** : `test_helpdesk.py` vérifie les outils et l'élicitation sans lancer
  Claude.
- **Les actions destructrices passent par l'humain** : la fermeture d'un ticket exige une confirmation explicite.
- **Tracer la session** : un hook (`.claude/hooks/export-prompt.sh`) recopie chaque prompt dans `prompt.md`, ce qui
  permet de rejouer la démo.

---

## 1. Démarrer le serveur

```bash
docker compose up -d --build
```

Vérifier qu'il tourne :

```bash
docker compose ps
curl http://127.0.0.1:8080/health
# {"status":"ok"}
```

Vérifier que le protocole MCP répond (requête `initialize`) :

```bash
curl -s -X POST http://127.0.0.1:8080/mcp \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"curl","version":"0"}}}'
```

Arrêter : `docker compose down` · Logs : `docker compose logs -f`

> **Pourquoi `127.0.0.1:8080` et pas `8080` ?**
> Sans l'adresse, Docker publie le port sur **toutes** les interfaces (`0.0.0.0`) :
> n'importe qui sur le réseau pourrait créer ou fermer des tickets. Le serveur n'a
> pas d'authentification, on le garde donc en local et on y accède via un tunnel
> SSH si besoin (voir §4).

---

## 2. Connecter Claude Code

### Option A — en ligne de commande

```bash
claude mcp add --transport http helpdesk http://127.0.0.1:8080/mcp
```

Portée (`--scope`) :

| Scope              | Où c'est enregistré                 | Pour qui                      |
|--------------------|-------------------------------------|-------------------------------|
| `local` (défaut)   | `~/.claude.json`, pour ce projet    | vous, dans ce dossier         |
| `project`          | `.mcp.json` à la racine du projet   | toute l'équipe (versionné)    |
| `user`             | `~/.claude.json`, global            | vous, dans tous vos projets   |

Exemple pour partager la config avec l'équipe :

```bash
claude mcp add --transport http --scope project helpdesk http://127.0.0.1:8080/mcp
```

### Option B — fichier `.mcp.json` (à la racine du projet)

```json
{
  "mcpServers": {
    "helpdesk": {
      "type": "http",
      "url": "http://127.0.0.1:8080/mcp"
    }
  }
}
```

Au premier lancement, Claude Code demande d'approuver les serveurs déclarés dans `.mcp.json`.

### Vérifier

```bash
claude mcp list          # helpdesk ... ✓ Connected
claude mcp get helpdesk
```

Dans une session Claude Code, tapez `/mcp` pour voir l'état du serveur et ses outils.

Exemples de demandes :

- « Liste les tickets ouverts de priorité haute »
- « Crée un ticket : le Wi-Fi de la salle de réunion ne marche plus »
- « Ferme le ticket 3 » → le serveur demande une **confirmation** (elicitation)
- Ressource : tapez `@helpdesk:` pour référencer `tickets://2` ou `helpdesk://procedure-escalade`
- Prompt : `/mcp__helpdesk__trier_tickets`

Supprimer : `claude mcp remove helpdesk`

---

## 3. Connecter Claude Desktop

Claude Desktop lit `claude_desktop_config.json` :

- Linux : `~/.config/Claude/claude_desktop_config.json`
- macOS : `~/Library/Application Support/Claude/claude_desktop_config.json`
- Windows : `%APPDATA%\Claude\claude_desktop_config.json`

Ce fichier ne lance que des serveurs **stdio**. Pour joindre notre serveur HTTP,
on utilise le pont `mcp-remote` (nécessite Node.js) :

```json
{
  "mcpServers": {
    "helpdesk": {
      "command": "npx",
      "args": ["-y", "mcp-remote", "http://127.0.0.1:8080/mcp"]
    }
  }
}
```

Redémarrez Claude Desktop : le serveur apparaît dans le menu des outils.

> Les **Connecteurs personnalisés** (Paramètres → Connecteurs) de claude.ai / Claude
> Desktop sont appelés depuis l'infrastructure d'Anthropic : ils exigent une URL
> **publique en HTTPS** avec authentification. Une URL `127.0.0.1` ne fonctionnera
> pas avec eux — utilisez `mcp-remote` ci-dessus ou Claude Code.

---

## 4. Serveur sur une autre machine (vraiment « distant »)

Le port n'étant ouvert que sur le `127.0.0.1` du serveur, on y accède par un
**tunnel SSH** depuis son poste :

```bash
# Sur votre poste : le 8080 local est relayé vers le 127.0.0.1:8080 du serveur
ssh -N -L 8080:127.0.0.1:8080 utilisateur@mon-serveur
```

Laissez cette commande tourner, puis configurez Claude exactement comme aux §2/§3
avec `http://127.0.0.1:8080/mcp`. Le trafic est chiffré par SSH et seuls les
utilisateurs ayant un accès SSH peuvent atteindre le serveur MCP.

> Si vous exposez un jour le serveur derrière un nom de domaine (reverse proxy
> HTTPS), ajoutez ce nom dans `MCP_ALLOWED_HOSTS` du `docker-compose.yml`
> (ex. `"localhost:*,127.0.0.1:*,mcp.exemple.be"`), sinon les requêtes sont
> rejetées (protection DNS rebinding) — et ajoutez une authentification.

---

## 5. Dépannage

| Symptôme                                 | Cause probable / solution                                                       |
|------------------------------------------|---------------------------------------------------------------------------------|
| `claude mcp list` → ✗ Failed to connect  | Conteneur arrêté : `docker compose ps`, `docker compose logs`                   |
| `curl: (7) Failed to connect`            | Mauvais port (8080 sur l'hôte, pas 8000) ou tunnel SSH non lancé               |
| `404 Not Found`                          | Il manque `/mcp` à la fin de l'URL                                              |
| `421 Misdirected Request` / Invalid Host | Host non autorisé → l'ajouter à `MCP_ALLOWED_HOSTS`                            |
| Port 8080 déjà utilisé                   | Changer la partie hôte : `"127.0.0.1:8081:8000"` et adapter l'URL              |

Inspecter le serveur visuellement avec le MCP Inspector :

```bash
npx @modelcontextprotocol/inspector
# Transport : Streamable HTTP — URL : http://127.0.0.1:8080/mcp
```

> ℹ️ La base SQLite est stockée dans le conteneur : `docker compose down` puis
> `up` repart des tickets de démonstration.

---

## 6. Authentification : qui a le droit de se connecter ?

Exemple : le serveur MCP tourne sur une machine distante derrière un reverse proxy
HAProxy en HTTPS, et on l'ajoute avec :

```bash
claude mcp add --transport http --scope project test-helpdesk https://mcp.exemple.be/mcp
```

**Par défaut, le serveur ne vérifie rien.** Toute personne qui atteint l'URL peut
lister, créer et fermer des tickets. HTTPS chiffre la connexion mais n'identifie
personne. MCP ne gère pas l'authentification lui-même : il s'appuie sur les
mécanismes HTTP habituels, l'en-tête `Authorization` et OAuth.

Deux approches sont possibles.

### 6.1 Un jeton partagé (simple, pour un petit groupe)

Claude envoie un en-tête fixe à chaque requête, et HAProxy ou le serveur le vérifie.

```bash
claude mcp add --transport http --scope project test-helpdesk https://mcp.exemple.be/mcp \
  --header 'Authorization: Bearer ${HELPDESK_TOKEN}'
```

Dans `.mcp.json`, la variable est remplacée au lancement, donc le secret n'est pas versionné :

```json
{ "mcpServers": { "test-helpdesk": {
    "type": "http",
    "url": "https://mcp.exemple.be/mcp",
    "headers": { "Authorization": "Bearer ${HELPDESK_TOKEN}" } } } }
```

Côté HAProxy, avec un jeton par personne listé dans un fichier :

```haproxy
http-request deny deny_status 401 unless { req.hdr(authorization),word(2,' ') -m str -f /etc/haproxy/mcp_tokens.lst }
```

- **Qui a le droit ?** Ceux qui ont un jeton présent dans `mcp_tokens.lst`. Pour
  retirer un accès, on supprime la ligne et on recharge HAProxy.
- **Limites :** le serveur MCP ne sait pas qui appelle, il n'y a pas de droits
  différents par outil, et les jetons n'expirent pas. C'est acceptable pour une
  démo ou une petite équipe.

### 6.2 OAuth 2.1 (la méthode prévue par la spec MCP)

Le serveur MCP ne gère aucun mot de passe. Il délègue la connexion à un
fournisseur d'identité (Keycloak, Entra ID, Auth0…) et se contente de vérifier
les jetons qu'il reçoit.

```
Claude Code                    Serveur MCP                  Keycloak (IdP)
    │ POST /mcp (sans jeton)        │                              │
    │──────────────────────────────▶│                              │
    │ 401 + WWW-Authenticate:       │                              │
    │  resource_metadata=".../.well-known/oauth-protected-resource"│
    │◀──────────────────────────────│                              │
    │ GET /.well-known/oauth-protected-resource                    │
    │──────────────────────────────▶│                              │
    │ {"authorization_servers": ["https://sso.exemple.be/..."]}    │
    │◀──────────────────────────────│                              │
    │ découverte + enregistrement du client ──────────────────────▶│
    │ ouvre le navigateur → l'utilisateur se connecte (SSO, MFA) ─▶│
    │ code + PKCE, resource=https://mcp.exemple.be/mcp ───────────▶│
    │◀────────────── access token (JWT, aud = le serveur MCP) ─────│
    │ POST /mcp  Authorization: Bearer eyJ...                      │
    │──────────────────────────────▶│ vérifie signature, iss,      │
    │                               │ aud, exp, scopes             │
```

Côté Claude Code : après `claude mcp add`, tapez `/mcp` puis choisissez
**Authenticate**. Le navigateur s'ouvre, et le jeton est ensuite stocké et
rafraîchi automatiquement.

#### Qui a le droit de se connecter ? Trois niveaux de contrôle

1. **L'IdP décide qui peut obtenir un jeton**, par exemple seuls les membres du
   groupe `support` dans Keycloak. C'est là qu'on ajoute ou retire des personnes.
2. **Le serveur décide quels jetons il accepte** : signature valide, bon émetteur,
   audience qui correspond à son URL (un jeton prévu pour une autre application
   est refusé), scope `helpdesk` présent.
3. **Chaque outil peut vérifier un droit plus fin.** Par exemple, `fermer_ticket`
   peut exiger le scope `helpdesk:admin`. Le `sub` du jeton dit aussi qui a fait
   quoi, ce qui sert pour l'audit.

#### Côté code (SDK `mcp`)

```python
import jwt  # pip install pyjwt[crypto]
from mcp.server.auth.provider import AccessToken, TokenVerifier
from mcp.server.auth.settings import AuthSettings
from mcp.server.auth.middleware.auth_context import get_access_token

ISSUER   = "https://sso.exemple.be/realms/bstorm"
RESOURCE = "https://mcp.exemple.be/mcp"
jwks = jwt.PyJWKClient(f"{ISSUER}/protocol/openid-connect/certs")

class JWTVerifier(TokenVerifier):
    async def verify_token(self, token: str) -> AccessToken | None:
        try:
            key = jwks.get_signing_key_from_jwt(token).key
            c = jwt.decode(token, key, algorithms=["RS256"], audience=RESOURCE, issuer=ISSUER)
        except jwt.PyJWTError:
            return None  # → 401
        return AccessToken(token=token, client_id=c.get("azp", ""), scopes=c.get("scope", "").split(),
                           expires_at=c["exp"], resource=RESOURCE, subject=c["sub"], claims=c)

mcp = MCPServer("helpdesk", token_verifier=JWTVerifier(),
                auth=AuthSettings(issuer_url=ISSUER, resource_server_url=RESOURCE,
                                  required_scopes=["helpdesk"]))

# Dans un outil sensible :
if "helpdesk:admin" not in get_access_token().scopes:
    raise ToolError("Droit helpdesk:admin requis pour fermer un ticket.")
```

Le SDK publie lui-même `/.well-known/oauth-protected-resource` et renvoie le 401
qui permet à Claude de lancer la connexion.

### 6.3 Points d'attention avec un reverse proxy HAProxy

- **`MCP_ALLOWED_HOSTS`** : ajoute le nom ou l'IP publique. Sinon le serveur
  répond `421`, car HAProxy transmet le `Host` d'origine.
- **Préfère un nom de domaine à une IP brute.** C'est plus simple pour le
  certificat et pour les redirections OAuth. Un certificat auto-signé sera refusé
  par Claude Code, sauf si tu ajoutes ta CA avec `NODE_EXTRA_CA_CERTS`.
- **Timeouts HAProxy** : les réponses peuvent arriver en flux (SSE). Prévois un
  `timeout server` assez long pour que HAProxy ne coupe pas la connexion pendant l'envoi.
- **Laisse le port Docker sur `127.0.0.1:8080`** si HAProxy tourne sur la même
  machine. Seul HAProxy doit être exposé.
- **Ne mets jamais de vrai jeton en clair dans `.mcp.json` en scope project**,
  puisque ce fichier est versionné. Utilise `${VAR}`.
- **Le serveur ne doit jamais renvoyer le jeton reçu de Claude vers une autre API.**
  La spec MCP l'interdit (« token passthrough »). Pour appeler une autre API, il
  doit obtenir son propre jeton.

**En résumé :** le jeton partagé (6.1) suffit pour une formation ou un usage
interne restreint. OAuth (6.2) convient dès qu'il y a de vrais utilisateurs, des
droits différents par outil, ou un besoin d'audit.

### 6.4 Exemple prêt à l'emploi

Le service `auth` du `docker-compose.yml` lance le même serveur protégé par un
jeton JWT Bearer, sur `http://127.0.0.1:8081/mcp`. Voir
[`authentification/README.md`](authentification/README.md).
