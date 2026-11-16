# Politique de la CI/CD (Jenkins) : renouveler le secret_id de l'API à chaque déploiement, rien d'autre.
path "auth/approle/role/api/secret-id" {
  capabilities = ["update"]
}
path "auth/approle/role/api/secret-id-accessor/destroy" {
  capabilities = ["update"]
}
path "auth/approle/role/api/role-id" {
  capabilities = ["read"]
}
