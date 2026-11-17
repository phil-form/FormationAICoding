#!/usr/bin/env bash
# Garde-fou PreToolUse des agents de migration (déclaré dans le frontmatter de chaque agent).
#   guard.sh carto         -> cartographer         : lit tout, écrit docs/migration/inventory/
#   guard.sh characterize  -> characterizer        : lit tout, écrit les fiches BH et le plan de tests
#   guard.sh tests         -> test-writer          : AUCUN accès au code de l'application ; écrit tests/
#   guard.sh migrate       -> migrator             : n'écrit jamais tests/, docs/, .claude/, tmp/, .ref/
#   guard.sh deprec        -> deprecation-analyst  : lit tout, écrit ses rapports de dépréciations/compatibilité
#   guard.sh security      -> security-auditor     : lit tout, écrit docs/security/
#   guard.sh auditor       -> auditor              : lit tout, écrit arbitrages, rapports et retours triés
#   guard.sh logs          -> log-analyst          : lecture seule, Bash limité à la lecture de logs
# Bloque via exit 2 (message stderr renvoyé à l'agent). Garde-fou, pas sandbox.
# Référence : .claude/reference/conventions.md (§ 4 scripts, § 8 agents).
set -euo pipefail

mode="${1:?usage: guard.sh carto|characterize|tests|migrate|deprec|security|auditor|logs}"
project="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
input="$(cat)"

tool="$(jq -r '.tool_name // ""' <<<"$input")"
field() { jq -r --arg k "$1" '.tool_input[$k] // ""' <<<"$input"; }
deny() { echo "⛔ [$mode-guard] $*" >&2; exit 2; }

rel() {
  local p="$1"
  [[ -z "$p" ]] && return 0
  [[ "$p" != /* ]] && p="$project/$p"
  p="$(realpath -m "$p")"
  if [[ "$p" == "$project" ]]; then echo "."
  elif [[ "$p" == "$project/"* ]]; then echo "${p#"$project/"}"
  else echo "$p"; fi
}
in_list() {
  local p="$1"; shift
  for pre in "$@"; do [[ "$p" == "${pre%/}" || "$p" == "${pre%/}/"* ]] && return 0; done
  return 1
}
covers() {
  local p="$1"; shift
  [[ "$p" == "." ]] && return 0
  for pre in "$@"; do [[ "${pre%/}" == "$p/"* ]] && return 0; done
  return 1
}

# Secrets locaux : jamais lisibles (les .env commités d'une app Flex restent lisibles).
SECRETS=(".env.local" ".env.dev.local" ".env.test.local" ".env.prod.local" "app/config/parameters.yml")
# Code de l'application (y compris la référence et les dépendances) : invisible pour test-writer.
APP_CODE=("src" "app" "web" "public" "config" "templates" "bin" "translations" "vendor" ".ref" "composer.json" "composer.lock" "symfony.lock" "var")

READ_DENY=()
case "$mode" in
  carto)
    WRITE_ALLOW=("docs/migration/inventory/" ".claude/state/migration/inventory/") ;;
  characterize)
    WRITE_ALLOW=("docs/migration/behaviors/" "docs/migration/test-plan.md" ".claude/state/migration/characterize/") ;;
  tests)
    READ_ALLOW=("tests/" "docs/migration/behaviors/" "docs/migration/inventory/" "docs/migration/test-plan.md"
                "docs/migration/accepted-changes.md" "README.md" "CLAUDE.md" "tmp/" ".claude/reference/"
                ".claude/skills/" ".claude/state/migration/feedback-tests.md" ".claude/state/migration/test-results/"
                ".claude/state/migration/state.json")
    WRITE_ALLOW=("tests/")
    WRITE_GLOB=("tmp/tests-*.md") ;;   # documents de fiches (conventions § 5.6)
  migrate)
    READ_DENY=(".claude/state/migration/feedback-tests.md" ".claude/state/migration/audit/")
    WRITE_DENY=("tests" "docs" ".claude" "tmp" ".ref" "README.md" "CLAUDE.md") ;;
  deprec)
    WRITE_ALLOW=("docs/migration/deprecations/" "docs/migration/deprecations.json" "docs/migration/compat/"
                 ".claude/state/migration/deprecations/") ;;
  security)
    WRITE_ALLOW=("docs/security/" ".claude/state/migration/security/") ;;
  auditor)
    WRITE_ALLOW=("docs/migration/accepted-changes.md" "docs/migration/steps/" "docs/migration/audit/"
                 ".claude/state/migration/feedback-code.md" ".claude/state/migration/feedback-tests.md"
                 ".claude/state/migration/audit/" ".claude/state/migration/triage/" ".claude/state/migration/coverage/"
                 ".claude/state/migration/mutation/") ;;
  logs)
    WRITE_ALLOW=("<aucune>") ;;
  *) deny "mode inconnu" ;;
esac

check_read() {
  local p; p="$(rel "$1")"
  [[ -z "$p" && "$mode" == tests ]] && deny "$tool sans chemin explicite interdit. Précise un chemin (ex. tests/, docs/migration/behaviors/)."
  [[ -z "$p" ]] && p="."
  [[ "$p" != "." ]] && in_list "$p" "${SECRETS[@]}" && deny "Lecture de '$p' interdite (configuration locale / secrets)."
  if [[ -n "${READ_ALLOW+x}" ]]; then
    in_list "$p" "${READ_ALLOW[@]}" || deny "Lecture de '$p' interdite. Zones autorisées : ${READ_ALLOW[*]}"
  elif ((${#READ_DENY[@]})); then
    in_list "$p" "${READ_DENY[@]}" && deny "Accès à '$p' interdit pour cet agent."
    covers "$p" "${READ_DENY[@]}" && [[ "$tool" != Read ]] \
      && deny "Recherche sur '$p' interdite : elle couvrirait une zone interdite. Cible un sous-dossier."
  fi
  return 0
}

check_write() {
  local p; p="$(rel "$1")"
  [[ -z "$p" ]] && deny "Écriture sans chemin."
  [[ "$p" == /* ]] && deny "Écriture hors du projet interdite ('$p')."
  if [[ -n "${WRITE_GLOB+x}" ]]; then
    local g; for g in "${WRITE_GLOB[@]}"; do [[ "$p" == $g && "$p" != */*/* ]] && return 0; done
  fi
  if [[ -n "${WRITE_ALLOW+x}" ]]; then
    in_list "$p" "${WRITE_ALLOW[@]}" || deny "Écriture de '$p' interdite. Zones autorisées : ${WRITE_ALLOW[*]} ${WRITE_GLOB[*]:-}"
  else
    in_list "$p" "${WRITE_DENY[@]}" && deny "Écriture de '$p' interdite pour cet agent (zones gelées : ${WRITE_DENY[*]})."
  fi
  return 0
}

# --- Bash ---------------------------------------------------------------------
meta_strict='[;&|`$<>()]'
meta_pipe='[;&`$<>()]'
SCRIPT_RE='^[[:space:]]*\.claude/(bin/[a-z0-9-]+\.(sh|py)|skills/[a-z0-9-]+/scripts/[a-z0-9-]+\.(sh|py))([[:space:]]|$)'
GIT_RE='^[[:space:]]*git[[:space:]]+(diff|log|show|status|ls-files|rev-parse|describe)([[:space:]]|$)'

check_bash() {
  local cmd; cmd="$(field command)"
  if [[ "$mode" == logs ]]; then
    [[ "$cmd" =~ $meta_pipe ]] && deny "Seuls les pipes '|' sont autorisés (pas de ; & redirection ni substitution)."
    local seg
    IFS='|' read -ra segs <<<"$cmd"
    for seg in "${segs[@]}"; do
      seg="${seg#"${seg%%[![:space:]]*}"}"
      [[ "$seg" =~ ^(docker[[:space:]]+compose([[:space:]]+(-p|-f|--profile)[[:space:]]+[[:alnum:]_./-]+)*[[:space:]]+(logs|ps)([[:space:]]|$)|docker[[:space:]]+(logs|ps|inspect)([[:space:]]|$)|grep[[:space:]]|zgrep[[:space:]]|head([[:space:]]|$)|tail([[:space:]]|$)|wc([[:space:]]|$)|sort([[:space:]]|$)|uniq([[:space:]]|$)|cut[[:space:]]|jq[[:space:]]|ls([[:space:]]|$)|zcat[[:space:]]) ]] \
        || deny "Segment '$seg' non autorisé (docker compose logs|ps, docker logs|ps|inspect, grep, head, tail, wc, sort, uniq, cut, jq, ls, zcat)."
      [[ "$seg" =~ \.env\.[a-z]*\.?local|parameters\.yml([[:space:]]|$) ]] && deny "Accès aux secrets locaux interdit."
    done
    return 0
  fi

  [[ "$cmd" =~ $meta_strict ]] && deny "Enchaînements, pipes, redirections et substitutions interdits : une commande à la fois."
  [[ "$cmd" == *..* ]] && deny "Chemins remontants ('..') interdits."

  # Réservé à l'utilisateur / aux orchestrateurs de la session principale.
  [[ "$cmd" =~ state\.py[[:space:]]+(freeze|init|set|alerts-clear) ]] && deny "Modification de l'état d'orchestration réservée à la session principale (seuls 'state.py get|event|alert' sont permis)."
  [[ "$cmd" =~ env\.sh[[:space:]]+ref-sync ]] && deny "La régénération de la référence (.ref/) est réservée à /baseline."
  [[ "$cmd" =~ seed\.sh.*--rebuild ]] && [[ "$mode" != tests ]] && deny "Régénération des scénarios réservée à test-writer."

  case "$mode" in
    tests)
      # Boîte noire : HTTP, console limitée aux commandes métier, chargement des données, exécution des tests.
      if [[ "$cmd" =~ ^[[:space:]]*\.claude/bin/(http\.sh|seed\.sh|tests\.sh|junit-summary\.py|test-tracking\.py)([[:space:]]|$) ]]; then return 0; fi
      if [[ "$cmd" =~ ^[[:space:]]*\.claude/bin/console\.sh([[:space:]]+--ref)?[[:space:]]+(fleet:[a-z:-]+|list|help)([[:space:]]|$) ]]; then return 0; fi
      deny "Autorisé : .claude/bin/http.sh, seed.sh, tests.sh, junit-summary.py, test-tracking.py, console.sh [--ref] fleet:*|list|help."
      ;;
    migrate)
      [[ "$cmd" =~ $SCRIPT_RE || "$cmd" =~ $GIT_RE ]] || deny "Autorisé : scripts .claude/bin/* et .claude/skills/*/scripts/*, git diff|log|show|status|ls-files|rev-parse."
      if [[ "$cmd" =~ tools\.sh[[:space:]]+(rector|phpcbf) ]] && [[ "$cmd" =~ (^|[[:space:]/])tests(/|[[:space:]]|$) ]]; then
        deny "Rector / phpcbf ne doivent jamais viser tests/."
      fi
      [[ "$cmd" =~ tools\.sh[[:space:]]+rector ]] && deny "Rector passe par .claude/bin/rector.sh <step-NN> (configurations rector/step-NN.php, sûreté bug pour bug)."
      ;;
    carto|characterize|deprec|security|auditor)
      [[ "$cmd" =~ $SCRIPT_RE || "$cmd" =~ $GIT_RE ]] || deny "Autorisé : scripts .claude/bin/* et .claude/skills/*/scripts/*, git diff|log|show|status|ls-files|rev-parse."
      [[ "$cmd" =~ tools\.sh[[:space:]]+(rector|phpcbf) ]] && [[ "$cmd" != *--dry-run* ]] \
        && deny "Cet agent est en lecture seule : rector uniquement avec --dry-run, phpcbf interdit."
      [[ "$cmd" =~ rector\.sh ]] && [[ "$cmd" =~ --apply ]] \
        && deny "Cet agent est en lecture seule : .claude/bin/rector.sh sans --apply (simulation)."
      [[ "$cmd" =~ composer\.sh ]] && [[ "$cmd" =~ (^|[[:space:]])(install|update|require|remove|bump)([[:space:]]|$) ]] \
        && deny "Cet agent ne modifie pas les dépendances."
      ;;
  esac
  return 0
}

case "$tool" in
  Read)          check_read "$(field file_path)" ;;
  Edit|Write)    check_write "$(field file_path)" ;;
  NotebookEdit)  check_write "$(field notebook_path)" ;;
  Grep)
    check_read "$(field path)"
    if [[ "$mode" == tests ]]; then g="$(field glob)"; [[ "$g" == *..* || "$g" == /* ]] && deny "Filtre glob '$g' interdit."; fi
    ;;
  Glob)
    check_read "$(field path)"
    pat="$(field pattern)"
    [[ "$pat" == /* || "$pat" == *..* ]] && deny "Motif Glob absolu ou remontant ('$pat') interdit."
    ;;
  Bash)          check_bash ;;
  WebFetch|WebSearch)
    [[ "$mode" == security || "$mode" == deprec || "$mode" == migrate ]] || deny "$tool non autorisé pour cet agent." ;;
  *)             deny "Outil '$tool' non autorisé pour cet agent." ;;
esac
exit 0
