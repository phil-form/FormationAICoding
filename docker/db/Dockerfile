# PostgreSQL 18 (Alpine), durci — plan de correction 2026-11-16, décision D1.
# - Suppression de gosu : binaire Go embarquant une bibliothèque standard vulnérable (26 HIGH/CRITICAL au scan),
#   utilisé uniquement par le point d'entrée pour quitter root.
# - Exécution directe en utilisateur postgres (UID 70) : PostgreSQL ne démarre plus jamais en root. Le point d'entrée
#   officiel le gère (initialisation comprise) quand il n'est pas root.
FROM postgres:18.7-alpine@sha256:b6972e1115a4ff8eba5d7f7d19184926d1e16d7d1136ae1e1a7ebf286da0f015

RUN rm -f /usr/local/bin/gosu

USER postgres
