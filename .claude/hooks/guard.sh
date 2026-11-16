#!/usr/bin/env bash
# Garde-fou PreToolUse pour les agents isolés (déclaré dans le frontmatter de chaque agent).
#   guard.sh tests    -> test-writer        : lit l'analyse + src/test, écrit uniquement dans src/test, pas de Bash
#   guard.sh code     -> code-writer        : aucun accès aux tests (sources, classes, rapports, retours non triés)
#   guard.sh review   -> analysis-reviewer  : lit docs/ + src/main, écrit uniquement docs/analyse/reviews/
#   guard.sh auditor  -> auditor            : lit tout, écrit uniquement ses rapports et les retours triés
#   guard.sh logs     -> log-analyst        : lecture seule, Bash limité à la lecture de logs
# Bloque via exit 2 (le message stderr est renvoyé à l'agent).
# C'est un garde-fou, pas un sandbox : il couvre les outils fichiers et restreint Bash à une liste blanche.
set -euo pipefail

mode="${1:?usage: guard.sh tests|code|review|auditor|logs}"
project="$(realpath -m "${CLAUDE_PROJECT_DIR:-$PWD}")"
input="$(cat)"

tool="$(jq -r '.tool_name // ""' <<<"$input")"
field() { jq -r --arg k "$1" '.tool_input[$k] // ""' <<<"$input"; }

deny() { echo "⛔ [$mode-guard] $*" >&2; exit 2; }

# Chemin relatif au projet (absolu si hors projet). Vide => vide.
rel() {
  local p="$1"
  [[ -z "$p" ]] && return 0
  [[ "$p" != /* ]] && p="$project/$p"
  p="$(realpath -m "$p")"
  if [[ "$p" == "$project" ]]; then echo "."
  elif [[ "$p" == "$project/"* ]]; then echo "${p#"$project/"}"
  else echo "$p"; fi
}

# in_list <path> <prefix...> : le chemin est-il égal à / sous un des préfixes ?
in_list() {
  local p="$1"; shift
  for pre in "$@"; do
    [[ "$p" == "${pre%/}" || "$p" == "${pre%/}/"* ]] && return 0
  done
  return 1
}

# covers <path> <prefix...> : le chemin est-il un parent (recherche récursive) d'un des préfixes ?
covers() {
  local p="$1"; shift
  [[ "$p" == "." ]] && return 0
  for pre in "$@"; do [[ "${pre%/}" == "$p/"* ]] && return 0; done
  return 1
}

# --- Règles par mode --------------------------------------------------------

SECRETS=(".env")   # jamais lisible par un agent isolé (.env.example reste lisible)

case "$mode" in
  tests)
    READ_ALLOW=("docs/analyse/" "src/test/" "CLAUDE.md" "pom.xml" "tmp/" ".claude/state/feedback-tests.md")
    WRITE_ALLOW=("src/test/")
    ;;
  code)
    READ_DENY=("src/test" "target/test-classes" "target/generated-test-sources" "target/surefire-reports"
               "target/failsafe-reports" "target/test-reports" "target/pit-reports"
               ".claude/state/test-run.log" ".claude/state/test-feedback.md" ".claude/state/feedback-tests.md"
               ".claude/state/audit")
    WRITE_DENY=("${READ_DENY[@]}" "docs/" "CLAUDE.md" ".claude/")
    ;;
  review)
    READ_ALLOW=("docs/" "CLAUDE.md" "tmp/" "src/main/" "pom.xml")
    WRITE_ALLOW=("docs/analyse/reviews/")
    ;;
  auditor)
    READ_DENY=()
    WRITE_ALLOW=(".claude/state/audit/" ".claude/state/feedback-code.md" ".claude/state/feedback-tests.md"
                 "docs/analyse/reviews/" "docs/audit/")
    ;;
  logs)
    READ_DENY=()
    WRITE_ALLOW=("<aucune>")
    ;;
  *) deny "mode inconnu" ;;
esac

check_read() {
  local p; p="$(rel "$1")"
  [[ -z "$p" && ( "$mode" == tests || "$mode" == review || "$mode" == code ) ]] \
    && deny "$tool sans chemin explicite interdit. Précise un chemin."
  [[ -z "$p" ]] && p="."
  [[ "$p" != "." ]] && in_list "$p" "${SECRETS[@]}" && deny "Lecture de '$p' interdite (secrets)."
  if [[ -n "${READ_ALLOW+x}" ]]; then
    in_list "$p" "${READ_ALLOW[@]}" || deny "Lecture de '$p' interdite. Zones autorisées : ${READ_ALLOW[*]}"
  elif ((${#READ_DENY[@]})); then
    in_list "$p" "${READ_DENY[@]}" && deny "Accès à '$p' interdit pour cet agent."
    covers "$p" "${READ_DENY[@]}" && deny "Recherche sur '$p' interdite : elle couvrirait une zone interdite. Cible un sous-dossier (ex. src/main)."
  fi
  return 0
}

check_write() {
  local p; p="$(rel "$1")"
  [[ -z "$p" ]] && deny "Écriture sans chemin."
  if [[ -n "${WRITE_ALLOW+x}" ]]; then
    in_list "$p" "${WRITE_ALLOW[@]}" || deny "Écriture de '$p' interdite. Zones autorisées : ${WRITE_ALLOW[*]:-aucune}"
  else
    in_list "$p" "${WRITE_DENY[@]}" && deny "Écriture de '$p' interdite pour cet agent."
  fi
  return 0
}

# --- Bash : listes blanches -------------------------------------------------

meta_strict='[;&|`$<>()]'
meta_pipe='[;&`$<>()]'

check_bash() {
  local cmd; cmd="$(field command)"
  case "$mode" in
    tests|review) deny "Bash n'est pas autorisé pour cet agent." ;;
    code)
      [[ "$cmd" =~ ^[[:space:]]*\./mvnw[[:space:]] ]] || deny "Seules les commandes './mvnw ...' sont autorisées (compilation)."
      [[ "$cmd" =~ $meta_strict ]] && deny "Enchaînements, redirections et substitutions interdits."
      [[ "$cmd" == *test* && "$cmd" != *"-Dmaven.test.skip=true"* ]] \
        && deny "Toute commande Maven mentionnant 'test' doit inclure -Dmaven.test.skip=true."
      if [[ "$cmd" =~ (^|[[:space:]])(verify|package|install|integration-test|pitest|mutationCoverage) ]] \
         && [[ "$cmd" != *"-Dmaven.test.skip=true"* ]]; then
        deny "Ce goal Maven exécute les tests : utilise ./mvnw -q -Dmaven.test.skip=true compile"
      fi
      ;;
    auditor)
      [[ "$cmd" =~ $meta_strict ]] && deny "Enchaînements, redirections et substitutions interdits."
      [[ "$cmd" =~ ^[[:space:]]*(\./mvnw[[:space:]]|git[[:space:]]+(diff|log|show|status|ls-files)([[:space:]]|$)|\.claude/skills/[a-z-]+/scripts/[a-z-]+\.(sh|py)([[:space:]]|$)) ]] \
        || deny "Commandes autorisées : ./mvnw, git diff|log|show|status|ls-files, scripts .claude/skills/*/scripts/."
      ;;
    logs)
      [[ "$cmd" =~ $meta_pipe ]] && deny "Seuls les pipes '|' sont autorisés (pas de ; & redirection ni substitution)."
      local seg
      IFS='|' read -ra segs <<<"$cmd"
      for seg in "${segs[@]}"; do
        seg="${seg#"${seg%%[![:space:]]*}"}"
        [[ "$seg" =~ ^(docker[[:space:]]+compose([[:space:]]+(-p|--profile)[[:space:]]+[[:alnum:]_-]+)*[[:space:]]+(logs|ps)([[:space:]]|$)|docker[[:space:]]+(logs|ps|inspect)([[:space:]]|$)|docker[[:space:]]+stats[[:space:]]+--no-stream|grep[[:space:]]|zgrep[[:space:]]|head([[:space:]]|$)|tail([[:space:]]|$)|wc([[:space:]]|$)|sort([[:space:]]|$)|uniq([[:space:]]|$)|cut[[:space:]]|jq[[:space:]]|ls([[:space:]]|$)|zcat[[:space:]]) ]] \
          || deny "Segment '$seg' non autorisé. Permis : docker compose logs|ps, docker logs|ps|inspect|stats --no-stream, grep, zgrep, zcat, head, tail, wc, sort, uniq, cut, jq, ls."
        [[ "$seg" == *.env* && "$seg" != *.env.example* ]] && deny "Accès à .env interdit."
      done
      ;;
  esac
  return 0
}

# --- Dispatch par outil -----------------------------------------------------

case "$tool" in
  Read)          check_read "$(field file_path)" ;;
  Edit|Write)    check_write "$(field file_path)" ;;
  NotebookEdit)  check_write "$(field notebook_path)" ;;
  Grep)
    check_read "$(field path)"
    g="$(field glob)"
    [[ "$mode" == "code" && "$g" == *[Tt]est* ]] && deny "Filtre glob '$g' interdit."
    ;;
  Glob)
    check_read "$(field path)"
    pat="$(field pattern)"
    [[ "$pat" == /* || "$pat" == *..* ]] && deny "Motif Glob absolu ou remontant ('$pat') interdit."
    [[ "$mode" == "code" && "$pat" == *[Tt]est* ]] && deny "Motif Glob '$pat' interdit."
    ;;
  Bash)          check_bash ;;
  WebFetch)      [[ "$mode" == "review" ]] || deny "WebFetch non autorisé pour cet agent." ;;
  *)             deny "Outil '$tool' non autorisé pour cet agent." ;;
esac

exit 0
