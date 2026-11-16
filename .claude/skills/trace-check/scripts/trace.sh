#!/usr/bin/env bash
# Matrice de traçabilité déterministe : identifiants de l'analyse ↔ méthodes de test ↔ dernier run.
#   trace.sh docs/analyse/features/NNN-slug.md
# Tests de la feature = classes portant @Tag("feature-NNN") ; méthodes nommées acxx_ / acsecxx_ / rgxx_.
# Sortie Markdown sur stdout. Code retour 1 si un AC / AC-SEC n'a aucun test.
set -euo pipefail

analysis="${1:?usage: trace.sh docs/analyse/features/NNN-slug.md}"
[[ -f "$analysis" ]] || { echo "Analyse introuvable : $analysis" >&2; exit 2; }
project="$(git -C "$(dirname "$0")" rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$project"

num="$(basename "$analysis" | grep -oE '^[0-9]+')"
tag="feature-$num"
feedback=".claude/state/test-feedback.md"
state=".claude/state/tests.json"

# Normalise AC-SEC-2 / acsec02 / ac3 / RG-01 → AC-SEC-02 / AC-03 / RG-01
norm() {
  local x="${1^^}"; x="${x//-/}"
  [[ "$x" =~ ^(ACSEC|AC|RG|SEC)([0-9]+)$ ]] || { echo "$1"; return; }
  local p="${BASH_REMATCH[1]}"
  [[ "$p" == ACSEC ]] && p="AC-SEC"
  printf '%s-%02d\n' "$p" "$((10#${BASH_REMATCH[2]}))"
}

# 1. Identifiants définis dans l'analyse (définition en gras : **AC-01**)
mapfile -t ids < <(grep -oE '\*\*(AC-SEC|AC|RG|SEC)-[0-9]+\*\*' "$analysis" | tr -d '*' | while read -r i; do norm "$i"; done | awk '!s[$0]++')

# 2. Fichiers de test de la feature
mapfile -t files < <(grep -rlE "@Tag\(\"$tag\"\)" src/test/java 2>/dev/null || true)

# 3. Méthodes de test → identifiant
declare -A tests_of
orphans=()
for f in "${files[@]}"; do
  cls="$(basename "$f" .java)"
  while read -r m; do
    [[ "$m" =~ ^(acsec|ac|rg)([0-9]+)_ ]] || continue
    id="$(norm "${BASH_REMATCH[1]}${BASH_REMATCH[2]}")"
    tests_of[$id]+="${cls}#${m} "
  done < <(grep -oE 'void[[:space:]]+[a-z][A-Za-z0-9_]*[[:space:]]*\(' "$f" | sed -E 's/void[[:space:]]+//; s/[[:space:]]*\($//')
done
for id in "${!tests_of[@]}"; do
  printf '%s\n' "${ids[@]}" | grep -qx "$id" || orphans+=("$id")
done

# 4. Dernier run : critères en échec
declare -A failed_of
if [[ -f "$feedback" ]]; then
  while IFS='|' read -r _ crit _; do
    crit="$(xargs <<<"$crit")"
    [[ "$crit" =~ ^(AC|RG)(-SEC)?-[0-9]+$ ]] && failed_of[$(norm "$crit")]=1
  done < <(grep -E '^\| (AC|RG)' "$feedback")
fi
run_status="aucun run"
[[ -f "$state" ]] && run_status="$(jq -r '"\(.status) — \(.passed) ok / \(.failed + .errors) ko — \(.updated_at | todate)"' "$state")"

# 5. Rapport
missing=0
echo "# Traçabilité — $(basename "$analysis")"
echo
echo "Tag de test : \`@Tag(\"$tag\")\` · ${#files[@]} classe(s) de test · dernier run : $run_status"
echo
echo "| Identifiant | Tests | Méthodes | Dernier run |"
echo "|---|---|---|---|"
for id in "${ids[@]}"; do
  [[ "$id" == SEC-* ]] && { echo "| $id | — | (exigence, testée via ses AC-SEC) | — |"; continue; }
  t="${tests_of[$id]:-}"
  n=$(wc -w <<<"$t")
  if ((n == 0)); then
    [[ "$id" == AC* ]] && { missing=$((missing + 1)); mark="❌ aucun"; } || mark="— (via AC)"
    echo "| $id | $mark | | — |"
  else
    st="✔"; [[ -n "${failed_of[$id]:-}" ]] && st="✘ échec"
    [[ "$run_status" == "aucun run" ]] && st="—"
    echo "| $id | $n | ${t% } | $st |"
  fi
done
echo
if ((${#orphans[@]})); then
  echo "**Tests orphelins** (identifiant absent de l'analyse) : ${orphans[*]}"
  echo
fi
echo "**Synthèse** : ${#ids[@]} identifiants, $missing critère(s) d'acceptation sans test, ${#orphans[@]} orphelin(s)."
((missing == 0))
