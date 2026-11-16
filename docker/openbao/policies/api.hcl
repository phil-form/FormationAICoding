# Politique de l'API (AppRole "api") — moindre privilège : opérations cryptographiques et identifiants DB.
# Aucune lecture de clé privée, aucun accès à sys/ ni à l'administration.

# JWT ES256 : signature par OpenBao, lecture des clés PUBLIQUES pour la vérification locale.
path "transit/sign/jwt" {
  capabilities = ["update"]
}
path "transit/keys/jwt" {
  capabilities = ["read"]
}

# NISS : chiffrement, déchiffrement, re-chiffrement après rotation, index aveugle.
# Lecture des MÉTADONNÉES de la clé niss (dernière version, pour le job de rewrap) — jamais le matériel de clé.
path "transit/keys/niss" {
  capabilities = ["read"]
}
path "transit/encrypt/niss" {
  capabilities = ["update"]
}
path "transit/decrypt/niss" {
  capabilities = ["update"]
}
path "transit/rewrap/niss" {
  capabilities = ["update"]
}
path "transit/hmac/niss-index" {
  capabilities = ["update"]
}

# Fichiers téléversés : chiffrement / déchiffrement des clés de données, rewrap, métadonnées de la clé file.
path "transit/keys/file" {
  capabilities = ["read"]
}
path "transit/encrypt/file" {
  capabilities = ["update"]
}
path "transit/decrypt/file" {
  capabilities = ["update"]
}
path "transit/rewrap/file" {
  capabilities = ["update"]
}

# Identifiants PostgreSQL dynamiques : runtime (DML) et migrations Flyway.
path "database/creds/app" {
  capabilities = ["read"]
}
path "database/creds/migrator" {
  capabilities = ["read"]
}

# Secrets statiques de l'application (lecture seule).
path "kv/data/formation" {
  capabilities = ["read"]
}

# Renouvellement / révocation de ses propres baux.
path "sys/leases/renew" {
  capabilities = ["update"]
}
path "sys/leases/revoke" {
  capabilities = ["update"]
}
