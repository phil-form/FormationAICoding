#!/bin/sh
# shellcheck disable=SC2086  # JAVA_OPTS est volontairement découpé en arguments
exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher "$@"
