# TP8 — Démarrer un projet avec Claude (1 journée)

**Objectif** : partir d'une analyse fonctionnelle et monter, avec Claude Code, un projet prêt à être développé en *vibe coding* sans perdre le contrôle : contexte projet, skills, garde-fous automatiques, traçabilité des règles métier.

**Support** : le document *Analyse — Gestion de flotte « Delvaux & Fils »* (`analyse-gestion-de-flotte.md`). Langage et framework libres.

**Règle du TP** : Claude écrit le code, vous décidez. Chaque décision prise (stack, structure, règle interprétée) est notée dans `DECISIONS.md`, avec qui l'a prise : vous ou Claude.

## Exercice A — Lire l'analyse avec Claude (45 min)

1. Donnez l'analyse à Claude et demandez-lui la liste des **ambiguïtés, contradictions et informations manquantes**, sans y répondre.
2. Pour chacune, rédigez la question à poser au client dans `QUESTIONS.md`.
3. Demandez ensuite à Claude un plan d'implémentation. Repérez dans ce plan chaque endroit où il a **tranché seul** une question de l'étape 1 sans le dire.
4. Le formateur joue le client : il répond à vos questions. Reportez les réponses dans `DECISIONS.md`.

## Exercice B — Démarrer le projet (1 h)

1. Demandez à Claude, en **mode plan**, trois stacks possibles pour ce projet avec leurs compromis. Choisissez-en une vous-mêmes et justifiez en deux lignes dans `DECISIONS.md`.
2. Faites générer le squelette : structure, base de données avec schéma versionné, une route de santé, un premier test qui passe, `README` d'installation.
3. Lancez `/init` pour générer le `CLAUDE.md`, puis réécrivez-le : moins de 60 lignes, commandes exactes, conventions, interdits, renvoi vers l'analyse pour le métier.
4. Vérification : un binôme voisin clone votre dépôt et doit lancer l'application et les tests en suivant **uniquement** le `README`.

## Exercice C — Créer les skills du projet (1 h 30)

Un skill est un dossier `.claude/skills/<nom>/` contenant un `SKILL.md` : un en-tête YAML (`name`, `description`) puis les instructions. Claude le charge quand la description correspond à la demande, ou quand vous tapez `/<nom>`.

Créez au minimum ces trois skills, adaptés à **votre** stack :

| Skill | Rôle |
| --- | --- |
| `nouvelle-entite` | Ajouter une entité de l'analyse de bout en bout : schéma et migration, validation, logique, routes, contrôle d'accès, tests, suppression logique (RM10) |
| `regle-metier` | Implémenter une règle RMx en TDD : test nommé d'après la règle d'abord, implémentation ensuite, traçabilité dans `REGLES.md` |
| `revue-securite` | Relire un diff : contrôle d'accès (RM9), secrets, validation des entrées, téléversement de fichiers |

Pour chaque skill :

1. Faites-le rédiger par Claude, puis **relisez-le ligne par ligne** : chaque instruction doit être vérifiable et propre à votre projet.
2. Testez le déclenchement automatique : formulez une demande sans nommer le skill (« ajoute les types de véhicule »). S'il ne se déclenche pas, améliorez la `description`, pas la demande.
3. Testez le résultat : `/nouvelle-entite` sur « Type de véhicule » doit produire une entité complète, testée et protégée, sans autre consigne.

## Exercice D — Des garde-fous qui ne dépendent pas du modèle (45 min)

`CLAUDE.md` et les skills sont des consignes : le modèle les suit généralement, sans garantie. Un **hook** est une commande que Claude Code exécute à coup sûr à un moment précis.

1. Dans `.claude/settings.json`, ajoutez un hook `PostToolUse` sur `Edit|Write` qui formate et analyse (linter) le fichier modifié.
2. Ajoutez une règle de permission qui interdit à Claude de lire les fichiers de secrets (`.env`).
3. Démontrez que les deux fonctionnent : provoquez volontairement le cas et montrez le résultat.

## Exercice E — Vibe coding encadré (2 h)

Avec vos skills, faites implémenter par Claude les **réservations** et leurs règles RM1, RM2, RM3 et RM8.

- Mode plan d'abord, plan validé par vous avant toute écriture.
- Une règle = un commit, avec son test nommé d'après la règle.
- Avant chaque commit : `/revue-securite` dans une **nouvelle session** (Claude ne relit pas son propre travail dans le même contexte).
- Les questions du client restent ouvertes ? Claude doit vous demander, pas décider. Ajoutez la consigne au `CLAUDE.md` si besoin.

## Exercice F — Débrief (30 min)

Présentez en 5 minutes : un skill qui a bien fonctionné et un qui a dû être réécrit, une décision que Claude avait prise seul et que vous avez annulée, le tableau `REGLES.md` (règle → test → statut).

## Livrables

Dépôt avec `CLAUDE.md`, `.claude/skills/` (3 skills minimum), `.claude/settings.json`, `QUESTIONS.md`, `DECISIONS.md`, `REGLES.md`, tests au vert.
