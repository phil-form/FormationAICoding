#!/usr/bin/env bash
# Lance l'API en dev : spring-boot:run (devtools) + debug JDWP sur 5005 (sans suspendre le démarrage).
# Hot reload : devtools redémarre quand target/classes change (build IDE délégué à Maven, ou ./mvnw compile).
set -euo pipefail
cd /app
exec ./mvnw -B spring-boot:run \
  -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005" \
  -Dspring-boot.run.profiles="${SPRING_PROFILES_ACTIVE:-dev}"
