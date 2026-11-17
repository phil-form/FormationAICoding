# Gabarit de test de caractérisation — `AdminController`

> **Objet** : appliquer le gabarit commun (`tmp/tests-vehicle-controller.md` § 1 et § 2) et le parcours avec
> formulaire (`tmp/tests-reservation-controller.md` § 1 : GET du formulaire, jeton CSRF, POST, suivi de la
> redirection, diff exact de la base) à l'unique route d'administration : l'ajout d'un véhicule, **protégé par
> authentification HTTP Basic**. C'est la route la plus exposée à la migration : le système de sécurité de Symfony
> est entièrement remplacé entre 2.8 et 6.4 (`security.context`, `anonymous`, `http_basic`, encodeur `plaintext` →
> authenticators, password hasher).
>
> Valeurs observées sur l'application step-00 réelle (PHP 7.2.34 / Symfony 2.8.52), soumissions complètes avec et
> sans identifiants, scénarios `demo` et `empty`, comparaison de la base avant / après (2026-11-17).


<!-- tracking:start -->
## Suivi d'avancement (généré)

> Régénéré par `.claude/bin/test-tracking.py` (fin de chaque `.claude/bin/tests.sh`) — 2026-11-17 10:57. Ne pas éditer ce bloc : pour bloquer une fiche, ajouter « - BH-NNN : ⏸ raison » dans la section « Blocages ».

**0/38 terminés (0 %)** `░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░`

✅ 0 terminés · 🟡 0 incomplets · ✍️ 0 écrits · 🔴 0 rouges réf. · ⏸ 0 bloqués · ⬜ 38 à écrire

| Route | Terminés |
|---|---|
| `admin_vehicle_new` | 0/38 |

<details><summary>Détail par fiche</summary>

| Fiche | Route | Fiche BH | Tests | Golden | Réf | App | Diff | Statut |
|---|---|---|---|---|---|---|---|---|
| BH-300 | `admin_vehicle_new` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-301 | `admin_vehicle_new` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-302 | `admin_vehicle_new` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-303 | `admin_vehicle_new` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-304 | `admin_vehicle_new` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-305 | `admin_vehicle_new` | — | — | — | — | — | — | ⬜ à écrire |
| BH-306 | `admin_vehicle_new` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-307 | `admin_vehicle_new` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-308 | `admin_vehicle_new` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-309 | `admin_vehicle_new` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-310 | `admin_vehicle_new` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-311 | `admin_vehicle_new` | — | — | — | — | — | — | ⬜ à écrire |
| BH-312 | `admin_vehicle_new` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-313 | `admin_vehicle_new` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-314 | `admin_vehicle_new` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-315 | `admin_vehicle_new` | — | — | — | — | — | — | ⬜ à écrire |
| BH-316 | `admin_vehicle_new` | — | — | — | — | — | — | ⬜ à écrire |
| BH-317 | `admin_vehicle_new` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-318 | `admin_vehicle_new` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-319 | `admin_vehicle_new` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-320 | `admin_vehicle_new` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-321 | `admin_vehicle_new` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-322 | `admin_vehicle_new` | — | — | — | — | — | — | ⬜ à écrire |
| BH-323 | `admin_vehicle_new` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-324 | `admin_vehicle_new` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-325 | `admin_vehicle_new` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-326 | `admin_vehicle_new` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-327 | `admin_vehicle_new` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-328 | `admin_vehicle_new` | — | — | — | — | — | — | ⬜ à écrire |
| BH-329 | `admin_vehicle_new` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-330 | `admin_vehicle_new` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-331 | `admin_vehicle_new` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-332 | `admin_vehicle_new` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-333 | `admin_vehicle_new` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-334 | `admin_vehicle_new` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-335 | `admin_vehicle_new` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-336 | `admin_vehicle_new` | — | — | — | — | — | ✘ | ⬜ à écrire |
| BH-337 | `admin_vehicle_new` | — | — | — | — | — | ✘ | ⬜ à écrire |

Légende — Fiche BH : décrite dans `docs/migration/behaviors/` · Tests : méthodes `bhNNN_*` · Golden : ✔ présents / ✘ manquants / — aucun · Réf / App : dernier run sur `app-ref` / `app` (✔ ✘ ↷ —) · Diff : variante comparative requise si la fiche est sensible à la migration.

</details>
<!-- tracking:end -->

## 1. Ce qui change par rapport aux gabarits précédents

- **Authentification dans l'Arrange** : chaque fiche précise les identifiants envoyés **au GET** du formulaire et
  **au POST** (en-tête `Authorization: Basic …`, ou rien). La session ouverte par l'authentification fait partie du
  comportement observé (voir BH-336, BH-337).
- **A2 sécurité** : sur un refus, `401` + `WWW-Authenticate: Basic realm="Secured Area"` (et corps vide côté
  contrat) ; sur un succès, `Set-Cookie: PHPSESSID`.
- **A4** : diff exact de la table `vehicle` ; `created_at` est la seule valeur **non déterministe** (horloge du
  conteneur qui démarre au 15/01/2026 10:00 puis avance) : asserter le format `Y-m-d H:i:s` et la date
  `2026-01-15`, **jamais les minutes ni les secondes**. Identifiant attendu : `max(id) + 1` (6 sur `demo`, 1 sur
  `empty`).
- **A5** : les erreurs 500 ne sont journalisées nulle part en step-00 (pas de MonologBundle) : on les constate par la
  réponse et par la base (inchangée, transaction annulée).

Rappels observés :

- identifiants : utilisateur en mémoire `gestionnaire` / `flotte2016`, rôle `ROLE_ADMIN` ;
- formulaire `vehicle` (page `<h1>Nouveau véhicule</h1>`, **aucun bouton de soumission**) :

  | Champ | Rendu | Valeur par défaut |
  |---|---|---|
  | `vehicle[plate]` | `<input type="text" required>` | — |
  | `vehicle[brand]` | `<input type="text" required>` | — |
  | `vehicle[model]` | `<input type="text" required>` | — |
  | `vehicle[mileage]` | `<input type="number" required>` | `0` |
  | `vehicle[status]` | `<select>` : `0` Disponible (**sélectionné**), `1` En entretien, `2` Hors service | `0` |
  | `vehicle[_token]` | caché | jeton CSRF |

- succès → 302 vers `/vehicules/{id}`, flash « Véhicule 1-XYZ-789 ajouté par gestionnaire. » ; plaque **normalisée**
  avant enregistrement (`PlateFormatter`), validation `BelgianPlate` sur la saisie **normalisée** ;
- le layout affiche le lien « Ajouter un véhicule » dans `<nav>` une fois authentifié.

## 2. Fiches

### 2.1 `admin_vehicle_new` — `GET|POST /admin/vehicules/nouveau`

Entrée par défaut des POST (sauf mention) : `plate=1-XYZ-789`, `brand=Renault`, `model=Master`, `mileage=1500`,
`status=0`, jeton valide, Basic `gestionnaire:flotte2016` au GET et au POST.

| Fiche | Entrée | Scénario | A1 | A2 / A3 / A4 — points clés (observés) | Sensible |
|---|---|---|---|---|---|
| BH-300 | `GET` sans identifiants | demo | 401 | `WWW-Authenticate: Basic realm="Secured Area"` | **oui** : `http_basic` → authenticator (step-06) |
| BH-301 | `GET`, mot de passe faux ; utilisateur inconnu `admin` ; mot de passe vide | demo | 401 | même en-tête | **oui** : idem |
| BH-302 | `GET`, utilisateur **`Gestionnaire`** ou **`GESTIONNAIRE`** | demo | **200** | le nom d'utilisateur est **insensible à la casse** (fournisseur en mémoire) | **oui** : `InMemoryUserProvider` (step-06) |
| BH-303 | `GET`, identifiants valides | demo | 200 | formulaire complet (tableau du § 1), kilométrage `0` et statut « Disponible » pré-remplis, lien « Ajouter un véhicule » dans `<nav>`, **pas de bouton**, cookie `PHPSESSID` | oui : rendu des formulaires (step-01→04) |
| BH-304 | POST nominal | demo | 302 | `Location: /vehicules/6` ; flash « Véhicule 1-XYZ-789 ajouté par gestionnaire. » ; A4 : +1 ligne `vehicle` id 6 `('1-XYZ-789', 'Renault', 'Master', 1500, 0, '2026-01-15 hh:mm:ss')` | **oui** : `security.context`, `new VehicleType()`, `isValid()` sans `isSubmitted()` (step-01/02) |
| BH-305 | POST nominal sur base vide | empty | 302 | `Location: /vehicules/1`, id 1 | non |
| BH-306 | plaque `1-xyz-789` ; ` 1 xyz 789 ` ; `1.XYZ.789` ; `1xyz789` | demo | 302 | toujours enregistrée **`1-XYZ-789`** (normalisation : majuscules, sans espaces, points ni tirets, puis `N-LLL-NNN`) | oui : `PlateFormatter` (regex), PHP 8 |
| BH-307 | plaque `ABC-123` ; `1-AB-123` ; `X-XYZ-789` | demo | 200 | `La plaque "ABC-123" n'est pas une plaque belge valide (format 1-ABC-123).` (la **saisie brute** est citée) ; base inchangée | oui : messages de validation |
| BH-308 | plaque vide `""` ou `"   "` | demo | 200 | `This value should not be blank.` (anglais ; la saisie est rognée) | oui : `NotBlank`, `trim` |
| BH-309 | plaque **déjà existante** `1-ABC-123`, ou `1abc123` (même plaque une fois normalisée) | demo | **500** | page d'erreur générique ; **base inchangée** (index unique SQLite, aucune contrainte `UniqueEntity`) | **oui** : exception DBAL différente (step-06), 500 à préserver ou non (§ 3) |
| BH-310 | même POST valide envoyé **deux fois** | demo | 302 puis **500** | 1re : créé ; 2e : doublon → 500 ; **une seule** ligne | **oui** : idem |
| BH-311 | marque et modèle vides | demo | 200 | `This value should not be blank.` ×2 | non |
| BH-312 | marque et modèle avec espaces autour (`  Renault  `, ` Master `) | demo | 302 | enregistrés **rognés** | oui : option `trim` |
| BH-313 | marque de **51** caractères, modèle de **60** | demo | 302 | **acceptés et enregistrés en entier** (colonnes VARCHAR(50), SQLite ne contrôle pas, aucune contrainte `Length`) | **oui** : DBAL 3 / validation |
| BH-314 | marque `<b>Citroën</b>`, modèle `"C3" & 'Aircross' 🚐` | demo | 302 | enregistrés tels quels ; échappés à l'affichage (fiche, liste) et dans l'API (cf. BH-021, BH-049, BH-206) | oui : échappement |
| BH-315 | kilométrage `-1` | demo | 200 | `This value should be greater than or equal to 0.` | non |
| BH-316 | kilométrage `0` | demo | 302 | accepté (`mileage 0`) | non |
| BH-317 | kilométrage `abc` ; `99999999999999999999` | demo | 200 | `This value is not valid.` | oui : `IntegerType`, ICU |
| BH-318 | kilométrage `1500,7` | demo | 302 | **tronqué à 1500** | **oui** : `IntegerType` (arrondi, locale `fr`) |
| BH-319 | kilométrage `1e3` | demo | 302 | **enregistré 1000** | **oui** : idem |
| BH-320 | kilométrage `2147483648` (2^31) | demo | 302 | accepté et enregistré tel quel | oui : entiers 32/64 bits, DBAL |
| BH-321 | kilométrage **vide** | demo | **500** | base inchangée (kilométrage NULL → `NOT NULL` violé ; cause déduite, aucune trace de journal) | **oui** : erreur différente attendue (step-05/06) |
| BH-322 | statut `2` | demo | 302 | `status 2` (INTEGER) | non |
| BH-323 | statut `3` ; `abc` | demo | 200 | `This value is not valid.` (choix inexistant) | oui : `ChoiceType` (choices clé/libellé inversés en Symfony 3) |
| BH-324 | statut **absent** du POST | demo | **500** | base inchangée (statut NULL → `NOT NULL` violé ; cause déduite) | **oui** : `ChoiceType`, step-01 |
| BH-325 | sans jeton CSRF ; jeton faux | demo | 200 | `The CSRF token is invalid. Please try to resubmit the form.` ; base inchangée | oui : CSRF |
| BH-326 | champ en trop `vehicle[createdAt]` ; `vehicle[id]` | demo | 200 | `This form should not contain extra fields.` ; **aucune** affectation | oui : `allow_extra_fields` |
| BH-327 | POST **sans corps** (authentifié) | demo | 200 | formulaire réaffiché sans erreur, rien écrit | **oui** : Symfony 4 (`isValid()` non soumis) |
| BH-328 | `PUT` (authentifié) | demo | 405 | `Allow: GET, POST, HEAD` | non |
| BH-329 | POST sans identifiants **ni session** | demo | 401 | `WWW-Authenticate` ; base inchangée | **oui** : authenticators |
| BH-330 | utilisateur `GESTIONNAIRE` puis POST | demo | 302 | flash « ajouté par **gestionnaire** » : le nom affiché est celui du compte, pas celui saisi | oui : `getUsername()` → `getUserIdentifier()` (step-06) |
| BH-331 | véhicule créé puis **fiche véhicule** | demo | 302 → 200 | `<h1>1-XYZ-789 — Renault Master</h1>`, `1.500 km`, « Disponible », lien « Réserver » ; aucune réservation | oui : lien avec BH-040 |
| BH-332 | véhicule créé puis **liste** et **API** | demo | 200 | 6 lignes / 6 éléments ; `1-XYZ-789` placé à sa place dans le tri par plaque | oui : lien avec BH-010, BH-201 |
| BH-333 | GET authentifié puis **POST sans en-tête `Authorization`**, même session | demo | 302 | **accepté** : l'authentification Basic est mémorisée dans la session (pare-feu avec état) | **oui, très** : pare-feu `stateless` ou non (step-06) |
| BH-334 | GET authentifié puis `GET /vehicules` sans en-tête, même session | demo | 200 | lien « Ajouter un véhicule » **affiché** (session) | **oui** : idem |
| BH-335 | `created_at` du véhicule créé | demo | 302 | format `Y-m-d H:i:s`, date `2026-01-15` (horloge du conteneur) ; `PrePersist` ne l'écrase pas s'il était fourni (non atteignable par le formulaire, cf. BH-326) | oui : `DateTime`, ORM |
| BH-336 | `GET` refusé (401) | demo | 401 | **`Set-Cookie: PHPSESSID`** : même refusée, la requête ouvre une session (le pare-feu y mémorise l'URL demandée) | oui : session, `TargetPathTrait` (step-06) |
| BH-337 | succès | demo | 302 | `Set-Cookie: PHPSESSID` (session ouverte par l'authentification) | oui : session |

## 3. Exemple complet : BH-309

```markdown
### BH-309 — Ajouter un véhicule avec une plaque existante provoque une erreur 500
- Route / interface : admin_vehicle_new · POST /admin/vehicules/nouveau
- Suite : http (+ db, + diff)            Groupe : zone-admin-vehicle-new
- Scénario de données : demo
- Authentification : Basic gestionnaire:flotte2016 au GET et au POST
- Sensible à la migration : oui → aucune contrainte UniqueEntity : le doublon n'est détecté que par l'index unique
  SQLite au flush ; l'exception (DBALException 2.5 → UniqueConstraintViolationException DBAL 3) et la page
  d'erreur changent avec les versions.
- Entrée : plate=1-ABC-123 (puis variante 1abc123), brand=Renault, model=Master, mileage=1500, status=0, jeton valide
- Attendu (step-00) :
  - A1 statut : 500
  - A2 en-têtes : X-Fleet-Version 1.4.2 · Content-Type text/html; charset=UTF-8
  - A3 corps : non contractuel (page d'erreur générique du framework)
  - A4 base : strictement inchangée (5 véhicules, 5 réservations, empreinte du scénario demo)
  - A5 journal PHP : aucune entrée (exceptions non journalisées en step-00)
- Golden : aucun (statut + base suffisent)
```

## 4. Points à trancher avant d'écrire les tests

| Question | Proposition |
|---|---|
| BH-309 / BH-310 / BH-321 / BH-324 (500 sur doublon, kilométrage vide, statut absent) : figer le 500 ? | Figer **statut 500 + base inchangée** ; corriger plus tard (message de formulaire) dans une étape séparée, après décision |
| BH-302 / BH-330 (nom d'utilisateur insensible à la casse, nom du compte affiché) : préserver ? | Oui : c'est le comportement de connexion vu par l'utilisateur ; à vérifier à step-06 |
| BH-333 / BH-334 (authentification mémorisée en session) : préserver ? | Oui pendant la migration ; le passage à un pare-feu sans état serait un changement accepté (et une amélioration de sécurité à proposer) |
| Mot de passe `flotte2016` en clair (`plaintext`) dans la configuration | Hors contrat de test ; **vulnérabilité de configuration** à tracer par `/vuln-audit` (CFG), le mot de passe est passé par variable d'environnement dans les tests |
| BH-313 (marque / modèle > 50 caractères acceptés) : préserver ? | Oui (données réellement enregistrées en step-00) ; une contrainte `Length` future est un changement accepté |
| BH-318 / BH-319 (`1500,7` → 1500, `1e3` → 1000) | Préserver, comme BH-164 / BH-167 pour le retour de véhicule |

## Blocages

Fiches mises en attente (décision, environnement…), une par ligne : `- BH-NNN : ⏸ raison`.
