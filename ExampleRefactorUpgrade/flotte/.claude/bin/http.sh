#!/usr/bin/env bash
# http.sh [--ref] [--session <nom>] [--user u:p] [--csrf-from <path>] [-H 'En-tête: valeur']…
#         <METHOD> <path> [-q clé=valeur]… [-d clé=valeur]…
# Requête vers app (http://localhost:8080) ou app-ref (http://localhost:8072).
# Affiche statut + en-têtes + corps. --session conserve les cookies (.claude/state/migration/http/<nom>.cookies).
# --csrf-from <path> : charge <path>, extrait le champ caché *_token du formulaire et l'ajoute aux données.
# -q : paramètre de requête (sans '&' dans la ligne de commande) ; -d : champ de formulaire (POST).
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/_common.sh"
base="http://localhost:8080"; session=""; user=""; csrf_from=""
headers=(); query=(); data=()
while (($#)); do
  case "$1" in
    --ref) base="http://localhost:8072"; shift ;;
    --session) session="$2"; shift 2 ;;
    --user) user="$2"; shift 2 ;;
    --csrf-from) csrf_from="$2"; shift 2 ;;
    -H) headers+=(-H "$2"); shift 2 ;;
    -q) query+=(--data-urlencode "$2"); shift 2 ;;
    -d) data+=(--data-urlencode "$2"); shift 2 ;;
    *) break ;;
  esac
done
method="${1:?METHOD manquant}"; path="${2:?path manquant}"; shift 2
while (($#)); do
  case "$1" in
    -q) query+=(--data-urlencode "$2"); shift 2 ;;
    -d) data+=(--data-urlencode "$2"); shift 2 ;;
    -H) headers+=(-H "$2"); shift 2 ;;
    *) die "Argument inconnu : $1" ;;
  esac
done
[[ "$path" == /* ]] || die "Le chemin doit commencer par /"

mkdir -p "$STATE/http"
jar=()
[[ -n "$session" ]] && jar=(-b "$STATE/http/$session.cookies" -c "$STATE/http/$session.cookies")
auth=(); [[ -n "$user" ]] && auth=(-u "$user")

if [[ -n "$csrf_from" ]]; then
  page="$(curl -s "${jar[@]}" "${auth[@]}" "$base$csrf_from")"
  token_line="$(grep -oE '<input[^>]*name="[^"]*_token[^"]*"[^>]*>' <<<"$page" | head -1 || true)"
  [[ -n "$token_line" ]] || die "Aucun champ *_token trouvé sur $csrf_from"
  tname="$(grep -oE 'name="[^"]*"' <<<"$token_line" | head -1 | cut -d'"' -f2)"
  tvalue="$(grep -oE 'value="[^"]*"' <<<"$token_line" | head -1 | cut -d'"' -f2)"
  data+=(--data-urlencode "$tname=$tvalue")
fi

url="$base$path"
args=(-s -i -X "$method" "${headers[@]}" "${jar[@]}" "${auth[@]}")
if ((${#query[@]})); then
  qs="$(curl -s -o /dev/null -w '%{url_effective}' -G "${query[@]}" "http://x/")"; qs="${qs#http://x/}"
  url="$url$qs"
fi
((${#data[@]})) && args+=("${data[@]}")
exec curl "${args[@]}" "$url"
