# Déploiement production — HAProxy + certbot

```
Internet ──80──▶ HAProxy ── /.well-known/acme-challenge/ ──▶ certbot (127.0.0.1:8888, pendant un renouvellement)
                        └── tout le reste ──▶ 301 https://
Internet ──443──▶ HAProxy (TLS 1.3, Let's Encrypt, limitation de débit, contrôle du Host)
                        └── TLS 1.3 re-chiffré, certificat vérifié ──▶ API Docker 127.0.0.1:8443 ──▶ PostgreSQL (réseau Docker)
```

- **Un seul certificat Let's Encrypt** (ECDSA P-256) pour `API_DOMAIN`, utilisé par HAProxy (frontal) **et** par l'API
  (HAProxy vérifie le certificat de l'API : `verify required` + `verifyhost`).
- **Renouvellement sans coupure** : certbot valide via HAProxy, le *deploy hook* installe le certificat
  (remplacement atomique) → l'API le recharge à chaud (~10 s, sans redémarrage), HAProxy est rechargé (`reload` sans perte de connexion).
- L'API n'est **jamais** exposée directement : publiée sur `127.0.0.1:8443` uniquement.

Fichiers de référence : `docker/prod/haproxy/haproxy.cfg`, `docker/prod/certbot/deploy-hook.sh`,
`docker-compose.prod.yml`. Un **WAF ModSecurity + OWASP CRS** s'intercale entre HAProxy et l'API ; sa configuration
de référence est celle de `docker-compose.test-prod.yml` (service `waf`), testée localement avec
`./start.sh --test-prod` + `docker/test-prod/check.sh` (attaques SQLi, XSS, traversal, RCE, Log4Shell, scanners
bloquées). Si le WAF tourne sur l'hôte avec HAProxy, `TRUSTED_PROXIES` doit couvrir l'IP par laquelle il atteint
l'API.

## 1. Préparation du serveur (une fois)

```bash
# Groupe dédié à la lecture de la clé TLS par le conteneur de l'API (utilisateur 10001)
sudo groupadd --system formation-tls
getent group formation-tls | cut -d: -f3          # → TLS_CERT_GID dans .env

sudo install -d -o root -g formation-tls -m 0750 /srv/formation/certs
sudo install -d -o root -g haproxy       -m 0750 /etc/haproxy/certs
sudo install -d -o root -g root          -m 0750 /etc/formation

# Configuration du deploy hook
sudo tee /etc/formation/deploy-hook.env >/dev/null <<'EOF'
API_DOMAIN=api.example.be
APP_CERT_DIR=/srv/formation/certs
APP_CERT_GROUP=formation-tls
EOF
sudo install -o root -g root -m 0750 docker/prod/certbot/deploy-hook.sh /etc/letsencrypt/renewal-hooks/deploy/formation.sh
```

`.env` de production (extrait) :

```dotenv
API_DOMAIN=api.example.be
API_BIND=127.0.0.1
API_HTTPS_PORT=8443
TLS_CERT_DIR=/srv/formation/certs
TLS_CERT_GID=<gid de formation-tls>
DOCKER_SUBNET=172.30.0.0/24
TRUSTED_PROXIES='172\.30\.0\.1'     # passerelle du réseau Docker = IP de HAProxy vue par l'API
CORS_ALLOWED_ORIGINS=https://app.example.be
```

## 2. Premier certificat (HAProxy pas encore démarré, port 80 libre)

```bash
sudo certbot certonly --standalone -d api.example.be \
  --key-type ecdsa --elliptic-curve secp256r1 \
  --deploy-hook /etc/letsencrypt/renewal-hooks/deploy/formation.sh
```

Le hook remplit `/srv/formation/certs` (API) et `/etc/haproxy/certs/api.example.be.pem` (HAProxy). Le
`systemctl reload haproxy` final échoue si HAProxy ne tourne pas encore : c'est sans conséquence ici.

## 3. HAProxy

```bash
sudo cp docker/prod/haproxy/haproxy.cfg /etc/haproxy/haproxy.cfg
sudo sed -i 's/presetenv API_DOMAIN .*/presetenv API_DOMAIN       api.example.be/' /etc/haproxy/haproxy.cfg
sudo haproxy -c -f /etc/haproxy/haproxy.cfg && sudo systemctl enable --now haproxy
```

## 4. Renouvellements via HAProxy (sans coupure)

Basculer le certificat de `--standalone` sur le port 80 vers le port local 8888 routé par HAProxy :

```bash
sudo certbot reconfigure --cert-name api.example.be \
  --http-01-address 127.0.0.1 --http-01-port 8888      # effectue un test de renouvellement (dry-run)
sudo certbot renew --dry-run                           # vérification
```

Le timer `certbot.timer` (paquet Debian/Ubuntu) renouvelle automatiquement ; le deploy hook s'exécute après chaque
renouvellement réussi.

## 5. OpenBao

Avant le premier démarrage de l'API : `docker/prod/openbao/bootstrap.sh` (parts Shamir → 1Password), puis le timer
de snapshot. Procédure complète : [`OPENBAO.md`](OPENBAO.md). Après chaque redémarrage du serveur, OpenBao est
**scellé** : `./start.sh --prod` guide le descellement (3 détenteurs de parts).

## 6. API

```bash
./start.sh --prod          # build + démarrage, attend le healthcheck
```

## Vérifications

```bash
curl -sI http://api.example.be/ | head -1                          # 301
curl -s -o /dev/null -w '%{http_code}\n' https://api.example.be/api/v1/x   # 401 (pas encore d'authentification)
echo | openssl s_client -connect api.example.be:443 -tls1_2 2>&1 | grep -c 'alert protocol version'   # 1 : TLS 1.2 refusé
ss -ltnp | grep 8443                                                # 127.0.0.1:8443 uniquement
```

Test externe recommandé : SSL Labs (note attendue A+ ; TLS 1.3 seul, HSTS preload).

## Points d'attention

- **HSTS preload** est activé (`includeSubDomains; preload`) : tous les sous-domaines de `API_DOMAIN` devront être
  en HTTPS. Ne soumettre le domaine à hstspreload.org qu'en connaissance de cause (retrait très lent).
- Si HAProxy n'est pas sur le même hôte que l'API : publier l'API sur l'interface privée (`API_BIND`), restreindre
  par pare-feu à l'IP de HAProxy, et mettre cette IP dans `TRUSTED_PROXIES`.
- Les dossiers de certificats sont montés **en lecture seule** ; le conteneur ne peut pas les modifier.
