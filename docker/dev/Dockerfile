# Image de développement : JDK + Maven, sources montées par volume, hot reload (devtools) et debug JDWP.
FROM maven:3.9.16-eclipse-temurin-25@sha256:93b8a14ea2f412782e4e842651273b4d903e35cc496284f178fbbe2d67d00976

ARG UID=1000
ARG GID=1000

# Utilisateur aligné sur l'hôte : les fichiers écrits dans le volume (target/) lui appartiennent.
RUN (getent group ${GID} || groupadd -g ${GID} dev) \
 && (getent passwd ${UID} && usermod -l dev -d /home/dev -m "$(getent passwd ${UID} | cut -d: -f1)" || useradd -u ${UID} -g ${GID} -m dev) \
 && mkdir -p /m2 /app /var/lib/formation/uploads \
 && chown ${UID}:${GID} /m2 /app /var/lib/formation/uploads && chmod 0700 /var/lib/formation/uploads

ENV MAVEN_USER_HOME=/m2 \
    MAVEN_OPTS="-Dmaven.repo.local=/m2/repository"

USER ${UID}:${GID}
WORKDIR /app

# Dépendances pré-téléchargées dans /m2 (copiées dans le volume nommé à sa création).
COPY --chown=${UID}:${GID} pom.xml mvnw ./
COPY --chown=${UID}:${GID} .mvn .mvn
RUN ./mvnw -B -q dependency:go-offline

COPY --chown=${UID}:${GID} docker/dev/entrypoint.sh /usr/local/bin/entrypoint.sh

EXPOSE 8080 5005
# Démarrage lent (compilation Maven au premier lancement) → période de grâce large.
HEALTHCHECK --interval=15s --timeout=3s --start-period=180s --retries=5 \
  CMD curl -fsS http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
