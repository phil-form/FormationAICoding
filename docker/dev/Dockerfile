# Image de développement : JDK + Maven, sources montées par volume, hot reload (devtools) et debug JDWP.
FROM maven:3.9-eclipse-temurin-25

ARG UID=1000
ARG GID=1000

# Utilisateur aligné sur l'hôte : les fichiers écrits dans le volume (target/) lui appartiennent.
RUN (getent group ${GID} || groupadd -g ${GID} dev) \
 && (getent passwd ${UID} && usermod -l dev -d /home/dev -m "$(getent passwd ${UID} | cut -d: -f1)" || useradd -u ${UID} -g ${GID} -m dev) \
 && mkdir -p /m2 /app && chown ${UID}:${GID} /m2 /app

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
ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
