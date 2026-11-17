#!/usr/bin/env bash
# env.sh up|down|build|status|reset|ref-sync — pilote l'environnement Docker de migration.
#   up [--ref]                 démarre app (et app-ref si --ref ou si .ref/ existe), attend les healthchecks
#   down                       arrête (volumes conservés)
#   build [service…]           reconstruit les images
#   status                     état des services
#   reset [--scenario s] [--ref|--both]   restaure un scénario de données (défaut : nominal) via seed.sh
#   php-version [--ref]        version de PHP réellement exécutée dans app (ou app-ref)
#   ref-sync <tag>             régénère .ref/ depuis un tag git (code step-00) et installe ses dépendances
# La création de l'infrastructure (docker-compose.yml, docker/) est faite par la skill /env init, pas par ce script.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/_common.sh"
cd "$ROOT"

cmd="${1:-}"; shift || true
case "$cmd" in
  up)
    require_compose
    services=(app)
    if [[ "${1:-}" == "--ref" || -d "$ROOT/.ref" ]]; then services+=(app-ref); fi
    "${COMPOSE[@]}" up -d --wait "${services[@]}"
    "${COMPOSE[@]}" ps
    ;;
  down)   require_compose; "${COMPOSE[@]}" down ;;
  build)
    require_compose
    if (($#)); then "${COMPOSE[@]}" --profile tools build "$@"; else "${COMPOSE[@]}" --profile tools build; fi
    ;;
  status)
    require_compose
    echo "Étape : $(grep -E '^(STEP|PHP_VERSION|SYMFONY_VERSION|FAKETIME)=' docker/step.env | tr '\n' ' ')"
    "${COMPOSE[@]}" ps
    ;;
  reset)
    scenario=nominal; target=app
    while (($#)); do
      case "$1" in
        --scenario) scenario="$2"; shift 2 ;;
        --ref) target=ref; shift ;;
        --both) target=both; shift ;;
        *) die "Option inconnue : $1" ;;
      esac
    done
    exec "$ROOT/.claude/bin/seed.sh" "$scenario" --target "$target"
    ;;
  php-version)
    require_compose
    svc=app; [[ "${1:-}" == "--ref" ]] && svc=app-ref
    "${COMPOSE[@]}" exec -T "$svc" php -r 'echo PHP_VERSION, PHP_EOL;'
    ;;
  ref-sync)
    tag="${1:?usage: env.sh ref-sync <tag>}"
    git -C "$ROOT" rev-parse -q --verify "refs/tags/$tag" >/dev/null || die "Tag introuvable : $tag"
    prefix="$(git -C "$ROOT" rev-parse --show-prefix)"; prefix="${prefix%/}"
    tmp="$(mktemp -d)"
    git -C "$ROOT" archive --format=tar "$tag:${prefix}" | tar -x -C "$tmp"
    rm -rf "$ROOT/.ref"; mv "$tmp" "$ROOT/.ref"
    # Référence (Symfony 2.8) : parameters.yml non versionné, base pointée sur le volume db-ref.
    if [[ -f .ref/app/config/parameters.yml.dist && ! -f .ref/app/config/parameters.yml ]]; then
      sed 's#^\( *database_path:\).*#\1 "/var/app-data/flotte.sqlite"#' .ref/app/config/parameters.yml.dist \
        > .ref/app/config/parameters.yml
    fi
    require_compose
    "${COMPOSE[@]}" run --rm tools composer --working-dir=.ref install --no-interaction --no-scripts --no-progress
    echo "$tag" > "$ROOT/.ref/.ref-tag"
    info ".ref/ synchronisé sur $tag"
    "${COMPOSE[@]}" up -d --wait --force-recreate app-ref
    ;;
  *) sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//'; exit 2 ;;
esac
