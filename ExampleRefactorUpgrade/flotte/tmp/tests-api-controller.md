# Gabarit de test de caractérisation — `ApiController`

> **Objet** : appliquer le gabarit commun (`tmp/tests-vehicle-controller.md` § 1 et § 2 : fiche `BH-NNN`, déroulé
> Arrange → Act → Assert, A1 à A5, variante `diff`, définition de « terminé ») à l'unique route de l'API JSON,
> consommée par **un client externe** (« l'écran de planning des dépôts ») : c'est la route où un écart, même d'un
> octet, casse quelqu'un d'autre.
>
> Valeurs observées sur l'application step-00 réelle (PHP 7.2.34 / Symfony 2.8.52), scénarios `empty`, `demo`,
> `booking`, `edge`, `anomalies`, `nominal`, `volume`, le 2026-11-17.


<!-- tracking:start -->
## Suivi d'avancement (généré)

> Régénéré par `.claude/bin/test-tracking.py` (fin de chaque `.claude/bin/tests.sh`) — 2026-11-17 10:40. Ne pas éditer ce bloc : pour bloquer une fiche, ajouter « - BH-NNN : ⏸ raison » dans la section « Blocages ».

**0/25 terminés (0 %)** `░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░`

✅ 0 terminés · 🟡 0 incomplets · ✍️ 0 écrits · 🔴 0 rouges réf. · ⏸ 0 bloqués · ⬜ 25 à écrire

| Route | Terminés |
|---|---|
| `api_vehicles` | 0/25 |

<details><summary>Détail par fiche</summary>

| Fiche | Route | Fiche BH | Tests | Golden | Réf | App | Diff | Statut |
|---|---|---|---|---|---|---|---|---|
| BH-200 | `api_vehicles` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-201 | `api_vehicles` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-202 | `api_vehicles` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-203 | `api_vehicles` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-204 | `api_vehicles` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-205 | `api_vehicles` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-206 | `api_vehicles` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-207 | `api_vehicles` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-208 | `api_vehicles` | — | — | — | — | — | — | ⬜ à écrire |
| BH-209 | `api_vehicles` | — | — | — | — | — | — | ⬜ à écrire |
| BH-210 | `api_vehicles` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-211 | `api_vehicles` | — | — | — | — | — | — | ⬜ à écrire |
| BH-212 | `api_vehicles` | — | — | — | — | — | — | ⬜ à écrire |
| BH-213 | `api_vehicles` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-214 | `api_vehicles` | — | — | — | — | — | — | ⬜ à écrire |
| BH-215 | `api_vehicles` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-216 | `api_vehicles` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-217 | `api_vehicles` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-218 | `api_vehicles` | — | — | — | — | — | — | ⬜ à écrire |
| BH-219 | `api_vehicles` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-220 | `api_vehicles` | — | — | — | — | — | — | ⬜ à écrire |
| BH-221 | `api_vehicles` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-222 | `api_vehicles` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-223 | `api_vehicles` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-224 | `api_vehicles` | — | — | — | — | — | ✘ | ⬜ à écrire |

Légende — Fiche BH : décrite dans `docs/migration/behaviors/` · Tests : méthodes `bhNNN_*` · Golden : ✔ présents / ✘ manquants / — aucun · Réf / App : dernier run sur `app-ref` / `app` (✔ ✘ ↷ —) · Diff : variante comparative requise si la fiche est sensible à la migration.

</details>
<!-- tracking:end -->

## 1. Ce qui change par rapport au gabarit commun

- **A3 = comparaison octet par octet** du corps (aucune normalisation) contre le golden : l'ordre des clés,
  l'échappement des caractères, les types JSON (`84210` ≠ `"84210"`, `null` ≠ `""`) et l'absence de retour à la ligne
  final sont contractuels. Une empreinte SHA-256 du corps est stockée avec le golden pour un diagnostic rapide.
- En complément, **assertions structurelles** (après `json_decode`) pour localiser l'écart : nombre d'éléments, ordre
  des plaques, clés exactes `id, plate, label, mileage, status, nextReservation` dans cet ordre, types.
- **Lecture seule** : A4 = base inchangée pour toutes les fiches.
- Les **erreurs** (404, 405, 401) ne sont **pas** du JSON mais les pages HTML génériques du framework : on asserte
  statut et en-têtes, corps non contractuel (§ 2.4 du gabarit commun).

Format observé de la réponse (scénario `demo`, 591 octets, empreinte `6fe447542af8…`) :

```json
[{"id":1,"plate":"1-ABC-123","label":"Renault Kangoo","mileage":84210,"status":"Disponible","nextReservation":"15\/06\/2030 08:00"},{"id":2,"plate":"1-BCD-234","label":"Peugeot Partner","mileage":120450,"status":"Disponible","nextReservation":""},…]
```

Règles d'encodage observées (`JsonResponse` de Symfony 2.8 : `JSON_HEX_TAG | JSON_HEX_APOS | JSON_HEX_AMP |
JSON_HEX_QUOT`, sans `JSON_UNESCAPED_UNICODE` ni `JSON_UNESCAPED_SLASHES`) :

| Caractère | Encodé | Exemple observé |
|---|---|---|
| `/` | `\/` | `"15\/06\/2030 08:00"` |
| non ASCII | `\uXXXX` (paires de substitution pour les emoji) | `"Citroën"`, `"🚐"` |
| `<` `>` | `<` `>` | `<script>` |
| `&` `'` `"` | `&` `'` `"` | `"Aircross" &` |

## 2. Fiches

### 2.1 `api_vehicles` — `GET /api/vehicules`

Champs par véhicule, dans cet ordre : `id` (entier), `plate` (plaque **brute** de la base, non normalisée),
`label` (`"<marque> <modèle>"`), `mileage` (entier), `status` (libellé ou `null`), `nextReservation` (début de la
**première réservation `confirmee` dans l'ordre des dates**, même passée, au format `d\/m\/Y H:i`, ou `""`).
Tri : plaque, ordre binaire SQLite.

| Fiche | Requête | Scénario | A1 | A2 / A3 — points clés (observés) | Sensible |
|---|---|---|---|---|---|
| BH-200 | `GET /api/vehicules` | empty | 200 | corps exactement `[]` (2 octets) | oui : `JsonResponse` (encodage d'un tableau vide) |
| BH-201 | `GET /api/vehicules` | demo | 200 | 591 octets, golden + empreinte ; 5 éléments ; `nextReservation` `"15\/06\/2030 08:00"` pour 1-ABC-123, `""` sinon | **oui** : options d'encodage JSON (Symfony 2.8 → 6.4), sérialisation |
| BH-202 | en-têtes de la réponse | demo | 200 | `Content-Type: application/json` (**sans** `charset`) ; `X-Fleet-Version: 1.4.2` ; `Cache-Control: no-cache` ; **aucun** `Set-Cookie`, aucun en-tête CORS, `ETag` ni `Last-Modified` | **oui** : `JsonResponse`, `Content-Type`, session |
| BH-203 | octets du corps | demo | 200 | pas de BOM, pas de retour à la ligne final (se termine par `"}]`), aucun espace hors des valeurs | oui : encodage |
| BH-204 | types JSON | demo | 200 | `id` et `mileage` **nombres**, les autres **chaînes** | oui : DBAL 3 (entiers rendus en chaînes ?) |
| BH-205 | `GET /api/vehicules` | edge | 200 | ordre binaire des plaques **brutes** : `" 2-BBB-004 "` (espaces conservés) en premier, puis `0-AAA-000`, `1-ABC-12`… ; `1-aaa-003` en minuscules (non normalisée, contrairement au HTML) | oui : tri SQLite, plaque brute |
| BH-206 | caractères spéciaux | edge | 200 | `label` du véhicule 18 = `"Citroën <b>C3<\/b> "Aircross" & <script>alert('x')<\/script>"` ; véhicule 20 : `"Škoda Énergie … 電気 🚐"` | **oui, très** : drapeaux `JSON_HEX_*`, `JSON_UNESCAPED_*` |
| BH-207 | grands nombres | edge | 200 | `"mileage":2147483647` (nombre, pas de notation exponentielle) | oui : entiers, DBAL |
| BH-208 | longueur maximale | edge | 200 | `label` de 101 caractères (50 + espace + 50) | non |
| BH-209 | réservation **en retard** | edge (1-OVD-001) | 200 | `"nextReservation":"05\/01\/2026 08:00"` : la réservation passée non rendue est présentée comme la « prochaine » | non (bug figé) |
| BH-210 | même date de début | edge (1-SAM-001) | 200 | `"20\/02\/2026 08:00"` | oui : ordre des réservations (`@OrderBy`, ORM) |
| BH-211 | statuts du véhicule | edge (11, 12) | 200 | « En entretien » avec `"03\/02\/2026 08:00"` ; « Hors service » avec la réservation en cours `"14\/01\/2026 08:00"` | non |
| BH-212 | longue liste de réservations | edge (1-LNG-040) | 200 | `nextReservation` = première confirmée (`"19\/01\/2026 08:00"`), annulées et terminées ignorées | non |
| BH-213 | heure d'été | edge (1-DST-001) | 200 | `"29\/03\/2026 01:30"` (heure naïve, telle qu'en base) | oui : `DateTime`, fuseau |
| BH-214 | création et blocages | booking | 200 | 8 éléments ; `nextReservation` : `"10\/02\/2026 10:00"` (BOK-002), `"02\/02\/2026 08:00"` (BOK-003 en entretien), `"14\/01\/2026 08:00"` (en cours), `"05\/01\/2026 08:00"` (en retard), `""` quand seules des annulées / terminées existent | non |
| BH-215 | données incohérentes | anomalies | 200 | statut inconnu (3, -1) → **`"status":null`** (notice PHP) ; plaque vide `""` triée en premier ; marque et modèle vides → `"label":" "` ; `"mileage":-500` ; fin avant début → `"08\/01\/2026 18:00"` ; statut `CONFIRMEE` (majuscules) **ignoré** ; A5 : notices `Undefined offset` | **oui** : notice → warning (PHP 8) ; `null` |
| BH-216 | flotte réaliste | nominal | 200 | 24 éléments, golden + empreinte (3 124 octets) | oui : comme BH-201 |
| BH-217 | volume | volume | 200 | 500 éléments, 58 504 octets ; **~300 ms** en step-00, contre ~40 ms pour la liste HTML du même volume (une requête SQL par véhicule pour charger ses réservations : N+1) ; suite `diff` : temps `app` ≤ 2 × `app-ref` | oui : performance, chargement paresseux (ORM) |
| BH-218 | `GET /api/vehicules?status=1&callback=x` | demo | 200 | paramètres **ignorés** : corps identique à BH-201 (pas de filtre, pas de JSONP) | non |
| BH-219 | `Accept: application/xml` ; `Origin: https://planning.example` | demo | 200 | aucune négociation de contenu (JSON quand même), aucun en-tête CORS | oui : si un composant CORS est ajouté |
| BH-220 | `HEAD /api/vehicules` | demo | 200 | corps vide, `Content-Type: application/json` | non |
| BH-221 | `POST` et `OPTIONS /api/vehicules` | demo | 405 | `Allow: GET, HEAD` ; corps **HTML** (pas de JSON d'erreur) | oui : page d'erreur |
| BH-222 | `GET /api/vehicules/`, `/api/vehicules.json`, `/api/vehicules/1` | demo | 404 | page HTML ; aucune autre route d'API | oui : routage |
| BH-223 | Basic `gestionnaire:mauvais` | demo | **401** | `WWW-Authenticate: Basic realm="Secured Area"` : de mauvais identifiants bloquent l'API pourtant publique | **oui** : authenticators (step-06) |
| BH-224 | Basic `gestionnaire:flotte2016` | demo | 200 | corps identique à BH-201, **mais** `Set-Cookie: PHPSESSID` (session ouverte par l'authentification) | **oui** : firewall sans état ou non (step-06) |

## 3. Exemple complet : BH-206

```markdown
### BH-206 — L'API échappe le HTML, les guillemets et tout caractère non ASCII
- Route / interface : api_vehicles · GET /api/vehicules
- Suite : http (+ diff)            Groupe : zone-api-vehicles
- Scénario de données : edge
- Authentification : aucune
- Sensible à la migration : oui, très → l'encodage dépend des options de `JsonResponse`
  (`JSON_HEX_TAG | JSON_HEX_APOS | JSON_HEX_AMP | JSON_HEX_QUOT` en 2.8) ; une autre combinaison (ou
  `JSON_UNESCAPED_UNICODE` / `JSON_UNESCAPED_SLASHES`) change les octets sans changer le sens.
- Entrée : aucune
- Attendu (step-00) :
  - A1 statut : 200
  - A2 en-têtes : Content-Type application/json · X-Fleet-Version 1.4.2 · pas de Set-Cookie
  - A3 corps : octet par octet = golden `api-vehicles/edge.json` (3 458 octets, empreinte 6f2bebf09564…) ;
    élément id 18 : label exactement
    "Citroën <b>C3<\/b> "Aircross" & <script>alert('x')<\/script>" ;
    après json_decode : label = `Citroën <b>C3</b> "Aircross" & <script>alert('x')</script>`
  - A4 base : inchangée
  - A5 journal PHP : aucune entrée
- Golden : tests/golden/http/api-vehicles/edge.json
```

## 4. Points à trancher avant d'écrire les tests

| Question | Proposition |
|---|---|
| Octets exacts (échappements `\/`, `\uXXXX`, `<`) ou seulement JSON équivalent ? | **Octets exacts** : le client externe n'est pas sous notre contrôle ; un changement d'encodage est un changement accepté à consigner |
| `nextReservation` qui renvoie une réservation **passée** (BH-209) | Préserver (bug figé), correction éventuelle dans une étape séparée, en prévenant le client du planning |
| `"status": null` pour un statut inconnu (BH-215) | Préserver le `null` ; la notice PHP devient warning en PHP 8 sans changer le corps |
| 401 avec de mauvais identifiants (BH-223), cookie de session après authentification (BH-224) | Préserver tant que le client ne s'authentifie pas ; à revoir à step-06 (nouveau système de sécurité) |
| Performance N+1 (BH-217) | Seuil ≤ 2 × la référence ; l'optimisation (jointure) est un changement interne, autorisé s'il ne change aucun octet |
| Erreurs HTML sur une route d'API (BH-221, BH-222) | Corps non contractuel ; une page d'erreur JSON future serait un changement accepté |

## Blocages

Fiches mises en attente (décision, environnement…), une par ligne : `- BH-NNN : ⏸ raison`.
