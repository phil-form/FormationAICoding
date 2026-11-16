# Politique des sauvegardes : snapshot Raft uniquement (timer systemd sur le serveur).
path "sys/storage/raft/snapshot" {
  capabilities = ["read"]
}
