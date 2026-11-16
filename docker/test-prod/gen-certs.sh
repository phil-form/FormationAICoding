#!/usr/bin/env bash
# PKI de TEST pour docker-compose.test-prod.yml (localhost) — jamais en production.
# Une CA locale signe un certificat par saut TLS, pour que chaque saut soit VÉRIFIÉ comme en prod :
#   client ──▶ haproxy (localhost) ──▶ waf (localhost, waf) ──▶ api-prod (localhost, api-prod)
#
#   docker/test-prod/gen-certs.sh [--force]
#
# Sortie : secrets/test-prod/{ca.crt,ca.key,haproxy/localhost.pem,waf/{waf.crt,waf.key,ca.crt},api/{tls.crt,tls.key}}
# Clés lisibles (0644) pour HAProxy et le WAF (utilisateurs de conteneur différents) : acceptable pour un test local
# uniquement ; la clé de l'API garde le modèle prod (0640, groupe HOST_GID).
set -euo pipefail
cd "$(dirname "$0")/../.."

out=secrets/test-prod
if [[ -f "$out/ca.crt" && "${1:-}" != "--force" ]]; then
  echo "PKI de test déjà présente ($out) — --force pour la régénérer."
  exit 0
fi
rm -rf "$out"
install -d -m 0755 "$out" "$out/haproxy" "$out/waf" "$out/api"
group="$(id -g)"

ec_key() { openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out "$1" 2>/dev/null; }

ec_key "$out/ca.key"
openssl req -x509 -new -key "$out/ca.key" -sha256 -days 90 -subj "/CN=Formation test-prod CA (local)" \
  -addext "basicConstraints=critical,CA:TRUE,pathlen:0" -addext "keyUsage=critical,keyCertSign,cRLSign" \
  -out "$out/ca.crt"

leaf() { # leaf <dossier> <nom> <SAN>
  local dir=$1 name=$2 san=$3 ext
  ec_key "$dir/$name.key"
  openssl req -new -key "$dir/$name.key" -subj "/CN=localhost" -out "$dir/$name.csr" 2>/dev/null
  ext="$(mktemp)"
  printf 'basicConstraints=critical,CA:FALSE\nkeyUsage=critical,digitalSignature\nextendedKeyUsage=serverAuth\nsubjectAltName=%s\n' "$san" > "$ext"
  openssl x509 -req -in "$dir/$name.csr" -CA "$out/ca.crt" -CAkey "$out/ca.key" -CAcreateserial \
    -CAserial "$out/ca.srl" -days 90 -sha256 -extfile "$ext" -out "$dir/$name.crt" 2>/dev/null
  rm -f "$ext" "$dir/$name.csr"
}

# HAProxy : PEM combiné (chaîne + clé), comme produit par le deploy hook certbot en prod.
leaf "$out/haproxy" localhost "DNS:localhost,IP:127.0.0.1"
cat "$out/haproxy/localhost.crt" "$out/ca.crt" "$out/haproxy/localhost.key" > "$out/haproxy/localhost.pem"
rm -f "$out/haproxy/localhost.crt" "$out/haproxy/localhost.key"
chmod 0644 "$out/haproxy/localhost.pem"

# WAF : certificat serveur + CA pour vérifier l'API.
leaf "$out/waf" waf "DNS:localhost,DNS:waf"
cp "$out/ca.crt" "$out/waf/ca.crt"
chmod 0644 "$out/waf/"*

# API : même emplacement / mêmes droits qu'en prod (tls.crt / tls.key).
leaf "$out/api" tls "DNS:localhost,DNS:api-prod"
chgrp "$group" "$out/api/tls.key"; chmod 0640 "$out/api/tls.key"; chmod 0644 "$out/api/tls.crt"

chmod 0600 "$out/ca.key"; rm -f "$out/ca.srl"
echo "PKI de test créée dans $out (90 jours) — CA : $out/ca.crt (curl --cacert $out/ca.crt https://localhost/…)"
