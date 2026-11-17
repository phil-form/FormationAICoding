# Flotte Delvaux & Fils

Gestion des véhicules de service : parc, réservations, retours, rapport mensuel.

Version 1.4.2 (mai 2016) — Symfony 2.8, Doctrine 2.5, SQLite.

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
