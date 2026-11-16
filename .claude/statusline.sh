#!/usr/bin/env bash
# Status line Claude Code (3 lignes max) :
#   1. ◆ modèle │ contexte (barre, %, tokens/taille) │ coût │ branche │ dossier
#   2. ⏱ session 5 h (barre, %, tokens, heure de reset) │ 📅 semaine (barre, %, tokens, date/heure de reset)
#   3. 🧪 tests (barre, %, classes, ETA, ✔/✘/↷) — seulement pendant un run et 15 min après
#
# Tokens session/semaine : somme des transcripts ~/.claude/projects/**/*.jsonl dans la fenêtre
# (fenêtre = resets_at - 5 h / - 7 j si rate_limits est fourni, sinon fenêtre glissante).
# Compte input + cache_creation + output ; STATUSLINE_CACHE_READ=1 ajoute les lectures de cache.
# Calcul en arrière-plan, mis en cache 30 s (STATUSLINE_USAGE_TTL).
# Couleurs dégradées si COLORTERM=truecolor|24bit, sinon ANSI 16 couleurs.

input="$(cat)"

IFS=$'\t' read -r dir model ctx_pct ctx_in ctx_size cost h5_pct h5_reset d7_pct d7_reset < <(
  jq -r '[
    (.workspace.project_dir // .cwd // "."),
    (.model.display_name // "?"),
    (.context_window.used_percentage // "" | tostring),
    (.context_window.total_input_tokens // "" | tostring),
    (.context_window.context_window_size // "" | tostring),
    (.cost.total_cost_usd // "" | tostring),
    (.rate_limits.five_hour.used_percentage // "" | tostring),
    (.rate_limits.five_hour.resets_at // "" | tostring),
    (.rate_limits.seven_day.used_percentage // "" | tostring),
    (.rate_limits.seven_day.resets_at // "" | tostring)
  ] | map(if . == "" then "-" else . end) | @tsv' <<<"$input")
for v in ctx_pct ctx_in ctx_size cost h5_pct h5_reset d7_pct d7_reset; do [[ ${!v} == "-" ]] && printf -v "$v" ''; done

now=$(date +%s)
E=$'\e[' N=$'\e[0m' D=$'\e[2m' B=$'\e[1m'
G=$'\e[32m' R=$'\e[31m' Y=$'\e[33m' C=$'\e[36m' BL=$'\e[34m' P=$'\e[35m'
SEP=" ${D}│${N} "
truecolor=0; [[ "$COLORTERM" == truecolor || "$COLORTERM" == 24bit ]] && truecolor=1
((truecolor)) && P=$'\e[38;2;114;102;234m'

# ---------------------------------------------------------------- helpers
int() { local x=${1%%.*}; echo "${x:-0}"; }

# Couleur d'un seuil : vert < 70, jaune < 90, rouge sinon.
lvl() { (( $1 >= 90 )) && echo "$R" || { (( $1 >= 70 )) && echo "$Y" || echo "$G"; }; }

# bar <pct> <largeur> [couleur fixe] : dégradé vert→rouge par cellule (truecolor) ou couleur unique.
GR=(46 116 186 241 239 236 233 231 211 192) GG=(204 195 186 196 161 126 101 76 66 57) GB=(113 89 64 15 24 34 44 60 50 43)
bar() {
  local pct=$1 w=$2 fixed=$3 fill i k s=""
  (( pct < 0 )) && pct=0; (( pct > 100 )) && pct=100
  fill=$(( (pct * w + 50) / 100 )); (( pct > 0 && fill == 0 )) && fill=1
  if [[ -z "$fixed" && $truecolor == 1 ]]; then
    for ((i = 0; i < fill; i++)); do
      k=$(( i * 10 / w ))
      s+="${E}38;2;${GR[k]};${GG[k]};${GB[k]}m█"
    done
    s+="${E}38;2;60;60;60m"
  else
    s+="${fixed:-$(lvl "$pct")}"
    for ((i = 0; i < fill; i++)); do s+="█"; done
    s+="$D"
  fi
  for ((i = fill; i < w; i++)); do s+="░"; done
  printf '%s' "$s$N"
}

# 1234 → 1.2k, 1234567 → 1.2M, 1.2G
human() {
  awk -v n="${1:-0}" 'BEGIN {
    if (n >= 1e9) printf "%.2fG", n/1e9; else if (n >= 1e6) printf "%.1fM", n/1e6;
    else if (n >= 1e3) printf "%.1fk", n/1e3; else printf "%d", n }'
}

# Durée restante lisible : 2h10, 45min, 3j 4h
left() {
  local s=$(( $1 - now )); (( s < 0 )) && s=0
  if (( s >= 86400 )); then printf '%dj' $((s / 86400))
  elif (( s >= 3600 )); then printf '%dh' $((s / 3600))
  else printf '%dmin' $((s / 60)); fi
}

JOURS=(dim. lun. mar. mer. jeu. ven. sam.)
day_fr() { echo "${JOURS[$(date -d "@$1" +%w)]} $(date -d "@$1" +%H:%M)"; }

# ---------------------------------------------------------------- tokens session / semaine (cache)
cache_dir="${XDG_CACHE_HOME:-$HOME/.cache}/claude-statusline"
cache="$cache_dir/usage.tsv"
mkdir -p "$cache_dir"
h5_start=$(( ${h5_reset:-0} > 0 ? h5_reset - 18000 : now - 18000 ))
d7_start=$(( ${d7_reset:-0} > 0 ? d7_reset - 604800 : now - 604800 ))

compute_usage() { # écrit "<tokens 5h>\t<tokens 7j>\t<calculé à>"
  local tmp="$cache.$$"
  find "${CLAUDE_CONFIG_DIR:-$HOME/.claude}/projects" -name '*.jsonl' -newermt "@$d7_start" -print0 2>/dev/null |
    xargs -0 -r cat 2>/dev/null |
    jq -Rr --argjson cr "${STATUSLINE_CACHE_READ:-0}" '
      fromjson? | select(.type == "assistant" and .message.usage != null and .timestamp != null)
      | .message.usage as $u
      | [ (.timestamp[0:19] + "Z" | fromdate),
          ((.message.id // "") + ":" + (.requestId // "")),
          (($u.input_tokens // 0) + ($u.cache_creation_input_tokens // 0) + ($u.output_tokens // 0)
            + (if $cr == 1 then ($u.cache_read_input_tokens // 0) else 0 end)) ]
      | @tsv' 2>/dev/null |
    awk -F'\t' -v s5="$h5_start" -v s7="$d7_start" -v now="$now" '
      $1 >= s7 && !seen[$2]++ { w += $3; if ($1 >= s5) h += $3 }
      END { printf "%d\t%d\t%d\n", h, w, now }' > "$tmp" && mv "$tmp" "$cache"
}

tok5="" tok7=""
if [[ -f "$cache" ]]; then IFS=$'\t' read -r tok5 tok7 cached_at < "$cache"; fi
if [[ ! -f "$cache" ]] || (( now - ${cached_at:-0} > ${STATUSLINE_USAGE_TTL:-30} )); then
  # Un seul calcul à la fois, détaché pour ne jamais bloquer l'affichage.
  ( exec 9>"$cache_dir/lock"; flock -n 9 || exit 0; compute_usage ) </dev/null >/dev/null 2>&1 &
  disown 2>/dev/null
fi

# ---------------------------------------------------------------- ligne 1 : modèle, contexte, git
branch="$(git -C "$dir" branch --show-current 2>/dev/null)"
dirty=""; [[ -n "$branch" && -n "$(git -C "$dir" status --porcelain -uno 2>/dev/null | head -1)" ]] && dirty="*"

l1="${P}◆${N} ${C}${model}${N}"
if [[ -n "$ctx_pct" ]]; then
  p=$(int "$ctx_pct")
  l1+="${SEP}ctx $(bar "$p" 10) $(lvl "$p")${p}%${N}"
  [[ -n "$ctx_in" && -n "$ctx_size" ]] && l1+=" ${D}$(human "$ctx_in")/$(human "$ctx_size")${N}"
  (( p >= 90 )) && l1+=" ${R}⚠${N}"
fi
[[ -n "$cost" ]] && l1+="${SEP}${Y}\$$(printf '%.2f' "$cost")${N}"
[[ -n "$branch" ]] && l1+="${SEP}${D}⎇ ${branch}${dirty}${N}"
l1+="${SEP}${BL}${dir##*/}${N}"

# ---------------------------------------------------------------- ligne 2 : session 5 h & semaine
seg() { # seg <icône> <libellé> <pct|vide> <tokens> <reset epoch|vide> <format reset>
  local out="$1 ${B}$2${N}" p
  if [[ -n "$3" ]]; then
    p=$(int "$3")
    out+=" $(bar "$p" 12) $(lvl "$p")${p}%${N}"
  fi
  out+=" ${D}·${N} $(human "$4") tok"
  if [[ -n "$5" ]]; then
    out+=" ${D}·${N} ↻ $6 ${D}($(left "$5"))${N}"
  else
    out+=" ${D}(fenêtre glissante)${N}"
  fi
  printf '%s' "$out"
}
l2="$(seg "⏱" "Session" "$h5_pct" "${tok5:-0}" "$h5_reset" "$([[ -n $h5_reset ]] && date -d "@$h5_reset" +%H:%M)")"
l2+="${SEP}$(seg "📅" "Semaine" "$d7_pct" "${tok7:-0}" "$d7_reset" "$([[ -n $d7_reset ]] && day_fr "$d7_reset")")"

printf '%s\n%s\n' "$l1" "$l2"

# ---------------------------------------------------------------- ligne 3 : tests (run-tests)
state="$dir/.claude/state/tests.json"
[[ -f "$state" ]] || exit 0
IFS=$'\t' read -r status scope pct done total tests passed ko skipped eta updated started current < <(
  jq -r '[.status, (.scope // "all"), .pct, .done_classes, .total_classes, .tests, .passed,
          (.failed + .errors), .skipped, .eta_s, .updated_at, (.started_at // .updated_at),
          ((.current // "") | split(".") | last // "")] | map(tostring) | @tsv' "$state" 2>/dev/null)
[[ -n "$status" ]] || exit 0
# Affiché pendant l'exécution, puis 15 min après la fin.
[[ "$status" == running ]] || (( now - updated < 900 )) || exit 0

mmss() { printf '%dm%02ds' $(($1 / 60)) $(($1 % 60)); }
case "$status" in
  running) col=$Y; icon="⏳"; label="${Y}${pct}%${N} ${D}(reste $((100 - pct))%)${N}" ;;
  passed)  col=$G; icon="✅"; label="${G}OK${N}" ;;
  failed)  col=$R; icon="❌"; label="${R}KO${N}" ;;
  *)       col=$R; icon="💥"; label="${R}ERREUR build${N}" ;;
esac
(( ko > 0 )) && [[ "$status" == running ]] && col=$R   # barre rouge dès le premier échec

l3="${B}Tests${N} ${D}[${scope}]${N} $(bar "$pct" 20 "$col") ${label}"
l3+=" ${D}·${N} ${done}/${total} classes"
l3+=" ${D}·${N} ${G}✔ ${passed}${N} ${R}✘ ${ko}${N} ${Y}↷ ${skipped}${N}"
if [[ "$status" == running ]]; then
  l3+=" ${D}· ETA${N} $(mmss "${eta:-0}")"
  [[ -n "$current" ]] && l3+=" ${D}· ${current:0:40}${N}"
else
  l3+=" ${D}· durée $(mmss $((updated - started))) · il y a $(( (now - updated) / 60 ))min${N}"
fi
printf "🧪%s %s\n" "$icon" "$l3"
