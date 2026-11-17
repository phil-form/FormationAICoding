---
name: test-plan
description: Construit ou met à jour la matrice de couverture des tests de caractérisation (route × méthode × rôle × scénario × cas limites, commandes, domaine) à partir de l'inventaire et des fiches BH ; avec --gaps, part des lacunes de couverture, des mutants survivants et des éléments non couverts. Produit docs/migration/test-plan.md.
when_to_use: Dans /build-test-suite après /inventory (plan initial) puis en boucle après /coverage et /mutation-check (--gaps) ; après /inventory --diff si un élément apparaît.
argument-hint: "[--gaps]"
context: fork
agent: characterizer
---

# Plan de tests de caractérisation

Arguments : `$ARGUMENTS`. Référence : `.claude/reference/conventions.md` § 2, § 5 ; plan § 3 et § 6.

## Règles

- Le plan dit **quoi** couvrir et **où** (couche, scénario), jamais **comment** (pas de code).
- Chaque élément d'inventaire de step-00 doit apparaître dans la matrice. Un élément sans comportement à tester
  est justifié explicitement.
- Le plan ne supprime jamais une ligne existante : une ligne obsolète est marquée.

## Étapes

1. Lis `docs/migration/inventory/step-00/*.json`, `docs/migration/behaviors/*.md`, `tests/fixtures/README.md`
   (s'il existe), et le `test-plan.md` existant.
2. Construis les matrices :
   - **HTTP** : route × méthode (dont méthode non autorisée) × rôle (anonyme, `ROLE_ADMIN` valide, identifiants
     faux) × scénario (`empty`, `demo`, `nominal`, `edge`) × cas (nominal, 404, 405, 401/403, formulaire invalide,
     CSRF, redirection, flash) ;
   - **CLI** : commande × options (absente, valide, invalide, limites) × scénario ;
   - **Base** : action qui écrit × état attendu ; non-dérive du schéma ;
   - **Edge** : un piège du plan § 0 = une ligne ;
   - **Diff** : parcours rejoués sur `app` et `app-ref` ;
   - **Unit** : classe du domaine × méthode publique × cas.
   Chaque cellule : `BH-xxx` couvrant, ou **lacune**, ou « sans objet (raison) ».
3. **Mode `--gaps`** : lis aussi
   - `.claude/state/migration/coverage/summary.json` (lignes et branches non couvertes de `src/`) ;
   - `.claude/state/migration/mutation/summary.json` (mutants survivants, avec fichier et ligne) ;
   - le dernier `docs/migration/inventory/<STEP>/diff.md` (éléments ajoutés).
   Traduis chaque lacune **en comportement manquant** (« le cas kilométrage égal au départ n'est pas observé ») avec
   la zone concernée — jamais en instruction de code. Liste les zones à re-caractériser
   (`/characterize <zone> --gaps …`).
4. Écris `docs/migration/test-plan.md` :
   - synthèse (éléments d'inventaire, BH, taux de cellules couvertes par couche) ;
   - matrices ;
   - lacunes priorisées (pièges § 0 d'abord, puis écritures en base, puis erreurs, puis affichage) ;
   - scénarios de données requis et manquants.
5. Rends ton rapport.

## Rapport final

- Taux de couverture de la matrice par couche, lacunes prioritaires, zones à caractériser, scénarios à ajouter.
