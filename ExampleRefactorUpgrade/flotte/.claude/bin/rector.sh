#!/usr/bin/env bash
# rector.sh <step-NN> [--apply] [--only <Règle\Complète>]… [--filter <motif>] [--list]
# Rector pour une marche de la migration (rector/step-NN.php, conteneur tools). Voir rector/README.md.
#   (défaut)      simulation (--dry-run) : diff dans .claude/state/migration/rector/step-NN-dry-run.diff
#   --apply       applique les changements (réservé au migrator, après relecture du diff ; jamais sur tests/)
#   --only R      limite à une règle (répétable) : un lot de /fix-deprecations = une famille de règles
#   --filter M    limite aux fichiers correspondant au motif (option --filter de Rector)
#   --list        liste les règles actives de la marche, puis s'arrête
# Génère d'abord le manifeste des versions visées (rector/targets.json → .claude/state/migration/rector/targets/
# step-NN/vendor/composer/installed.json) : Rector 2 active ses règles liées aux versions d'après ce manifeste.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/_common.sh"
require_compose
step="${1:-}"; shift || true
[[ "$step" =~ ^step-0[1-7]$ ]] || die "usage: rector.sh <step-01…step-07> [--apply] [--only Règle] [--filter motif] [--list]" 2
[[ -f "$ROOT/rector/$step.php" ]] || die "Configuration absente : rector/$step.php"
apply=0; list=0; extra=()
while (($#)); do
  case "$1" in
    --apply) apply=1; shift ;;
    --list) list=1; shift ;;
    --only) extra+=("--only=$2"); shift 2 ;;
    --filter) extra+=("--filter=$2"); shift 2 ;;
    *) die "Option inconnue : $1" ;;
  esac
done
if ((apply)) && is_frozen; then
  # Le gel protège tests/ ; Rector ne touche jamais tests/ (rector/common.php), on le signale seulement.
  info "état gelé : tests/ reste intouchable (exclu de Rector)"
fi

out="$STATE/rector"; mkdir -p "$out/targets/$step/vendor/composer" "$out/cache"
python3 - "$ROOT/rector/targets.json" "$step" "$out/targets/$step/vendor/composer/installed.json" <<'PY'
import json, sys
cfg, step, dest = json.load(open(sys.argv[1])), sys.argv[2], sys.argv[3]
t = cfg["steps"][step]
pkgs = [{"name": c, "version": t["symfony"], "version_normalized": t["symfony"] + ".0", "type": "library"} for c in cfg["symfony_components"]]
for name, v in t.items():
    if "/" in name:
        pkgs.append({"name": name, "version": v, "version_normalized": v + ".0", "type": "library"})
json.dump({"packages": pkgs, "dev": False, "dev-package-names": []}, open(dest, "w"), indent=1)
print(f"› manifeste {step} : PHP {t['php']}, Symfony {t['symfony']}, " + ", ".join(f"{k} {v}" for k, v in t.items() if "/" in k), file=sys.stderr)
PY

if ((list)); then
  exec "$ROOT/.claude/bin/tools.sh" rector list-rules --config "/srv/app/rector/$step.php"
fi
mode=dry-run; flags=(--dry-run); ((apply)) && { mode=apply; flags=(); }
log="$out/$step-$mode.diff"
set +e
"$ROOT/.claude/bin/tools.sh" rector process --config "/srv/app/rector/$step.php" --no-progress-bar "${flags[@]}" "${extra[@]}" > "$log" 2>&1
rc=$?
set -e
changed="$(grep -cE '^[0-9]+\) ' "$log" || true)"
info "rector $step ($mode) : code $rc, $changed fichier(s) concerné(s) — détail : ${log#$ROOT/}"
grep -E '^ *\* [A-Za-z\\]+Rector' "$log" | sed 's/^ *\* //' | sort | uniq -c | sort -rn | sed 's/^/  règle appliquée : /' >&2 || true
grep -E "^\s*\[ERROR\]|Fatal error|PHP Parse error" "$log" | head -5 >&2 || true
# Rector renvoie 2 en dry-run quand il y a des changements à faire : ce n'est pas une erreur.
if ((rc == 2 && !apply)); then exit 0; fi
exit $rc
