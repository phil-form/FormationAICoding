#!/usr/bin/env bash
# Recette de la phase 1 (ADR 0001) sur la stack dev : droits OpenBao (smoke-test.sh) + droits PostgreSQL des
# utilisateurs dynamiques + révocation. Prérequis : ./start.sh (ou db, openbao-dev, db-roles, openbao-init démarrés).
#   docker/openbao/check-phase1.sh
set -euo pipefail
cd "$(dirname "$0")/../.."
set -a; source .env; set +a

net="formation_default"
work="$(mktemp -d)"; trap 'rm -rf "$work"' EXIT
chmod 777 "$work"

echo "== OpenBao : droits de l'AppRole api =="
docker run --rm --network "$net" -e BAO_ADDR=http://openbao:8200 -e DB_CREDS_OUT=/work/db.env \
  -v "formation_openbao-dev-creds:/creds:ro" -v "$PWD/docker/openbao:/scripts:ro" -v "$work:/work" \
  --entrypoint sh openbao/openbao:2.7.2@sha256:5046911075e137e154ce49ab051502f4ec7cf367d484ae4a4dce665d9b90561f /scripts/smoke-test.sh
source "$work/db.env"

pass=0 fail=0
ok() { pass=$((pass + 1)); echo "  ✔ $*"; }
ko() { fail=$((fail + 1)); echo "  ✘ $*"; }
sql() { # sql <user> <password> <requête> → 0 si succès
  docker run --rm --network "$net" -e PGPASSWORD="$2" postgres:18.7-alpine@sha256:b6972e1115a4ff8eba5d7f7d19184926d1e16d7d1136ae1e1a7ebf286da0f015 \
    psql -h db -U "$1" -d "$DB_NAME" -v ON_ERROR_STOP=1 -qtAc "$3" 2>&1
}
expect_ok()   { if out=$(sql "$1" "$2" "$4"); then ok "$3"; else ko "$3 → $out"; fi; }
expect_fail() { if out=$(sql "$1" "$2" "$4"); then ko "$3 (aurait dû échouer)"; else ok "$3 (refusé : $(echo "$out" | grep -oE 'ERROR: .*' | head -1 | cut -c8-70))"; fi; }

echo
echo "== PostgreSQL : utilisateur migrator ($MIG_username) =="
expect_ok   "$MIG_username" "$MIG_password" "SET ROLE formation_owner + CREATE TABLE" \
  "SET ROLE formation_owner; CREATE TABLE phase1_probe(id int primary key, v text);"
owner=$(sql "$MIG_username" "$MIG_password" "select tableowner from pg_tables where tablename='phase1_probe'")
[[ "$owner" == formation_owner ]] && ok "la table appartient à formation_owner (pas à l'utilisateur temporaire)" || ko "propriétaire : $owner"

echo
echo "== PostgreSQL : utilisateur app ($APP_username) =="
expect_ok   "$APP_username" "$APP_password" "INSERT / SELECT / UPDATE / DELETE" \
  "INSERT INTO phase1_probe VALUES (1,'a'); UPDATE phase1_probe SET v='b'; SELECT count(*) FROM phase1_probe; DELETE FROM phase1_probe;"
expect_fail "$APP_username" "$APP_password" "CREATE TABLE" "CREATE TABLE evil(id int);"
expect_fail "$APP_username" "$APP_password" "DROP TABLE" "DROP TABLE phase1_probe;"
expect_fail "$APP_username" "$APP_password" "ALTER TABLE" "ALTER TABLE phase1_probe ADD COLUMN x int;"
expect_fail "$APP_username" "$APP_password" "TRUNCATE" "TRUNCATE phase1_probe;"
expect_fail "$APP_username" "$APP_password" "SET ROLE formation_owner" "SET ROLE formation_owner;"
expect_fail "$APP_username" "$APP_password" "CREATE ROLE" "CREATE ROLE evil LOGIN;"

echo
echo "== Révocation d'un bail =="
docker compose exec -T openbao-dev sh -c "BAO_ADDR=http://127.0.0.1:8200 BAO_TOKEN=\$BAO_DEV_ROOT_TOKEN_ID bao lease revoke '$APP_lease_id'" >/dev/null
exists=$(docker compose exec -T db psql -U "$DB_USER" -d "$DB_NAME" -tAc "select count(*) from pg_roles where rolname='$APP_username'")
[[ "$exists" == 0 ]] && ok "utilisateur supprimé de PostgreSQL à la révocation" || ko "utilisateur toujours présent"
expect_fail "$APP_username" "$APP_password" "connexion après révocation" "select 1"

docker compose exec -T db psql -q -U "$DB_USER" -d "$DB_NAME" -c "DROP TABLE IF EXISTS phase1_probe;" >/dev/null

echo
echo "Résultat SQL : $pass OK, $fail KO"
((fail == 0))
