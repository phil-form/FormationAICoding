# Flotte Delvaux & Fils

Gestion des véhicules de service : parc, réservations, retours, rapport mensuel.

Version 1.4.2 (mai 2016) — Symfony 2.8, Doctrine 2.5, SQLite.

> **Exemple de formation.** Ce dossier sert à montrer comment migrer une application legacy avec l'IA, de
> PHP 7.2 / Symfony 2.8 vers **PHP 8.4 / Symfony 6.4**, sans changer son comportement. La méthodologie est décrite
> dans la section [« Méthodologie de la migration »](#méthodologie-de-la-migration-bug-pour-bug) ci-dessous ; les
> sections « Installation » et « Fonctionnalités » sont le README d'origine de l'application (2016).

## Installation

Prérequis : PHP 7.2 avec `pdo_sqlite`, Composer.

```bash
cp app/config/parameters.yml.dist app/config/parameters.yml
composer install
php app/console doctrine:schema:create
php app/console fleet:demo:load
php -S 127.0.0.1:8000 -t web web/app_dev.php
```

Application : http://127.0.0.1:8000/vehicules

## Fonctionnalités

| URL | Rôle |
| --- | --- |
| `/vehicules` | Liste des véhicules, filtre `?status=` (0 disponible, 1 en entretien, 2 hors service) |
| `/vehicules/{id}` | Fiche véhicule, réservations à venir, historique |
| `/vehicules/{id}/reserver` | Réservation (dates au format JJ/MM/AAAA HH:MM) |
| `/reservations/{id}/retour` | Retour du véhicule : kilométrage, calcul du coût (0,35 €/km) |
| `/api/vehicules` | API JSON utilisée par l'écran de planning des dépôts |
| `/admin/vehicules/nouveau` | Ajout d'un véhicule (authentification HTTP Basic) |

Commandes :

- `php app/console fleet:report --month=2016-05` : rapport mensuel par véhicule.
- `php app/console fleet:demo:load` : vide la base et charge les données de démonstration.

Compte gestionnaire : `gestionnaire` / `flotte2016`.

---

## Méthodologie de la migration « bug pour bug »

### Le problème

L'application tourne en production depuis 2016 sur des versions qui ne reçoivent plus de correctifs de sécurité
(PHP 7.2, Symfony 2.8). Elle n'a **aucun test** et pas de `composer.lock`. Ses utilisateurs comptent sur son
comportement actuel, **y compris ses bugs** (un tri approximatif dans le rapport, un filtre `?status=abc` qui
renvoie une liste inattendue…). Demander à l'IA « migre ce projet vers Symfony 6.4 » produirait un code moderne, mais
personne ne pourrait dire ce qui a changé en chemin.

### Le principe : figer d'abord, migrer ensuite

1. **On décrit ce que fait l'application, pas ce qu'elle devrait faire.** Chaque comportement observable est noté
   dans une fiche `BH-NNN` (entrée, sortie, effet en base), bugs compris, sans les juger ni les corriger.
2. **On transforme chaque fiche en test de caractérisation.** Les tests pilotent l'application **de l'extérieur**
   (HTTP, ligne de commande, base) depuis un projet de tests séparé, en PHP 8.4. Ils ne dépendent d'aucune classe de
   l'application : ils restent **identiques** d'un bout à l'autre de la migration. C'est ce qui en fait un juge
   fiable.
3. **On monte une marche à la fois.** Une seule version change par étape (Symfony **ou** PHP), LTS par LTS, jamais
   de saut. Avant de passer à la marche suivante : zéro dépréciation bloquante et une suite de tests 100 % verte.
4. **On ne change pas les tests pour les faire passer.** Pendant une marche, tests, jeux de données et golden masters
   sont **gelés** (un hook refuse toute modification). Si un test échoue, c'est la migration qui doit s'adapter.
5. **Tout changement de comportement est une décision humaine.** Il est consigné dans
   `docs/migration/accepted-changes.md` ; la correction d'un bug historique est une étape séparée, après la
   migration.

### Le chemin de migration

```
step-00  PHP 7.2 + Symfony 2.8   référence figée (lock, tests, audits) — servie en parallèle par app-ref
step-01  PHP 7.2 + Symfony 3.4   security.context, formulaires par FQCN, isSubmitted()
step-02  PHP 7.2 + Symfony 4.4   structure Flex (config/, public/, templates/), AppBundle → App
step-03  PHP 7.4 + Symfony 4.4
step-04  PHP 7.4 + Symfony 5.4   Twig 3, fin de SensioFrameworkExtraBundle
step-05  PHP 8.1 + Symfony 5.4   comparaisons chaîne/nombre, tri stable : les pièges « bug pour bug »
step-06  PHP 8.1 + Symfony 6.4   authenticators, password hasher, attributs
step-07  PHP 8.4 + Symfony 6.4   cible
```

Étapes et notes : `.claude/reference/steps.json` ; étape courante : `docker/step.env`.

### Quatre filets de sécurité complémentaires

| Filet | Ce qu'il vérifie | Ce qu'il attrape |
|---|---|---|
| **Tests boîte noire** (http, cli, db) | Chaque route et chaque commande, cas nominaux et erreurs | Une régression sur un comportement connu |
| **Cas limites** (edge) | Un test par piège repéré dans le code (arrondis, dates, comparaisons lâches…) | Les changements silencieux de PHP 8 |
| **Golden masters** | Pages HTML, JSON, sorties CLI, schéma de base, comparés octet par octet (après normalisation) | Un détail de rendu que personne n'a pensé à tester |
| **Tests différentiels** | Les mêmes requêtes rejouées sur la version de référence (`app-ref`) et la version en cours | Tout écart, même non décrit dans une fiche |

La qualité de ces filets est elle-même mesurée : **couverture** du code (PCOV) et **mutation testing** (on introduit
volontairement des défauts : la suite doit les détecter). Le déterminisme est assuré par une horloge de référence
(libfaketime), un fuseau, une locale et des jeux de données identiques à chaque exécution.

### L'IA en équipe d'agents isolés

Chaque rôle est confié à un agent qui ne voit que ce dont il a besoin ; l'isolement est imposé par des hooks
(`.claude/hooks/guard.sh`) :

| Agent | Rôle | Ce qui lui est interdit |
|---|---|---|
| `cartographer` | Inventorie routes, commandes, services, formulaires, entités, templates | Modifier le code |
| `characterizer` | Observe l'application et rédige les fiches `BH-NNN` | Écrire des tests ou du code |
| `test-writer` | Écrit les tests à partir des seules fiches | **Lire le code de l'application** |
| `migrator` | Fait monter les versions (Composer, Rector, configuration) | **Toucher aux tests**, aux golden masters, à la doc |
| `deprecation-analyst`, `security-auditor` | Suivent dépréciations et vulnérabilités d'étape en étape | Corriger quoi que ce soit |
| `auditor` | Arbitre les échecs : régression, changement de comportement, test faux, environnement | — (seul à tout voir, il ne transmet que des références `BH-NNN`) |

### Le déroulé, avec les skills

```text
/env init                          # environnement Docker : app, app-ref (step-00), tests, outils
/baseline                          # référence step-00 : lock, empreintes, audits, tag
/build-test-suite                  # inventaire → fiches BH → tests → golden masters → couverture → mutation
/upgrade-symfony 6.4 --interactive # marche par marche ; appelle /upgrade-php quand Symfony l'exige
/upgrade-php 8.4                   # dernière marche
```

À chaque marche, l'orchestrateur enchaîne : inventaire → dépréciations → correction → montée de version → tests →
tri des échecs → audit de sécurité → rapport d'étape → commit. Il **s'arrête et demande à l'humain** sur chaque
*gate* : suite rouge après plusieurs corrections (`G-TESTS`), changement de comportement (`G-BEHAVIOR`), nouvelle
vulnérabilité (`G-VULN`), dépréciation bloquante (`G-DEPREC`), route ou commande disparue (`G-INVENTORY`), zone gelée
touchée (`G-FROZEN`), environnement indisponible (`G-ENV`).

### Ce qu'il faut retenir

- **Sans tests, pas de migration** : la moitié du travail est de construire le filet avant de toucher au code.
- **Des tests indépendants de la version** : tester par l'extérieur permet de garder les mêmes tests du début à la fin.
- **De petites marches** : quand un test casse, on sait exactement quelle montée de version en est la cause.
- **L'IA propose, les hooks contraignent, l'humain tranche.**

### Pour aller plus loin

- Plan complet : [`tmp/plan-migration-php84-sf64.md`](tmp/plan-migration-php84-sf64.md)
- Conventions de l'outillage (chemins, formats, gates) : [`.claude/reference/conventions.md`](.claude/reference/conventions.md)
- Suivi des 209 fiches de tests : [`tmp/tests-application.md`](tmp/tests-application.md)
- Règles du projet pour Claude Code : [`CLAUDE.md`](CLAUDE.md)
- Prompts des sessions de la formation : [`tmp/prompts.md`](tmp/prompts.md)
