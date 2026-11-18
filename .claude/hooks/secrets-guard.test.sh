#!/usr/bin/env bash
# Tests de .claude/hooks/secrets-guard.sh (dépôt parent) : refus (2) / accord (0) d'appels d'outils simulés.
# Le script est une copie de ExampleRefactorUpgrade/flotte/.claude/hooks/secrets-guard.sh (suite complète là-bas) ;
# ce fichier vérifie la copie et les chemins propres au projet Java.
set -uo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project="$(cd "$here/../.." && pwd)"
guard="$here/secrets-guard.sh"
pass=0; fail=0

run() {
  local expect="$1" tool="$2" ti="$3" out code
  out="$(jq -nc --arg t "$tool" --argjson i "$ti" --arg c "$project" '{tool_name:$t, tool_input:$i, cwd:$c}' | "$guard" 2>&1)"; code=$?
  if [[ "$code" == "$expect" ]]; then pass=$((pass+1)); else
    fail=$((fail+1)); printf '✘ attendu %s, obtenu %s : %s %s\n   %s\n' "$expect" "$code" "$tool" "$ti" "$out"; fi
}
cmd() { run "$1" Bash "$(jq -nc --arg c "$2" '{command:$c}')"; }
read_() { run "$1" Read "$(jq -nc --arg p "$2" '{file_path:$p}')"; }

# Refus
read_ 2 .env
read_ 2 .env.dev
read_ 2 secrets/openbao-snapshot.token
read_ 2 secrets/openbao-tls/tls.key
read_ 2 secrets/test-prod/haproxy/localhost.pem
read_ 2 certs/tls.key
read_ 2 init_db/dump.sql
read_ 2 .worktree-backup/test-wt/.env
read_ 2 ExampleRefactorUpgrade/flotte/app/config/parameters.yml
run 2 Write '{"file_path":".env","content":"X=1"}'
run 2 Grep '{"pattern":"PASSWORD","path":".env.dev"}'
cmd 2 'cat .env'
cmd 2 'cat secrets/openbao-snapshot.token'
cmd 2 'grep -rn password .'
cmd 2 'env'
cmd 2 'docker compose -f docker-compose.prod.yml config'
cmd 2 'docker inspect formation-prod-api-prod-1'
cmd 2 'docker exec formation-openbao-1 bao kv get secret/api'
cmd 2 'docker compose exec openbao bao read database/creds/api'
cmd 2 'openssl x509 -in certs/tls.key -text'

# Accords
read_ 0 .env.example
read_ 0 .env.dev.example
read_ 0 docker-compose.prod.yml
read_ 0 docs/adr/0001-gestion-des-secrets.md
read_ 0 src/main/java/be/bstorm/formation/controller/AuthController.java
read_ 0 src/main/resources/application.yml
run 0 Grep '{"pattern":"OpenBao","path":"docs"}'
cmd 0 './mvnw verify'
cmd 0 './start.sh --test'
cmd 0 'git grep -n OpenBao -- docs'
cmd 0 'grep -rn password src'
cmd 0 "docker inspect --format '{{.State.Health.Status}}' formation-db-1"
cmd 0 'docker compose logs api-dev --tail 100'
cmd 0 'docker/test-prod/check.sh'

echo "secrets-guard (parent) : $pass réussi(s), $fail échec(s)"
((fail == 0))
