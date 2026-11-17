#!/usr/bin/env bash
# Hook Stop (settings.json) : commit automatique après chaque génération de tests.
# À la fin d'une réponse, si des changements non commités existent dans tests/ (tests, fixtures, goldens) ou dans
# les documents de fiches tmp/tests-*.md, et qu'ils n'ont pas déjà été signalés, bloque l'arrêt et demande à Claude
# de lancer /step-commit. Pas de boucle : empreinte des changements mémorisée (un même lot n'est signalé qu'une fois),
# et rien n'est demandé si le hook a déjà relancé ce tour (stop_hook_active) ou si tests/ est gelé (marche en cours).
set -uo pipefail
project="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
state_dir="$project/.claude/state"
input="$(cat)"
[[ "$(jq -r '.stop_hook_active // false' <<<"$input" 2>/dev/null)" == "true" ]] && exit 0
[[ -f "$state_dir/migration/state.json" && "$(jq -r '.frozen // false' "$state_dir/migration/state.json")" == "true" ]] && exit 0

cd "$project" || exit 0
changes="$(git status --porcelain --untracked-files=all -- tests $(ls tmp/tests-*.md 2>/dev/null) 2>/dev/null)"
[[ -z "$changes" ]] && exit 0

fingerprint="$( { echo "$changes"; git diff -- tests tmp 2>/dev/null; } | sha256sum | cut -c1-16)"
mkdir -p "$state_dir"
[[ "$(cat "$state_dir/commit-after-tests.last" 2>/dev/null)" == "$fingerprint" ]] && exit 0
echo "$fingerprint" > "$state_dir/commit-after-tests.last"

files="$(echo "$changes" | awk '{print $2}' | head -15 | tr '\n' ' ')"
jq -n --arg r "Génération de tests terminée avec des changements non commités ($files). Règle du projet (CLAUDE.md, « Commit automatique des tests ») : lance maintenant la skill /step-commit (mode hors marche) pour ces fichiers — un commit dédié aux tests, sans y mêler d'autres changements." \
  '{decision: "block", reason: $r}'
exit 0
