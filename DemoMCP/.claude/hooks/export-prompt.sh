#!/usr/bin/env bash
# Exporte les prompts utilisateur dans prompt.md, à la fin de chaque réponse.
#   capture : (UserPromptSubmit) mémorise le prompt en attente
#   flush   : (Stop) ajoute le prompt en attente à prompt.md
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
state="$root/.claude/state/pending-prompt.json"
out="$root/prompt.md"

case "${1:-}" in
  capture)
    mkdir -p "$(dirname "$state")"
    jq -c '{prompt: .prompt, at: (now | strflocaltime("%Y-%m-%d %H:%M"))}' > "$state"
    ;;
  flush)
    [ -s "$state" ] || exit 0
    [ -f "$out" ] || printf '# Prompts\n' > "$out"
    jq -r '"\n## " + .at + "\n\n" + .prompt + "\n"' "$state" >> "$out"
    rm -f "$state"
    ;;
esac
