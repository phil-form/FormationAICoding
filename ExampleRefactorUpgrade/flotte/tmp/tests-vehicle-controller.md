# Gabarit de test de caractérisation — `VehicleController`

> **Objet** : définir ce que **chaque** test doit faire pour garantir qu'une montée de version (PHP 7.2 → 8.4,
> Symfony 2.8 → 6.4) ne change **rien** au comportement observable de l'application, bugs compris ; puis appliquer ce
> gabarit aux 3 routes du premier contrôleur.
>
> Valeurs « observées » : application step-00 réelle (PHP 7.2.34 / Symfony 2.8.52), scénarios `demo` et `edge`,
> le 2026-11-17. Références : `tmp/routes-et-commandes.md`, `tests/fixtures/README.md`,
> `.claude/reference/conventions.md` § 5.


<!-- tracking:start -->
## Suivi d'avancement (généré)

> Régénéré par `.claude/bin/test-tracking.py` (fin de chaque `.claude/bin/tests.sh`) — 2026-11-17 10:30. Ne pas éditer ce bloc : pour bloquer une fiche, ajouter « - BH-NNN : ⏸ raison » dans la section « Blocages ».

**0/40 terminés (0 %)** `░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░`

✅ 0 terminés · 🟡 0 incomplets · ✍️ 0 écrits · 🔴 0 rouges réf. · ⏸ 0 bloqués · ⬜ 40 à écrire

| Route | Terminés |
|---|---|
| `homepage` | 0/4 |
| `vehicle_index` | 0/19 |
| `vehicle_show` | 0/17 |

<details><summary>Détail par fiche</summary>

| Fiche | Route | Fiche BH | Tests | Golden | Réf | App | Diff | Statut |
|---|---|---|---|---|---|---|---|---|
| BH-001 | `homepage` | — | — | — | — | — | — | ⬜ à écrire |
| BH-002 | `homepage` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-003 | `homepage` | — | — | — | — | — | — | ⬜ à écrire |
| BH-004 | `homepage` | — | — | — | — | — | — | ⬜ à écrire |
| BH-010 | `vehicle_index` | — | — | — | — | — | — | ⬜ à écrire |
| BH-011 | `vehicle_index` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-012 | `vehicle_index` | — | — | — | — | — | — | ⬜ à écrire |
| BH-013 | `vehicle_index` | — | — | — | — | — | — | ⬜ à écrire |
| BH-014 | `vehicle_index` | — | — | — | — | — | — | ⬜ à écrire |
| BH-015 | `vehicle_index` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-016 | `vehicle_index` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-017 | `vehicle_index` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-018 | `vehicle_index` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-019 | `vehicle_index` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-020 | `vehicle_index` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-021 | `vehicle_index` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-022 | `vehicle_index` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-023 | `vehicle_index` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-024 | `vehicle_index` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-025 | `vehicle_index` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-026 | `vehicle_index` | — | — | — | — | — | — | ⬜ à écrire |
| BH-027 | `vehicle_index` | — | — | — | — | — | — | ⬜ à écrire |
| BH-028 | `vehicle_index` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-040 | `vehicle_show` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-041 | `vehicle_show` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-042 | `vehicle_show` | — | — | — | — | — | — | ⬜ à écrire |
| BH-043 | `vehicle_show` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-044 | `vehicle_show` | — | — | — | — | — | — | ⬜ à écrire |
| BH-045 | `vehicle_show` | — | — | — | — | — | — | ⬜ à écrire |
| BH-046 | `vehicle_show` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-047 | `vehicle_show` | — | — | — | — | — | — | ⬜ à écrire |
| BH-048 | `vehicle_show` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-049 | `vehicle_show` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-050 | `vehicle_show` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-051 | `vehicle_show` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-052 | `vehicle_show` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-053 | `vehicle_show` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-054 | `vehicle_show` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-055 | `vehicle_show` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-056 | `vehicle_show` | — | — | — | — | — | — | ⬜ à écrire |

Légende — Fiche BH : décrite dans `docs/migration/behaviors/` · Tests : méthodes `bhNNN_*` · Golden : ✔ présents / ✘ manquants / — aucun · Réf / App : dernier run sur `app-ref` / `app` (✔ ✘ ↷ —) · Diff : variante comparative requise si la fiche est sensible à la migration.

</details>
<!-- tracking:end -->

## 1. Ce qu'un test garantit

Un test de caractérisation affirme : **« pour cette entrée, sur ces données, l'application répond exactement
ainsi »**. Il est écrit et validé sur la référence (`app-ref`, step-00), puis rejoué inchangé à chaque marche (`app`).
Il ne juge pas : un bug figé est un comportement attendu, jusqu'à décision contraire consignée dans
`docs/migration/accepted-changes.md`.

Est **observable**, donc contractuel :

| Canal | Contractuel | Non contractuel (à ignorer ou normaliser) |
|---|---|---|
| Statut HTTP | toujours | — |
| En-têtes | `Location`, `Content-Type`, `X-Fleet-Version`, `Cache-Control`, `Allow`, `WWW-Authenticate`, présence et attributs de `Set-Cookie` | `Date`, `Server`, `X-Powered-By`, `Content-Length`, valeur de `PHPSESSID` |
| Corps HTML | contenu, ordre, libellés, liens, formats de nombres et de dates, échappement | indentation et espaces entre balises, jetons CSRF, pages d'erreur génériques du framework (voir § 2.4) |
| Corps JSON | octet par octet | — |
| Base de données | état exact après l'action (lecture seule = **inchangée**) | — |
| Journal PHP | signatures d'erreurs/notices produites (comparées à la référence, en suite `diff`) | horodatage, chemins absolus |

## 2. Gabarit d'un test

Chaque test (une méthode `bhNNN_…`) suit **exactement** ces étapes. Une étape non applicable est notée « N/A » dans
la fiche, jamais omise silencieusement.

### 2.1 Fiche

```markdown
### BH-NNN — <titre : comportement observé, pas l'implémentation>
- Route / interface : <nom de route> · <MÉTHODE> <chemin + requête exacte>
- Suite : http | edge | diff | db          Groupe : zone-<zone>
- Scénario de données : <scénario>         (restauré avant le test, empreinte vérifiée)
- Authentification : aucune | Basic <utilisateur> | Basic invalide
- Sensible à la migration : non | oui → <quoi, quelle étape> (ex. « comparaison lâche, PHP 8.0, step-05 »)
- Entrée : <paramètres, en-têtes, corps>
- Attendu (step-00) :
  - A1 statut : …
  - A2 en-têtes : …
  - A3 corps : golden `<nom>` + assertions ciblées : …
  - A4 base : inchangée | <différence exacte>
  - A5 journal PHP : aucune entrée | <signatures attendues>
- Golden : tests/golden/http/<zone>/<nom>.html
```

### 2.2 Déroulé (Arrange → Act → Assert)

1. **Arrange — données** : `Fixtures::restore('<scénario>', TARGET)` copie la base figée et vérifie son empreinte.
   Ne jamais dépendre de l'état laissé par un autre test.
2. **Arrange — contexte** : client HTTP **neuf** (pas de cookie, pas de redirection suivie automatiquement),
   authentification seulement si la fiche le dit. Horloge : celle du conteneur (`2026-01-15 10:00` au démarrage du
   processus) ; un test ne doit jamais dépendre des secondes.
3. **Arrange — référence** : noter la taille du journal PHP (`/var/log/php/error.log`) de la cible, pour A5.
4. **Act** : **une seule** requête, exactement celle de la fiche (méthode, chemin, chaîne de requête encodée telle
   quelle, en-têtes).
5. **Assert A1 — statut** : égalité stricte.
6. **Assert A2 — en-têtes contractuels** : égalité stricte des valeurs (insensible à la casse du nom).
   `Set-Cookie` : présence ou absence, attributs (`path=/`, `HttpOnly`), jamais la valeur.
7. **Assert A3 — corps** :
   - normaliser (`Normalizer::html()` : espaces entre balises, fins de ligne ; `Normalizer::json()` : rien) ;
   - comparer au **golden** (égalité stricte) ;
   - **et** faire 2 à 5 assertions ciblées sur ce que la fiche met en avant (nombre de lignes, ordre, libellé
     exact). Elles disent *pourquoi* le golden compte et localisent l'écart quand il casse.
8. **Assert A4 — base** : empreinte du dump canonique de la base = empreinte du scénario (route en lecture seule) ;
   pour une écriture, diff exact attendu (fiches des autres contrôleurs).
9. **Assert A5 — journal PHP** : nouvelles lignes depuis l'étape 3, réduites à leur signature (type + message sans
   chemin) ; égalité avec la liste de la fiche. Une notice PHP 7 qui devient warning PHP 8 est un **écart à
   trier**, pas un échec silencieux.
10. **Exécuter sur la référence** : le test doit être vert sur `app-ref` (`tests.sh --target ref`) avant d'être
    rendu. Rouge sur la référence = test ou fiche faux, jamais « à corriger dans l'application ».

### 2.3 Variante différentielle (suite `diff`)

Pour chaque fiche marquée « sensible à la migration », un test `diff` rejoue la **même requête** sur `app-ref` et
`app` (même scénario restauré des deux côtés) et compare A1, A2, A3 normalisés et A5. Il ne connaît pas la valeur
attendue : il garantit seulement « identique à la référence », ce qui couvre aussi ce qu'aucune fiche n'a prévu.

### 2.4 Règles de normalisation et décisions

| Élément | Règle |
|---|---|
| Espaces / indentation HTML | supprimés entre balises, conservés dans le texte |
| Pages d'erreur 404 / 405 génériques (« Oops! An Error Occurred » de Twig 2.8) | **corps non contractuel** par défaut (le gabarit d'erreur du framework change à chaque version) : on asserte statut + en-têtes. À confirmer par l'utilisateur et consigner dans `accepted-changes.md` |
| Corps des redirections 302 (HTML généré par `RedirectResponse`) | non contractuel ; `Location` l'est |
| `X-Powered-By` | non contractuel (dépend de la version de PHP) — mais signalé à `/vuln-audit` |
| Ordre des attributs HTML | conservé (une différence est un vrai écart) |

### 2.5 Définition de « terminé » pour un test

- [ ] une fiche `BH-NNN` existe et le test porte son numéro (méthode `bhNNN_…`, `#[Group('BH-NNN')]`) ;
- [ ] scénario restauré dans le test, empreinte vérifiée ;
- [ ] A1 à A5 présents (ou « N/A » justifié dans la fiche) ;
- [ ] golden enregistré sur step-00 (`/golden-master --record`) ;
- [ ] vert sur `app-ref` ; aucun appel à une classe de l'application ;
- [ ] si « sensible à la migration » : variante `diff` et, le cas échéant, test `edge` dédié ;
- [ ] déterministe : 3 exécutions consécutives identiques, ordre des tests indifférent.

## 3. `VehicleController` — tests attendus

Rappels communs à toutes les réponses de ce contrôleur (observés) :

- `X-Fleet-Version: 1.4.2`, `Cache-Control: no-cache`, `Content-Type: text/html; charset=UTF-8` — **y compris** sur
  les 302, 404 et 405 ;
- pages HTML : `Set-Cookie: PHPSESSID=…; path=/; HttpOnly` (le layout lit les messages flash) ;
- titre `<title>Flotte Delvaux & Fils</title>`, `<nav>` avec le lien « Véhicules » ; lien « Ajouter un véhicule »
  **seulement** si authentifié `ROLE_ADMIN` ;
- lecture seule : **A4 = base inchangée** pour tous les tests de ce contrôleur.

### 3.1 `homepage` — `/` (toutes méthodes)

| Fiche | Requête | Scénario | A1 | A2 / A3 — points clés | Sensible |
|---|---|---|---|---|---|
| BH-001 | `GET /` | demo | 302 | `Location: /vehicules` (**relatif**) ; corps non contractuel | non |
| BH-002 | `POST /`, `PUT /`, `DELETE /` | demo | 302 | même `Location` : aucune restriction de méthode | oui : `@Method` absent, routage par attributs (step-04/06) |
| BH-003 | `GET /?status=1` | demo | 302 | `Location: /vehicules` : la chaîne de requête est **perdue** | non |
| BH-004 | `HEAD /` | demo | 302 | corps vide, mêmes en-têtes | non |

### 3.2 `vehicle_index` — `GET /vehicules`

| Fiche | Requête | Scénario | A1 | A3 — points clés | Sensible |
|---|---|---|---|---|---|
| BH-010 | `GET /vehicules` | demo | 200 | 5 lignes triées par plaque `1-ABC-123, 1-BCD-234, 1-CDE-345, 1-DEF-456, 2-EFG-567` ; colonnes Plaque (lien `/vehicules/{id}`), « Marque Modèle », `84.210 km`, libellé de statut ; option « Tous » sans `selected` | non |
| BH-011 | `GET /vehicules` | empty | 200 | une ligne `<td colspan="4">Aucun véhicule.</td>` | oui : `{% for … else %}` (Twig) |
| BH-012 | `GET /vehicules?status=0` / `=1` / `=2` | demo | 200 | 3 / 1 / 1 lignes ; option correspondante `selected` | non |
| BH-013 | `GET /vehicules?status=` | demo | 200 | 5 lignes, aucune option `selected` | non |
| BH-014 | `GET /vehicules?status=3` | demo | 200 | « Aucun véhicule. », aucune option `selected` | non |
| BH-015 | `GET /vehicules?status=abc` | demo | 200 | **3 lignes** (statut 0) **et option « Disponible » `selected`** (comparaison lâche PHP et Twig) | **oui** : PHP 8.0 → 0 ligne, aucune sélection (step-05) — test `edge` + `diff` |
| BH-016 | `GET /vehicules?status=0abc` | demo | 200 | 3 lignes | **oui** : PHP 8.0 (chaîne à préfixe numérique) |
| BH-017 | `GET /vehicules?status=01` et `?status=%201` | demo | 200 | 1 ligne (statut 1) | oui : chaînes numériques avec zéro / espace |
| BH-018 | `GET /vehicules?status%5B%5D=1` | demo | 200 | **0 ligne** (tableau ≠ entier) | **oui** : `InputBag::get()` sur un tableau lève une exception en Symfony 6 (→ 400 attendu) |
| BH-019 | `GET /vehicules` | edge | 200 | ordre **binaire** : ` 2-BBB-004` (espace en tête) en premier, puis `0-AAA-000`, `1ABC12`…, plaques en minuscules après les majuscules ; affichage **normalisé** (`2-BBB-004`, `ABC123`…) | oui : tri SQLite + filtre `plate` |
| BH-020 | `GET /vehicules` | edge | 200 | formats : `0 km`, `2.147.483.647 km` | oui : `number_format` |
| BH-021 | `GET /vehicules` | edge | 200 | échappement : `Citroën &lt;b&gt;C3&lt;/b&gt;`, `&quot;Aircross&quot; &amp; &lt;script&gt;…`, Unicode et emoji intacts | oui : auto-échappement Twig 1 → 3 |
| BH-022 | `GET /vehicules` | anomalies | 200 | statut inconnu → cellule de statut **vide** ; plaque vide, km négatif `-500 km` ; A5 : notices `Undefined offset: 3`, `Undefined offset: -1` | **oui** : notice → warning (PHP 8), libellé |
| BH-023 | `GET /vehicules` (Basic `gestionnaire:flotte2016`) | demo | 200 | lien « Ajouter un véhicule » présent dans `<nav>` | oui : sécurité (step-06) |
| BH-024 | `GET /vehicules` (Basic `gestionnaire:mauvais`) | demo | **401** | `WWW-Authenticate: Basic realm="Secured Area"` : de mauvais identifiants sur une page **publique** sont refusés | **oui** : authenticators (step-06) |
| BH-025 | `GET /vehicules/` | demo | 404 | barre oblique finale non redirigée | oui : routage |
| BH-026 | `POST /vehicules` | demo | 405 | `Allow: GET, HEAD` | non |
| BH-027 | `HEAD /vehicules` | demo | 200 | corps vide, mêmes en-têtes que GET | non |
| BH-028 | `GET /vehicules` | volume | 200 | 500 lignes ; suite `diff` : temps de réponse `app` ≤ 2 × `app-ref` (repère step-00 : ~40 ms) | oui : performance |

### 3.3 `vehicle_show` — `GET /vehicules/{id}`

| Fiche | Requête | Scénario | A1 | A3 — points clés | Sensible |
|---|---|---|---|---|---|
| BH-040 | `GET /vehicules/1` | demo | 200 | `<h1>1-ABC-123 — Renault Kangoo</h1>`, `Kilométrage : 84.210 km · Statut : Disponible`, lien `/vehicules/1/reserver` « Réserver ce véhicule », à venir : `15/06/2030 08:00 → 15/06/2030 18:00 : Sophie Martin` + lien `/reservations/5/retour`, historique : `02/05/2016 : Jean Dupont, 210 km, 73,50 €` | oui : `{% for … if %}` (Twig 3, step-04) |
| BH-041 | `GET /vehicules/2` | demo | 200 | « Aucune réservation à venir. » ; historique de 2 lignes dans l'ordre des dates | oui : idem |
| BH-042 | `GET /vehicules/3` et `/4` | demo | 200 | statut « En entretien » / « Hors service », **pas** de lien « Réserver » | non |
| BH-043 | `GET /vehicules/<véhicule sans historique>` | edge (#1) | 200 | « Aucune réservation à venir. » et historique = `<ul>` **vide** (pas de message) | oui : Twig |
| BH-044 | `GET /vehicules/<véhicule en entretien avec réservation future>` | edge (#11) | 200 | réservation future affichée, pas de lien « Réserver » | non |
| BH-045 | `GET /vehicules/<véhicule en retard>` | edge (`1-OVD-001`) | 200 | la réservation **passée non rendue** figure dans « Réservations à venir », avant la future | non (bug figé) |
| BH-046 | `GET /vehicules/<même date de début>` | edge (`1-SAM-001`) | 200 | ordre des deux réservations de même début = ordre observé (dépend de la base) | oui : `@OrderBy`, ORM |
| BH-047 | `GET /vehicules/<40 réservations>` | edge (`1-LNG-040`) | 200 | toutes listées (aucune pagination), annulées absentes | non |
| BH-048 | `GET /vehicules/<arrondis>` | edge (`1-CST-001`) | 200 | historique : `1 km, 0,35 €` · `3 km, 1,05 €` · `7 km, 2,45 €` · `0 km, 0,00 €` · `999 km, 349,65 €` | oui : flottants, `number_format` |
| BH-049 | `GET /vehicules/<XSS>` | edge (`1-XSS-001`) | 200 | `<h1>` échappé (`&lt;script&gt;…&#039;x&#039;`), 11 conducteurs « difficiles » échappés (vide, 100 caractères, guillemets, script, emoji, arabe) | oui : échappement Twig |
| BH-050 | `GET /vehicules/<changements d'heure>` | edge (`1-DST-001`) | 200 | heures naïves affichées telles qu'en base (`29/03/2026 01:30 → 29/03/2026 03:30`) | oui : `DateTime`, fuseau |
| BH-051 | `GET /vehicules/<anomalies>` | anomalies (#1, #6, #8) | 200 | libellé de statut vide ; km retour < départ → `-100 km, -35,00 €` ; coût NULL → `0,00 €` ; coût `N/A` → `10 km,  €` (montant vide, warning) ; coût `12.345` → `12,35 €` ; statuts `CONFIRMEE`/vide absents des deux listes ; A5 : notice + warning `number_format()` | **oui** : `number_format(null)` déprécié (8.1), `number_format('N/A')` → TypeError (8.0) → 500 probable |
| BH-052 | `GET /vehicules/01` | demo | **200** | même page que `/vehicules/1` (contenu dupliqué) | oui : ParamConverter → MapEntity (step-06) |
| BH-053 | `GET /vehicules/999`, `/vehicules/0` | demo | 404 | en-têtes communs ; corps non contractuel (§ 2.4) | oui : page d'erreur |
| BH-054 | `GET /vehicules/abc`, `/vehicules/-1`, `/vehicules/99999999999999999999` | demo | 404 | prérequis `\d+` / dépassement d'entier : 404, jamais 500 | **oui** : très grand id (PHP 8 / DBAL 3) |
| BH-055 | `GET /vehicules/1/` | demo | 404 | barre oblique finale | oui : routage |
| BH-056 | `POST /vehicules/1` | demo | 405 | `Allow: GET, HEAD` | non |

### 3.4 Exemple complet : BH-015

```markdown
### BH-015 — Le filtre de statut non numérique affiche les véhicules disponibles
- Route / interface : vehicle_index · GET /vehicules?status=abc
- Suite : edge (+ diff)            Groupe : zone-vehicles-list
- Scénario de données : demo
- Authentification : aucune
- Sensible à la migration : oui → comparaison lâche `0 != 'abc'` (PHP) et `status == value` (Twig) ; PHP 8.0
  (step-05) donnera 0 ligne et aucune option sélectionnée
- Entrée : paramètre de requête status=abc
- Attendu (step-00) :
  - A1 statut : 200
  - A2 en-têtes : Content-Type text/html; charset=UTF-8 · X-Fleet-Version 1.4.2 · Cache-Control no-cache ·
    Set-Cookie PHPSESSID (path=/, HttpOnly)
  - A3 corps : golden `vehicles-list/status-abc` ; 3 lignes : 1-ABC-123, 1-BCD-234, 2-EFG-567 ;
    `<option value="0" selected>Disponible</option>` ; aucune autre option sélectionnée
  - A4 base : inchangée
  - A5 journal PHP : aucune entrée
- Golden : tests/golden/http/vehicles-list/status-abc.html
```

Déroulé correspondant (pseudo-code, pas une implémentation) :

```text
restore('demo', target)                     → empreinte OK
log0 = phpLogSize(target)
r = http(target).get('/vehicules?status=abc')   (client neuf, sans redirection)
assert r.status == 200
assert headers(r) ⊇ {Content-Type, X-Fleet-Version: 1.4.2, Cache-Control: no-cache}; cookie PHPSESSID présent
html = normalizeHtml(r.body)
assertMatchesGolden('vehicles-list/status-abc', html)
assert rows(html) == ['1-ABC-123', '1-BCD-234', '2-EFG-567']
assert selectedOptions(html) == ['0']
assert dbFingerprint(target) == fingerprint('demo')
assert phpLogSignatures(target, since=log0) == []
```

## 4. Points à trancher avant d'écrire les tests

| Question | Proposition |
|---|---|
| Corps des pages d'erreur 404/405 du framework : contractuels ? | **Non** (statut et en-têtes seulement) — à consigner dans `accepted-changes.md` |
| BH-015 à BH-018 (filtre lâche, tableau) : conserver le comportement PHP 7 en 8.4 ? | **Oui** pendant la migration (bug pour bug) ; correction éventuelle dans une étape séparée |
| BH-024 (401 sur page publique avec mauvais identifiants) : à préserver ? | Oui, c'est un comportement visible des clients HTTP |
| BH-052 (`/vehicules/01` → 200) : à préserver ? | Oui (liens externes possibles) |
| A5 (journal PHP) : contractuel ou seulement comparé en `diff` ? | Comparé en `diff` uniquement : une notice PHP 7 devient warning en PHP 8 sans changer la page |
| Seuil de performance BH-028 | ≤ 2 × la référence |

## Blocages

Fiches mises en attente (décision, environnement…), une par ligne : `- BH-NNN : ⏸ raison`.
