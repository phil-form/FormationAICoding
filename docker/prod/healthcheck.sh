#!/bin/sh
# Actuator sur le port de management, lié à 127.0.0.1 dans le conteneur (non publié).
wget -qO- http://127.0.0.1:8081/actuator/health 2>/dev/null | grep -q '"status":"UP"'
