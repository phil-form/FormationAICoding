#!/usr/bin/env bash
# Amorçage d'OpenBao en production (ADR 0001) — idempotent, à exécuter par un administrateur sur le serveur.
#
#   1. TLS interne (si absent)            5. Configuration commune (configure.sh) + rotate-root DB
#   2. Démarrage db + openbao-prod        6. Identifiants AppRole de l'API → OPENBAO_CREDS_DIR (0440)
#   3. Initialisation (1re fois) : parts   7. Token de snapshot (politique snapshot) → OPENBAO_SNAPSHOT_TOKEN_FILE
#      Shamir affichées UNE SEULE FOIS    8. Révocation du token root
#   4. Descellement + rôles PostgreSQL
#
# Rejeu (mise à jour de la configuration) : docker/prod/openbao/admin-token.sh -- docker/prod/openbao/bootstrap.sh
# (token admin temporaire généré avec les parts Shamir, transmis via BAO_TOKEN et révoqué à la fin).
set -euo pipefail
cd "$(dirname "$0")/../../.."
[[ -f .env ]] || { echo ".env absent" >&2; exit 1; }
set -a; source .env; set +a

TLS_DIR="${OPENBAO_TLS_DIR:-./secrets/openbao-tls}"
CREDS_DIR="${OPENBAO_CREDS_DIR:-./secrets/openbao-creds}"
SNAP_TOKEN_FILE="${OPENBAO_SNAPSHOT_TOKEN_FILE:-./secrets/openbao-snapshot.token}"
GROUP_ID="${TLS_CERT_GID:-$(id -g)}"
dc() { docker compose -f docker-compose.prod.yml "$@"; }
# stdin coupé : docker compose exec -T transmettrait sinon les saisies en attente au conteneur.
bao() { dc exec -T -e BAO_TOKEN="${BAO_TOKEN:-}" openbao-prod bao "$@" </dev/null; }
field() { sed -n "s/.*\"$1\": *\([^,}]*\).*/\1/p" | head -1 | tr -d ' "'; }
step() { echo; echo "▶ $*"; }

# 1. TLS interne ---------------------------------------------------------------
if [[ ! -f "$TLS_DIR/tls.crt" ]]; then
  step "PKI interne absente : création"
  docker/prod/openbao/gen-internal-tls.sh "$TLS_DIR" "$GROUP_ID"
fi

# 2. Démarrage -------------------------------------------------------------------
step "Démarrage de db et openbao-prod"
dc up -d db openbao-prod >/dev/null </dev/null
for _ in $(seq 1 30); do bao status -format=json >/dev/null 2>&1 && break; [[ $? -eq 2 ]] && break; sleep 1; done
status="$(bao status -format=json 2>/dev/null || true)"
[[ -n "$status" ]] || { echo "OpenBao ne répond pas : docker compose -f docker-compose.prod.yml logs openbao-prod" >&2; exit 1; }

# 3. Initialisation --------------------------------------------------------------
if [[ "$(field initialized <<<"$status")" != true ]]; then
  step "Initialisation : 5 parts, seuil 3"
  cat <<'EOF'
╔══════════════════════════════════════════════════════════════════════════════════════════════╗
║  Les 5 PARTS et le TOKEN ROOT vont s'afficher UNE SEULE FOIS. Rien n'est écrit sur disque.     ║
║  → Chaque part dans un item 1Password DISTINCT (détenteurs différents si possible).            ║
║  → Le token root sert uniquement à finir cet amorçage ; il est révoqué à la fin du script.     ║
║  Effacez l'historique du terminal ensuite (clear ; tmux clear-history…).                       ║
╚══════════════════════════════════════════════════════════════════════════════════════════════╝
EOF
  read -r -p "Prêt à noter les parts ? Tapez OUI : " ans; [[ "$ans" == OUI ]] || exit 1
  bao operator init -key-shares=5 -key-threshold=3
  echo
  read -r -p "Les 5 parts et le token root sont-ils enregistrés dans 1Password ? Tapez ENREGISTRÉ : " ans
  [[ "$ans" == "ENREGISTRÉ" ]] || { echo "Arrêt. OpenBao est initialisé : sans les parts, il faudra tout réinitialiser." >&2; exit 1; }
fi

# 4. Descellement et rôles PostgreSQL ----------------------------------------------
docker/prod/openbao/unseal.sh

# Token admin : fourni par admin-token.sh (rejeu) ou saisi (token root initial du premier amorçage).
if [[ -z "${BAO_TOKEN:-}" ]]; then
  read -r -s -p "Token admin (root initial, saisie masquée) : " BAO_TOKEN; echo
fi
export BAO_TOKEN
bao token lookup >/dev/null 2>&1 || { echo "Token invalide." >&2; exit 1; }

db_admin_pw=""
if ! bao read database/config/formation >/dev/null 2>&1; then
  # Premier amorçage : mot de passe initial aléatoire, tourné juste après par OpenBao (rotate-root).
  db_admin_pw="$(openssl rand -base64 36 | tr -d '/+=\n')"
fi
step "Rôles PostgreSQL"
docker run --rm --network "formation-prod_default" \
  -e PGHOST=db -e PGUSER="$DB_USER" -e PGPASSWORD="$DB_PASSWORD" -e PGDATABASE="$DB_NAME" \
  -e OPENBAO_DB_ADMIN_PASSWORD="$db_admin_pw" \
  -v "$PWD/docker/db/roles.sh:/roles.sh:ro" --entrypoint sh postgres:18-alpine /roles.sh </dev/null

# 5. Configuration commune ---------------------------------------------------------
step "Configuration OpenBao (configure.sh)"
dc exec -T -e BAO_TOKEN -e OPENBAO_DB_ADMIN_PASSWORD="$db_admin_pw" \
  -e DB_HOST=db -e DB_NAME="$DB_NAME" -e BOUND_CIDRS="${DOCKER_SUBNET:-172.31.0.0/24}" \
  -e DB_CONFIG_MODE=if-absent -e ROTATE_DB_ROOT=true -e SECRET_ID_TTL="${OPENBAO_SECRET_ID_TTL:-2160h}" \
  openbao-prod sh /scripts/configure.sh </dev/null
unset db_admin_pw

# 6. Identifiants AppRole de l'API ---------------------------------------------------
step "Identifiants AppRole api → $CREDS_DIR"
install -d -m 0750 -g "$GROUP_ID" "$CREDS_DIR"
umask 077
bao read -field=role_id auth/approle/role/api/role-id > "$CREDS_DIR/.role-id.new"
bao write -f -field=secret_id auth/approle/role/api/secret-id > "$CREDS_DIR/.secret-id.new"
for f in role-id secret-id; do
  chgrp "$GROUP_ID" "$CREDS_DIR/.$f.new"; chmod 0440 "$CREDS_DIR/.$f.new"; mv -f "$CREDS_DIR/.$f.new" "$CREDS_DIR/$f"
done
echo "  role-id et secret-id écrits (0440, groupe $GROUP_ID). Le secret_id expire après ${OPENBAO_SECRET_ID_TTL:-2160h} : renouvelé à chaque déploiement (phase 6)."

# 7. Token de snapshot ----------------------------------------------------------------
if [[ ! -s "$SNAP_TOKEN_FILE" ]]; then
  step "Token de snapshot → $SNAP_TOKEN_FILE"
  # Ne jamais modifier les droits d'un dossier existant (il peut être partagé, ex. ./certs lu par l'API).
  [[ -d "$(dirname "$SNAP_TOKEN_FILE")" ]] || install -d -m 0700 "$(dirname "$SNAP_TOKEN_FILE")"
  bao token create -orphan -policy=snapshot -period=768h -display-name=snapshot -field=token > "$SNAP_TOKEN_FILE"
  chmod 0400 "$SNAP_TOKEN_FILE"
  echo "  Token périodique (renouvelé à chaque snapshot)."
fi

# 8. Révocation du token admin -----------------------------------------------------------
step "Révocation du token admin"
bao token revoke -self >/dev/null
unset BAO_TOKEN
echo
echo "✔ Amorçage terminé. OpenBao est descellé et configuré ; plus aucun token root valide."
echo "  Vérifier : docker compose -f docker-compose.prod.yml exec openbao-prod bao status"
