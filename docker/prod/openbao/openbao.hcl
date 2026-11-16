# OpenBao — production (ADR 0001) : nœud unique Raft, TLS 1.3 interne, audit déclaratif, scellé au démarrage.
ui            = false
log_level     = "info"
# Raft gère sa propre mémoire ; recommandé à true avec le stockage intégré (et en conteneur sans IPC_LOCK).
disable_mlock = true

api_addr     = "https://openbao:8200"
cluster_addr = "https://openbao:8201"

storage "raft" {
  path    = "/openbao/file"
  node_id = "openbao-1"
}

listener "tcp" {
  address         = "0.0.0.0:8200"
  cluster_address = "0.0.0.0:8201"
  tls_cert_file   = "/openbao/tls/tls.crt"
  tls_key_file    = "/openbao/tls/tls.key"
  tls_min_version = "tls13"
}

# Audit obligatoire : OpenBao refuse les requêtes si aucun audit ne peut écrire (pas de trou dans la trace).
audit "file" "file" {
  description = "Journal d'audit (volume openbao-audit)"
  options {
    file_path = "/openbao/logs/audit.log"
    mode      = "0600"
  }
}
