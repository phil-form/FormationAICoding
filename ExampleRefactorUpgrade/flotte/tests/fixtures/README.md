# Jeux de données de test (scénarios)

Données **déterministes** pour la caractérisation « bug pour bug » de flotte. Chargées par `tests/bin/seed.php`
(PDO SQLite direct, conteneur `tests`), **jamais** par le code de l'application : le même scénario produit
exactement la même base en step-00 et en step-07.

| Fichier | Rôle |
|---|---|
| `schema.sql` | Schéma SQLite **réel** de step-00 (capturé par `doctrine:schema:create --dump-sql`). Ne jamais modifier. |
| `<scenario>.yaml` | Source de vérité du scénario (une ligne JSON par enregistrement, `note` = intention du cas). |
| `db/<scenario>.sqlite` | Base figée construite depuis le YAML (`--rebuild`), copiée telle quelle avant chaque test. |
| `db/<scenario>.sqlite.sha256` | Empreinte du **contenu logique** (schéma + lignes triées, valeurs typées via `quote()`). |

`db/volume.sqlite` n'est pas versionné (1 Mo, reconstruit à la demande) ; son empreinte l'est.

## Commandes

```bash
.claude/bin/seed.sh list                       # scénarios et volumes
.claude/bin/seed.sh nominal                    # charge dans app et app-ref (défaut --target both)
.claude/bin/seed.sh edge --target app          # une seule cible
.claude/bin/seed.sh nominal --verify --target app   # la base de app est-elle exactement le scénario ?
.claude/bin/seed.sh all --verify               # bases figées conformes aux YAML ?
.claude/bin/seed.sh <s> --rebuild              # reconstruit la base figée (refusé si tests/ est gelé)
.claude/bin/seed.sh <s> --dump                 # dump canonique lisible
```

## Conventions des données

- Date de référence : **2026-01-15 10:00:00** (jeudi), heure de l'horloge des conteneurs (`FAKETIME`).
  « Passé », « en cours », « à venir » sont définis par rapport à elle.
- Dates naïves `Y-m-d H:i:s` en heure de Bruxelles, comme l'application.
- Coût = km × 0,35 au centime, fourni en texte (`"73.50"`) : l'affinité NUMERIC de SQLite le stocke en REAL/INTEGER
  (`73.5`, `84`), **exactement** comme l'application (vérifié : `demo` est identique à `fleet:demo:load`).
- Kilométrage du véhicule = `end_km` de son dernier retour (sauf cas volontairement incohérents).
- Identifiants explicites et stables : les tests peuvent cibler `/vehicules/12`.

## Scénarios

| Scénario | Véhicules | Réservations | Contenu | Zones |
|---|---:|---:|---|---|
| `empty` | 0 | 0 | Schéma seul : listes vides, API `[]`, rapport vide, première création | liste, API, rapport, admin |
| `demo` | 5 | 5 | Copie exacte de `fleet:demo:load` (vérifiée contre la commande réelle) | commande, parcours README |
| `nominal` | 24 | 476 | Flotte réaliste (18 dispo / 4 entretien / 2 hors service), historique sept. 2025 → janv. 2026, en cours, à venir, annulées | tous les parcours |
| `booking` | 8 | 10 | Création de réservation : chaque statut de véhicule, créneaux pour toutes les positions relatives (avant, après, bord à bord, inclus, englobant), réservations qui bloquent ou non, en cours, en retard | réservation |
| `returns` | 8 | 10 | Retour : nominal, départ à 0, km < départ, double clôture, annulée, future, proche de 2^31-1, arrondis | retour |
| `report-months` | 8 | 30 | Rapport : oct. 2025 → mars 2026, décembre vide, bornes de mois (minuit, dernière seconde, Nouvel An), ex aequo, 0 km, très grands nombres, changements d'heure, 29 février | `fleet:report` |
| `edge` | 27 | 75 | Données valides extrêmes : formats et tri des plaques, 2^31-1 km, Unicode/emoji, HTML et guillemets à échapper, 40 réservations, ex aequo, retard, même date de début, bord à bord, changements d'heure, arrondis | toutes |
| `anomalies` | 8 | 10 | Données **incohérentes** de base legacy : statut inconnu (3, -1), négatifs, vides, coût NULL ou `"N/A"`, fin avant début, statut `CONFIRMEE` | toutes (divergences PHP 7 / 8) |
| `volume` | 500 | 9 583 | Généré (graine fixe 20260115) : performances, listes longues | performance |

## Comportements déjà observés en step-00 (à caractériser en fiches BH)

Relevés lors du chargement de chaque scénario dans l'application de référence (2026-11-17) :

- Toutes les pages (liste, filtre, fiche, réserver, retour, API) répondent **200** sur tous les scénarios, y compris
  `anomalies` : PHP 7.2 émet des notices (`Undefined offset: 3` dans `getStatusLabel()`) et des warnings
  (`number_format() expects parameter 1 to be float, string given`) sans interrompre la page.
- `fleet:report --month=2026-01` **échoue** sur `anomalies` (`ContextErrorException: A non-numeric value
  encountered`, coût `"N/A"`) : en PHP 8 l'erreur change de nature (TypeError) → écart attendu à arbitrer.
- Totaux de `fleet:report --month=2026-01` : `nominal` 10.181 km / 3 563,35 € ; `report-months` 1.707 km / 597,45 € ;
  `edge` 665 km / 232,75 € ; `booking` et `returns` 300 km / 105,00 €.
- Chargement de `/vehicules` sur `volume` : ~40 ms (step-00, PHP 7.2) — repère de performance.

## Qui modifie quoi

Les YAML et `seed.php` relèvent de `test-writer` (`/write-bh-tests db`) ; les bases figées de `/seed --rebuild`.
Pendant une marche de migration, tout `tests/` est **gelé** (hook `freeze-guard`) : un scénario ne se modifie pas
pour faire passer une migration.
