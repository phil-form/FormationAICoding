#!/usr/bin/env bash
# Snapshot Raft d'OpenBao, chiffré avec age, avec rétention. À lancer par un timer systemd (voir docs/ops/OPENBAO.md).
#   docker/prod/openbao/snapshot.sh
#
# Le snapshot est déjà chiffré par la barrière OpenBao (inutilisable sans les parts Shamir) ; age ajoute une
# seconde couche pour le stockage hors site. Clé privée age : 1Password (jamais sur le serveur).
#
# Variables (.env) : OPENBAO_SNAPSHOT_TOKEN_FILE, OPENBAO_BACKUP_DIR, OPENBAO_BACKUP_RETENTION (défaut 30),
#                    AGE_RECIPIENT (clé publique age, « age1… »), AGE_BIN (défaut age)
set -euo pipefail
cd "$(dirname "$0")/../../.."
set -a; source .env; set +a

token_file="${OPENBAO_SNAPSHOT_TOKEN_FILE:-./secrets/openbao-snapshot.token}"
backup_dir="${OPENBAO_BACKUP_DIR:-./backups/openbao}"
retention="${OPENBAO_BACKUP_RETENTION:-30}"
: "${AGE_RECIPIENT:?AGE_RECIPIENT (clé publique age) requis}"
age_bin=(${AGE_BIN:-age})

[[ -r "$token_file" ]] || { echo "Token de snapshot illisible : $token_file" >&2; exit 1; }
install -d -m 0700 "$backup_dir"
out="$backup_dir/openbao-$(date -u +%Y%m%dT%H%M%SZ).snap.age"
tmp="$(mktemp "$backup_dir/.snapshot.XXXXXX")"
trap 'rm -f "$tmp"' EXIT

# Le snapshot transite par stdout (le /tmp du conteneur est un tmpfs, invisible pour docker cp).
docker compose --profile prod exec -T -e BAO_TOKEN="$(cat "$token_file")" openbao-prod \
  sh -c 'f=$(mktemp) && bao operator raft snapshot save "$f" >/dev/null && cat "$f"; rc=$?; rm -f "$f"; exit $rc' > "$tmp"
[[ -s "$tmp" ]] || { echo "Snapshot vide" >&2; exit 1; }

"${age_bin[@]}" -r "$AGE_RECIPIENT" -o "$out" "$tmp"
chmod 0600 "$out"

# Renouvelle le token périodique (sinon il expire après 32 jours sans snapshot).
docker compose --profile prod exec -T -e BAO_TOKEN="$(cat "$token_file")" openbao-prod bao token renew >/dev/null

# Rétention
ls -1t "$backup_dir"/openbao-*.snap.age 2>/dev/null | tail -n +$((retention + 1)) | xargs -r rm -f
echo "Snapshot : $out ($(du -h "$out" | cut -f1)) — $(ls -1 "$backup_dir"/openbao-*.snap.age | wc -l) conservé(s)"
