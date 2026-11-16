#!/usr/bin/env bash
# Démarre le projet via Docker Compose.
set -euo pipefail
cd "$(dirname "$0")"

usage() {
  cat <<'EOF'
Usage : ./start.sh [options]

  --dev        (défaut) démarre db + api-dev (hot reload, debug sur DEBUG_PORT)
  --prod       démarre db + api-prod (HTTPS, image durcie)
  --test       lance la suite de tests dans docker/test, rapports dans target/test-reports
  --seed       applique les données de seed au démarrage (dev uniquement)
  --build      force la reconstruction des images
  --fresh      supprime la base (volume pgdata) avant de démarrer — demande confirmation
  --logs       suit les logs de l'API après démarrage
  -h, --help   affiche cette aide
EOF
}

R=$'\e[31m' G=$'\e[32m' Y=$'\e[33m' B=$'\e[1m' D=$'\e[2m' N=$'\e[0m'
[[ -t 1 ]] || { R='' G='' Y='' B='' D='' N=''; }
info() { echo "${B}▶${N} $*"; }
ok()   { echo "${G}✔${N} $*"; }
warn() { echo "${Y}⚠${N} $*"; }
die()  { echo "${R}✘ $*${N}" >&2; exit 1; }

mode=dev seed=0 build=() fresh=0 logs=0
while (($#)); do
  case "$1" in
    --dev) mode=dev ;;
    --prod) mode=prod ;;
    --test) mode=test ;;
    --seed) seed=1 ;;
    --build) build=(--build) ;;
    --fresh) fresh=1 ;;
    --logs) logs=1 ;;
    -h|--help) usage; exit 0 ;;
    *) usage; die "Option inconnue : $1" ;;
  esac
  shift
done

# --- Prérequis ---------------------------------------------------------------
command -v docker >/dev/null || die "docker introuvable."
docker compose version >/dev/null 2>&1 || die "Docker Compose v2 requis (docker compose)."
[[ -f .env ]] || die "Fichier .env absent : cp .env.example .env puis adaptez les valeurs."
set -a; source .env; set +a

((seed)) && [[ "$mode" != dev ]] && die "--seed n'est autorisé qu'en dev."

compose() { docker compose --profile "$mode" "$@"; }

# --- Tests -------------------------------------------------------------------
if [[ "$mode" == test ]]; then
  mkdir -p target/test-reports
  info "Exécution des tests (docker/test)…"
  set +e
  compose run --rm "${build[@]}" api-test
  rc=$?
  set -e
  ((rc == 0)) && ok "Tests OK" || echo "${R}✘ Tests en échec (code $rc)${N} — rapports : target/test-reports"
  exit "$rc"
fi

# --- Prod : prérequis spécifiques -------------------------------------------
if [[ "$mode" == prod ]]; then
  cert_dir="${TLS_CERT_DIR:-./certs}"
  if [[ ! -f "$cert_dir/tls.crt" || ! -f "$cert_dir/tls.key" ]]; then
    warn "Certificat TLS absent dans $cert_dir (tls.crt / tls.key)."
    read -r -p "Générer un certificat auto-signé pour un test local ? [y/N] " ans
    [[ "$ans" =~ ^[yYoO]$ ]] || die "Déposez un certificat valide dans $cert_dir puis relancez."
    docker/prod/gen-selfsigned-cert.sh "$cert_dir"
  fi
  IFS=',' read -ra origins <<<"${CORS_ALLOWED_ORIGINS:-}"
  ((${#origins[@]})) || die "CORS_ALLOWED_ORIGINS est requis en prod (origines HTTPS)."
  for o in "${origins[@]}"; do
    o="${o// /}"
    [[ "$o" == https://* && "$o" != *"*"* ]] || die "Origine CORS refusée en prod : '$o' (HTTPS explicite requis)."
  done
fi

# --- Reset de la base --------------------------------------------------------
if ((fresh)); then
  read -r -p "${Y}La base '${DB_NAME}' (volume pgdata) va être SUPPRIMÉE. Continuer ? [y/N] ${N}" ans
  [[ "$ans" =~ ^[yYoO]$ ]] || die "Annulé."
  docker compose --profile dev --profile prod down -v --remove-orphans
  ok "Volumes supprimés."
fi

# --- Démarrage ---------------------------------------------------------------
label="$mode"
if ((seed)); then
  export SPRING_PROFILES_ACTIVE=dev,seed
  label+=", seed"
else
  export SPRING_PROFILES_ACTIVE=dev
fi
info "Démarrage ($label)…"
compose up -d "${build[@]}"

service="api-$mode"
timeout=180 start=$(date +%s)
info "Attente de l'API ($service)…"
while :; do
  if [[ "$mode" == dev ]]; then
    curl -fsS "http://localhost:${API_PORT:-8080}/actuator/health" 2>/dev/null | grep -q '"status":"UP"' && break
  else
    status="$(docker inspect -f '{{.State.Health.Status}}' "$(compose ps -q "$service")" 2>/dev/null || true)"
    [[ "$status" == healthy ]] && break
    [[ "$status" == unhealthy ]] && { compose logs --tail 50 "$service"; die "L'API est unhealthy."; }
  fi
  if (( $(date +%s) - start > timeout )); then
    compose logs --tail 80 "$service"
    die "L'API n'a pas démarré en ${timeout}s."
  fi
  printf '.'; sleep 2
done
echo
ok "API démarrée en $(( $(date +%s) - start ))s"

if [[ "$mode" == dev ]]; then
  echo "  API         http://localhost:${API_PORT:-8080}/api/v1"
  echo "  Swagger UI  http://localhost:${API_PORT:-8080}/swagger-ui.html"
  echo "  Health      http://localhost:${API_PORT:-8080}/actuator/health"
  echo "  Debug JDWP  localhost:${DEBUG_PORT:-5005}"
  echo "  Seed        $( ((seed)) && echo activé || echo désactivé)"
else
  echo "  API (backend HAProxy)  https://${API_BIND:-127.0.0.1}:${API_HTTPS_PORT:-8443}/api/v1"
  echo "  Public                 https://${API_DOMAIN:-?} via HAProxy (docs/ops/DEPLOIEMENT-PROD.md)"
fi
echo "  PostgreSQL  127.0.0.1:${DB_PORT:-5432} (${DB_NAME})"

((logs)) && compose logs -f "$service"
exit 0
