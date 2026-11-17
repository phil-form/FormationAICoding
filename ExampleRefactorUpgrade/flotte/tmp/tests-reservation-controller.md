# Gabarit de test de caractérisation — `ReservationController`

> **Objet** : appliquer le gabarit commun (`tmp/tests-vehicle-controller.md` § 1 et § 2 : fiche `BH-NNN`, déroulé
> Arrange → Act → Assert, A1 à A5, variante `diff`, définition de « terminé ») aux 2 routes **qui écrivent en base** :
> création d'une réservation et retour d'un véhicule.
>
> Toutes les valeurs « observées » viennent de l'application step-00 réelle (PHP 7.2.34 / Symfony 2.8.52), par des
> soumissions de formulaires complètes (session, jeton CSRF) sur les scénarios `booking`, `returns` et `anomalies`,
> avec comparaison de la base avant / après (2026-11-17).


<!-- tracking:start -->
## Suivi d'avancement (généré)

> Régénéré par `.claude/bin/test-tracking.py` (fin de chaque `.claude/bin/tests.sh`) — 2026-11-17 10:36. Ne pas éditer ce bloc : pour bloquer une fiche, ajouter « - BH-NNN : ⏸ raison » dans la section « Blocages ».

**0/69 terminés (0 %)** `░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░`

✅ 0 terminés · 🟡 0 incomplets · ✍️ 0 écrits · 🔴 0 rouges réf. · ⏸ 0 bloqués · ⬜ 69 à écrire

| Route | Terminés |
|---|---|
| `reservation_new` | 0/39 |
| `reservation_return` | 0/30 |

<details><summary>Détail par fiche</summary>

| Fiche | Route | Fiche BH | Tests | Golden | Réf | App | Diff | Statut |
|---|---|---|---|---|---|---|---|---|
| BH-100 | `reservation_new` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-101 | `reservation_new` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-102 | `reservation_new` | — | — | — | — | — | — | ⬜ à écrire |
| BH-103 | `reservation_new` | — | — | — | — | — | — | ⬜ à écrire |
| BH-104 | `reservation_new` | — | — | — | — | — | — | ⬜ à écrire |
| BH-105 | `reservation_new` | — | — | — | — | — | — | ⬜ à écrire |
| BH-106 | `reservation_new` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-107 | `reservation_new` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-108 | `reservation_new` | — | — | — | — | — | — | ⬜ à écrire |
| BH-109 | `reservation_new` | — | — | — | — | — | — | ⬜ à écrire |
| BH-110 | `reservation_new` | — | — | — | — | — | — | ⬜ à écrire |
| BH-111 | `reservation_new` | — | — | — | — | — | — | ⬜ à écrire |
| BH-112 | `reservation_new` | — | — | — | — | — | — | ⬜ à écrire |
| BH-113 | `reservation_new` | — | — | — | — | — | — | ⬜ à écrire |
| BH-114 | `reservation_new` | — | — | — | — | — | — | ⬜ à écrire |
| BH-115 | `reservation_new` | — | — | — | — | — | — | ⬜ à écrire |
| BH-116 | `reservation_new` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-117 | `reservation_new` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-118 | `reservation_new` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-119 | `reservation_new` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-120 | `reservation_new` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-121 | `reservation_new` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-122 | `reservation_new` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-123 | `reservation_new` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-124 | `reservation_new` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-125 | `reservation_new` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-126 | `reservation_new` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-127 | `reservation_new` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-128 | `reservation_new` | — | — | — | — | — | — | ⬜ à écrire |
| BH-129 | `reservation_new` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-130 | `reservation_new` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-131 | `reservation_new` | — | — | — | — | — | — | ⬜ à écrire |
| BH-132 | `reservation_new` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-133 | `reservation_new` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-134 | `reservation_new` | — | — | — | — | — | — | ⬜ à écrire |
| BH-135 | `reservation_new` | — | — | — | — | — | — | ⬜ à écrire |
| BH-136 | `reservation_new` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-137 | `reservation_new` | — | — | — | — | — | — | ⬜ à écrire |
| BH-138 | `reservation_new` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-150 | `reservation_return` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-151 | `reservation_return` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-152 | `reservation_return` | — | — | — | — | — | — | ⬜ à écrire |
| BH-153 | `reservation_return` | — | — | — | — | — | — | ⬜ à écrire |
| BH-154 | `reservation_return` | — | — | — | — | — | — | ⬜ à écrire |
| BH-155 | `reservation_return` | — | — | — | — | — | — | ⬜ à écrire |
| BH-156 | `reservation_return` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-157 | `reservation_return` | — | — | — | — | — | — | ⬜ à écrire |
| BH-158 | `reservation_return` | — | — | — | — | — | — | ⬜ à écrire |
| BH-159 | `reservation_return` | — | — | — | — | — | — | ⬜ à écrire |
| BH-160 | `reservation_return` | — | — | — | — | — | — | ⬜ à écrire |
| BH-161 | `reservation_return` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-162 | `reservation_return` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-163 | `reservation_return` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-164 | `reservation_return` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-165 | `reservation_return` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-166 | `reservation_return` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-167 | `reservation_return` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-168 | `reservation_return` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-169 | `reservation_return` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-170 | `reservation_return` | — | — | — | — | — | — | ⬜ à écrire |
| BH-171 | `reservation_return` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-172 | `reservation_return` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-173 | `reservation_return` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-174 | `reservation_return` | — | — | — | — | — | — | ⬜ à écrire |
| BH-175 | `reservation_return` | — | — | — | — | — | — | ⬜ à écrire |
| BH-176 | `reservation_return` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-177 | `reservation_return` | — | — | — | — | — | — | ⬜ à écrire |
| BH-178 | `reservation_return` | — | — | — | — | — | — | ⬜ à écrire |
| BH-179 | `reservation_return` | — | — | — | — | — | ✘ | ⬜ à écrire |

Légende — Fiche BH : décrite dans `docs/migration/behaviors/` · Tests : méthodes `bhNNN_*` · Golden : ✔ présents / ✘ manquants / — aucun · Réf / App : dernier run sur `app-ref` / `app` (✔ ✘ ↷ —) · Diff : variante comparative requise si la fiche est sensible à la migration.

</details>
<!-- tracking:end -->

## 1. Ce qui change par rapport au gabarit commun

Ces routes **modifient la base** et passent par un formulaire protégé par CSRF. Le déroulé d'un test devient un
**parcours** de 3 requêtes au lieu d'une :

1. **Arrange** : restaurer le scénario, client neuf **avec session** (cookies conservés), noter la taille du journal PHP.
2. **GET du formulaire** (`/vehicules/{id}/reserver` ou `/reservations/{id}/retour`) : extraire le jeton
   `reservation[_token]` / `form[_token]` (cas « sans jeton » ou « jeton faux » : ne pas l'envoyer / en envoyer un faux).
3. **Act — POST** : `application/x-www-form-urlencoded`, champs **exactement** ceux de la fiche (valeurs brutes,
   pas de reformatage par le test).
4. **Assert A1/A2** sur la réponse au POST : `302` + `Location` relatif, ou `200` (formulaire réaffiché), ou erreur.
5. **Assert A3** :
   - 302 → **GET de `Location`** avec la même session et vérification du message flash
     `<p class="flash-success">…</p>` (il n'apparaît qu'une fois : un second GET ne l'affiche plus) ;
   - 200 → erreurs du formulaire, **texte exact et emplacement** : erreur globale (règle métier, CSRF, champs en trop)
     en tête du formulaire, erreur de champ (`This value is not valid.`) dans le `<div>` du champ, avant l'`<input>` ;
     valeurs saisies réaffichées (`value="…"`) ;
   - golden du HTML normalisé (jeton CSRF masqué).
6. **Assert A4 — diff exact de la base** (le point le plus important pour ces routes) : lignes ajoutées et modifiées,
   colonne par colonne, **types SQLite compris** (`quote()` : un coût `35` INTEGER n'est pas `35.0` REAL), et **rien
   d'autre**. Cas refusés : base **strictement inchangée**.
7. **Assert A5** : journal PHP (attention : en step-00 les exceptions ne sont **journalisées nulle part**, MonologBundle
   n'est pas installé — le 500 ne se constate que par la réponse et la base).

Rappels observés communs aux deux routes :

- les formulaires n'ont **aucun bouton de soumission** (`{{ form(form) }}` sans bouton) : seule la touche Entrée soumet ;
- messages Symfony **en anglais** (pas de traducteur configuré), messages métier en français ;
- `GET` du formulaire : 200 même quand la soumission sera refusée (véhicule indisponible, réservation clôturée) ;
- `POST` sans aucun champ du formulaire : **200 sans erreur**, rien n'est écrit (`isValid()` appelé sur un formulaire
  non soumis — **lève une exception à partir de Symfony 4**) ;
- en-têtes : `X-Fleet-Version: 1.4.2`, `Cache-Control: no-cache`, cookie `PHPSESSID` ; 404 si l'id n'existe pas,
  405 pour une autre méthode que GET/POST.

## 2. Fiches

### 2.1 `reservation_new` — `GET|POST /vehicules/{id}/reserver`

Formulaire `reservation` : `driverName` (texte), `startAt` et `endAt` (texte `dd/MM/yyyy HH:mm`), `_token`.
Page : `<h1>Réserver 1-BOK-001</h1>`, `<p>Format des dates : JJ/MM/AAAA HH:MM</p>`.
Création réussie → ligne `reservation` : `status = 'confirmee'`, `start_km` = kilométrage du véhicule,
`end_km` et `cost` NULL ; 302 vers `/vehicules/{id}` ; flash « Réservation enregistrée. ».

Scénario `booking` (date de référence 15/01/2026 10:00) : véhicule 2 réservé le 10/02/2026 10:00–12:00 ; 3 en
entretien ; 4 hors service ; 5 n'a sur le créneau qu'une réservation terminée et une annulée ; 6 en cours (14/01 → 16/01) ;
7 en retard (05/01 → 06/01, jamais rendu) ; 8 à 0 km.

| Fiche | Entrée (POST sauf mention) | Scénario | A1 | A3 / A4 — points clés (observés) | Sensible |
|---|---|---|---|---|---|
| BH-100 | `GET /vehicules/1/reserver` | booking | 200 | titre, rappel du format, 3 champs `required`, jeton caché, **pas de bouton** | oui : rendu des formulaires (Twig/Form, step-01→04) |
| BH-101 | véhicule 1, `Jean Test`, `20/02/2026 08:00` → `20/02/2026 18:00` | booking | 302 | `Location: /vehicules/1` ; flash « Réservation enregistrée. » ; A4 : +1 ligne id 11 `(1, 'Jean Test', '2026-02-20 08:00:00', '2026-02-20 18:00:00', 'confirmee', 10000, NULL, NULL)` | oui : `isValid()` sans `isSubmitted()`, types par nom (step-01/02) |
| BH-102 | véhicule 3 (entretien) puis 4 (hors service), créneau libre | booking | 200 | erreur globale « Ce véhicule n'est pas disponible. » ; base inchangée | non |
| BH-103 | fin = début (`08:00` → `08:00`) | booking | 200 | « La date de fin doit être postérieure à la date de début. » | non |
| BH-104 | fin avant début | booking | 200 | même message | non |
| BH-105 | véhicule 2, `10/02/2026 10:30` → `11:30` (inclus) | booking | 200 | « Ce véhicule est déjà réservé sur cette période. » | non |
| BH-106 | véhicule 2, `08:00` → `10:00` (se termine pile au début de l'existante) | booking | 200 | **refusé** : bornes incluses, se toucher = chevaucher | oui : requête DQL (ORM 2.5 → 2.17/3) |
| BH-107 | véhicule 2, `12:00` → `14:00` (commence pile à la fin) | booking | 200 | **refusé** (bornes incluses) | oui : idem |
| BH-108 | véhicule 2, `09:00` → `13:00` (englobe) | booking | 200 | refusé | non |
| BH-109 | véhicule 2, `08:00` → `09:59` puis `12:01` → `14:00` | booking | 302 | acceptés (une minute d'écart suffit) ; +1 ligne chacun | non |
| BH-110 | véhicule 5, créneau d'une réservation **terminée** et d'une **annulée** | booking | 302 | accepté : seules les `confirmee` bloquent | non |
| BH-111 | véhicule 2, `20/02/2026 09:00` → `10:00` (sur une annulée) | booking | 302 | accepté | non |
| BH-112 | véhicule 6, `15/01/2026 12:00` → `14:00` (pendant une réservation en cours) | booking | 200 | « déjà réservé » | non |
| BH-113 | véhicule 7, `05/01/2026 12:00` → `14:00` (créneau d'une réservation **en retard**) | booking | 200 | « déjà réservé » : une réservation passée non rendue bloque toujours son créneau | non |
| BH-114 | véhicule 1, `01/12/2025 08:00` → `10:00` (dans le **passé**) | booking | 302 | **accepté** : aucun contrôle de date passée | non |
| BH-115 | véhicule 8 (0 km) | booking | 302 | `start_km = 0` | non |
| BH-116 | conducteur vide `""`, puis `"   "` | booking | 200 | « Le nom du conducteur est obligatoire. » (les espaces sont supprimés avant validation) | oui : option `trim`, NotBlank |
| BH-117 | conducteur `"  Paul Martin  "` | booking | 302 | enregistré **`Paul Martin`** (espaces supprimés) | oui : idem |
| BH-118 | conducteur de **101** caractères | booking | 302 | **accepté et enregistré en entier** (colonne VARCHAR(100) non contrôlée par SQLite, aucune contrainte `Length`) | **oui** : DBAL 3 / validation |
| BH-119 | conducteur `<script>alert(1)</script> Émile 🚐` | booking | 302 | enregistré tel quel ; affiché **échappé** sur la fiche véhicule (lien avec BH-049) | oui : échappement Twig |
| BH-120 | dates `2026-02-20 08:00` (ISO) | booking | 200 | `This value is not valid.` sur **les deux** champs (erreur de champ, en anglais) | oui : `IntlDateFormatter`, messages |
| BH-121 | dates `31/02/2026 08:00` ; `20/02/2026 25:00` ; `20/02/2026 8` (sans minutes) | booking | 200 | `This value is not valid.` ×2 (dates non « lenient ») | oui : ICU |
| BH-122 | dates `20/02/26 08:00` → `20/02/26 18:00` (**année sur 2 chiffres**) | booking | 302 | **accepté**, enregistré **`0026-02-18 07:42:30`** → `0026-02-18 17:42:30` : an 26, **décalé de 2 jours** (calendrier julien) et de **−17 min 30 s** (heure solaire de Bruxelles avant 1892) | **oui, très** : ICU, `DateTime`, PHP 8 (step-05), DBAL 3 |
| BH-123 | dates `1/2/2026 8:05` → `1/2/2026 9:05` (chiffres uniques) | booking | 302 | accepté : `2026-02-01 08:05:00` | oui : ICU |
| BH-124 | dates avec secondes `20/02/2026 08:00:30` | booking | 302 | accepté, **secondes supprimées** (`08:00:00`) | oui : ICU |
| BH-125 | `29/03/2026 02:30` → `03:30` (heure **inexistante**, passage à l'heure d'été) | booking | 200 | `This value is not valid.` sur `startAt` uniquement | **oui** : ICU / fuseau |
| BH-126 | `25/10/2026 02:30` → `02:45` (heure **ambiguë**, passage à l'heure d'hiver) | booking | 302 | accepté, enregistré tel quel | oui : idem |
| BH-127 | dates en `31/12/9999` | booking | 200 | `This value is not valid.` ×2 | oui : ICU, dépassement |
| BH-128 | minutes quelconques `08:07` → `08:08` (1 minute) | booking | 302 | accepté (pas de pas imposé) | non |
| BH-129 | sans jeton CSRF, puis jeton faux | booking | 200 | erreur globale `The CSRF token is invalid. Please try to resubmit the form.` ; base inchangée | oui : composant CSRF (step-04/06) |
| BH-130 | champ en trop dans le formulaire `reservation[status]=terminee` | booking | 200 | `This form should not contain extra fields.` ; **pas** d'affectation | oui : `allow_extra_fields` |
| BH-131 | champ en trop **hors** du formulaire (`foo=bar`) | booking | 302 | ignoré, réservation créée | non |
| BH-132 | seulement le jeton (aucun champ) | booking | 200 | 3 erreurs : « Le nom du conducteur est obligatoire. », `This value should not be blank.` ×2 (mélange FR / EN) | oui : messages de validation |
| BH-133 | POST **sans corps** | booking | 200 | formulaire réaffiché **sans erreur**, rien écrit | **oui** : Symfony 4 → exception (500) attendue sans précaution |
| BH-134 | même POST envoyé **deux fois** (même jeton) | booking | 302 puis 200 | 1re : créée ; 2e : « déjà réservé » ; **1 seule** ligne | non |
| BH-135 | `GET /vehicules/3/reserver` (véhicule en entretien) | booking | 200 | formulaire affiché normalement (refus seulement à la soumission) | non |
| BH-136 | `POST /vehicules/999/reserver` ; `GET /vehicules/abc/reserver` | booking | 404 | base inchangée | oui : ParamConverter → MapEntity |
| BH-137 | `PUT /vehicules/1/reserver` | booking | 405 | `Allow: GET, POST, HEAD` | non |
| BH-138 | réservation créée puis **fiche véhicule** | booking | 302 → 200 | la nouvelle réservation apparaît dans « Réservations à venir » avec son lien de retour (`/reservations/11/retour`) | oui : lien avec BH-040 |

### 2.2 `reservation_return` — `GET|POST /reservations/{id}/retour`

Formulaire `form` : `endKm` (`<input type="number">`), `_token`. Page : `<h1>Retour de 1-RET-001</h1>`,
`<p>Kilométrage au départ : 10.000 km</p>`.
Retour réussi → `status = 'terminee'`, `end_km`, `cost = round(km × 0,35, 2)`, **kilométrage du véhicule =
`end_km`** ; 302 vers `/vehicules/{id}` ; flash « Retour enregistré. Coût : 35,00 € ».

Scénario `returns` : réservation 1 (véhicule 1, départ 10 000 km), 2 (départ 0), 3 terminée, 4 annulée, 5 (départ
50 000, véhicule déjà à 50 500), 6 (départ 2 147 483 000), 7 (véhicule en entretien), 8 future et 9 en cours sur le
même véhicule 8 (départ 8 000).

| Fiche | Entrée (POST `form[endKm]` sauf mention) | Scénario | A1 | A3 / A4 — points clés (observés) | Sensible |
|---|---|---|---|---|---|
| BH-150 | `GET /reservations/1/retour` | returns | 200 | titre, kilométrage au départ formaté, champ `type="number"` `required`, **pas de bouton** | oui : rendu des formulaires |
| BH-151 | réservation 1, `10100` | returns | 302 | `Location: /vehicules/1` ; flash « Retour enregistré. Coût : 35,00 € » ; A4 : r1 `status 'terminee'`, `end_km 10100`, `cost 35` (**INTEGER**) ; v1 `mileage 10100` | oui : arithmétique, types (step-05, DBAL 3) |
| BH-152 | réservation 1, `10000` (0 km) | returns | 302 | « Coût : 0,00 € » ; `cost 0` ; kilométrage du véhicule inchangé | non |
| BH-153 | réservation 1, `9999` (moins qu'au départ) | returns | 200 | « Kilométrage invalide. » ; base inchangée | non |
| BH-154 | réservation 3 (**terminée**) | returns | 200 | « Cette réservation est déjà clôturée. » | non |
| BH-155 | réservation 4 (**annulée**) | returns | 200 | **même** message « déjà clôturée » | non |
| BH-156 | réservation 2, `1` / `3` / `7` / `13` km | returns | 302 | coûts `0,35 €` / `1,05 €` / `2,45 €` / `4,55 €` ; stockés `0.35`, `1.05`, `2.45`, `4.55` (REAL) | **oui** : flottants, `round`, `number_format` |
| BH-157 | réservation 7 (véhicule **en entretien**), `7050` | returns | 302 | accepté, `cost 17.5` (REAL), « 17,50 € » | non |
| BH-158 | réservation 5 (`50200` alors que le véhicule est à 50 500) | returns | 302 | accepté ; **le kilométrage du véhicule recule** à 50 200 ; « 70,00 € » | non |
| BH-159 | réservation 8 (**pas encore commencée**), `8100` | returns | 302 | accepté (aucun contrôle de date) | non |
| BH-160 | réservation 9 (en cours, même véhicule que 8), `8300` | returns | 302 | r9 clôturée « 105,00 € », v8 à 8 300 ; **r8 reste `confirmee` avec `start_km 8000`** | non |
| BH-161 | réservation 6, `2147484000` | returns | 302 | « 350,00 € » ; **kilométrage du véhicule > 2^31-1** enregistré | oui : entiers 32/64 bits, DBAL |
| BH-162 | `99999999999999999999` | returns | 200 | `This value is not valid.` (champ) | oui : `IntegerType`, ICU |
| BH-163 | `abc` ; `+10100` ; `10 100` (espace) | returns | 200 | `This value is not valid.` | oui : `IntegerType` (locale `fr`) |
| BH-164 | `10100.6` et `10100,6` | returns | 302 | **tronqué à 10100** (arrondi vers le bas), 35,00 € | **oui** : `IntegerType` (`rounding_mode`, `scale`), step-02/04 |
| BH-165 | `10.100` (point des milliers) | returns | 200 | interprété comme **10** → « Kilométrage invalide. » | **oui** : idem, locale |
| BH-166 | `010100` (zéro initial) | returns | 302 | accepté = 10100 | oui : idem |
| BH-167 | `1e4` | returns | 302 | **interprété comme 10000** → clôture à 0 km, « 0,00 € » | **oui** : idem |
| BH-168 | réservation 1, valeur **vide** | returns | 200 | « Kilométrage invalide. » (`null < 10000`) | **oui** : comparaison `null` (PHP 8) |
| BH-169 | réservation 2 (départ **0**), valeur **vide** | returns | **500** | page d'erreur générique ; **base inchangée** ; aucune trace dans un journal (cause déduite : kilométrage du véhicule mis à `NULL` → violation `NOT NULL`, transaction annulée) | **oui** : erreur différente attendue en PHP 8 / DBAL 3 |
| BH-170 | réservation 2, `-5` | returns | 200 | « Kilométrage invalide. » | non |
| BH-171 | sans jeton CSRF, puis jeton faux | returns | 200 | `The CSRF token is invalid. Please try to resubmit the form.` ; base inchangée | oui : CSRF |
| BH-172 | champ en trop `form[cost]=0` | returns | 200 | `This form should not contain extra fields.` | oui : `allow_extra_fields` |
| BH-173 | POST **sans corps** | returns | 200 | formulaire réaffiché sans erreur, rien écrit | **oui** : Symfony 4 (`isValid()` non soumis) |
| BH-174 | même POST **deux fois** | returns | 302 puis 200 | 1re : clôturée ; 2e : « déjà clôturée » ; une seule mise à jour | non |
| BH-175 | `GET /reservations/3/retour` (terminée) | returns | 200 | formulaire affiché normalement | non |
| BH-176 | `POST /reservations/999/retour` ; `GET /reservations/abc/retour` | returns | 404 | base inchangée | oui : ParamConverter |
| BH-177 | `PUT /reservations/1/retour` | returns | 405 | `Allow: GET, POST, HEAD` | non |
| BH-178 | réservation `terminee` du scénario `anomalies` (id 3, `end_km` NULL) | anomalies | 200 | « déjà clôturée » | non |
| BH-179 | retour réussi puis **fiche véhicule** | returns | 302 → 200 | la réservation passe de « Réservations à venir » à « Historique » (`10100 − 10000 = 100 km, 35,00 €`) ; kilométrage affiché `10.100 km` | oui : lien avec BH-040 |

## 3. Exemple complet : BH-122

```markdown
### BH-122 — Une année saisie sur 2 chiffres est enregistrée en l'an 26, décalée
- Route / interface : reservation_new · POST /vehicules/1/reserver
- Suite : edge (+ diff, + db)            Groupe : zone-reservation-new
- Scénario de données : booking
- Authentification : aucune
- Sensible à la migration : oui, très → le format ICU `dd/MM/yyyy` accepte `26` comme année 26 ; la conversion en
  DateTime applique le calendrier julien (−2 jours) et l'heure solaire de Bruxelles d'avant 1892 (−17 min 30 s).
  Toute montée d'ICU, de PHP (step-03/05/07) ou de DBAL (step-06) peut changer ce résultat.
- Entrée : reservation[driverName]=X, reservation[startAt]=20/02/26 08:00, reservation[endAt]=20/02/26 18:00, jeton valide
- Attendu (step-00) :
  - A1 statut : 302
  - A2 en-têtes : Location /vehicules/1 · X-Fleet-Version 1.4.2
  - A3 corps : (après GET de Location) flash « Réservation enregistrée. » ; « Réservations à venir » contient
    `18/02/0026 07:42 → 18/02/0026 17:42 : X` (observé)
  - A4 base : +1 ligne reservation id 11 = (1, 'X', '0026-02-18 07:42:30', '0026-02-18 17:42:30', 'confirmee',
    10000, NULL, NULL) ; rien d'autre
  - A5 journal PHP : aucune entrée
- Golden : tests/golden/http/reservation-new/two-digit-year.html
```

## 4. Points à trancher avant d'écrire les tests

| Question | Proposition |
|---|---|
| BH-122 (année sur 2 chiffres → an 26 décalé), BH-164/165/167 (`10100,6` tronqué, `10.100` = 10, `1e4` = 10000) : préserver ? | **Oui** pendant la migration (données réellement enregistrées) ; corrections dans une étape séparée, après décision |
| BH-169 (500 sur retour vide quand le départ est à 0) : figer le 500 ? | Figer **statut 500 + base inchangée** ; corps non contractuel (page d'erreur du framework) |
| BH-133 / BH-173 (POST vide → 200 sans erreur) : préserver malgré Symfony 4 ? | Oui : le migrator devra ajouter le contrôle `isSubmitted()` **sans** changer la réponse |
| Messages Symfony en anglais (`This value is not valid.`…) : contractuels ? | Oui (texte visible par l'utilisateur) — toute traduction future est un changement accepté à consigner |
| Absence de bouton de soumission : préserver ? | Oui (le HTML est contractuel) ; à signaler comme bug d'ergonomie |
| Exceptions non journalisées (pas de MonologBundle) | Hors contrat de test ; à signaler pour la migration (ajout de journalisation = changement accepté) |

## Blocages

Fiches mises en attente (décision, environnement…), une par ligne : `- BH-NNN : ⏸ raison`.
