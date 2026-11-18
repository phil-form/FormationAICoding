#!/usr/bin/env bash
# Tests de .claude/hooks/secrets-guard.sh : chaque cas envoie un appel d'outil simulé et vérifie refus (2) / accord (0).
# Usage : .claude/hooks/secrets-guard.test.sh
set -uo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project="$(cd "$here/../.." && pwd)"
parent="$(cd "$project/../.." && pwd)"
guard="$here/secrets-guard.sh"
args=(--allow-env .env --allow-env .env.test)   # comme dans .claude/settings.json de flotte
pass=0; fail=0

run() { # attendu outil json-input [cwd]
  local expect="$1" tool="$2" ti="$3" cwd="${4:-$project}" out code
  out="$(jq -nc --arg t "$tool" --argjson i "$ti" --arg c "$cwd" '{tool_name:$t, tool_input:$i, cwd:$c}' | "$guard" "${args[@]}" 2>&1)"; code=$?
  if [[ "$code" == "$expect" ]]; then pass=$((pass+1)); else
    fail=$((fail+1)); printf '✘ attendu %s, obtenu %s : %s %s\n   %s\n' "$expect" "$code" "$tool" "$ti" "$out"; fi
}
cmd() { run "$1" Bash "$(jq -nc --arg c "$2" '{command:$c}')" "${3:-$project}"; }
read_() { run "$1" Read "$(jq -nc --arg p "$2" '{file_path:$p}')" "${3:-$project}"; }

# --- Refus : fichiers -----------------------------------------------------------------------------------------------
read_ 2 "$parent/.env"
read_ 2 "$parent/.env.dev"
read_ 2 ../../.env
read_ 2 .env.local
read_ 2 app/config/parameters.yml
read_ 2 .ref/app/config/parameters.yml
read_ 2 app/cache/prod/appProdProjectContainer.php
read_ 2 "$parent/secrets/openbao-snapshot.token"
read_ 2 "$parent/secrets/openbao-tls/ca.key"
read_ 2 "$parent/certs/tls.key"
read_ 2 "$parent/init_db/dump.sql"
read_ 2 "$parent/.worktree-backup/test-wt/.env"
read_ 2 "~/.ssh/id_ed25519"
read_ 2 "$HOME/.claude/.credentials.json"
read_ 2 "$HOME/.docker/config.json"
read_ 2 "$HOME/.claude/projects/x/abc.jsonl"
run 2 Write '{"file_path":".env","content":"X=1"}' "$parent"
run 2 Edit '{"file_path":"app/config/parameters.yml","old_string":"a","new_string":"b"}'
run 2 Grep '{"pattern":"secret","path":"app/config/parameters.yml"}'
run 2 Grep '{"pattern":"x","path":"../../secrets"}'
run 2 Grep '{"pattern":"x","glob":".env"}'
run 2 Grep '{"pattern":"x","glob":"**/.env*"}'
run 0 Grep '{"pattern":"x","glob":"*.php"}'

# --- Refus : commandes ---------------------------------------------------------------------------------------------
cmd 2 'cat ../../.env'
cmd 2 'head -5 app/config/parameters.yml'
cmd 2 "grep secret $parent/.env.dev"
cmd 2 'cat $HOME/.ssh/id_rsa'
cmd 2 'cp ../../secrets/openbao-tls/tls.key /tmp/x'
cmd 2 'env'
cmd 2 'env | grep APP'
cmd 2 'printenv APP_SECRET'
cmd 2 "bash -c 'env'"
cmd 2 'sh -c "printenv"'
cmd 2 'export -p'
cmd 2 'docker inspect flotte-mig-app'
cmd 2 'docker compose config'
cmd 2 'docker compose -f docker-compose.prod.yml config'
cmd 2 'docker exec flotte-mig-app env'
cmd 2 'docker exec flotte-mig-app cat app/config/parameters.yml'
cmd 2 'cat /proc/1/environ'
cmd 2 'bao kv get secret/api'
cmd 2 'docker exec openbao bao read database/creds/api'
cmd 2 'bao token lookup'
cmd 2 'gh auth token'
cmd 2 'grep -rn secret .'
cmd 2 'grep -R APP_SECRET'
cmd 2 'grep -rn password ../..'
cmd 2 'rg --no-ignore secret'
cmd 2 'tar czf /tmp/s.tgz ../../secrets'
cmd 2 'ls ~/.aws'

# --- Accords --------------------------------------------------------------------------------------------------------
read_ 0 CLAUDE.md
read_ 0 app/config/parameters.yml.dist
read_ 0 "$parent/.env.example"
read_ 0 "$parent/.env.dev.example"
read_ 0 .env          # .env Flex de flotte (commité, sans secret)
read_ 0 .env.test
read_ 0 "$parent/docker-compose.prod.yml"
read_ 0 "$parent/docs/adr/0001-gestion-des-secrets.md"
read_ 0 "$parent/DemoMCP/.venv/lib/python3.13/site-packages/pip/_vendor/certifi/cacert.pem"
read_ 0 "$HOME/.claude/projects/x/memory/MEMORY.md"
run 0 Grep '{"pattern":"secret","path":"app/config"}'
run 0 Grep '{"pattern":"x"}'
run 0 Glob '{"pattern":"**/.env*"}'
cmd 0 'git status --short -- .'
cmd 0 'grep -rn "write-" .claude/skills CLAUDE.md'
cmd 0 'git grep -n APP_SECRET'
cmd 0 'rg -n secret .claude'
cmd 0 '.claude/bin/tests.sh --suite all'
cmd 0 '.claude/bin/env.sh status'
cmd 0 "docker inspect --format '{{.State.Health.Status}}' flotte-mig-app"
cmd 0 'docker compose logs app --tail 50'
cmd 0 'env -i PATH=/usr/bin php -v'
cmd 0 'cat app/config/parameters.yml.dist'
cmd 0 'ls docker/'
cmd 0 "git grep -nE 'compose config|printenv|docker inspect' -- .claude"
cmd 0 'grep -n "env |" CLAUDE.md'

# --- Sans --allow-env (dépôt parent) : le .env du projet est refusé -----------------------------------------------
args=()
read_ 2 .env
read_ 0 .env.example

echo "secrets-guard : $pass réussi(s), $fail échec(s)"
((fail == 0))
