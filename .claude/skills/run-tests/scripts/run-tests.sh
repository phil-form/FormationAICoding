#!/usr/bin/env bash
# Lance la suite de tests Maven et suit son avancement.
#   - barre de progression dans le terminal (si TTY), une ligne par classe terminée sinon ;
#   - état courant dans .claude/state/tests.json (lu par la status line Claude Code) ;
#   - log complet dans .claude/state/test-run.log ;
#   - retour anonymisé (AC en échec + message) dans .claude/state/test-feedback.md pour l'agent code-writer.
#
# Usage : run-tests.sh [--unit | --it] [--docker] [-- <args maven supplémentaires>]
#   RUN_TESTS_CMD="<commande>" remplace la commande lancée (démo / debug du parseur).
set -uo pipefail

project="$(git -C "$(dirname "$0")" rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$project"

state_dir=".claude/state"
state="$state_dir/tests.json"
log="$state_dir/test-run.log"
feedback="$state_dir/test-feedback.md"
mkdir -p "$state_dir"

scope="all" docker=0 extra=()
while (($#)); do
  case "$1" in
    --unit) scope="unit" ;;
    --it) scope="it" ;;
    --docker) docker=1 ;;
    --) shift; extra=("$@"); break ;;
    -h|--help) sed -n '2,10p' "$0"; exit 0 ;;
    *) extra+=("$1") ;;
  esac
  shift
done

# --- Total attendu (classes de test) ----------------------------------------
case "$scope" in
  unit) name_re='(Test|Tests)\.java$' ;;
  it)   name_re='IT\.java$' ;;
  *)    name_re='(Test|Tests|IT)\.java$' ;;
esac
total=0
[[ -d src/test/java ]] && total=$(find src/test/java -type f | grep -cE "$name_re")

# --- Commande ---------------------------------------------------------------
if [[ -n "${RUN_TESTS_CMD:-}" ]]; then
  cmd=(bash -c "$RUN_TESTS_CMD")
elif ((docker)); then
  cmd=(docker compose -f docker-compose.yml --profile test run --rm --build api-test)
else
  case "$scope" in
    unit) cmd=(./mvnw -B test) ;;
    it)   cmd=(./mvnw -B verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false) ;;
    *)    cmd=(./mvnw -B verify) ;;
  esac
  cmd+=("${extra[@]}")
fi

# --- Affichage & état -------------------------------------------------------
tty=0; [[ -t 1 ]] && tty=1
G=$'\e[32m' R=$'\e[31m' Y=$'\e[33m' D=$'\e[2m' B=$'\e[1m' N=$'\e[0m'
((tty)) || { G='' R='' Y='' D='' B='' N=''; }

started=$(date +%s)
done_cls=0 tests=0 failed=0 errors=0 skipped=0 current="" status="running"
failures=()        # "nom|type|message"
pending_fail=""    # échec dont on attend la ligne de message

bar() { # bar <pct> <largeur>
  local pct=$1 w=$2 fill
  fill=$(( pct * w / 100 ))
  printf '%*s' "$fill" '' | sed 's/ /█/g'
  printf '%*s' $((w - fill)) '' | sed 's/ /░/g'
}

pct() { ((total > 0)) && echo $(( done_cls * 100 / total > 100 ? 100 : done_cls * 100 / total )) || echo 0; }

eta() {
  local el=$(( $(date +%s) - started ))
  if ((done_cls > 0 && total > done_cls)); then echo $(( el * (total - done_cls) / done_cls )); else echo 0; fi
}

write_state() {
  local passed=$(( tests - failed - errors - skipped ))
  jq -n --arg status "$status" --arg current "$current" --arg scope "$scope" \
    --argjson total "$total" --argjson done "$done_cls" --argjson tests "$tests" \
    --argjson passed "$passed" --argjson failed "$failed" --argjson errors "$errors" \
    --argjson skipped "$skipped" --argjson started "$started" --argjson updated "$(date +%s)" \
    --argjson eta "$(eta)" --argjson pct "$(pct)" \
    '{status:$status, scope:$scope, total_classes:$total, done_classes:$done, pct:$pct,
      tests:$tests, passed:$passed, failed:$failed, errors:$errors, skipped:$skipped,
      current:$current, started_at:$started, updated_at:$updated, eta_s:$eta}' \
    > "$state.tmp" && mv "$state.tmp" "$state"
}

render() {
  local p passed=$(( tests - failed - errors - skipped )) cls="${current##*.}"
  p=$(pct)
  if ((tty)); then
    printf '\r\e[2K%s %3d%% %s(%d/%d classes)%s  %s✔ %d%s  %s✘ %d%s  %s↷ %d%s  ETA %ss  %s%s%s' \
      "$(bar "$p" 30)" "$p" "$D" "$done_cls" "$total" "$N" "$G" "$passed" "$N" "$R" "$((failed + errors))" "$N" \
      "$Y" "$skipped" "$N" "$(eta)" "$D" "${cls:0:40}" "$N"
  fi
}

line_done() { # une ligne par classe terminée (mode non-TTY : lisible par Claude / CI)
  local cls="$1" t="$2" f="$3" e="$4" s="$5" p
  p=$(pct)
  if ((tty)); then
    ((f + e > 0)) && printf '\r\e[2K%s✘ %s%s (%d échec(s))\n' "$R" "${cls##*.}" "$N" "$((f + e))"
  else
    printf '[%3d%%] %d/%d %s — tests:%d ok:%d ko:%d skip:%d\n' "$p" "$done_cls" "$total" "${cls##*.}" \
      "$t" "$((t - f - e - s))" "$((f + e))" "$s"
  fi
}

write_state
echo "${B}▶ ${cmd[*]}${N}  ${D}(log : $log)${N}"

# --- Exécution + parsing ----------------------------------------------------
summary_re='Tests run: ([0-9]+), Failures: ([0-9]+), Errors: ([0-9]+), Skipped: ([0-9]+).* in ([[:alnum:]_.$]+)[[:space:]]*$'
fail3_re='^\[ERROR\] ([[:alnum:]_.$]+)\.([[:alnum:]_$]+)(\(.*\))?[[:space:]]+--? ?Time elapsed.*<<< (FAILURE|ERROR)!'
fail2_re='^\[ERROR\] ([[:alnum:]_$]+)\(([[:alnum:]_.$]+)\)[[:space:]]+Time elapsed.*<<< (FAILURE|ERROR)!'

while IFS= read -r l; do
  printf '%s\n' "$l" >> "$log"
  l="${l%$'\r'}"
  if [[ -n "$pending_fail" ]]; then
    failures+=("$pending_fail|${l:0:300}"); pending_fail=""; continue
  fi
  if [[ "$l" =~ \[INFO\]\ Running\ ([[:alnum:]_.$]+) ]]; then
    current="${BASH_REMATCH[1]}"
  elif [[ "$l" =~ $summary_re ]]; then
    t=${BASH_REMATCH[1]} f=${BASH_REMATCH[2]} e=${BASH_REMATCH[3]} s=${BASH_REMATCH[4]} cls=${BASH_REMATCH[5]}
    ((done_cls++, tests += t, failed += f, errors += e, skipped += s))
    ((done_cls > total)) && total=$done_cls
    line_done "$cls" "$t" "$f" "$e" "$s"
    write_state
  elif [[ "$l" =~ $fail3_re ]]; then
    pending_fail="${BASH_REMATCH[1]##*.}#${BASH_REMATCH[2]}|${BASH_REMATCH[4]}"
  elif [[ "$l" =~ $fail2_re ]]; then
    pending_fail="${BASH_REMATCH[2]##*.}#${BASH_REMATCH[1]}|${BASH_REMATCH[3]}"
  fi
  render
done < <( : > "$log"; "${cmd[@]}" 2>&1; echo "__EXIT__=$?" )

rc=$(grep -oE '^__EXIT__=[0-9]+' "$log" | tail -1 | cut -d= -f2)
sed -i '/^__EXIT__=/d' "$log"
rc=${rc:-1}

if ((rc == 0)); then status="passed"
elif ((failed + errors > 0)); then status="failed"
else status="error"; fi
current=""
write_state
((tty)) && printf '\r\e[2K'

# --- Retour anonymisé pour code-writer --------------------------------------
{
  echo "# Retour de tests — $(date '+%F %T')"
  echo
  echo "Statut : **$status** — $tests tests, $((tests - failed - errors - skipped)) OK, $((failed + errors)) KO, $skipped ignorés."
  echo
  if ((${#failures[@]})); then
    echo "| Critère | Test | Type | Message |"
    echo "|---|---|---|---|"
    for f in "${failures[@]}"; do
      IFS='|' read -r name type msg <<<"$f"
      method="${name#*#}"
      crit="—"
      if [[ "$method" =~ ^(acsec|ac|rg)([0-9]+) ]]; then
        crit="$(tr '[:lower:]' '[:upper:]' <<<"${BASH_REMATCH[1]}")-${BASH_REMATCH[2]}"
        crit="${crit/ACSEC/AC-SEC}"
      fi
      echo "| $crit | \`$method\` | $type | ${msg//|/\\|} |"
    done
  elif [[ "$status" == "error" ]]; then
    echo "Échec avant/hors exécution des tests (compilation, démarrage, Docker). Extrait :"
    echo
    echo '```'
    grep -E '^\[ERROR\]' "$log" | grep -v '/src/test/' | head -20
    echo '```'
  fi
} > "$feedback"

# --- Résumé -----------------------------------------------------------------
passed=$(( tests - failed - errors - skipped ))
dur=$(( $(date +%s) - started ))
case "$status" in
  passed) icon="${G}✔ SUCCÈS${N}" ;;
  failed) icon="${R}✘ ÉCHECS${N}" ;;
  *)      icon="${R}✘ ERREUR (build/infra)${N}" ;;
esac
echo
echo "$icon  $(bar "$(pct)" 30) $(pct)%  —  $done_cls/$total classes, $tests tests en ${dur}s"
echo "  ${G}✔ réussis : $passed${N}   ${R}✘ échecs : $failed   ✘ erreurs : $errors${N}   ${Y}↷ ignorés : $skipped${N}"
if ((${#failures[@]})); then
  echo "  Tests en échec :"
  for f in "${failures[@]}"; do IFS='|' read -r name type msg <<<"$f"; echo "   ${R}•${N} $name ${D}[$type] ${msg:0:120}${N}"; done
fi
[[ "$status" == "error" ]] && { echo "  Dernières erreurs :"; grep -E '^\[ERROR\]' "$log" | tail -10 | sed 's/^/   /'; }
echo "  ${D}Log : $log · Retour anonymisé : $feedback${N}"
exit "$rc"
