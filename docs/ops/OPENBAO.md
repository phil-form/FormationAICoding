# OpenBao — runbook d'exploitation

Décisions et justifications : [`docs/adr/0001-gestion-des-secrets.md`](../adr/0001-gestion-des-secrets.md).
Feuille de route : [`Next.md`](../../Next.md).

## Vue d'ensemble

| | Dev | Prod |
|---|---|---|
| Service compose | `openbao-dev` (mode dev, mémoire, descellé) | `openbao-prod` (Raft, TLS 1.3, scellé au démarrage) |
| Adresse (réseau Docker) | `http://openbao:8200` | `https://openbao:8200` (CA interne) |
| Accès hôte | `127.0.0.1:8200` (confort dev) | **aucun** port publié |
| Configuration | `openbao-init` lit `.env.dev` → `docker/openbao/configure.sh` | `bootstrap.sh` → **même** `configure.sh` |
| Audit | sortie standard du conteneur | `/openbao/logs/audit.log` (volume `openbao-audit`, 0600) |
| Rôles PostgreSQL | service `db-roles` à chaque démarrage | `bootstrap.sh` |

### Fichiers

| Fichier | Rôle |
|---|---|
| `docker/openbao/configure.sh` | Configuration **unique** dev/prod, idempotente (KV, Transit, database, politiques, AppRole) |
| `docker/openbao/policies/*.hcl` | Politiques `api`, `deployer`, `snapshot` |
| `docker/openbao/dev.hcl` | Audit déclaratif du mode dev |
| `docker/openbao/smoke-test.sh` | Recette des droits de l'AppRole `api` (doit / ne doit pas) |
| `docker/openbao/check-phase1.sh` | Recette complète en dev (OpenBao + droits SQL + révocation) |
| `docker/db/roles.sh` | Rôles `formation_owner`, `formation_rw`, `openbao_admin` (idempotent) |
| `docker/prod/openbao/openbao.hcl` | Configuration serveur prod |
| `docker/prod/openbao/gen-internal-tls.sh` | CA interne + certificat serveur (1 an) |
| `docker/prod/openbao/bootstrap.sh` | Amorçage / rejeu de la configuration prod |
| `docker/prod/openbao/unseal.sh` | Descellement Shamir interactif |
| `docker/prod/openbao/admin-token.sh` | Accès admin break-glass (aucun token root permanent) |
| `docker/prod/openbao/snapshot.sh` | Snapshot Raft chiffré (age) + rétention |

### Ce que contient OpenBao

| Chemin | Contenu | Utilisé par |
|---|---|---|
| `transit/keys/jwt` | ECDSA P-256, non exportable | signature des JWT (`transit/sign/jwt`, `marshaling_algorithm=jws`) |
| `transit/keys/niss` | AES-256-GCM96 **dérivée** (contexte = type + id de la ligne), non exportable, non supprimable | chiffrement du NISS (`CryptoService`) |
| `transit/keys/niss-index` | HMAC 256 bits, non exportable | index aveugle du NISS |
| `transit/keys/file` | AES-256-GCM96 **dérivée** (contexte = type + id du fichier), non exportable, non supprimable | chiffrement des **clés de données** des fichiers téléversés (enveloppe, `FileStorage`) |
| `database/roles/app` | utilisateur PG dynamique, groupe `formation_rw`, 1 h (max 7 j) | runtime de l'API |
| `database/roles/migrator` | utilisateur PG dynamique, groupe `formation_owner`, 15 min (max 1 h) | Flyway |
| `kv/formation` | secrets statiques (dev : variables `APP_SECRET_*` de `.env.dev`) | API |
| `auth/approle/role/api` | token **périodique** 1 h (renouvelé tant que l'API tourne), lié au CIDR Docker | API |

## Intégration dans l'API (phase 2)

Code : `src/main/java/be/bstorm/formation/framework/openbao/` — configuration `app.openbao.*` (activée en dev et prod).

| Élément | Fonctionnement |
|---|---|
| Authentification | AppRole : `role-id` / `secret-id` lus dans `/run/secrets/openbao/` (lecture seule). Token **périodique** (1 h) renouvelé en continu |
| TLS (prod) | CA interne montée sur `/run/secrets/openbao-ca.crt` (PEM), TLS 1.3 uniquement |
| Démarrage | `OpenBaoReadiness` : refus explicite si OpenBao est injoignable, non initialisé ou **scellé** |
| Pool DB | `database/creds/app` en **rotation** : renouvellement du bail, nouvel utilisateur avant `max_ttl` (marge `lease-expiry-threshold` = 10 min), Hikari reçoit les nouveaux identifiants et retire les anciennes connexions à leur libération |
| Flyway | `database/creds/migrator` + `SET ROLE formation_owner` ; bail **révoqué** dès la fin des migrations |
| Arrêt | baux révoqués → utilisateurs PostgreSQL supprimés, sessions coupées |
| Santé | `/actuator/health` → composant `openBao` (DOWN si scellé / injoignable) |

Forcer une rotation des identifiants DB (fuite suspectée) : bean `RotatingDatabaseCredentials#rotateNow()` (ou
redémarrer l'API). Tests : `OpenBaoIntegrationIT` (vrais PostgreSQL + OpenBao, mêmes scripts que dev/prod).

## Dev

```bash
./start.sh                       # crée .env.dev s'il manque, démarre db, OpenBao, db-roles, openbao-init, API
docker/openbao/check-phase1.sh   # recette : 26 contrôles OpenBao + 11 contrôles SQL
```

CLI depuis l'hôte : `BAO_ADDR=http://127.0.0.1:8200 BAO_TOKEN=<BAO_DEV_ROOT_TOKEN_ID de .env.dev> bao …`
(ou `docker compose exec openbao-dev sh`). OpenBao dev est **en mémoire** : tout est recréé à chaque
démarrage à partir de `.env.dev`. Les identifiants AppRole de l'API sont dans le volume `openbao-dev-creds`, monté en
lecture seule dans `api-dev` sur `/run/secrets/openbao`.

Ajouter un secret statique : `APP_SECRET_MON_SECRET=valeur` dans `.env.dev` → `kv/formation` clé `mon_secret`.

## Prod — premier amorçage

Prérequis : `.env` de prod (voir `.env.example`, section OpenBao), groupe de lecture des secrets (`TLS_CERT_GID`),
détenteurs des 5 parts désignés, accès 1Password prêt.

```bash
docker/prod/openbao/bootstrap.sh
```

1. Crée la PKI interne si elle manque (`OPENBAO_TLS_DIR`). **Déplacer `ca.key` dans 1Password** puis la supprimer du serveur.
2. Démarre `db` et `openbao-prod`.
3. **Initialise** (5 parts, seuil 3) : parts et token root **affichés une seule fois** → un item 1Password par part.
   Effacer ensuite l'historique du terminal.
4. Descelle (3 parts), crée les rôles PostgreSQL avec un mot de passe `openbao_admin` aléatoire, applique
   `configure.sh`, puis **`rotate-root`** : plus personne ne connaît le mot de passe d'`openbao_admin`.
5. Écrit `role-id` / `secret-id` de l'API dans `OPENBAO_CREDS_DIR` (0440, groupe `TLS_CERT_GID`).
6. Crée le token périodique de snapshot (`OPENBAO_SNAPSHOT_TOKEN_FILE`, 0400).
7. **Révoque le token root.** Plus aucun token root n'existe : voir « Accès administrateur ».

## Prod — descellement (après chaque redémarrage)

OpenBao redémarre **scellé** (reboot du serveur, mise à jour, crash). Tant qu'il l'est, l'API ne peut plus émettre de
token, lire de NISS ni ouvrir de nouvelle connexion DB.

```bash
./start.sh --prod                 # détecte l'état et lance le descellement si un terminal est disponible
docker/prod/openbao/unseal.sh     # ou directement
```

Chaque détenteur saisit sa part (saisie masquée). Les parts passent par le corps d'une requête HTTPS sur la boucle
locale du conteneur (CA vérifiée) : jamais dans un argument de processus, l'historique ou un fichier. La progression
est conservée si un détenteur n'est pas encore disponible (relancer le script).

## Prod — accès administrateur (break-glass)

OpenBao ≥ 2.5 désactive les endpoints `generate-root` non authentifiés (CVE-2025-52894). Comme aucun token root
permanent n'existe, l'accès admin passe par :

```bash
docker/prod/openbao/admin-token.sh                                       # shell admin dans le conteneur
docker/prod/openbao/admin-token.sh -- docker/prod/openbao/bootstrap.sh   # rejouer la configuration
```

Déroulé (≈ 2 min, **fenêtre de maintenance** : OpenBao redémarre deux fois) :

1. redémarrage avec une configuration dérivée d'`openbao.hcl` + `disable_unauthed_generate_root_endpoints = false` ;
2. descellement (3 parts) puis génération d'un token root (3 parts) — **jamais affiché** ;
3. retour **immédiat** à la configuration normale et nouveau descellement (3 parts) ;
4. exécution de la commande avec `BAO_TOKEN`, puis **révocation** du token, même en cas d'erreur.

En cas d'interruption pendant le mode break-glass, le script rétablit la configuration normale (OpenBao est alors
scellé : relancer `unseal.sh`). Il ne laisse jamais OpenBao en mode break-glass.

## Rejouer la configuration (nouvelle politique, nouveau rôle…)

Modifier `docker/openbao/configure.sh` ou `docker/openbao/policies/*.hcl`, tester en dev (`./start.sh` +
`check-phase1.sh`), puis en prod :

```bash
docker/prod/openbao/admin-token.sh -- docker/prod/openbao/bootstrap.sh
```

`configure.sh` est idempotent ; en prod il ne réécrit **jamais** la connexion database (`DB_CONFIG_MODE=if-absent`),
dont le mot de passe n'est connu que d'OpenBao. `bootstrap.sh` renouvelle aussi le `secret_id` de l'API.

## Rotation des clés

| Clé | Commande (token admin) | Effet |
|---|---|---|
| `jwt` | `bao write -f transit/keys/jwt/rotate` | nouvelle version → nouveau `kid` ; les tokens signés avec l'ancienne version restent vérifiables (5 min de vie) |
| `niss` | 1. `bao write -f transit/keys/niss/rotate` ; 2. `KeyRotationService.rewrapAll(<RewrapTarget>)` pour chaque colonne (rewrap côté OpenBao, la valeur ne sort jamais en clair) ; 3. `bao write transit/keys/niss/config min_decryption_version=<n>` | les anciens chiffrés restent lisibles jusqu'à l'étape 3 ; déclenchement du job : phase 6 |
| `file` | 1. `bao write -f transit/keys/file/rotate` ; 2. `KeyRotationService.rewrapAll(storedFileRewrapTarget)` (re-chiffre les clés de données, pas les contenus) ; 3. `min_decryption_version` | idem `niss` |
| `niss-index` | **ne pas faire tourner** sans plan : l'index aveugle changerait pour tous les NISS (recalcul complet requis) | — |
| `openbao_admin` (PostgreSQL) | `bao write -f database/rotate-root/formation` | nouveau mot de passe connu d'OpenBao seul |
| parts Shamir | `bao operator rotate-keys -init -key-shares=5 -key-threshold=3` puis 3 parts actuelles | nouvelles parts → remplacer **toutes** les entrées 1Password |

Fréquences : question ouverte Q3 de l'ADR (hypothèse : `jwt` 90 j, `niss` 1 an).

## Renouvellement

| Élément | Durée | Renouvellement |
|---|---|---|
| `secret_id` de l'API | `OPENBAO_SECRET_ID_TTL` (90 j) | à chaque déploiement (phase 6, politique `deployer`) ; d'ici là : `admin-token.sh -- bootstrap.sh` |
| Certificat TLS interne | 1 an | `docker/prod/openbao/gen-internal-tls.sh` (nécessite `ca.key` depuis 1Password) puis `docker compose -f docker-compose.prod.yml kill -s HUP openbao-prod` |
| Token de snapshot | période 32 j | renouvelé automatiquement à chaque snapshot |

## Sauvegarde et restauration

### Snapshot quotidien (timer systemd)

```ini
# /etc/systemd/system/openbao-snapshot.service
[Service]
Type=oneshot
WorkingDirectory=/srv/formation/app
ExecStart=/srv/formation/app/docker/prod/openbao/snapshot.sh

# /etc/systemd/system/openbao-snapshot.timer
[Timer]
OnCalendar=*-*-* 02:30:00
RandomizedDelaySec=15m
Persistent=true
[Install]
WantedBy=timers.target
```

`sudo apt install age` ; `AGE_RECIPIENT` = clé publique age (`age-keygen` sur un poste admin ; **clé privée dans
1Password**, jamais sur le serveur). Les fichiers `openbao-*.snap.age` sont à synchroniser hors site (Q5).
Un snapshot n'est exploitable qu'avec **la clé privée age et 3 parts Shamir**.

### Restauration (testée)

```bash
age -d -i cle-privee.txt -o openbao.snap openbao-AAAAMMJJ.snap.age
docker/prod/openbao/admin-token.sh        # puis, dans le shell admin :
#   (copier openbao.snap dans le conteneur au préalable : docker compose -f docker-compose.prod.yml exec -T openbao-prod sh -c 'cat > /tmp/r.snap' < openbao.snap)
#   bao operator raft snapshot restore -force /tmp/r.snap
```

L'état revient à celui du snapshot (y compris les tokens : le token admin créé après le snapshot devient invalide).
Les baux de base de données émis après le snapshot ne sont plus connus d'OpenBao : redémarrer l'API (phase 2) pour
obtenir de nouveaux identifiants ; les utilisateurs PostgreSQL orphelins expirent seuls (`VALID UNTIL`).

**Exercice de restauration complet obligatoire avant la mise en production** (Next.md, 6.4).

## Incidents

| Situation | Action |
|---|---|
| OpenBao scellé (redémarrage) | `docker/prod/openbao/unseal.sh` avec 3 détenteurs |
| Une part perdue ou compromise | Avec 3 parts valides : `rotate-keys` (nouvelles parts), mettre à jour 1Password, détruire les anciennes |
| Moins de 3 parts disponibles | **Données irrécupérables** (par conception) : restaurer… impossible sans parts. D'où 5 parts chez des détenteurs distincts |
| `secret_id` de l'API compromis | `admin-token.sh`, puis `bao write auth/approle/role/api/secret-id-accessor/destroy secret_id_accessor=…` et `bootstrap.sh` (nouveau secret_id) |
| Fuite suspectée du mot de passe `openbao_admin` | `bao write -f database/rotate-root/formation` |
| Audit impossible à écrire (disque plein) | OpenBao **refuse les requêtes** : libérer de l'espace sur le volume `openbao-audit` (rotation des logs) |
| Utilisateurs PG dynamiques orphelins | `SELECT rolname, rolvaliduntil FROM pg_roles WHERE rolname LIKE 'v-approle-%';` — expirent seuls ; suppression manuelle possible |

## Vérifications

```bash
docker compose -f docker-compose.prod.yml exec openbao-prod bao status                       # Sealed: false
docker compose -f docker-compose.prod.yml port openbao-prod 8200                             # aucun port publié
docker compose -f docker-compose.prod.yml exec openbao-prod ls -l /openbao/logs/audit.log    # audit présent, 0600
```

Recette des droits de l'API sur la prod (identifiants réels, lecture seule) :

```bash
docker run --rm --network formation-prod_default --group-add "$TLS_CERT_GID" \
  -e BAO_ADDR=https://openbao:8200 -e BAO_CACERT=/tls/ca.crt \
  -v "$OPENBAO_CREDS_DIR:/creds:ro" -v "$OPENBAO_TLS_DIR:/tls:ro" -v "$PWD/docker/openbao:/scripts:ro" \
  --entrypoint sh openbao/openbao:2.7.2 /scripts/smoke-test.sh
```
