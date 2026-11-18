# Marche `php` — montée d'une version de PHP (ex. 7.4 → 8.1)

Image Docker de l'étape, `config.platform.php`, extensions, `php.ini`, `docker/step.env`, sets Rector PHP.
Références propres : `docs/migration/compat/php-X.Y.md` (rapport de `/compat-scan`, à lire en premier).

## Règles propres

- La version visée est celle de l'étape `kind: php` suivante de `steps.json`.
- Les changements de sémantique PHP (comparaisons chaîne/nombre, tri stable, `serialize_precision`, `null` vers
  fonctions internes) doivent être **neutralisés** dans le code pour reproduire l'ancien résultat, ou signalés
  comme risque — jamais « corrigés ».

## Étapes

1. **Image** — vérifier/compléter `docker/app/phpXY/Dockerfile` (même structure que l'image précédente : mêmes
   extensions, libfaketime, Composer compatible, image épinglée par digest). Ne jamais modifier l'image d'une étape
   déjà validée.
2. **php.ini** — `docker/app/php.ini` commun : retirer seulement les directives supprimées par la nouvelle version,
   sans changer les valeurs (`precision`, `serialize_precision`, `date.timezone`…).
3. **Étape courante** — `docker/step.env` : `STEP`, `PHP_VERSION` mis à jour (le reste inchangé).
4. **Composer** — `config.platform.php` = version visée (patch le plus récent de l'image) ; contrainte `require.php`
   ajustée ; `composer.sh update --with-all-dependencies --no-scripts` si des paquets doivent suivre, sinon
   `composer.sh update --lock`.
5. **Rector** — `.claude/bin/rector.sh <step-NN>` (step-03, step-05 ou step-07 : liste blanche de règles de
   compatibilité de `rector/step-NN.php`, jamais `UP_TO_PHP_XY` ni aucune modernisation) ; simulation relue, puis
   `--apply --only '<Règle>'` par lot, jusqu'à stabilité. Les changements de comportement de PHP 8 (comparaisons,
   tri, arithmétique sur chaîne) ne sont pas traités par Rector : les préserver à la main (§ 6). Voir `rector/README.md`.
6. **Code** — traiter les points de `compat/php-X.Y.md` restés ouverts, en préservant le résultat observable.
7. **Vérifications** — `tools.sh phpstan analyse` (phpVersion cible), `console.sh cache:clear`,
   `console.sh lint:twig` si disponible. L'orchestrateur reconstruit et redémarre l'environnement.

## Rapport final (ta réponse)

Version avant → après, fichiers modifiés (image, ini, step.env, Composer, code), règles Rector appliquées,
neutralisations de changements de sémantique (fichier:ligne + explication), risques de comportement, résultat des
vérifications.
