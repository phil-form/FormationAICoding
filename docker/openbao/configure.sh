#!/bin/sh
# Configuration OpenBao — UNIQUE pour dev et prod (ADR 0001, D9) : seule la source des valeurs change.
# Idempotent : peut être rejoué sans effet de bord (engines, clés, politiques et rôles sont créés ou mis à jour).
#
# Exécuté dans un conteneur openbao/openbao (sh POSIX, CLI bao, sans jq ni bash).
#
# Entrées (environnement) :
#   BAO_ADDR, BAO_TOKEN (ou BAO_DEV_ROOT_TOKEN_ID en dev), BAO_CACERT (prod)
#   DB_HOST, DB_NAME, OPENBAO_DB_ADMIN_PASSWORD   connexion du moteur database (rôle openbao_admin)
#   DB_SSLMODE                  défaut disable (réseau Docker interne)
#   BOUND_CIDRS                 réseau autorisé à utiliser l'AppRole api (défaut 172.30.0.0/24)
#   SECRET_ID_TTL               durée de vie d'un secret_id de l'API (défaut 2160h = 90 j)
#   APPROLE_ROLE_ID             role_id fixe (dev uniquement, pour des identifiants stables)
#   DB_CONFIG_MODE              always (dev) | if-absent (prod : après rotate-root, ne jamais réécrire le mot de passe)
#   ROTATE_DB_ROOT              true (prod) : OpenBao change le mot de passe d'openbao_admin, plus personne ne le connaît
#   CREDS_OUT_DIR               si défini : y écrit role-id et un nouveau secret-id pour l'API
#   CREDS_MODE                  droits de ces fichiers (défaut 0400 ; dev : 0444, volume lu par l'API non-root)
#   APP_SECRET_*                chaque variable APP_SECRET_FOO_BAR=v devient kv/formation foo_bar=v (dev : .env.dev)
#   POLICY_DIR                  défaut : dossier policies/ à côté de ce script
set -eu

: "${BAO_TOKEN:=${BAO_DEV_ROOT_TOKEN_ID:-}}"
export BAO_TOKEN
[ -n "$BAO_TOKEN" ] || { echo "BAO_TOKEN requis" >&2; exit 1; }
: "${DB_HOST:=db}" "${DB_NAME:=formation}" "${DB_SSLMODE:=disable}"
: "${BOUND_CIDRS:=172.30.0.0/24}" "${SECRET_ID_TTL:=2160h}"
: "${DB_CONFIG_MODE:=always}" "${ROTATE_DB_ROOT:=false}"
POLICY_DIR="${POLICY_DIR:-$(dirname "$0")/policies}"

log() { echo "[openbao-configure] $*"; }

# --- Attente : OpenBao joignable, initialisé et descellé ----------------------
i=0
until bao status >/dev/null 2>&1; do
  i=$((i + 1))
  [ "$i" -le 60 ] || { echo "OpenBao indisponible ou scellé ($BAO_ADDR)" >&2; bao status >&2 || true; exit 1; }
  sleep 1
done

enabled() { bao "$1" list 2>/dev/null | grep -q "^$2/ "; }

# --- Audit : déclaré dans la configuration du serveur (OpenBao 2.x refuse l'activation par l'API) ---------
bao audit list 2>/dev/null | grep -q '^[a-z-]*/ *file' || log "ATTENTION : aucun audit actif (vérifier la configuration HCL)"

# --- KV v2 : secrets statiques de l'application -----------------------------------
enabled secrets kv || { bao secrets enable -path=kv -version=2 kv >/dev/null; log "kv v2 activé"; }
set --
for name in $(env | sed -n 's/^\(APP_SECRET_[A-Za-z0-9_]*\)=.*/\1/p'); do
  key=$(echo "${name#APP_SECRET_}" | tr 'A-Z' 'a-z')
  eval "val=\${$name}"
  set -- "$@" "$key=$val"
done
if [ "$#" -gt 0 ]; then
  bao kv put -mount=kv formation "$@" >/dev/null
  log "kv/formation : $# secret(s) injecté(s)"
fi

# --- Transit : JWT (ES256), NISS (AES-256-GCM), index aveugle (HMAC), fichiers -------
enabled secrets transit || { bao secrets enable transit >/dev/null; log "transit activé"; }
key() { # key <nom> <type> [options...]
  name=$1 type=$2; shift 2
  if ! bao read "transit/keys/$name" >/dev/null 2>&1; then
    bao write "transit/keys/$name" type="$type" exportable=false allow_plaintext_backup=false "$@" >/dev/null
    log "clé transit $name ($type) créée"
  fi
  bao write "transit/keys/$name/config" deletion_allowed=false exportable=false >/dev/null
}
key jwt ecdsa-p256
# NISS : clé DÉRIVÉE → chaque chiffré est lié à un contexte (type + identifiant de la ligne) : impossible de
# déplacer un chiffré d'une ligne à une autre ; rewrap et min_decryption_version restent disponibles.
key niss aes256-gcm96 derived=true
[ "$(bao read -field=derived transit/keys/niss)" = true ] || {
  echo "ERREUR : la clé transit/keys/niss existe mais n'est pas dérivée (derived=true requis, ADR 0001 D6)." >&2; exit 1; }
key niss-index hmac key_size=32
# Fichiers téléversés : chiffre les CLÉS DE DONNÉES (enveloppe), clé dérivée liée à l'id du fichier.
key file aes256-gcm96 derived=true
[ "$(bao read -field=derived transit/keys/file)" = true ] || {
  echo "ERREUR : la clé transit/keys/file existe mais n'est pas dérivée (derived=true requis)." >&2; exit 1; }

# --- Database : identifiants PostgreSQL dynamiques --------------------------------
enabled secrets database || { bao secrets enable database >/dev/null; log "database activé"; }
if [ "$DB_CONFIG_MODE" = always ] || ! bao read database/config/formation >/dev/null 2>&1; then
  : "${OPENBAO_DB_ADMIN_PASSWORD:?OPENBAO_DB_ADMIN_PASSWORD requis pour configurer la connexion}"
  bao write database/config/formation \
    plugin_name=postgresql-database-plugin \
    connection_url="postgresql://{{username}}:{{password}}@${DB_HOST}:5432/${DB_NAME}?sslmode=${DB_SSLMODE}" \
    username=openbao_admin password="$OPENBAO_DB_ADMIN_PASSWORD" \
    password_authentication=scram-sha-256 \
    allowed_roles=app,migrator verify_connection=true >/dev/null
  log "connexion database/config/formation écrite"
  if [ "$ROTATE_DB_ROOT" = true ]; then
    bao write -f database/rotate-root/formation >/dev/null
    log "mot de passe openbao_admin tourné : connu d'OpenBao seul"
  fi
fi

# Révocation : les utilisateurs dynamiques ne possèdent aucun objet ni droit direct (appartenance à un groupe
# uniquement) → on coupe leurs sessions ouvertes (un identifiant révoqué ne garde pas de connexion vivante), puis
# on supprime le rôle.
REVOKE_SQL="SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE usename = '{{name}}'; DROP ROLE IF EXISTS \"{{name}}\";"
bao write database/roles/app db_name=formation \
  creation_statements="CREATE ROLE \"{{name}}\" WITH LOGIN PASSWORD '{{password}}' VALID UNTIL '{{expiration}}' IN ROLE formation_rw;" \
  revocation_statements="$REVOKE_SQL" \
  default_ttl=1h max_ttl=168h >/dev/null
bao write database/roles/migrator db_name=formation \
  creation_statements="CREATE ROLE \"{{name}}\" WITH LOGIN PASSWORD '{{password}}' VALID UNTIL '{{expiration}}' IN ROLE formation_owner;" \
  revocation_statements="$REVOKE_SQL" \
  default_ttl=15m max_ttl=1h >/dev/null
log "rôles database app (1 h / 7 j) et migrator (15 min / 1 h) à jour"

# --- Politiques --------------------------------------------------------------------
for p in "$POLICY_DIR"/*.hcl; do
  bao policy write "$(basename "$p" .hcl)" "$p" >/dev/null
done
log "politiques à jour : $(cd "$POLICY_DIR" && ls ./*.hcl | sed 's|./||; s|\.hcl||' | tr '\n' ' ')"

# --- AppRole de l'API -----------------------------------------------------------------
enabled auth approle || { bao auth enable approle >/dev/null; log "auth approle activé"; }
# Token PÉRIODIQUE (1 h, renouvelé par l'API tant qu'elle tourne) : les baux DB dynamiques sont rattachés au token
# qui les a créés ; un token_max_ttl les révoquerait (sessions coupées) avant leur propre max_ttl de 7 jours.
bao write auth/approle/role/api \
  token_policies=api token_type=service token_period=1h token_ttl=0 token_max_ttl=0 \
  token_bound_cidrs="$BOUND_CIDRS" secret_id_bound_cidrs="$BOUND_CIDRS" \
  secret_id_ttl="$SECRET_ID_TTL" secret_id_num_uses=0 >/dev/null
if [ -n "${APPROLE_ROLE_ID:-}" ]; then
  bao write auth/approle/role/api/role-id role_id="$APPROLE_ROLE_ID" >/dev/null
fi
log "AppRole api à jour (token périodique 1 h, CIDR $BOUND_CIDRS, secret_id $SECRET_ID_TTL)"

if [ -n "${CREDS_OUT_DIR:-}" ]; then
  mkdir -p "$CREDS_OUT_DIR"
  umask 077
  bao read -field=role_id auth/approle/role/api/role-id > "$CREDS_OUT_DIR/role-id"
  bao write -f -field=secret_id auth/approle/role/api/secret-id > "$CREDS_OUT_DIR/secret-id"
  chmod "${CREDS_MODE:-0400}" "$CREDS_OUT_DIR/role-id" "$CREDS_OUT_DIR/secret-id"
  log "identifiants AppRole écrits dans $CREDS_OUT_DIR"
fi

log "configuration terminée"
