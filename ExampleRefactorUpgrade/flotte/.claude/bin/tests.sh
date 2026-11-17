#!/usr/bin/env bash
# tests.sh [--suite http|cli|db|edge|diff|unit|all] [--target app|ref] [--filter <motif>] [--group BH-xxx]
#          [--coverage] [--record-golden]
# Suites boîte noire dans le conteneur tests (PHPUnit 11) ; suite unit dans le conteneur app ou app-ref
# (PHPUnit de l'image, version selon PHP). --target ref : exécute contre la référence step-00
# (TARGET=ref dans le conteneur tests : APP_URL et la console pointent sur app-ref ; la suite diff l'ignore).
# --record-golden : GOLDEN_RECORD=1 (réservé à /golden-master --record, refusé si l'état est gelé).
# JUnit : .claude/state/migration/test-results/junit-<suite>.xml (monté en /results dans tests), puis résumé.
# Progression en direct : PHPUnit en --teamcity (8.5 → 11) → .claude/bin/test-progress.py → progress.json
#   (lu par la status line, ligne 🧪) + barre dans le terminal.
# --coverage : active la collecte PCOV côté app (prepend docker/app/coverage-prepend.php) le temps du run ;
#   données brutes dans .claude/state/migration/coverage/raw/ (fusionnées en clover.xml par /coverage).
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/_common.sh"
require_compose
suite=all; extra=(); coverage=0; target=app; golden=0
while (($#)); do
  case "$1" in
    --suite) suite="$2"; shift 2 ;;
    --filter) extra+=(--filter "$2"); shift 2 ;;
    --group) extra+=(--group "$2"); shift 2 ;;
    --coverage) coverage=1; shift ;;
    --target) target="$2"; shift 2 ;;
    --record-golden) golden=1; shift ;;
    *) die "Option inconnue : $1" ;;
  esac
done
[[ "$target" == app || "$target" == ref ]] || die "Cible inconnue : $target (app|ref)"
if ((golden)) && is_frozen; then die "État gelé : enregistrement des golden masters interdit (G-FROZEN)." 4; fi
if ((coverage)) && [[ "$target" == ref ]]; then die "La couverture se mesure sur app, pas sur la référence."; fi
results="$STATE/test-results"; mkdir -p "$results"
envs=(-e "TARGET=$target" -e "GOLDEN_RECORD=$golden")
# Résultats séparés par cible : junit-<suite>.xml (app) / junit-<suite>.ref.xml (app-ref), lus par test-tracking.py
sfx=""; [[ "$target" == ref ]] && sfx=".ref"
case "$suite" in
  all) suites=(http cli db edge diff unit) ;;
  http|cli|db|edge|diff|unit) suites=("$suite") ;;
  *) die "Suite inconnue : $suite" ;;
esac
if ((coverage)); then
  mkdir -p "$STATE/coverage"; rm -rf "$STATE/coverage/raw"; mkdir -p "$STATE/coverage/raw"
  touch "$STATE/coverage/enabled"
  trap 'rm -f "$STATE/coverage/enabled"' EXIT
fi
status=0
progress="$ROOT/.claude/bin/test-progress.py"
"$progress" --start --scope "$suite" --target "$target"
idx=0
for s in "${suites[@]}"; do
  idx=$((idx + 1))
  rm -f "$results/junit-$s$sfx.xml"
  info "Suite $s…"
  if [[ "$s" == unit ]]; then
    [[ -f "$ROOT/tests/unit/phpunit.xml.dist" ]] || { info "tests/unit absent, suite ignorée"; continue; }
    svc=app; [[ "$target" == ref ]] && svc=app-ref
    "${COMPOSE[@]}" exec -T "$svc" phpunit -c tests/unit/phpunit.xml.dist --teamcity \
      --log-junit "/results/junit-unit$sfx.xml" "${extra[@]}" \
      | "$progress" --suite unit --index "$idx" --count "${#suites[@]}" || status=1
  else
    dir="$ROOT/tests/src/${s^}"   # http → Http, cli → Cli…
    if ! compgen -G "$dir/*Test.php" >/dev/null; then info "suite $s : aucun test (tests/src/${s^}/), ignorée"; continue; fi
    "${COMPOSE[@]}" run --rm -T "${envs[@]}" tests vendor/bin/phpunit --testsuite "$s" --teamcity \
      --log-junit "/results/junit-$s$sfx.xml" "${extra[@]}" \
      | "$progress" --suite "$s" --index "$idx" --count "${#suites[@]}" || status=1
  fi
done
"$progress" --finish "$status"
# Une suite sans test laisse un JUnit vide : on le retire pour ne pas fausser le résumé.
find "$results" -maxdepth 1 -name 'junit-*.xml' -empty -delete
# Résumé limité aux suites de CE run (pas de résultats périmés d'un run précédent)
files=()
for s in "${suites[@]}"; do [[ -s "$results/junit-$s$sfx.xml" ]] && files+=("$results/junit-$s$sfx.xml"); done
((${#files[@]})) && "$ROOT/.claude/bin/junit-summary.py" "${files[@]}" --write "$results/summary$sfx.json"
# Suivi d'avancement dans le document de fiches (tmp/tests-application.md) ; n'échoue jamais le run.
"$ROOT/.claude/bin/test-tracking.py" || true
exit $status
