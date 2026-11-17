---
name: export-prompts
description: Exporte dans tmp/prompts.md tous les prompts des sessions Claude Code du projet (texte verbatim, horodatés, regroupés par session), avec sous chaque prompt les skills invoquées et les prompts délégués aux sous-agents - support de la formation « utilisation de l'IA ».
when_to_use: Quand l'utilisateur demande d'exporter, d'archiver ou de récapituler les prompts ; en fin de session de formation ; avant de partager le déroulé d'une démonstration.
argument-hint: "[--since AAAA-MM-JJ] [--session <id>] [--project <dossier>]… [--no-delegated] [--no-skills] [--out <fichier>]"
allowed-tools: Bash(.claude/skills/export-prompts/scripts/export-prompts.py:*)
---

# Export des prompts de formation

Arguments : `$ARGUMENTS` (transmis tels quels au script).

## Règles

- Le fichier `tmp/prompts.md` est **régénéré entièrement** à chaque export, à partir des transcripts de Claude Code
  (`~/.claude/projects/<dossier>/<session>.jsonl`) : ne jamais l'éditer à la main. Les commentaires pédagogiques
  vont dans un autre fichier (par exemple `tmp/prompts-commentaires.md`).
- Les prompts sont restitués **verbatim**, fautes de frappe comprises : c'est le matériau de la formation.
- Ne jamais lire ni recopier les transcripts dans la conversation : seul le script les lit.
- Les transcripts peuvent contenir des données personnelles ou des secrets collés dans un prompt : relire l'export
  avant de le diffuser et signaler à l'utilisateur tout élément sensible repéré (sans le recopier).

## Export automatique

L'export par défaut (sessions de `flotte/`) est relancé sans intervention par `.claude/hooks/export-prompts-on-end.sh`
(déclaré dans `.claude/settings.json`) :
- **à la fin de chaque réponse** de Claude (hook `Stop`, en arrière-plan, sans ralentir la session) ;
- **à la fin de chaque session** (hook `SessionEnd` : `exit`, `/clear`, déconnexion).

Journal : `.claude/state/export-prompts.log`. Un échec n'interrompt jamais la session. Cette skill reste utile pour
un export avec options (`--project`, `--since`…) ou pour contrôler les éléments sensibles avant diffusion.

## Étapes

1. Lancer `.claude/skills/export-prompts/scripts/export-prompts.py $ARGUMENTS`.
   - Par défaut : toutes les sessions lancées depuis `flotte/`.
   - `--project <dossier>` (répétable) : inclure les sessions d'un autre dossier, par exemple le dépôt parent de la
     formation (`--project . --project ../..`).
   - `--since` / `--session` : limiter la période ou les sessions.
   - `--no-delegated` / `--no-skills` : ne garder que les prompts tapés par l'utilisateur.
2. Lire l'en-tête de `tmp/prompts.md` (nombre de sessions et de prompts) et vérifier rapidement le sommaire.
3. Rechercher dans le fichier les motifs sensibles évidents (`password`, `token`, `secret`, `BEGIN .* KEY`, adresses
   e-mail) et les signaler à l'utilisateur.

## Contenu produit

- Sommaire des sessions (date, nombre de prompts).
- Par session : identifiant, dossier, branche git, version de Claude Code.
- Par prompt : numéro global, date et heure locales, texte intégral dans un bloc `text` ; les commandes tapées
  (`/upgrade-symfony 6.4 …`) apparaissent telles quelles.
- Sous chaque prompt : skills invoquées par Claude pendant le tour, et prompts délégués aux sous-agents (repliés
  dans un bloc `<details>`), qui montrent comment une demande est découpée et briefée.

## Rapport final

Chemin du fichier, nombre de sessions et de prompts exportés, éléments sensibles repérés à relire avant diffusion.
