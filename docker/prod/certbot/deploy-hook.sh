#!/usr/bin/env bash
# Deploy hook certbot : exécuté (en root) après chaque émission / renouvellement réussi.
# Installation : /etc/letsencrypt/renewal-hooks/deploy/formation.sh (chmod 750) + /etc/formation/deploy-hook.env
#
# 1. Vérifie que la clé privée correspond au certificat.
# 2. API : copie fullchain + clé dans APP_CERT_DIR (root:APP_CERT_GROUP, clé 0640) par remplacement ATOMIQUE
#    → Spring Boot (reload-on-update) recharge le certificat à chaud, sans redémarrage.
# 3. HAProxy : construit le PEM combiné, valide la configuration, recharge HAProxy (sans coupure).
#
# Variables fournies par certbot : RENEWED_LINEAGE (/etc/letsencrypt/live/<nom>), RENEWED_DOMAINS.
set -euo pipefail

CONF=/etc/formation/deploy-hook.env
[[ -f "$CONF" ]] && source "$CONF"
: "${API_DOMAIN:?API_DOMAIN non défini ($CONF)}"
APP_CERT_DIR="${APP_CERT_DIR:-/srv/formation/certs}"
APP_CERT_GROUP="${APP_CERT_GROUP:-formation-tls}"
HAPROXY_CERT_DIR="${HAPROXY_CERT_DIR:-/etc/haproxy/certs}"
HAPROXY_CFG="${HAPROXY_CFG:-/etc/haproxy/haproxy.cfg}"

log() { logger -t formation-cert "$*"; echo "$*"; }

# Uniquement pour le certificat de l'API (le hook s'exécute pour tous les certificats du serveur).
[[ " ${RENEWED_DOMAINS:-} " == *" ${API_DOMAIN} "* ]] || exit 0
src="${RENEWED_LINEAGE:?}"

# 1. Cohérence clé / certificat
cert_pub="$(openssl x509 -in "$src/cert.pem" -noout -pubkey | openssl sha256)"
key_pub="$(openssl pkey -in "$src/privkey.pem" -pubout | openssl sha256)"
[[ "$cert_pub" == "$key_pub" ]] || { log "ERREUR : clé et certificat ne correspondent pas ($src)"; exit 1; }

# Remplacement atomique : fichier temporaire dans le MÊME dossier, puis rename(2).
atomic_install() { # atomic_install <source> <destination> <mode> <groupe>
  local tmp
  tmp="$(mktemp "$(dirname "$2")/.$(basename "$2").XXXXXX")"
  cat "$1" > "$tmp"
  chown "root:$4" "$tmp"
  chmod "$3" "$tmp"
  mv -f "$tmp" "$2"
}

# 2. API — les deux fichiers sont remplacés en < 1 s, bien sous la période de calme du rechargement Spring (10 s).
install -d -o root -g "$APP_CERT_GROUP" -m 0750 "$APP_CERT_DIR"
atomic_install "$src/privkey.pem"   "$APP_CERT_DIR/tls.key" 0640 "$APP_CERT_GROUP"
atomic_install "$src/fullchain.pem" "$APP_CERT_DIR/tls.crt" 0644 "$APP_CERT_GROUP"
log "Certificat API installé dans $APP_CERT_DIR (rechargement à chaud par l'application)"

# 3. HAProxy
install -d -o root -g haproxy -m 0750 "$HAPROXY_CERT_DIR"
combined="$(mktemp)"
trap 'rm -f "$combined"' EXIT
cat "$src/fullchain.pem" "$src/privkey.pem" > "$combined"
atomic_install "$combined" "$HAPROXY_CERT_DIR/${API_DOMAIN}.pem" 0640 haproxy
if haproxy -c -q -f "$HAPROXY_CFG"; then
  systemctl reload haproxy
  log "HAProxy rechargé avec le nouveau certificat"
else
  log "ERREUR : configuration HAProxy invalide, pas de rechargement"
  exit 1
fi
