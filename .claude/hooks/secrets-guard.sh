#!/usr/bin/env bash
# Garde-fou PreToolUse « secrets » de TOUTE la session (session principale et sous-agents), déclaré dans
# .claude/settings.json. Empêche Claude de lire, écrire, rechercher ou afficher des données sensibles :
#   - fichiers d'environnement (.env, .env.dev, .env.local…), sauf modèles (.env.example, .env.dist, *.example)
#     et les fichiers passés par --allow-env <chemin relatif au projet> (flotte : .env et .env.test Flex, commités
#     sans secret par règle de migration) ;
#   - configuration locale Symfony (app/config/parameters.yml) et caches compilés (app/cache, var/cache : le
#     conteneur compilé contient kernel.secret), y compris dans .ref/ ;
#   - clés, certificats, magasins de clés, jetons (*.key, *.pem, *.p12, *.pfx, *.jks, *.keystore, *.token, id_rsa…),
#     dossiers secrets/ et certs/ privés, coffres Symfony (config/secrets/), auth.json Composer ;
#   - dumps de base (init_db/, *.dump, *.sql.gz) : données personnelles ;
#   - identifiants de l'utilisateur (~/.ssh, ~/.gnupg, ~/.aws, ~/.kube, ~/.docker/config.json, ~/.netrc, ~/.pgpass,
#     ~/.git-credentials, ~/.config/gh, ~/.claude/.credentials.json) et transcripts bruts (~/.claude/projects/*.jsonl,
#     lus uniquement par le script de /export-prompts) ;
#   - commandes qui affichent l'environnement ou des secrets : env/printenv, docker inspect, docker compose config,
#     lecture de /proc/*/environ, lectures OpenBao/Vault (read, kv get, token, login, operator init/unseal) ;
#   - recherches récursives qui ignorent .gitignore (grep -r sur un dossier qui contient un fichier sensible,
#     rg --no-ignore / -uu) : utiliser l'outil Grep ou « git grep », qui respectent .gitignore.
# Exit 2 = refus (message renvoyé à Claude). Garde-fou, pas sandbox : un script appelé peut toujours lire ce qu'il
# veut ; seules les commandes tapées par Claude sont contrôlées. L'utilisateur garde la main avec « ! commande ».
# Usage : secrets-guard.sh [--allow-env <chemin relatif au projet>]…   (entrée : JSON du hook PreToolUse)
# Test : .claude/hooks/secrets-guard.test.sh
set -uo pipefail

deny() { echo "⛔ [secrets-guard] $*" >&2; exit 2; }
command -v jq >/dev/null 2>&1 || deny "jq absent : contrôle impossible, outil refusé par prudence."

input="$(cat)"
tool="$(jq -r '.tool_name // ""' <<<"$input")"
cwd="$(jq -r '.cwd // ""' <<<"$input")"
field() { jq -r --arg k "$1" '.tool_input[$k] // ""' <<<"$input"; }

project="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ALLOWED_ENV=()
while (($#)); do
  case "$1" in
    --allow-env) ALLOWED_ENV+=("$project/${2:?--allow-env <chemin>}"); shift 2 ;;
    *) shift ;;
  esac
done
[[ -n "$cwd" && -d "$cwd" ]] || cwd="$project"
home="${HOME:-/nonexistent}"

# Chemin absolu normalisé (~ développé, relatif résolu depuis le cwd de l'outil).
absolute() {
  local p="$1"
  [[ "$p" == "~" ]] && p="$home"
  [[ "$p" == "~/"* ]] && p="$home/${p#\~/}"
  [[ "$p" != /* ]] && p="$cwd/$p"
  realpath -m -- "$p" 2>/dev/null || echo "$p"
}

# Raison si le chemin est sensible, rien sinon.
sensitive_reason() {
  local p; p="$(absolute "$1")"
  local base="${p##*/}" lower
  lower="${base,,}"

  # Fichiers d'environnement.
  if [[ "$base" == .env || "$base" == .env.* ]]; then
    case "$base" in
      .env.example|.env.dist|.env.*.example|.env.*.dist) ;;
      *)
        local a ok=""
        for a in "${ALLOWED_ENV[@]}"; do [[ "$p" == "$a" ]] && ok=1; done
        [[ -z "$ok" ]] && { echo "fichier d'environnement ($base)"; return; } ;;
    esac
  fi
  # Symfony : configuration locale, caches compilés, coffre de secrets.
  [[ "$base" == parameters.yml || "$base" == parameters.yaml ]] && { echo "configuration locale Symfony (secret, identifiants)"; return; }
  [[ "$p" =~ /(app|var)/cache(/|$) ]] && { echo "cache compilé Symfony (contient kernel.secret)"; return; }
  [[ "$p" =~ /config/secrets(/|$) ]] && { echo "coffre de secrets Symfony"; return; }
  # Dossiers de secrets, dumps.
  [[ "$p" =~ /secrets(/|$) ]] && { echo "dossier secrets/"; return; }
  [[ "$p" =~ /init_db(/|$) ]] && { echo "dumps de base (données personnelles)"; return; }
  [[ "$p" =~ /\.worktree-backup/.*/\.env ]] && { echo "fichier d'environnement sauvegardé"; return; }
  # Clés, certificats, jetons.
  case "$lower" in
    *.key|*.pem|*.p12|*.pfx|*.jks|*.keystore|*.kdbx|*.token|*.crt.key|id_rsa*|id_ed25519*|id_ecdsa*|id_dsa*|\
    *.dump|*.sql.gz|*.backup|auth.json|.htpasswd|.netrc|.pgpass|.git-credentials|.npmrc|.pypirc|credentials|credentials.json|.credentials.json)
      # Les certificats d'autorités publiques embarqués dans les dépendances ne sont pas des secrets.
      [[ "$p" =~ /(site-packages|node_modules|vendor)/ && "$lower" == *.pem ]] || { echo "clé, certificat, jeton ou identifiant ($base)"; return; } ;;
  esac
  # Identifiants de l'utilisateur.
  local d
  for d in .ssh .gnupg .aws .kube .azure .config/gh .config/gcloud .docker/config.json .claude/.credentials.json .vault-token; do
    [[ "$p" == "$home/$d" || "$p" == "$home/$d/"* ]] && { echo "identifiants de l'utilisateur (~/$d)"; return; }
  done
  [[ "$p" == "$home/.claude/projects/"*.jsonl ]] && { echo "transcript brut Claude Code (lu seulement par /export-prompts)"; return; }
  [[ "$p" == /proc/*/environ ]] && { echo "variables d'environnement d'un processus"; return; }
  return 0
}

check_path() {
  local p="$1" what="$2" r
  [[ -z "$p" ]] && return 0
  r="$(sensitive_reason "$p")"
  [[ -n "$r" ]] && deny "$what de '$p' refusé : $r. Demande à l'utilisateur de le consulter lui-même (« ! commande ») s'il le juge nécessaire, sans en recopier le contenu."
  return 0
}

# Le dossier (ou un sous-dossier, profondeur limitée) contient-il un fichier sensible ? Renvoie le premier trouvé.
contains_sensitive() {
  local dir; dir="$(absolute "$1")"
  [[ -d "$dir" ]] || return 1
  local f
  while IFS= read -r f; do
    [[ -n "$(sensitive_reason "$f")" ]] && { echo "$f"; return 0; }
  done < <(find "$dir" -maxdepth 6 \( -name .git -o -name node_modules -o -name vendor -o -name .venv -o -name target \) -prune -o \
             \( -name '.env*' -o -name 'parameters.y*ml' -o -name '*.key' -o -name '*.pem' -o -name '*.token' -o -name '*.p12' \
                -o -name 'id_rsa*' -o -name 'auth.json' -o -type d \( -name secrets -o -name init_db -o -name cache \) \) -print 2>/dev/null)
  return 1
}

check_bash() {
  local cmd; cmd="$(field command)"
  # $HOME / ${HOME} développés : « cat $HOME/.ssh/id_rsa » est contrôlé comme « cat ~/.ssh/id_rsa ».
  cmd="${cmd//\$\{HOME\}/$home}"; cmd="${cmd//\$HOME/$home}"
  # Texte des commandes contrôlées : contenu entre guillemets RETIRÉ (motifs de grep, messages), sauf s'il est
  # exécuté (sh -c, eval, docker exec … sh -c) : il est alors gardé, guillemets remplacés par des espaces.
  local sh re_exec='(^|[[:space:]])(-c|eval)[[:space:]]'
  if [[ "$cmd" =~ $re_exec ]]; then sh="$(tr "'\"" '  ' <<<"$cmd")"
  else sh="$(sed -E "s/'[^']*'//g; s/\"[^\"]*\"//g" <<<"$cmd")"; fi
  local lc="${sh,,}"
  local re_env='(^|[;&|`($[:space:]])(printenv|env)[[:space:]]*($|[;&|)])'
  local re_printenv='(^|[;&|`($[:space:]])printenv[[:space:]]'
  local re_fmt='--format[=[:space:]]*.?\{\{[^}]*(\.State|\.RestartCount|\.Id|\.Image|\.Name|\.Config\.Image|\.NetworkSettings)[^}]*\}\}'

  # Commandes qui affichent l'environnement ou des secrets.
  [[ "$sh" =~ $re_env || "$sh" =~ $re_printenv ]] && deny "Affichage des variables d'environnement refusé (env/printenv)."
  [[ "$sh" =~ (^|[;&|[:space:]])export[[:space:]]+-p ]] && deny "Affichage des variables exportées refusé."
  [[ "$sh" =~ docker[[:space:]]+(container[[:space:]]+)?inspect ]] && [[ ! "$cmd" =~ $re_fmt ]] \
    && deny "docker inspect refusé sans --format ciblé (.State, .RestartCount, .Id, .Image, .Name, .NetworkSettings) : la sortie complète contient les variables d'environnement."
  [[ "$sh" =~ docker[[:space:]]+(image[[:space:]]+)?history.*--no-trunc ]] && deny "docker history --no-trunc refusé (peut révéler des secrets de build)."
  [[ "$sh" =~ (docker[[:space:]]+compose|docker-compose)([[:space:]]+[^|;&]*)?[[:space:]]config([[:space:]]|$) ]] \
    && deny "docker compose config refusé : il interpole et affiche les valeurs du .env. Lire le fichier compose directement."
  [[ "$sh" =~ /proc/[^[:space:]]*/environ ]] && deny "Lecture de /proc/*/environ refusée."
  [[ "$lc" =~ (^|[;&|[:space:]/])(bao|vault)[[:space:]]+(read|kv[[:space:]]+get|token|login|print|operator[[:space:]]+(init|unseal|generate-root|rekey)|write[[:space:]].*transit/(export|backup)) ]] \
    && deny "Lecture de secrets OpenBao/Vault refusée (read, kv get, token, login, operator init/unseal…)."
  [[ "$lc" =~ (^|[;&|[:space:]])(security[[:space:]]+find-|secret-tool[[:space:]]+lookup|pass[[:space:]]+show|gpg[[:space:]].*--decrypt|gh[[:space:]]+auth[[:space:]]+token) ]] \
    && deny "Lecture d'un gestionnaire de secrets refusée."

  # Recherches qui ignorent .gitignore.
  [[ "$sh" =~ (^|[;&|[:space:]])rg[[:space:]].*(--no-ignore|-uu|[[:space:]]-u([[:space:]]|$)) ]] && deny "rg sans .gitignore refusé : il lirait les fichiers sensibles ignorés."

  # Jetons du texte de la commande : tout chemin sensible cité est refusé.
  local tok cleaned
  # Découpe grossière sur les séparateurs shell ; on retire guillemets et préfixes d'option (--file=…).
  for tok in $(tr ';&|()<>`"'\''=,' '            ' <<<"$cmd"); do
    cleaned="${tok#@}"
    [[ "$cleaned" == -* ]] && continue
    [[ "$cleaned" == *'*'* || "$cleaned" == *'?'* ]] && cleaned="${cleaned//\*/x}"   # glob : tester le motif tel quel
    cleaned="${cleaned//\?/x}"
    [[ "$cleaned" == *'$'* ]] && continue
    local r; r="$(sensitive_reason "$cleaned")"
    [[ -n "$r" ]] && deny "Commande refusée : elle cite '$tok' ($r). Demande à l'utilisateur de le faire lui-même (« ! commande ») si nécessaire."
  done

  # grep / egrep / fgrep récursif : refusé si une cible contient un fichier sensible (ne respecte pas .gitignore).
  if [[ "$cmd" =~ (^|[;&|[:space:]])(grep|egrep|fgrep)[[:space:]]([^;&|]*[[:space:]])?-[a-zA-Z]*[rR] || "$cmd" =~ (^|[;&|[:space:]])(grep|egrep|fgrep)[[:space:]].*--(recursive|dereference-recursive) ]]; then
    local seg targets=() t found
    seg="$(grep -oE '(^|[;&|[:space:]])(grep|egrep|fgrep)[[:space:]][^;&|]*' <<<"$cmd" | head -1)"
    for t in $seg; do
      [[ "$t" == -* || "$t" == grep || "$t" == egrep || "$t" == fgrep ]] && continue
      t="${t//[\"\']/}"
      [[ -d "$(absolute "$t")" ]] && targets+=("$t")
    done
    ((${#targets[@]})) || targets=(".")
    for t in "${targets[@]}"; do
      if found="$(contains_sensitive "$t")"; then
        deny "grep récursif sur '$t' refusé : il lirait '${found#"$cwd/"}' (ignoré par git, sensible). Utilise l'outil Grep ou « git grep », qui respectent .gitignore, ou cible un sous-dossier sans secret."
      fi
    done
  fi
  return 0
}

case "$tool" in
  Read)          check_path "$(field file_path)" "Lecture" ;;
  Edit|Write)    check_path "$(field file_path)" "Écriture" ;;
  NotebookEdit)  check_path "$(field notebook_path)" "Écriture" ;;
  Grep)          check_path "$(field path)" "Recherche" ;
                 g="$(field glob)"; g="${g##*/}"
                 if [[ -n "$g" ]]; then
                   for n in .env .env.local .env.dev .env.prod parameters.yml x.key x.pem x.token x.p12 id_rsa auth.json; do
                     # shellcheck disable=SC2053
                     [[ "$n" == $g ]] && deny "Recherche avec le filtre '$(field glob)' refusée : il cible des fichiers sensibles ($n)."
                   done
                 fi ;
                 [[ "$(jq -r '.tool_input | tostring' <<<"$input")" =~ \"(no_ignore|hidden|-uu)\" ]] && deny "Recherche sans .gitignore refusée." ;;
  Glob)          : ;;   # liste des noms seulement, jamais de contenu
  Bash)          check_bash ;;
  *)             : ;;
esac
exit 0
