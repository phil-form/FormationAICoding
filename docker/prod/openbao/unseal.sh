#!/usr/bin/env bash
# Descellement d'OpenBao prod (Shamir 3 parts sur 5, ADR 0001 D3).
# Les parts sont saisies sans écho et transmises par stdin : elles n'apparaissent ni dans l'historique, ni dans la
# liste des processus, ni dans un fichier. Chaque détenteur peut saisir la sienne à tour de rôle.
#   docker/prod/openbao/unseal.sh
set -euo pipefail
cd "$(dirname "$0")/../../.."

# stdin coupé par défaut : docker compose exec -T transmettrait sinon les saisies en attente au conteneur.
bao() { docker compose --profile prod exec -T openbao-prod bao "$@" </dev/null; }
# La CLI refuse une part transmise par pipe (TTY exigé) : on appelle l'API sur la boucle locale du conteneur, la part
# voyageant dans le CORPS de la requête lu sur stdin (jamais dans les arguments d'un processus). TLS vérifié (CA interne).
unseal_api() {
  docker compose --profile prod exec -T -e SSL_CERT_FILE=/openbao/tls/ca.crt openbao-prod \
    wget -qO- --header 'Content-Type: application/json' --post-file=/dev/stdin https://127.0.0.1:8200/v1/sys/unseal
}
field() { sed -n "s/.*\"$1\": *\([^,}]*\).*/\1/p" | head -1 | tr -d ' "'; }

status="$(bao status -format=json 2>/dev/null || true)"
[[ -n "$status" ]] || { echo "OpenBao prod injoignable (conteneur openbao-prod démarré ?)" >&2; exit 1; }
[[ "$(field initialized <<<"$status")" == true ]] || { echo "OpenBao n'est pas initialisé : lancez docker/prod/openbao/bootstrap.sh" >&2; exit 1; }
[[ "$(field sealed <<<"$status")" == true ]] || { echo "OpenBao est déjà descellé."; exit 0; }

threshold="$(field t <<<"$status")"
echo "OpenBao est SCELLÉ. ${threshold} parts (sur $(field n <<<"$status")) sont nécessaires — source : 1Password."
while :; do
  progress="$(field progress <<<"$status")"
  read -r -s -p "Part $((progress + 1))/${threshold} (saisie masquée, vide = abandon) : " share; echo
  [[ -n "$share" ]] || { echo "Abandon : OpenBao reste scellé (progression conservée $progress/$threshold)."; exit 1; }
  [[ "$share" =~ ^[A-Za-z0-9+/=]+$ ]] || { echo "Format de part invalide (base64 attendu)."; continue; }
  status="$(printf '{"key":"%s"}' "$share" | unseal_api 2>/dev/null || true)"
  unset share
  if [[ "$(field sealed <<<"$status")" == false ]]; then
    echo "✔ OpenBao descellé."
    exit 0
  fi
  grep -q '"progress"' <<<"$status" || { echo "Part refusée par OpenBao."; status="$(bao status -format=json 2>/dev/null || true)"; }
done
