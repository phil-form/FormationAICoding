---
name: commit
description: Crée un commit après la suite de tests complète et des contrôles de sécurité - secrets, fichiers interdits, SAST Semgrep, configuration Docker/compose, dépendances Maven et scan Trivy des images Docker utilisées. En cas de détection, ne modifie rien, produit un rapport, prévient l'utilisateur et lui demande quoi faire (planification, questions, dérogation). Ne pousse jamais.
when_to_use: Chaque fois que l'utilisateur demande de commiter (« commit », « commite », « fais un commit »…). Toujours préférer cette skill à un git commit direct.
argument-hint: "[intention du message] [--no-images] [--no-build] [--no-pull] [--all-images]"
allowed-tools: Bash(.claude/skills/run-tests/scripts/run-tests.sh:*), Bash(.claude/skills/commit/scripts/security-scan.sh:*), Bash(.claude/skills/commit/scripts/commit-report.sh:*), Bash(git status:*), Bash(git diff:*), Bash(git log:*), Bash(git add:*), Bash(git commit:*)
---

# Commit sécurisé

Arguments : `$ARGUMENTS` — les options `--…` sont transmises au scan ; le reste décrit l'intention du commit.

## Règles absolues

- **Aucune modification** de code, de configuration, de Dockerfile, de dépendance ni de fichier d'exclusion
  (`.trivyignore`, `.semgrepignore`, `nosemgrep`…) pendant cette skill : on **constate**, on **rapporte**, l'utilisateur
  **décide**.
- Jamais `git commit --no-verify`, jamais `git push`, jamais de réécriture d'historique (`--amend` sur un commit déjà
  existant, `rebase`, `reset`) sauf demande explicite.
- Jamais de secret dans un commit : `.env`, `.env.dev`, `secrets/`, `backups/`, `certs/*`, clés, PEM, `.claude/state/`.

## 1. Préparer

1. `git status --short` et `git diff --stat` (+ `git diff --cached --stat`).
2. Si rien n'est indexé : présenter la liste des changements et indexer ceux qui relèvent de l'intention du commit
   (`git add <chemins>` — jamais `git add -A` aveugle). Si des changements sans rapport entre eux sont mélangés,
   proposer de les séparer en plusieurs commits.
3. Ne rien indexer de la liste des fichiers interdits ci-dessus.

## 2. Tester (toujours, avant le scan)

La branche principale doit rester fonctionnelle : la **suite complète** (unitaires + intégration, conteneur
`docker/test`) tourne avant chaque commit. Lancer en arrière-plan (progression dans la status line) et attendre la fin :

```bash
.claude/skills/run-tests/scripts/run-tests.sh --docker
```

- **Tests verts** → § 3 (contrôles de sécurité).
- **Tests en échec ou build cassé** → **ne pas commiter, ne rien corriger** : résumer les échecs (critères `AC-xx`
  concernés via `.claude/state/test-feedback.md`, premières erreurs du log `.claude/state/test-run.log`), puis demander
  avec l'outil de question : **trier les échecs** (`/triage-failures`), **analyser les logs** (`/analyse-logs tests`,
  si erreur d'infrastructure), **commiter malgré tout** (dérogation justifiée → trailer
  `Tests-Exception: <justification>`), ou **annuler**.
- Ne jamais sauter les tests de sa propre initiative : uniquement sur demande explicite de l'utilisateur, tracée par
  le trailer `Tests-Exception`.
- Si le résumé signale des dépréciations, le mentionner et proposer `/deprecations` après le commit (non bloquant).

## 3. Contrôler la sécurité

Lancer (premier passage : quelques minutes — bases Trivy, construction de l'image de l'API) :

```bash
.claude/skills/commit/scripts/security-scan.sh [options transmises]
```

Par défaut le scan **met à jour les images tierces** (`docker pull`) et signale celles qui étaient obsolètes en local.
`--no-images` uniquement si le commit ne touche ni Docker, ni dépendances, ni `pom.xml` — le signaler à l'utilisateur.

Lire le rapport : `.claude/state/audit/commit-scan/latest/report.md`.

| Code | Signification | Suite |
|---|---|---|
| 0 | aucun résultat bloquant | § 4 |
| 10 | résultats bloquants | § 5 |
| 2 | scan incomplet (erreur d'outil) | § 6 |

Résultats **bloquants** : fichier interdit indexé, secret, SAST `ERROR`, configuration HIGH/CRITICAL, vulnérabilité
HIGH/CRITICAL **corrigeable** (dépendance ou image). Les autres résultats (WARNING, MEDIUM/LOW, HIGH sans correctif)
sont **signalés** dans le résumé mais ne bloquent pas.

## 4. Tests verts et aucun bloquant → commit

1. Résumer en une ou deux lignes : tests (nombre, durée) et résultats signalés non bloquants (s'il y en a).
2. Rédiger le message **en anglais**, au format Conventional Commits :
   `type(scope): résumé à l'impératif` (≤ 72 caractères), ligne vide, corps en puces expliquant le **pourquoi**.
   Types : `feat`, `fix`, `refactor`, `test`, `docs`, `build`, `ci`, `chore`, `security`.
3. Terminer par les lignes d'attribution demandées dans le contexte de la session (ex. `Co-Authored-By: …`).
4. Écrire le message dans un fichier temporaire, puis **juste avant le commit**, le rapport (dossier gitignoré
   `reports/commits/`) :
   `.claude/skills/commit/scripts/commit-report.sh prepare <fichier-message>`
5. `git commit -F <fichier-message>`, puis `.claude/skills/commit/scripts/commit-report.sh finalize` (ajoute le hash).
6. Afficher `git log --oneline -1` et le chemin du rapport de commit.

## 5. Résultats bloquants → rapport et décision de l'utilisateur

**Ne pas commiter. Ne rien corriger.**

1. Présenter un **rapport synthétique**, groupé par contrôle, pour chaque résultat bloquant :
   - quoi (règle / CVE / paquet), où (fichier:ligne ou image), sévérité ;
   - pourquoi c'est un risque **dans ce projet** (exploitable ? exposé ? image tierce ou code du projet ?) ;
   - piste de correction **proposée** (ex. montée de version de l'image ou de la dépendance, digest épinglé,
     `USER` non-root, `defusedxml`…) — sans l'appliquer.
   Distinguer clairement ce qui relève **de notre code** et ce qui relève **d'images tierces** (correctif amont attendu ?).
2. Demander la suite avec l'outil de question (une seule question, options) :
   - **Planifier les corrections** — produire un plan ordonné (fichiers concernés, impact, vérification), sans
     modifier de code ; l'utilisateur relancera ensuite les corrections et `/commit`.
   - **Poser des questions / approfondir** — détailler un résultat précis (exploitabilité, alternatives).
   - **Commiter malgré tout (dérogation)** — exige une **justification** écrite de l'utilisateur ; la reporter dans le
     message de commit (trailer `Security-Exception: <justification>`) et rappeler les résultats non traités.
   - **Annuler** — laisser l'index tel quel, rien n'est commité.
   Dans tous les cas, un rapport de commit est écrit (`commit-report.sh prepare`) ; si aucun commit n'est créé :
   `commit-report.sh abort "<raison>"`.
3. Appliquer strictement le choix ; ne jamais passer d'une option à une autre sans nouvelle confirmation.

## 6. Scan incomplet (code 2)

Expliquer l'erreur (`report.md`, fichiers `*.stderr` / `build.log` du dossier du rapport : réseau, quota d'un dépôt,
image introuvable…), puis demander : **relancer** (éventuellement avec une option), **commiter sans le contrôle
manquant** (dérogation justifiée, trailer `Security-Exception`), ou **annuler**.

## Réponse finale

Commit créé (hash + titre) ou décision prise, résultat des tests, chemin du rapport de commit (`reports/commits/`)
et du rapport de sécurité, résultats signalés restant à traiter.
