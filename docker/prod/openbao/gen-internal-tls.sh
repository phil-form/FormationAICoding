#!/usr/bin/env bash
# PKI interne du lien API → OpenBao (réseau Docker interne, jamais exposé) :
#   - CA ECDSA P-256 (5 ans), créée une seule fois : ca.crt (distribué à l'API) / ca.key ;
#   - certificat serveur OpenBao (1 an) : tls.crt / tls.key, SAN openbao, openbao-prod, localhost, 127.0.0.1.
# Rejouer le script renouvelle uniquement le certificat serveur, puis : docker compose kill -s HUP openbao-prod
#
#   docker/prod/openbao/gen-internal-tls.sh [dossier=${OPENBAO_TLS_DIR:-./secrets/openbao-tls}] [groupe]
#
# Après création, mettre ca.key HORS du serveur (1Password) : elle ne sert qu'à renouveler le certificat serveur.
set -euo pipefail

dir="${1:-${OPENBAO_TLS_DIR:-./secrets/openbao-tls}}"
group="${2:-$(id -gn)}"
install -d -m 0750 "$dir"

if [[ ! -f "$dir/ca.crt" ]]; then
  [[ -f "$dir/ca.key" ]] || openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out "$dir/ca.key"
  chmod 0600 "$dir/ca.key"
  openssl req -x509 -new -key "$dir/ca.key" -sha256 -days 1826 -subj "/CN=Formation OpenBao internal CA" \
    -addext "basicConstraints=critical,CA:TRUE,pathlen:0" -addext "keyUsage=critical,keyCertSign,cRLSign" \
    -out "$dir/ca.crt"
  echo "CA interne créée : $dir/ca.crt (sauvegarder ca.key dans 1Password puis la retirer du serveur)"
fi
[[ -f "$dir/ca.key" ]] || { echo "ca.key absente : la récupérer depuis 1Password pour renouveler le certificat." >&2; exit 1; }

tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out "$tmp/tls.key"
openssl req -new -key "$tmp/tls.key" -subj "/CN=openbao" -out "$tmp/tls.csr"
cat > "$tmp/ext.cnf" <<'EOF'
basicConstraints=critical,CA:FALSE
keyUsage=critical,digitalSignature
extendedKeyUsage=serverAuth
subjectAltName=DNS:openbao,DNS:openbao-prod,DNS:localhost,IP:127.0.0.1
EOF
openssl x509 -req -in "$tmp/tls.csr" -CA "$dir/ca.crt" -CAkey "$dir/ca.key" -CAcreateserial -CAserial "$tmp/ca.srl" \
  -days 365 -sha256 -extfile "$tmp/ext.cnf" -out "$tmp/tls.crt" 2>/dev/null

# Remplacement atomique ; clé lisible par le groupe (le conteneur OpenBao y est ajouté via group_add).
install -m 0640 -g "$group" "$tmp/tls.key" "$dir/.tls.key.new" && mv -f "$dir/.tls.key.new" "$dir/tls.key"
install -m 0644 -g "$group" "$tmp/tls.crt" "$dir/.tls.crt.new" && mv -f "$dir/.tls.crt.new" "$dir/tls.crt"
chmod 0644 "$dir/ca.crt"
echo "Certificat serveur OpenBao émis (1 an) : $dir/tls.crt — expire le $(openssl x509 -in "$dir/tls.crt" -noout -enddate | cut -d= -f2)"
