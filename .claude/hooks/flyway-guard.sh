#!/usr/bin/env bash
# Hook PreToolUse projet : protège les migrations Flyway versionnées déjà commitées.
#   - interdit de modifier / écraser / supprimer un V<n>__*.sql présent dans HEAD ;
#   - interdit de créer une migration dont la version est <= à la plus haute version commitée
#     ou qui réutilise une version existante.
# Les migrations répétables (R__*.sql, ex. db/seed) restent modifiables.
set -euo pipefail

project="$(realpath -m "${CLAUDE_PROJECT_DIR:-$PWD}")"
input="$(cat)"
tool="$(jq -r '.tool_name // ""' <<<"$input")"
mig_dir="src/main/resources/db/migration"

deny() { echo "⛔ [flyway-guard] $*" >&2; exit 2; }

git -C "$project" rev-parse --verify -q HEAD >/dev/null || exit 0   # pas encore de commit

committed() { git -C "$project" cat-file -e "HEAD:$1" 2>/dev/null; }

version_of() { # V1_2__x.sql -> 1.2 (format comparable par sort -V)
  local b; b="$(basename "$1")"
  b="${b#V}"; b="${b%%__*}"; echo "${b//_/.}"
}

max_committed() {
  git -C "$project" ls-tree --name-only HEAD "$mig_dir/" 2>/dev/null \
    | grep -E '/V[0-9][0-9_.]*__.*\.sql$' | while read -r f; do version_of "$f"; done | sort -V | tail -1
}

check_file() {
  local p="$1" rp
  [[ -z "$p" ]] && return 0
  [[ "$p" != /* ]] && p="$project/$p"
  rp="$(realpath -m "$p")"; rp="${rp#"$project/"}"
  [[ "$rp" == "$mig_dir/"V*.sql ]] || return 0

  if committed "$rp"; then
    deny "La migration '$rp' est déjà commitée : ne jamais la modifier. Crée une nouvelle migration V<n+1>__<description>.sql."
  fi
  local v max dup
  v="$(version_of "$rp")"; max="$(max_committed)"
  dup="$(git -C "$project" ls-tree --name-only HEAD "$mig_dir/" 2>/dev/null | while read -r f; do
           [[ "$(version_of "$f")" == "$v" ]] && echo "$f"; done; true)"
  [[ -n "$dup" ]] && deny "La version $v existe déjà ($dup)."
  if [[ -n "$max" && "$(printf '%s\n%s\n' "$v" "$max" | sort -V | tail -1)" != "$v" ]]; then
    deny "Version $v <= dernière version commitée ($max) : migration hors ordre. Utilise une version > $max."
  fi
  return 0
}

case "$tool" in
  Edit|Write) check_file "$(jq -r '.tool_input.file_path // ""' <<<"$input")" ;;
  Bash)
    cmd="$(jq -r '.tool_input.command // ""' <<<"$input")"
    if [[ "$cmd" == *"db/migration"* ]] && [[ "$cmd" =~ (^|[[:space:];&|])(rm|mv|sed[[:space:]]+-i|perl[[:space:]]+-[a-z]*i|truncate|tee)([[:space:]]|$) || "$cmd" =~ \>[[:space:]]*[^[:space:]]*db/migration ]]; then
      while read -r f; do
        committed "$f" && [[ "$cmd" == *"$(basename "$f")"* || "$cmd" =~ db/migration/?\*?([[:space:]]|$) ]] \
          && deny "Commande touchant une migration commitée ($f) refusée."
      done < <(git -C "$project" ls-tree --name-only HEAD "$mig_dir/" 2>/dev/null | grep -E '/V[^/]*\.sql$'; true)
    fi
    ;;
esac
exit 0
