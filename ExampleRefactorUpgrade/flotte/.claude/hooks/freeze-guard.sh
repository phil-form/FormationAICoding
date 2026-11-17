#!/usr/bin/env bash
# Hook PreToolUse de session (settings.json) : quand l'état d'orchestration est gelé
# (.claude/state/migration/state.json : frozen=true, posé par 'state.py freeze on' pendant une marche),
# interdit toute modification de tests/ (tests, fixtures, scénarios SQLite, golden masters), quel que soit l'agent.
# Le gel n'est levé que par l'utilisateur (state.py freeze off), après décision consignée dans
# docs/migration/accepted-changes.md. Référence : .claude/reference/conventions.md § 6.
set -euo pipefail
project="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
state="$project/.claude/state/migration/state.json"
input="$(cat)"
[[ -f "$state" ]] || exit 0
[[ "$(jq -r '.frozen // false' "$state")" == "true" ]] || exit 0

tool="$(jq -r '.tool_name // ""' <<<"$input")"
deny() { echo "⛔ [freeze-guard] G-FROZEN : $* — tests/ est gelé pendant la marche. Levée du gel : décision de l'utilisateur (state.py freeze off)." >&2; exit 2; }

frozen_path() {
  local p="$1"
  [[ -z "$p" ]] && return 1
  [[ "$p" != /* ]] && p="$project/$p"
  p="$(realpath -m "$p")"
  [[ "$p" == "$project/tests" || "$p" == "$project/tests/"* ]]
}

case "$tool" in
  Edit|Write)
    p="$(jq -r '.tool_input.file_path // ""' <<<"$input")"
    frozen_path "$p" && deny "écriture de '${p#"$project/"}'" ;;
  NotebookEdit)
    p="$(jq -r '.tool_input.notebook_path // ""' <<<"$input")"
    frozen_path "$p" && deny "écriture de '${p#"$project/"}'" ;;
  Bash)
    cmd="$(jq -r '.tool_input.command // ""' <<<"$input")"
    if [[ "$cmd" =~ state\.py[[:space:]]+freeze[[:space:]]+off ]]; then
      # Levée normale en fin de marche : suite verte et aucune alerte ouverte. Sinon, décision de l'utilisateur
      # (qui lance lui-même '! .claude/bin/state.py freeze off', hors hooks).
      summary="$project/.claude/state/migration/test-results/summary.json"
      alerts="$(jq -r '(.alerts // []) | length' "$state")"
      bad="$( [[ -f "$summary" ]] && jq -r '(.totals.failed // 1) + (.totals.error // 0)' "$summary" || echo 1)"
      [[ "$alerts" == 0 && "$bad" == 0 ]] \
        || deny "levée du gel refusée (alertes ouvertes : $alerts, tests en échec : $bad) — décision de l'utilisateur ('! .claude/bin/state.py freeze off')"
      exit 0
    fi
    [[ "$cmd" =~ seed\.sh.*--rebuild ]] && deny "régénération des scénarios"
    [[ "$cmd" =~ golden-master.*--record ]] && deny "réenregistrement des golden masters"
    re_tests='(^|[[:space:];&|(/])tests(/|[[:space:]]|$)'
    re_write='(^|[[:space:];&|(])(rm|mv|cp|tee|truncate|sed[[:space:]]+-i|perl[[:space:]]+-pi|git[[:space:]]+(checkout|restore|rm|mv|stash|reset|clean)|rector|phpcbf)([[:space:]]|$)|[>]'
    if [[ "$cmd" =~ $re_tests ]] && [[ "$cmd" =~ $re_write ]]; then
      deny "commande qui modifierait tests/"
    fi
    ;;
esac
exit 0
