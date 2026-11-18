# Rector dans la migration flotte

Rector automatise une partie des modifications de code de chaque marche (PHP 7.2 / Symfony 2.8 → PHP 8.4 /
Symfony 6.4). Il tourne dans le conteneur `tools` (Rector 2.x, PHP 8.4), sur le code monté en `/srv/app`, et ne
touche **jamais** `tests/`, `vendor/`, `.ref/`, `var/` ni `app/cache/`.

## Principe de sûreté : bug pour bug

Rector ne doit apporter **que des changements de compatibilité** : renommages de classes et de méthodes, API
supprimées remplacées par leur équivalent, constructions dépréciées réécrites à l'identique. Sont **interdits** dans
ces configurations : `withPhpSets()`, `withPreparedSets()`, les jeux de qualité de code, de code mort, de déclaration
de types, de modernisation (fonctions fléchées, `??=`, propriétés typées, `match`…) : ils changent le comportement ou
le rendent plus strict. Toute règle ajoutée doit être justifiée dans le fichier de l'étape.

Rector ne traite **pas** les changements de comportement de PHP 8.0 (comparaisons chaîne / nombre, tri stable avec
comparateur booléen, arithmétique sur chaîne non numérique, `number_format()` sur du texte) : le migrator les préserve
explicitement, en s'appuyant sur les fiches `BH-xxx` de `tmp/tests-application.md`.

## Fichiers

| Fichier | Contenu |
|---|---|
| `targets.json` | versions visées par chaque marche (Symfony, Twig, Doctrine, SensioFrameworkExtraBundle) |
| `common.php` | chemins (selon la structure de l'étape), exclusions, cache, version de PHP, lecture du manifeste |
| `step-01.php` | Symfony 2.8 → 3.4 : règles Symfony / Twig / Doctrine liées aux versions |
| `step-02.php` | Symfony 3.4 → 4.4 : idem (la restructuration Flex reste manuelle, recettes Flex) |
| `step-03.php` | PHP 7.2 → 7.4 : liste blanche de règles de dépréciation PHP 7.3 / 7.4 |
| `step-04.php` | Symfony 4.4 → 5.4, Twig 3, Doctrine ORM 2.17 / DBAL 3 |
| `step-05.php` | PHP 7.4 → 8.1 : `null` passé aux fonctions internes rendu explicite |
| `step-06.php` | Symfony 5.4 → 6.4 : règles liées aux versions + annotations → attributs (Symfony, Doctrine, Sensio) |
| `step-07.php` | PHP 8.1 → 8.4 : types nullables implicites, paramètre `$escape` explicite |

## Versions visées (règles « composer-based » de Rector 2)

Rector 2 n'a plus de jeux « Symfony 3.4 », « Symfony 4.4 »… : chaque règle est liée à une version de paquet
(`symfony/form >=3.0`, `twig/twig >=3.0`, `doctrine/orm >=2.13`…) et ne s'active que si le projet **déclare** cette
version. `.claude/bin/rector.sh` génère, à partir de `targets.json`, un manifeste
`.claude/state/migration/rector/targets/step-NN/vendor/composer/installed.json` que `common.php` présente à Rector.
On peut donc appliquer les règles d'une version **avant** le `composer update` (`/fix-deprecations`) comme après
(`/upgrade-step`). Si `/upgrade-step` retient d'autres versions, il met `targets.json` à jour.

`rector.sh step-NN --list` affiche les règles **enregistrées** (toutes les règles liées aux versions, y compris celles
de versions ultérieures) ; seules celles dont la contrainte est satisfaite par le manifeste **s'appliquent**.

## Utilisation

```bash
.claude/bin/rector.sh step-01                    # simulation : diff dans .claude/state/migration/rector/step-01-dry-run.diff
.claude/bin/rector.sh step-01 --only 'Rector\Symfony\Symfony30\Rector\MethodCall\StringFormTypeToClassRector'
.claude/bin/rector.sh step-01 --apply --only '…' # appliquer un lot (migrator uniquement), puis tests
.claude/bin/rector.sh step-01 --list             # règles enregistrées
```

Déroulé d'un lot (`/fix-deprecations`, `/upgrade-step`) :
1. simulation, lecture du diff, choix d'**une famille** de règles (`--only`) ;
2. `--apply` sur ce lot ;
3. **relancer jusqu'à stabilité** : certaines règles ne traitent qu'une occurrence par passage (observé à step-01 :
   `StringFormTypeToClassRector` convertit `'text'`, puis `'datetime'` et `'integer'` aux passages suivants) ;
4. `.claude/bin/tests.sh --suite all` (puis `--target ref` pour la variante `diff`) : toute régression → annuler le
   lot (`git restore`), pas « ajuster » les tests ;
5. relecture humaine du diff avant commit (`/step-commit`).

## Constaté à step-00 (simulation step-01, 2026-11-17)

8 fichiers concernés : annotations Sensio `@Route` / `@Method` fusionnées en `@Route` de Symfony (toutes les
routes), classes Twig namespacées (`Twig\Extension\AbstractExtension`, `Twig\TwigFilter`), types de formulaire par
classe (un par passage), import inutilisé retiré. **Restent manuels** à step-01 : `security.context` →
`isGranted()`, `getName()` → `getBlockPrefix()`, `isValid()` sans `isSubmitted()`, `choices_as_values` de
`VehicleType` (risque élevé, BH-323) — voir `docs/migration/deprecations/step-00.md`.
