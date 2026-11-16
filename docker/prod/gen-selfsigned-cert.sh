#!/usr/bin/env bash
# Génère un certificat auto-signé ECDSA P-256 pour tester le profil prod en local.
# EN PRODUCTION : utiliser un certificat d'une autorité reconnue (ex. Let's Encrypt) déposé dans TLS_CERT_DIR.
#   docker/prod/gen-selfsigned-cert.sh [dossier=./certs] [nom=localhost]
set -euo pipefail

dir="${1:-./certs}"
cn="${2:-localhost}"
mkdir -p "$dir"

openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:prime256v1 -sha256 -days 90 -nodes \
  -keyout "$dir/tls.key" -out "$dir/tls.crt" -subj "/CN=$cn" \
  -addext "subjectAltName=DNS:$cn,DNS:localhost,IP:127.0.0.1" \
  -addext "keyUsage=critical,digitalSignature" -addext "extendedKeyUsage=serverAuth" 2>/dev/null

# Clé lisible par le propriétaire et son groupe uniquement (le conteneur prod est ajouté au groupe HOST_GID).
chmod 640 "$dir/tls.key"
chmod 644 "$dir/tls.crt"
echo "Certificat auto-signé créé : $dir/tls.crt (CN=$cn, 90 jours)"
