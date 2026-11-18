# Marche `symfony` — montée d'une version de Symfony (ex. 2.8 → 3.4)

Contraintes Composer alignées, `composer update` ciblé, recettes Flex, sets Rector Symfony/Doctrine/Twig, adaptation
de la configuration. Références propres : `docs/migration/compat/` (lecture).

## Règle propre

- La version visée doit être la **LTS qui suit immédiatement** la version installée (`steps.json`). Sinon :
  s'arrêter et le signaler.

## Étapes

1. **État** — lire `composer.json`, `composer.lock`, `docker/step.env` ; identifier la version installée.
2. **Contraintes Composer**
   - Remplacer/aligner toutes les contraintes `symfony/*` sur `<version>.*` ; à partir de 4.4, remplacer
     `symfony/symfony` par les composants réellement utilisés (framework-bundle, twig-bundle, security-bundle,
     form, validator, console, yaml, monolog-bundle si présent…) et `symfony/flex`.
   - Aligner Doctrine (bundle, ORM, DBAL), Twig, SensioFrameworkExtraBundle sur les versions compatibles ;
     supprimer `symfony/security-acl` (non utilisé) dès que la montée l'exige.
   - `composer.sh update --with-all-dependencies --no-scripts` limité aux paquets concernés ; puis
     `composer.sh install`.
3. **Structure** (step-02, Symfony 4.4 uniquement) — passer à la structure Flex : `config/`, `public/`,
   `templates/`, `src/` en namespace `App\`, `bin/console`, `.env` **sans secret** (valeurs de test par variables
   d'environnement) ; recettes Flex appliquées puis relues ; routes et noms de routes **inchangés** ; chemins
   logiques des templates conservés autant que possible.
3 bis. **Étape courante** — mettre à jour `docker/step.env` : `STEP=step-NN` et `SYMFONY_VERSION=<version>` (et
   `APP_PUBLIC_DIR` / `APP_FRONT_CONTROLLER` si la structure change, step-02). Les tests en dépendent (`APP_STEP` :
   les changements acceptés `ACC-xxx` s'appliquent à partir de leur étape), comme la status line et les outils.
4. **Rector** (`rector/README.md`) —
   - Si les versions retenues au § 2 diffèrent de `rector/targets.json` pour cette marche : mettre `targets.json` à jour.
   - `.claude/bin/rector.sh <step-NN>` (simulation) ; lire `.claude/state/migration/rector/step-NN-dry-run.diff`.
   - Appliquer **par famille de règles** : `rector.sh <step-NN> --apply --only '<Règle complète>'` (une famille = un lot :
     routes Sensio, types de formulaire, classes Twig, Doctrine…), **relancer jusqu'à stabilité** (certaines règles ne
     traitent qu'une occurrence par passage), relire le diff appliqué (`git diff`), puis passer la main à
     l'orchestrateur pour les tests avant le lot suivant.
   - Ne jamais ajouter de règle ou de jeu à `rector/step-NN.php` sans le signaler : seules des règles de
     compatibilité sont admises (jamais `withPhpSets`, `withPreparedSets`, qualité de code, typage).
   - Ce que Rector ne fait pas (sécurité, `getBlockPrefix()`, `isSubmitted()`, structure Flex, configuration) reste
     manuel : le lister dans le rapport.
5. **Configuration** — adapter `config*.yml` / `config/packages/*` : clés supprimées, sécurité (encodeurs →
   password hasher, `anonymous`, authenticators selon la version), services (`$this->get()` sur services privés →
   injection), `templating` supprimé.
6. **Vérifications statiques** — `console.sh cache:clear`, `console.sh debug:router`, `console.sh lint:twig`
   (si disponible), `console.sh lint:yaml config` (ou `app/config`), `tools.sh phpstan analyse` au niveau configuré.
   Corriger les erreurs de démarrage. Ne jamais lancer la suite de tests.

## Rapport final (ta réponse)

- Version avant → après ; paquets ajoutés, retirés, mis à jour (directs).
- Fichiers modifiés, par famille (Composer, structure, Rector, configuration, code).
- Règles Rector appliquées (par lot, avec le nombre de passages jusqu'à stabilité) et corrections restées manuelles.
- **Risques de changement de comportement** identifiés (avec la route/commande concernée).
- Résultat des vérifications statiques ; points non résolus.
