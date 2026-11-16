#!/usr/bin/env bash
# Restaure un dump de ./init_db dans la base du conteneur db (sélection via fzf).
set -euo pipefail
cd "$(dirname "$0")"

usage() {
  cat <<'EOF'
Usage : ./restore.sh [fichier] [--prod] [-y]

  fichier     dump à restaurer (sinon : sélection interactive fzf dans ./init_db)
  --prod      base de docker-compose.prod.yml (défaut : base de dev, docker-compose.yml)
  -y, --yes   pas de confirmation (usage scripté)
  -h, --help  affiche cette aide

Formats : .sql (psql) · .sql.gz (gunzip | psql) · .dump / .backup (pg_restore, format custom pg_dump -Fc)
EOF
}

R=$'\e[31m' G=$'\e[32m' Y=$'\e[33m' B=$'\e[1m' N=$'\e[0m'
[[ -t 1 ]] || { R='' G='' Y='' B='' N=''; }
info() { echo "${B}▶${N} $*"; }
ok()   { echo "${G}✔${N} $*"; }
die()  { echo "${R}✘ $*${N}" >&2; exit 1; }

file="" yes=0 files=(-f docker-compose.yml) apis=(api-dev)
while (($#)); do
  case "$1" in
    -y|--yes) yes=1 ;;
    --prod) files=(-f docker-compose.prod.yml); apis=(api-prod) ;;
    -h|--help) usage; exit 0 ;;
    -*) usage; die "Option inconnue : $1" ;;
    *) file="$1" ;;
  esac
  shift
done

# --- Prérequis ---------------------------------------------------------------
command -v docker >/dev/null || die "docker introuvable."
[[ -f .env ]] || die "Fichier .env absent : cp .env.example .env"
set -a; source .env; set +a
mapfile -t dumps < <(find init_db -maxdepth 1 -type f \( -name '*.sql' -o -name '*.sql.gz' -o -name '*.dump' -o -name '*.backup' \) -printf '%T@\t%p\n' | sort -rn | cut -f2)
((${#dumps[@]})) || die "Aucun dump dans ./init_db (.sql, .sql.gz, .dump, .backup)."

dc() { docker compose "${files[@]}" "$@"; }
psql_db() { dc exec -T db psql -v ON_ERROR_STOP=1 -U "$DB_USER" "$@"; }

# --- Base démarrée ? ---------------------------------------------------------
if [[ "$(docker inspect -f '{{.State.Health.Status}}' "$(dc ps -q db 2>/dev/null)" 2>/dev/null || true)" != healthy ]]; then
  info "Démarrage du service db…"
  dc up -d db
  for _ in $(seq 1 30); do
    [[ "$(docker inspect -f '{{.State.Health.Status}}' "$(dc ps -q db)")" == healthy ]] && break
    sleep 2
  done
  [[ "$(docker inspect -f '{{.State.Health.Status}}' "$(dc ps -q db)")" == healthy ]] || die "La base ne démarre pas."
fi

# --- Sélection -----------------------------------------------------------------
if [[ -z "$file" ]]; then
  command -v fzf >/dev/null || die "fzf requis pour la sélection interactive : sudo apt install fzf (ou passez le fichier en argument)."
  export COMPOSE_FILES="${files[*]}"
  file="$(printf '%s\n' "${dumps[@]}" | fzf --prompt="Backup à restaurer > " --height=60% --reverse --border \
    --header="Entrée : restaurer · Échap : annuler" \
    --preview='f={}; echo "Fichier : $f"; echo "Taille  : $(du -h "$f" | cut -f1)"; echo "Date    : $(date -r "$f" "+%F %T")"; echo; \
      case "$f" in
        *.sql.gz) zcat "$f" | head -n 60 ;;
        *.sql) head -n 60 "$f" ;;
        *) docker compose $COMPOSE_FILES exec -T db pg_restore -l "/init_db/$(basename "$f")" 2>&1 | head -n 60 ;;
      esac' \
    --preview-window=right:60%:wrap)" || { echo "Annulé."; exit 130; }
fi
[[ -f "$file" ]] || die "Fichier introuvable : $file"
[[ "$(realpath "$(dirname "$file")")" == "$(realpath init_db)" ]] || die "Le dump doit se trouver dans ./init_db (monté dans le conteneur)."
name="$(basename "$file")"

# --- Confirmation -------------------------------------------------------------
if ((!yes)); then
  read -r -p "${Y}La base '${DB_NAME}' va être ÉCRASÉE par '${name}'. Continuer ? [y/N] ${N}" ans
  [[ "$ans" =~ ^[yYoO]$ ]] || { echo "Annulé."; exit 1; }
fi

start=$(date +%s)

# --- Arrêt de l'API -------------------------------------------------------------
running=()
for s in "${apis[@]}"; do
  [[ -n "$(dc ps -q --status running "$s" 2>/dev/null)" ]] && running+=("$s")
done
if ((${#running[@]})); then
  info "Arrêt de ${running[*]}…"
  dc stop "${running[@]}"
fi

# --- Recréation et restauration ----------------------------------------------
info "Recréation de la base ${DB_NAME}…"
psql_db -d postgres -c "DROP DATABASE IF EXISTS \"${DB_NAME}\" WITH (FORCE);" -c "CREATE DATABASE \"${DB_NAME}\" OWNER \"${DB_USER}\";"

info "Restauration de ${name}…"
case "$name" in
  *.sql.gz) dc exec -T db sh -c "gunzip -c '/init_db/$name' | psql -v ON_ERROR_STOP=1 -q -U '$DB_USER' -d '$DB_NAME'" ;;
  *.sql)    dc exec -T db psql -v ON_ERROR_STOP=1 -q -U "$DB_USER" -d "$DB_NAME" -f "/init_db/$name" ;;
  *)        dc exec -T db pg_restore --no-owner --no-acl --exit-on-error -U "$DB_USER" -d "$DB_NAME" "/init_db/$name" ;;
esac

# --- Redémarrage ------------------------------------------------------------------
if ((${#running[@]})); then
  info "Redémarrage de ${running[*]}…"
  dc start "${running[@]}"
fi

ok "Restauration de '${name}' terminée en $(( $(date +%s) - start ))s."
echo "  Au démarrage, Flyway valide flyway_schema_history : le dump doit correspondre aux migrations du code."
