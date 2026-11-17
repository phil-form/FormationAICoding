# Gabarit de test de caractérisation — commandes console

> **Objet** : appliquer le gabarit commun (`tmp/tests-vehicle-controller.md` § 1 et § 2) aux 2 commandes de
> l'application : `fleet:report` (rapport mensuel) et `fleet:demo:load` (rechargement destructif des données de
> démonstration). Ce sont des interfaces utilisées par des personnes et, potentiellement, par des scripts
> planifiés (cron) qui lisent la sortie et le code de retour : leur texte exact est contractuel.
>
> Valeurs observées sur l'application step-00 réelle (PHP 7.2.34 / Symfony 2.8.52), via
> `.claude/bin/console.sh` (`app/console`, sans terminal), scénarios `report-months`, `edge`, `anomalies`, `demo`,
> `volume`, `empty`, `nominal`, le 2026-11-17.


<!-- tracking:start -->
## Suivi d'avancement (généré)

> Régénéré par `.claude/bin/test-tracking.py` (fin de chaque `.claude/bin/tests.sh`) — 2026-11-17 11:00. Ne pas éditer ce bloc : pour bloquer une fiche, ajouter « - BH-NNN : ⏸ raison » dans la section « Blocages ».

**0/37 terminés (0 %)** `░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░`

✅ 0 terminés · 🟡 0 incomplets · ✍️ 0 écrits · 🔴 0 rouges réf. · ⏸ 0 bloqués · ⬜ 37 à écrire

| Route | Terminés |
|---|---|
| `fleet:report` | 0/26 |
| `fleet:demo:load` | 0/11 |

<details><summary>Détail par fiche</summary>

| Fiche | Route | Fiche BH | Tests | Golden | Réf | App | Diff | Statut |
|---|---|---|---|---|---|---|---|---|
| BH-400 | `fleet:report` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-401 | `fleet:report` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-402 | `fleet:report` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-403 | `fleet:report` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-404 | `fleet:report` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-405 | `fleet:report` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-406 | `fleet:report` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-407 | `fleet:report` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-408 | `fleet:report` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-409 | `fleet:report` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-410 | `fleet:report` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-411 | `fleet:report` | — | — | — | — | — | — | ⬜ à écrire |
| BH-412 | `fleet:report` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-413 | `fleet:report` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-414 | `fleet:report` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-415 | `fleet:report` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-416 | `fleet:report` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-417 | `fleet:report` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-418 | `fleet:report` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-419 | `fleet:report` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-420 | `fleet:report` | — | — | — | — | — | — | ⬜ à écrire |
| BH-421 | `fleet:report` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-422 | `fleet:report` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-423 | `fleet:report` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-424 | `fleet:report` | — | — | — | — | — | — | ⬜ à écrire |
| BH-425 | `fleet:report` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-450 | `fleet:demo:load` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-451 | `fleet:demo:load` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-452 | `fleet:demo:load` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-453 | `fleet:demo:load` | — | — | — | — | — | — | ⬜ à écrire |
| BH-454 | `fleet:demo:load` | — | — | — | — | — | — | ⬜ à écrire |
| BH-455 | `fleet:demo:load` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-456 | `fleet:demo:load` | — | — | — | — | — | — | ⬜ à écrire |
| BH-457 | `fleet:demo:load` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-458 | `fleet:demo:load` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-459 | `fleet:demo:load` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-460 | `fleet:demo:load` | — | — | — | — | — | ✘ | ⬜ à écrire |

Légende — Fiche BH : décrite dans `docs/migration/behaviors/` · Tests : méthodes `bhNNN_*` · Golden : ✔ présents / ✘ manquants / — aucun · Réf / App : dernier run sur `app-ref` / `app` (✔ ✘ ↷ —) · Diff : variante comparative requise si la fiche est sensible à la migration.

</details>
<!-- tracking:end -->

## 1. Ce qui change par rapport au gabarit HTTP

| Vérification | Pour une commande |
|---|---|
| **Act** | une exécution, **sans terminal** (pas de TTY : aucune couleur ANSI), dans le conteneur de la cible : `CONSOLE_APP_CMD` / `CONSOLE_REF_CMD` (`docker exec -i flotte-mig-app[-ref] sf-console …`), arguments **exactement** ceux de la fiche ; horloge du conteneur (démarre au 15/01/2026 10:00) |
| **A1** | **code de retour** (0, 1…) |
| **A2** | **sortie d'erreur** (stderr) : vide, ou message attendu (bloc d'exception du framework : seule la ligne du message est contractuelle) |
| **A3** | **sortie standard** comparée **octet par octet** au golden (titre, largeur des colonnes du tableau, séparateurs de milliers, `€`, fins de ligne `\n`) + assertions ciblées (ordre des lignes, totaux) |
| **A4** | `fleet:report` : base **inchangée** ; `fleet:demo:load` : base **égale au scénario `demo`** (empreinte `.claude/bin/seed.sh demo --verify`) |
| **A5** | journal PHP (les warnings deviennent des exceptions en environnement `dev`, cf. BH-416) |

Rappels observés communs :

- environnement par défaut **`dev`** avec débogage (`app/console`) : un warning PHP **interrompt** la commande
  (`ContextErrorException`, code 1) ; avec `--env=prod`, le même warning est ignoré et la commande va au bout ;
- messages applicatifs écrits sur la **sortie standard**, même les erreurs (balise `<error>`) ; exceptions et erreurs
  de syntaxe de la ligne de commande sur la **sortie d'erreur** ;
- aucune couleur sans terminal ; avec `--ansi`, le titre est en vert (`\e[32m…\e[39m`) ;
- `-q` : aucune sortie, même code de retour.

## 2. Fiches

### 2.1 `fleet:report` — rapport mensuel d'utilisation

`fleet:report [-m|--month=AAAA-MM]` ; défaut = **mois courant calculé au démarrage de la commande**
(`date('Y-m')` → `2026-01` avec l'horloge des conteneurs). Prend les réservations **terminées** dont la **date de
fin** est dans `[1er du mois 00:00:00, 1er du mois suivant 00:00:00[`, regroupées par plaque (nombre, km, coût),
triées par km décroissants (`usort` avec comparateur booléen).

| Fiche | Commande | Scénario | A1 | A2 / A3 — points clés (observés) | Sensible |
|---|---|---|---|---|---|
| BH-400 | `fleet:report --month=2016-05` | demo | 0 | exemple du README, sortie exacte : `1-BCD-234 2 450 157,50 €`, `1-ABC-123 1 210 73,50 €`, `2-EFG-567 1 157 54,95 €`, `TOTAL 817 285,95 €` | oui : tableau `Table` (console 2.8 → 6.4) |
| BH-401 | `fleet:report --month=2026-01` | report-months | 0 | 7 lignes + TOTAL `1.707` km / `597,45 €` ; tableau au format `+---+` / `\| … \|` ; titre `Rapport de flotte - 2026-01` | **oui** : rendu de `Table` (bordures, alignement, largeur Unicode du `€`) |
| BH-402 | `fleet:report` (sans option) | report-months | 0 | **identique** à BH-401 : le défaut suit l'horloge (2026-01) | oui : défaut calculé à `configure()` |
| BH-403 | `fleet:report -m 2025-11` ; `-m2025-11` ; `--month 2025-11` | report-months | 0 | trois syntaxes équivalentes, sortie identique | oui : analyse des options |
| BH-404 | `fleet:report --month=2025-11` | report-months | 0 | **ex æquo à 300 km** : ordre observé `1-REP-001`, `1-REP-002`, `1-REP-003` (2 réservations), puis `1-REP-008` (3 km), `1-REP-004` (1 km) ; TOTAL `904` / `316,40 €` | **oui, très** : `usort` booléen, tri stable de PHP 8 (step-05) |
| BH-405 | `fleet:report --month=2026-01` | report-months | 0 | **ex æquo à 250 km** : `1-REP-002`, `1-REP-003`, `1-REP-004` (3 réservations) | **oui, très** : idem |
| BH-406 | `fleet:report --month=2026-01` | edge | 0 | `1-LNG-040` (265 km) puis **4 ex æquo à 100 km** `1-TIE-001` … `1-TIE-004` ; TOTAL `665` / `232,75 €` | **oui, très** : idem |
| BH-407 | `fleet:report --month=2025-12` | report-months | 0 | mois **sans donnée** : tableau réduit à l'en-tête et `TOTAL 0 0,00 €` | oui : `Table` vide |
| BH-408 | `fleet:report --month=2026-01` | report-months | 0 | bornes : réservation finissant **le 01/01/2026 à 00:00:00** comptée (900 km) ; celle **à cheval sur le Nouvel An** comptée (44 km) ; **dernière seconde** de janvier comptée (13 km) ; celle finissant le **01/02/2026 à 00:00:00 exclue** | oui : comparaison de dates (DQL, DBAL) |
| BH-409 | `fleet:report --month=2026-02` | report-months | 0 | la réservation exclue de janvier y figure (101 km) ; très grands nombres `999.999` km, `349 999,65 €`, TOTAL `1.000.112` / `350 039,20 €` | oui : `number_format`, flottants |
| BH-410 | `fleet:report --month=2025-10` et `--month=2026-03` | report-months | 0 | réservations autour des **changements d'heure** comptées (heures naïves) ; 7 km = `2,45 €` | oui : `DateTime`, fuseau |
| BH-411 | `fleet:report --month=2024-02` et `--month=2024-03` | report-months | 0 | **29 février 2024** compté en février ; réservation du 29/02 au 01/03 08:00 comptée en **mars** (date de fin) | non |
| BH-412 | `fleet:report --month=2025-13` | report-months | 0 | **accepté** : titre `Rapport de flotte - 2025-13` mais **données de janvier 2026** (débordement de `createFromFormat`) | **oui** : `DateTime::createFromFormat` (PHP 8) |
| BH-413 | `fleet:report --month=2026-00` ; `2016-13` ; `0000-01` ; `9999-12` | report-months | 0 | acceptés ; `2026-00` = décembre 2025 (vide ici) ; tableaux vides | oui : idem |
| BH-414 | `fleet:report --month=abc` ; `2026-1` ; `26-01` ; `2026/01` ; `2026-01-15` ; `" 2026-01"` ; `"2026-01 "` | report-months | **1** | **sortie standard** (pas d'erreur) : `Format de mois invalide (attendu : AAAA-MM).` ; stderr vide | oui : sortie `<error>` |
| BH-415 | `fleet:report --month=` | report-months | **1** | stderr : `The "--month" option requires a value.` + rappel de syntaxe ; stdout vide | **oui** : messages et format des erreurs de console (step-02/06) |
| BH-416 | `fleet:report --month=2026-01` | anomalies | **1** | env `dev` : stderr `[Symfony\Component\Debug\Exception\ContextErrorException]` `Warning: A non-numeric value encountered` (coût `"N/A"`), **stdout vide** | **oui, très** : PHP 8 lève `TypeError` (`Unsupported operand types`), composant Debug → ErrorHandler |
| BH-417 | `fleet:report --month=2026-01 --env=prod` | anomalies | 0 | rapport produit : `1-ANO-001 1 100 35,00 €`, `1-ANO-008 2 20 12,35 €` (`"N/A"` compté 0, `12.345` arrondi à l'affichage), `1-ANO-006 2 -50 -35,00 €`, `1-ANO-007 1 -7.000 0,00 €` (fin sans `end_km`), TOTAL `-6.930` / `12,35 €` | **oui, très** : idem, nombres négatifs |
| BH-418 | `fleet:report --mois=2026-01` | demo | **1** | stderr : `The "--mois" option does not exist.` | oui : messages console |
| BH-419 | `fleet:report 2026-01` | demo | **1** | stderr : `Too many arguments, expected arguments "command".` | oui : idem |
| BH-420 | `fleet:report --month=2026-01 -q` | report-months | 0 | **aucune** sortie | non |
| BH-421 | `fleet:report --month=2026-01 --env=prod` | report-months | 0 | sortie **identique** à BH-401 (dev) quand les données sont saines | oui : environnements |
| BH-422 | `fleet:report --month=2026-01 --ansi` | report-months | 0 | titre entouré de `\e[32m` … `\e[39m`, tableau sans couleur | oui : styles de sortie |
| BH-423 | `fleet:report --month=2024-03` | volume | 0 | 172 véhicules, TOTAL `187.606` km / `65 662,10 €` ; ~130 ms en step-00 ; suite `diff` : durée `app` ≤ 2 × `app-ref` | oui : performance |
| BH-424 | toute exécution de `fleet:report` | report-months | 0 | **base inchangée** (lecture seule) | non |
| BH-425 | `list` ; `help fleet:report` | demo | 0 | `fleet:report` décrite « Rapport mensuel d'utilisation de la flotte » ; option `-m, --month=MONTH Mois au format AAAA-MM [default: "2026-01"]` (défaut dépendant de l'horloge : à normaliser) | oui : rendu de l'aide (step-02/06) |

### 2.2 `fleet:demo:load` — rechargement des données de démonstration

**Destructive et sans confirmation** : supprime toutes les réservations puis tous les véhicules (DQL `DELETE`), puis
insère 5 véhicules (créés le `2016-01-04 08:00:00`) et 5 réservations ; les identifiants repartent à 1.
Sortie : `Données de démonstration chargées : 5 véhicules, 5 réservations.` (code 0).

| Fiche | Commande | Scénario | A1 | A2 / A3 / A4 — points clés (observés) | Sensible |
|---|---|---|---|---|---|
| BH-450 | `fleet:demo:load` | empty | 0 | stdout exact (une ligne) ; stderr vide ; **base = scénario `demo`** (empreinte identique, types SQLite compris) | **oui** : `ContainerAwareCommand`, DQL `DELETE` (step-02/06) |
| BH-451 | `fleet:demo:load` | nominal | 0 | 24 véhicules et 476 réservations **supprimés sans demande** ; base = `demo` ; ids repartent à 1 | oui : idem |
| BH-452 | `fleet:demo:load` | anomalies | 0 | données incohérentes supprimées sans erreur ; base = `demo` | oui : idem |
| BH-453 | `fleet:demo:load` | edge | 0 | 75 réservations supprimées (y compris celles des véhicules aux plaques non normalisées) ; base = `demo` | non |
| BH-454 | `fleet:demo:load` **deux fois** de suite | demo | 0, 0 | même sortie, base = `demo` : commande **idempotente** | non |
| BH-455 | `fleet:demo:load -n` (non interactif) | demo | 0 | même comportement : aucune question n'est jamais posée | oui : si une confirmation est ajoutée |
| BH-456 | `fleet:demo:load -q` | nominal | 0 | **aucune** sortie, base = `demo` quand même | non |
| BH-457 | `fleet:demo:load --env=prod` | nominal | 0 | **exécutée aussi en production** (aucun garde-fou d'environnement), base = `demo` | **oui** : un garde-fou futur serait un changement accepté |
| BH-458 | `fleet:demo:load extra` | demo | **1** | stderr : `Too many arguments, expected arguments "command".` ; **base inchangée** | oui : messages console |
| BH-459 | `help fleet:demo:load` ; `list` | demo | 0 | description `Vide la base et charge les donnees de demonstration` (**sans accents**, comme dans le code) | oui : rendu de l'aide |
| BH-460 | après `fleet:demo:load` : `GET /vehicules`, `GET /api/vehicules`, `fleet:report --month=2016-05` | nominal | 0 / 200 | mêmes sorties que BH-010, BH-201 et BH-400 | oui : liens avec ces fiches |

## 3. Exemple complet : BH-404

```markdown
### BH-404 — Le rapport ordonne les véhicules ex æquo dans leur ordre d'apparition (tri instable figé)
- Interface : console · fleet:report --month=2025-11
- Suite : cli (+ edge, + diff)            Groupe : zone-cli-report
- Scénario de données : report-months
- Sensible à la migration : oui, très → usort() avec un comparateur qui renvoie un booléen
  ($a['km'] < $b['km']) : PHP 8.0 (step-05) rend le tri stable et déprécie ce comparateur ; l'ordre des ex æquo
  peut changer, puis le comparateur booléen sera refusé dans une version future.
- Entrée : argument --month=2025-11, aucun terminal
- Attendu (step-00) :
  - A1 code de retour : 0
  - A2 stderr : vide
  - A3 stdout : golden `cli/report/2025-11.txt`, exactement :
      Rapport de flotte - 2025-11
      +-----------+--------------+-----+----------+
      | Plaque    | Réservations | Km  | Coût     |
      +-----------+--------------+-----+----------+
      | 1-REP-001 | 1            | 300 | 105,00 € |
      | 1-REP-002 | 1            | 300 | 105,00 € |
      | 1-REP-003 | 2            | 300 | 105,00 € |
      | 1-REP-008 | 1            | 3   | 1,05 €   |
      | 1-REP-004 | 1            | 1   | 0,35 €   |
      | TOTAL     |              | 904 | 316,40 € |
      +-----------+--------------+-----+----------+
    assertions ciblées : ordre des plaques ; TOTAL 904 / 316,40 €
  - A4 base : inchangée
  - A5 journal PHP : aucune entrée en step-00 (en PHP 8 : dépréciation du comparateur booléen attendue)
- Golden : tests/golden/cli/report/2025-11.txt
```

## 4. Points à trancher avant d'écrire les tests

| Question | Proposition |
|---|---|
| Ordre des ex æquo (BH-404 à BH-406) : figer l'ordre observé ? | **Oui** : le migrator devra rendre l'ordre explicite (même résultat qu'en PHP 7.2), sans s'appuyer sur la stabilité du tri |
| `--month=2025-13` accepté avec les données du mois suivant (BH-412, BH-413) | Préserver pendant la migration ; une validation stricte du mois serait un changement accepté |
| Message d'erreur de format sur la **sortie standard** avec code 1 (BH-414) | Préserver (des scripts peuvent lire stdout) |
| BH-416 / BH-417 : comportement différent `dev` / `prod` sur données incohérentes | Figer les deux ; c'est la divergence PHP 7 / 8 la plus probable du projet |
| `fleet:demo:load` destructive, sans confirmation ni garde-fou en `prod` (BH-450 à BH-457) | Préserver le comportement ; signaler le risque (perte de données en production) comme point d'amélioration post-migration |
| Défaut de `--month` affiché dans l'aide (BH-425) | Normaliser la date dans le golden (dépend de l'horloge) |
| Rendu exact des tableaux (bordures, largeurs, caractère `€`) | Contractuel (octet par octet) ; un changement de rendu du composant `Table` est un changement accepté à consigner |

## Blocages

Fiches mises en attente (décision, environnement…), une par ligne : `- BH-NNN : ⏸ raison`.
