#!/usr/bin/env bash
# Collecte des dépréciations après les tests — n'écrit JAMAIS dans le code : produit un inventaire dédupliqué.
#
#   .claude/skills/deprecations/scripts/collect.sh [--log <fichier>] [--no-compile]
#
#   --log         log du run de tests à analyser (défaut : .claude/state/test-run.log, produit par /run-tests)
#   --no-compile  ne relance pas la compilation détaillée (-Xlint) — logs du run de tests uniquement
#
# Sources
#   1. Compilateur (javac -Xlint:deprecation,removal) sur main ET test : compilation d'une COPIE du projet dans un
#      conteneur Maven (target/ et ses rapports ne sont pas touchés)
#   2. Plugins Maven (paramètres / goals dépréciés)
#   3. Log du run de tests : JVM (sun.misc.Unsafe, agents chargés dynamiquement…), Mockito, Spring (propriétés,
#      API), Hibernate (codes HHH9000xxxx), bibliothèques
#
# Sortie : .claude/state/audit/deprecations/<horodatage>/{findings.md,findings.tsv,compile.log}, lien « latest ».
# Code retour : 0 aucune dépréciation · 10 dépréciations trouvées · 2 erreur (log absent, compilation impossible).
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"

MAVEN_IMAGE="maven:3.9.16-eclipse-temurin-25@sha256:93b8a14ea2f412782e4e842651273b4d903e35cc496284f178fbbe2d67d00976"
log_file=".claude/state/test-run.log" compile=1
while (($#)); do
  case "$1" in
    --log) log_file="$2"; shift ;;
    --no-compile) compile=0 ;;
    -h|--help) sed -n '2,19p' "$0"; exit 0 ;;
    *) echo "Option inconnue : $1" >&2; exit 2 ;;
  esac
  shift
done

ts="$(date +%Y%m%d-%H%M%S)"
base=".claude/state/audit/deprecations"
out="$base/$ts"
mkdir -p "$out" "$HOME/.cache/formation-m2"
ln -sfn "$ts" "$base/latest"
error=0

[[ -s "$log_file" ]] || { echo "Log de tests absent ou vide : $log_file (lancer /run-tests d'abord)" >&2; error=1; }

# ---- 1/2. Compilation détaillée (copie du projet) ------------------------------------------------------
if ((compile)); then
  echo "▶ Compilation -Xlint:deprecation,removal (main + test) dans une copie du projet…" >&2
  docker run --rm -u "$(id -u):$(id -g)" -e HOME=/tmp -e MAVEN_USER_HOME=/m2 \
    -v "$PWD:/src:ro" -v "$HOME/.cache/formation-m2:/m2" "$MAVEN_IMAGE" sh -c '
      mkdir -p /tmp/b && cd /src && tar --exclude=./target --exclude=./.git --exclude=./secrets --exclude=./certs \
        --exclude=./backups --exclude=./.claude/state -cf - . | tar -xf - -C /tmp/b && cd /tmp/b &&
      ./mvnw -B -Dmaven.repo.local=/m2/repository -Dmaven.compiler.showDeprecation=true \
        -Dmaven.compiler.showWarnings=true "-Dmaven.compiler.compilerArgument=-Xlint:deprecation,removal" \
        clean test-compile' > "$out/compile.log" 2>&1 \
    || { echo "Compilation en échec (voir $out/compile.log)" >&2; error=1; }
fi

# ---- 3. Extraction et normalisation ---------------------------------------------------------------------
# Une ligne par occurrence : source <TAB> signature normalisée <TAB> exemple
extract() { # extract <fichier> <origine>
  awk -v origin="$2" '
    function norm(s) {
      gsub(/[0-9]{4}-[0-9]{2}-[0-9]{2}[T ][0-9:.,]+Z?/, "", s)     # horodatages
      gsub(/\[[^]]*(main|thread|Thread|pool|exec)[^]]*\]/, "", s)     # noms de thread
      gsub(/(\/tmp\/b|\/app|\/src)\//, "", s)                         # racines des conteneurs
      gsub(/ +/, " ", s); sub(/^ /, "", s); return s
    }
    function emit(src, sig, ex) { printf "%s\t%s\t%s\n", src, norm(sig), norm(ex) }
    # Bruit : options de compilation affichées par Maven, en-têtes JVM redondants avec la ligne « called by »
    /^\[INFO\] Compiling [0-9]+ source files/ { next }
    /terminally deprecated method in sun\.misc\.Unsafe has been called/ { next }
    /Please consider reporting this to the maintainers/ { next }
    # javac : [WARNING] /chemin/X.java:[l,c] message
    /^\[WARNING\] .*\.java:\[[0-9]+,[0-9]+\]/ && /deprecat|removal|marked for removal/ {
      src = ($0 ~ /\/src\/test\//) ? "compilateur (tests)" : "compilateur (main)"
      line = $0; sub(/^\[WARNING\] /, "", line)
      sig = line; sub(/:\[[0-9]+,[0-9]+\]/, "", sig)                  # signature : fichier + message (sans ligne)
      emit(src, sig, line); next
    }
    # résumé javac sans -Xlint (log de tests)
    /uses or overrides a deprecated API|Recompile with -Xlint:deprecation/ { emit("compilateur (résumé)", $0, $0); next }
    # plugins Maven
    /^\[WARNING\]/ && /deprecated/ && !/\.java:/ { emit("Maven (plugin)", $0, $0); next }
    # JVM : bloc « terminally deprecated » → retenir la ligne « called by »
    /^WARNING: sun\.misc\.Unsafe::/ { emit("JVM", $0, $0); next }
    /^WARNING: .* has been called by / { emit("JVM", $0, $0); next }
    /^WARNING: A Java agent has been loaded dynamically/ { l = $0; sub(/\(.*\)/, "(…)", l); emit("JVM", l, $0); next }
    /^WARNING: .*(deprecat|will be removed|future release)/ && !/terminally deprecated method in sun\.misc\.Unsafe has been called/ { emit("JVM", $0, $0); next }
    /Mockito is currently self-attaching/ { emit("Mockito", "Mockito is currently self-attaching (inline-mock-maker) : ne fonctionnera plus dans un futur JDK", $0); next }
    /HHH9000[0-9]+/ { m = $0; match(m, /HHH9000[0-9]+.*/); emit("Hibernate", substr(m, RSTART), $0); next }
    /[Dd]eprecated (configuration )?propert|[Pp]roperty .* is deprecated/ { emit("Spring (propriété)", $0, $0); next }
    /[Dd]eprecat|marked for removal|will be removed in/ && !/^\[INFO\] Tests run/ { emit("Autre (" origin ")", $0, $0) }
  ' "$1"
}

: > "$out/raw.tsv"
[[ -s "$log_file" ]] && extract "$log_file" "tests" >> "$out/raw.tsv"
[[ -s "$out/compile.log" ]] && extract "$out/compile.log" "compilation" >> "$out/raw.tsv"
# Le résumé javac est redondant si la compilation détaillée a produit les avertissements précis.
if grep -q '^compilateur (main)\|^compilateur (tests)' "$out/raw.tsv"; then
  grep -v '^compilateur (résumé)' "$out/raw.tsv" > "$out/raw.tmp" && mv "$out/raw.tmp" "$out/raw.tsv"
fi

# Dédoublonnage : source + signature → nombre d'occurrences + un exemple
awk -F'\t' '{ k = $1 "\t" $2; n[k]++; if (!(k in ex)) ex[k] = $3 }
            END { for (k in n) printf "%d\t%s\t%s\n", n[k], k, ex[k] }' "$out/raw.tsv" \
  | sort -t$'\t' -k2,2 -k1,1nr > "$out/findings.tsv"
total=$(wc -l < "$out/findings.tsv")

{
  echo "# Dépréciations — $(date '+%F %T')"
  echo
  echo "Sources : log de tests \`$log_file\`$( ((compile)) && echo ", compilation -Xlint (\`$out/compile.log\`)")"
  echo
  if ((total == 0)); then
    echo "Aucune dépréciation détectée."
  else
    echo "**$total dépréciation(s) distincte(s)**"
    echo
    echo "| Source | Occurrences | Signature |"
    echo "|---|---|---|"
    awk -F'\t' '{ s = $3; gsub(/\|/, "\\|", s); printf "| %s | %d | `%s` |\n", $2, $1, substr(s, 1, 220) }' "$out/findings.tsv"
  fi
  echo
  echo "_Inventaire brut : \`$out/findings.tsv\` (occurrences, source, signature, exemple). Aucune modification de code._"
} > "$out/findings.md"

cat "$out/findings.md"
((error)) && exit 2
((total > 0)) && exit 10
exit 0
