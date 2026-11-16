#!/bin/sh
# Test de recette OpenBao côté API (exécuté dans un conteneur openbao/openbao, sur le réseau du projet).
# Se connecte avec l'AppRole "api" (role-id / secret-id de CREDS_DIR) et vérifie :
#   - ce que l'API DOIT pouvoir faire (signature JWT, NISS, index, identifiants DB, KV) ;
#   - ce qu'elle NE DOIT PAS pouvoir faire (export de clé, administration, écriture KV, configuration DB…).
# Si DB_CREDS_OUT est défini, y écrit des identifiants app / migrator pour les vérifications SQL (check-phase1.sh).
set -u
: "${BAO_ADDR:?}" "${CREDS_DIR:=/creds}"

pass=0 fail=0
ok()  { pass=$((pass + 1)); echo "  ✔ $*"; }
ko()  { fail=$((fail + 1)); echo "  ✘ $*"; }
allowed() { desc=$1; shift; if "$@" >/dev/null 2>&1; then ok "autorisé : $desc"; else ko "devrait être autorisé : $desc"; fi; }
denied()  { desc=$1; shift; if "$@" >/dev/null 2>&1; then ko "devrait être REFUSÉ : $desc"; else ok "refusé : $desc"; fi; }

BAO_TOKEN=$(bao write -field=token auth/approle/login \
  role_id="$(cat "$CREDS_DIR/role-id")" secret_id="$(cat "$CREDS_DIR/secret-id")") \
  || { echo "Connexion AppRole impossible" >&2; exit 1; }
export BAO_TOKEN
echo "Connexion AppRole api : OK (politiques : $(bao token lookup -field=policies))"

echo "JWT (ES256 via Transit)"
input=$(printf 'eyJhbGciOiJFUzI1NiJ9.eyJzdWIiOiJ0ZXN0In0' | base64 | tr -d '\n')
sig=$(bao write -field=signature transit/sign/jwt input="$input" marshaling_algorithm=jws 2>/dev/null)
case "$sig" in vault:v*:*) ok "signature JWS ($(echo "$sig" | cut -d: -f2))" ;; *) ko "signature JWS absente" ;; esac
# Format JWS : r||s bruts (64 octets → 86 caractères base64url), pas du DER ASN.1.
raw=${sig##*:}
[ "${#raw}" -eq 86 ] && ok "signature au format JWS (64 octets r||s)" || ko "format de signature inattendu (${#raw} car.)"
denied  "vérification via Transit (faite localement par l'API avec la clé publique)" bao write transit/verify/jwt input="$input" signature="$sig" marshaling_algorithm=jws
allowed "lecture des clés publiques jwt" bao read transit/keys/jwt
denied  "export de la clé privée jwt" bao read transit/export/signing-key/jwt

echo "NISS (Transit AES-256-GCM + index HMAC)"
pt=$(printf '85073003328' | base64)
ct=$(bao write -field=ciphertext transit/encrypt/niss plaintext="$pt" 2>/dev/null)
case "$ct" in vault:v*:*) ok "chiffrement ($(echo "$ct" | cut -c1-20)…)" ;; *) ko "chiffrement" ;; esac
back=$(bao write -field=plaintext transit/decrypt/niss ciphertext="$ct" 2>/dev/null | base64 -d)
[ "$back" = 85073003328 ] && ok "déchiffrement = valeur d'origine" || ko "déchiffrement"
ct2=$(bao write -field=ciphertext transit/encrypt/niss plaintext="$pt" 2>/dev/null)
[ "$ct" != "$ct2" ] && ok "chiffrement non déterministe (IV aléatoire)" || ko "chiffrement déterministe"
h1=$(bao write -field=hmac transit/hmac/niss-index input="$pt" algorithm=sha2-256 2>/dev/null)
h2=$(bao write -field=hmac transit/hmac/niss-index input="$pt" algorithm=sha2-256 2>/dev/null)
[ -n "$h1" ] && [ "$h1" = "$h2" ] && ok "index aveugle déterministe" || ko "index aveugle"
denied  "export de la clé niss" bao read transit/export/encryption-key/niss
denied  "lecture de la clé niss-index (hmac)" bao read transit/export/hmac-key/niss-index
denied  "suppression / reconfiguration de la clé niss" bao write transit/keys/niss/config deletion_allowed=true
denied  "rotation de la clé niss" bao write -f transit/keys/niss/rotate
denied  "chiffrement avec la clé jwt" bao write transit/encrypt/jwt plaintext="$pt"

echo "Base de données (identifiants dynamiques)"
allowed "identifiants app" bao read database/creds/app
allowed "identifiants migrator" bao read database/creds/migrator
denied  "lecture de la configuration database (mot de passe admin)" bao read database/config/formation
denied  "modification d'un rôle database" bao write database/roles/app default_ttl=999h
denied  "rotate-root" bao write -f database/rotate-root/formation

echo "KV et administration"
allowed "lecture kv/formation" bao kv get -mount=kv formation
denied  "écriture kv/formation" bao kv put -mount=kv formation x=y
denied  "liste des politiques" bao policy list
denied  "lecture de la politique api" bao policy read api
denied  "création d'un secret_id" bao write -f auth/approle/role/api/secret-id
denied  "liste des moteurs (sys/mounts)" bao secrets list
denied  "création d'un token enfant avec d'autres politiques" bao token create -policy=deployer

if [ -n "${DB_CREDS_OUT:-}" ]; then
  umask 077
  {
    bao read -format=json database/creds/app | sed -n 's/.*"\(username\|password\|lease_id\)": "\([^"]*\)".*/APP_\1=\2/p'
    bao read -format=json database/creds/migrator | sed -n 's/.*"\(username\|password\)": "\([^"]*\)".*/MIG_\1=\2/p'
  } > "$DB_CREDS_OUT"
  chmod 0644 "$DB_CREDS_OUT"   # lu par check-phase1.sh sur l'hôte (dossier temporaire, dev uniquement)
fi

echo
echo "Résultat : $pass OK, $fail KO"
[ "$fail" -eq 0 ]
