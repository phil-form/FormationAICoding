#!/bin/sh
# apt-get update, avec repli sur archive.debian.org pour les distributions Debian archivées
# (buster pour PHP 7.2, bullseye pour PHP 7.4 une fois archivée).
set -e
if apt-get update; then exit 0; fi
echo "apt-get update en échec : bascule sur archive.debian.org" >&2
for f in /etc/apt/sources.list /etc/apt/sources.list.d/*.list; do
  [ -f "$f" ] || continue
  sed -i -e 's#https\?://deb.debian.org/debian-security#http://archive.debian.org/debian-security#g' \
         -e 's#https\?://security.debian.org/debian-security#http://archive.debian.org/debian-security#g' \
         -e 's#https\?://deb.debian.org/debian#http://archive.debian.org/debian#g' \
         -e '/-updates/d' "$f"
done
echo 'Acquire::Check-Valid-Until "false";' > /etc/apt/apt.conf.d/99archive
apt-get update
