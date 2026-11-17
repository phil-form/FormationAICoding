# Fonctions communes aux scripts de .claude/bin (sourcé, non exécutable seul).
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
STATE="$ROOT/.claude/state/migration"
COMPOSE=(docker compose -p flotte-mig -f "$ROOT/docker-compose.yml" --env-file "$ROOT/docker/step.env")

# Identité de l'utilisateur hôte (fichiers créés dans les montages) et groupe du socket Docker (tests CLI).
export HOST_UID="${HOST_UID:-$(id -u)}" HOST_GID="${HOST_GID:-$(id -g)}"
export DOCKER_GID="${DOCKER_GID:-$(stat -c %g /var/run/docker.sock 2>/dev/null || echo 0)}"
# Dossiers montés : créés ici (et non par Docker, qui les créerait au nom de root).
mkdir -p "$STATE/test-results" "$STATE/coverage" "$ROOT/tests" 2>/dev/null || true

die() { echo "✘ $*" >&2; exit "${2:-1}"; }
info() { echo "› $*" >&2; }

require_compose() {
  [[ -f "$ROOT/docker-compose.yml" && -f "$ROOT/docker/step.env" ]] \
    || die "Environnement absent (docker-compose.yml ou docker/step.env) : lancer /env init." 3
}

is_frozen() {
  [[ -f "$STATE/state.json" ]] && [[ "$(jq -r '.frozen // false' "$STATE/state.json")" == "true" ]]
}
