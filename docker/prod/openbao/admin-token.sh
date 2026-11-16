#!/usr/bin/env bash
# Accès administrateur « break-glass » à OpenBao prod (ADR 0001) : aucun token root permanent n'existe.
#
# OpenBao ≥ 2.5 désactive par défaut les endpoints generate-root non authentifiés (CVE-2025-52894). Ce script :
#   1. redémarre OpenBao avec disable_unauthed_generate_root_endpoints=false (config dérivée d'openbao.hcl) ;
#   2. descelle (3 parts) puis génère un token root avec 3 parts — le token n'est JAMAIS affiché ;
#   3. rétablit immédiatement la configuration normale et redescelle (3 parts) ;
#   4. exécute la commande demandée avec BAO_TOKEN, puis RÉVOQUE le token, quoi qu'il arrive.
#
#   docker/prod/openbao/admin-token.sh                                   # shell admin dans le conteneur OpenBao
#   docker/prod/openbao/admin-token.sh -- docker/prod/openbao/bootstrap.sh   # rejouer la configuration
#
# ⚠ L'API perd l'accès à OpenBao pendant les redémarrages (≈ 1 min) : planifier une fenêtre de maintenance.
set -euo pipefail
cd "$(dirname "$0")/../../.."
set -a; source .env; set +a
[[ "${1:-}" == "--" ]] && shift

dc() { docker compose -f docker-compose.prod.yml "$@"; }
bao() { dc exec -T openbao-prod bao "$@" </dev/null; }
field() { sed -n "s/.*\"$1\": *\"*\([^\",}]*\).*/\1/p" | head -1; }
api_post() { # api_post <chemin> : corps JSON lu sur stdin, TLS vérifié, boucle locale du conteneur
  dc exec -T -e SSL_CERT_FILE=/openbao/tls/ca.crt openbao-prod \
    wget -qO- --header 'Content-Type: application/json' --post-file=/dev/stdin "https://127.0.0.1:8200/v1/$1"
}

restart_with() { # restart_with <fichier de config>
  OPENBAO_CONFIG_PATH="$1" dc up -d --force-recreate --no-deps openbao-prod </dev/null >/dev/null 2>&1
  sleep 3
}

# Décodage local du token généré : encoded_token = base64(token XOR otp). La CLI 2.7 (-decode) interroge
# l'endpoint authentifié et ne peut donc pas servir ici.
decode_token() { # decode_token <encoded> <otp>
  local enc="$1" otp="$2" out="" i c
  enc="${enc//-/+}"; enc="${enc//_//}"
  while (( ${#enc} % 4 )); do enc+="="; done
  local -a eb ob
  read -r -a eb <<<"$(printf '%s' "$enc" | base64 -d | od -An -v -tu1 | tr -s ' \n' ' ')"
  read -r -a ob <<<"$(printf '%s' "$otp" | od -An -v -tu1 | tr -s ' \n' ' ')"
  ((${#eb[@]} == ${#ob[@]})) || return 1
  for i in "${!eb[@]}"; do printf -v c '\\x%02x' $(( eb[i] ^ ob[i] )); out+="$c"; done
  printf "$out"
}

base_cfg="./docker/prod/openbao/openbao.hcl"   # le ./ est requis : sinon compose y voit un nom de volume
bg_dir="$(mktemp -d)"; chmod 0755 "$bg_dir"
bg_cfg="$bg_dir/openbao-breakglass.hcl"
token="" breakglass=0
cleanup() {
  if ((breakglass)); then
    # Ne jamais laisser OpenBao en mode break-glass : retour à la config normale, quitte à le resceller.
    echo "⚠ Interruption : retour à la configuration normale (OpenBao sera SCELLÉ → docker/prod/openbao/unseal.sh)." >&2
    restart_with "$base_cfg"
  fi
  if [[ -n "$token" ]]; then
    if ! dc exec -T -e BAO_TOKEN="$token" openbao-prod bao token lookup </dev/null >/dev/null 2>&1; then
      echo "✔ Token admin déjà invalide (révoqué par la commande)."
    elif dc exec -T -e BAO_TOKEN="$token" openbao-prod bao token revoke -self </dev/null >/dev/null 2>&1; then
      echo "✔ Token admin révoqué."
    else
      echo "⚠ Révocation du token admin impossible : vérifier l'état d'OpenBao !" >&2
    fi
  fi
  rm -rf "$bg_dir"
}
trap cleanup EXIT


# 1. Configuration break-glass, dérivée de la configuration normale --------------------
sed 's/^\([[:space:]]*\)tls_min_version.*/&\n\1disable_unauthed_generate_root_endpoints = false/' "$base_cfg" > "$bg_cfg"
grep -q 'disable_unauthed_generate_root_endpoints = false' "$bg_cfg" || { echo "Dérivation de la configuration impossible" >&2; exit 1; }
chmod 0644 "$bg_cfg"
echo "▶ Redémarrage d'OpenBao en mode break-glass (generate-root activé temporairement)"
breakglass=1
restart_with "$bg_cfg"
docker/prod/openbao/unseal.sh

# 2. Génération du token root (3 parts) -----------------------------------------------------
# La CLI 2.7 utilise l'endpoint authentifié sys/generate-root-token : on passe par l'endpoint historique
# sys/generate-root/*, que seul le mode break-glass réactive. Le décodage final (-decode) reste local à la CLI.
init="$(printf '{}' | api_post sys/generate-root/attempt 2>/dev/null || true)"
nonce="$(field nonce <<<"$init")" otp="$(field otp <<<"$init")"
[[ -n "$nonce" && -n "$otp" ]] || { echo "Initialisation de generate-root impossible : $init" >&2; exit 1; }
echo "▶ Génération d'un token admin : 3 parts Shamir nécessaires"
encoded=""
while [[ -z "$encoded" ]]; do
  read -r -s -p "Part (saisie masquée, vide = abandon) : " share; echo
  [[ -n "$share" ]] || { echo "Abandon." >&2; exit 1; }
  [[ "$share" =~ ^[A-Za-z0-9+/=]+$ ]] || { echo "Format invalide."; continue; }
  resp="$(printf '{"key":"%s","nonce":"%s"}' "$share" "$nonce" | api_post sys/generate-root/update 2>/dev/null || true)"
  unset share
  [[ -n "$resp" ]] || { echo "Part refusée."; continue; }
  encoded="$(field encoded_token <<<"$resp")"
  [[ -n "$encoded" ]] || echo "  progression : $(field progress <<<"$resp")/$(field required <<<"$resp")"
done
token="$(decode_token "$encoded" "$otp")"
unset encoded otp nonce
[[ "$token" =~ ^[A-Za-z0-9._-]+$ ]] || { token=""; echo "Décodage du token impossible." >&2; exit 1; }

# 3. Retour immédiat à la configuration normale -----------------------------------------------
echo "▶ Retour à la configuration normale (generate-root non authentifié de nouveau désactivé)"
restart_with "$base_cfg"
breakglass=0
docker/prod/openbao/unseal.sh

# 4. Commande admin ------------------------------------------------------------------------------
if (($#)); then
  echo "▶ Exécution : $*"
  BAO_TOKEN="$token" "$@"
else
  echo "▶ Shell admin (BAO_TOKEN défini, révoqué à la sortie). Tapez exit pour terminer."
  dc exec -e BAO_TOKEN="$token" openbao-prod sh
fi
