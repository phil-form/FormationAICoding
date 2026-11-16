#!/usr/bin/env bash
# Démarre le projet via Docker Compose.
set -euo pipefail
cd "$(dirname "$0")"

usage() {
  cat <<'EOF'
Usage : ./start.sh [mode] [options]

Modes
  --dev        (défaut) docker-compose.yml : db, OpenBao dev, api-dev (hot reload, debug sur DEBUG_PORT)
  --prod       docker-compose.prod.yml : db, OpenBao prod (scellé), api-prod — reverse proxy + WAF sur le serveur
  --test-prod  prod + docker-compose.test-prod.yml : toute la chaîne de prod en local
               (HAProxy → WAF ModSecurity/CRS → api-prod), certificats de test pour https://localhost
  --test       suite de tests dans docker/test, rapports dans target/test-reports

Options
  --seed       applique les données de seed au démarrage (dev uniquement)
  --build      force la reconstruction des images
  --fresh      supprime les volumes du mode (base, OpenBao prod…) avant de démarrer — demande confirmation
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
    --test-prod) mode=test-prod ;;
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

case "$mode" in
  dev|test)  files=(-f docker-compose.yml) ;;
  prod)      files=(-f docker-compose.prod.yml) ;;
  test-prod) files=(-f docker-compose.prod.yml -f docker-compose.test-prod.yml) ;;
esac
compose() { docker compose "${files[@]}" "$@"; }

# --- Tests -------------------------------------------------------------------
if [[ "$mode" == test ]]; then
  mkdir -p target/test-reports
  info "Exécution des tests (docker/test)…"
  set +e
  compose --profile test run --rm "${build[@]}" api-test
  rc=$?
  set -e
  ((rc == 0)) && ok "Tests OK" || echo "${R}✘ Tests en échec (code $rc)${N} — rapports : target/test-reports"
  exit "$rc"
fi

# --- Prérequis spécifiques -------------------------------------------------------
if [[ "$mode" == dev && ! -f .env.dev ]]; then
  # Secrets dev générés aléatoirement à partir du modèle (jamais commités).
  sed -e "s/^BAO_DEV_ROOT_TOKEN_ID=.*/BAO_DEV_ROOT_TOKEN_ID=dev-$(openssl rand -hex 16)/" \
      -e "s/^OPENBAO_DB_ADMIN_PASSWORD=.*/OPENBAO_DB_ADMIN_PASSWORD=$(openssl rand -hex 24)/" .env.dev.example > .env.dev
  chmod 600 .env.dev
  warn ".env.dev absent : créé depuis .env.dev.example avec des secrets aléatoires."
fi

if [[ "$mode" == prod ]]; then
  cert_dir="${TLS_CERT_DIR:-./certs}"
  if [[ ! -f "$cert_dir/tls.crt" || ! -f "$cert_dir/tls.key" ]]; then
    warn "Certificat TLS absent dans $cert_dir (tls.crt / tls.key)."
    read -r -p "Générer un certificat auto-signé pour un test local ? [y/N] " ans
    [[ "$ans" =~ ^[yYoO]$ ]] || die "Déposez un certificat valide dans $cert_dir puis relancez."
    docker/prod/gen-selfsigned-cert.sh "$cert_dir"
  fi
fi

if [[ "$mode" == test-prod ]]; then
  docker/test-prod/gen-certs.sh
fi

if [[ "$mode" == prod || "$mode" == test-prod ]]; then
  IFS=',' read -ra origins <<<"${CORS_ALLOWED_ORIGINS:-}"
  ((${#origins[@]})) || die "CORS_ALLOWED_ORIGINS est requis (origines HTTPS)."
  for o in "${origins[@]}"; do
    o="${o// /}"
    [[ "$o" == https://* && "$o" != *"*"* ]] || die "Origine CORS refusée en prod : '$o' (HTTPS explicite requis)."
  done
fi

# --- Reset ---------------------------------------------------------------------
if ((fresh)); then
  read -r -p "${Y}Les volumes du mode '$mode' (base '${DB_NAME}', OpenBao prod…) vont être SUPPRIMÉS. Continuer ? [y/N] ${N}" ans
  [[ "$ans" =~ ^[yYoO]$ ]] || die "Annulé."
  compose down -v --remove-orphans
  ok "Volumes supprimés."
fi

# --- OpenBao prod (ADR 0001) ---------------------------------------------------------
if [[ "$mode" == prod || "$mode" == test-prod ]]; then
  [[ -f "${OPENBAO_TLS_DIR:-./secrets/openbao-tls}/tls.crt" ]] || die "PKI interne OpenBao absente : lancez docker/prod/openbao/bootstrap.sh"
  compose up -d db openbao-prod "${build[@]}" </dev/null
  bao_status=""
  for _ in $(seq 1 30); do
    bao_status="$(compose exec -T openbao-prod bao status -format=json 2>/dev/null </dev/null || true)"
    [[ -n "$bao_status" ]] && break
    sleep 1
  done
  [[ -n "$bao_status" ]] || die "OpenBao ne répond pas : docker compose ${files[*]} logs openbao-prod"
  grep -q '"initialized": *true' <<<"$bao_status" || die "OpenBao n'est pas initialisé : lancez docker/prod/openbao/bootstrap.sh"
  if grep -q '"sealed": *true' <<<"$bao_status"; then
    warn "OpenBao est SCELLÉ (redémarrage) : 3 parts Shamir sur 5 sont nécessaires (1Password)."
    [[ -t 0 ]] || die "Descellement interactif requis : docker/prod/openbao/unseal.sh puis relancez ./start.sh --$mode"
    docker/prod/openbao/unseal.sh || die "OpenBao reste scellé."
  fi
  ok "OpenBao descellé."
fi

# --- Démarrage ---------------------------------------------------------------------
label="$mode"
if ((seed)); then
  export SPRING_PROFILES_ACTIVE=dev,seed
  label+=", seed"
else
  export SPRING_PROFILES_ACTIVE=dev
fi
info "Démarrage ($label)…"
compose up -d "${build[@]}"

service=api-dev; [[ "$mode" == dev ]] || service=api-prod
timeout=240 start=$(date +%s)
info "Attente de l'API ($service)…"
while :; do
  case "$mode" in
    dev)
      curl -fsS "http://localhost:${API_PORT:-8080}/actuator/health" 2>/dev/null | grep -q '"status":"UP"' && break ;;
    prod)
      [[ "$(docker inspect -f '{{.State.Health.Status}}' "$(compose ps -q api-prod)" 2>/dev/null)" == healthy ]] && break ;;
    test-prod)
      # Toute la chaîne : HAProxy → WAF → API (401 = l'API a répondu, refus par défaut).
      code="$(curl -s -o /dev/null -w '%{http_code}' --cacert secrets/test-prod/ca.crt \
        "https://localhost:${TESTPROD_HTTPS_PORT:-443}/api/v1/ping" 2>/dev/null || true)"
      [[ "$code" == 401 ]] && break ;;
  esac
  st="$(docker inspect -f '{{.State.Health.Status}}' "$(compose ps -q "$service")" 2>/dev/null || true)"
  [[ "$st" == unhealthy ]] && { compose logs --tail 50 "$service"; die "L'API est unhealthy."; }
  if (( $(date +%s) - start > timeout )); then
    compose logs --tail 60
    die "La stack n'a pas démarré en ${timeout}s."
  fi
  printf '.'; sleep 2
done
echo
ok "Prêt en $(( $(date +%s) - start ))s"

case "$mode" in
  dev)
    echo "  API         http://localhost:${API_PORT:-8080}/api/v1"
    echo "  Swagger UI  http://localhost:${API_PORT:-8080}/swagger-ui.html"
    echo "  Health      http://localhost:${API_PORT:-8080}/actuator/health"
    echo "  Debug JDWP  localhost:${DEBUG_PORT:-5005}"
    echo "  OpenBao     http://127.0.0.1:${OPENBAO_DEV_PORT:-8200} (mode dev, token : BAO_DEV_ROOT_TOKEN_ID de .env.dev)"
    echo "  Seed        $( ((seed)) && echo activé || echo désactivé)"
    ;;
  prod)
    echo "  API (backend du reverse proxy)  https://${API_BIND:-127.0.0.1}:${API_HTTPS_PORT:-8443}/api/v1"
    echo "  Public                          https://${API_DOMAIN:-?} via HAProxy + WAF (docs/ops/DEPLOIEMENT-PROD.md)"
    echo "  OpenBao                         descellé, réseau interne uniquement (docs/ops/OPENBAO.md)"
    ;;
  test-prod)
    echo "  Public      https://localhost:${TESTPROD_HTTPS_PORT:-443}/api/v1  (CA de test : secrets/test-prod/ca.crt)"
    echo "              curl --cacert secrets/test-prod/ca.crt https://localhost/api/v1/…"
    echo "  Chaîne      HAProxy → WAF ModSecurity/CRS → api-prod (non publiée) → OpenBao / PostgreSQL"
    echo "  Logs WAF    docker compose ${files[*]} logs -f waf"
    ;;
esac
if [[ "$mode" == dev ]]; then
  echo "  PostgreSQL  127.0.0.1:${DB_PORT:-5432} (${DB_NAME})"
else
  echo "  PostgreSQL  non publiée — docker compose ${files[*]} exec db psql -U ${DB_USER} ${DB_NAME}"
fi

((logs)) && compose logs -f "$service"
exit 0
