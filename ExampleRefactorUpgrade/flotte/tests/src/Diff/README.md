# Suite `diff` — normalisations

Chaque test `diff` restaure **le même scénario** sur `app` et `app-ref`, envoie la même requête (client neuf,
redirections non suivies) et compare. Implémentation : `src/Support/DiffTestCase.php`.

| Élément | Traitement | Justification |
|---|---|---|
| Statut | égalité stricte | contractuel |
| `Location`, `Content-Type`, `X-Fleet-Version`, `Allow`, `WWW-Authenticate` | égalité stricte | contractuels (gabarit § 1) |
| `Cache-Control`, `Expires` | égalité stricte après **ACC-001** : sur une cible ≥ step-01, une valeur conforme à l'ACC (`no-cache, private` sans session ; `max-age=0, must-revalidate, private` + `Expires` présent avec session) est ramenée à son équivalent step-00 (`no-cache`, `Expires` absent) ; toute autre valeur reste brute et fait échouer | changement accepté (`docs/migration/accepted-changes.md`) ; valeur d'`Expires` jamais comparée |
| `Set-Cookie` | nom + attributs (`path=/`, `httponly`), **sans la valeur** | identifiant de session aléatoire |
| `Date`, `Server`, `X-Powered-By`, `Content-Length`, `Vary` | ignorés | dépendent de l'heure, d'Apache, de la version de PHP |
| Corps HTML (2xx) | `Normalizer::html()` : fins de ligne, blancs de début/fin de ligne, lignes vides, jetons CSRF | indentation des gabarits Twig ; jeton aléatoire. Les blancs **dans** une ligne sont conservés |
| Corps des 3xx | ignoré | HTML généré par `RedirectResponse` (non contractuel, § 2.4) |
| Corps des pages d'erreur ≥ 400 | ignoré | gabarit d'erreur du framework (proposition § 4, **en attente de décision**) |
| Journal PHP (A5) | nouvelles lignes depuis la requête, réduites à « type + message » (sans horodatage, chemin, ligne) | horodatage et chemins varient ; un changement de type (notice → warning) **reste** un écart |
| Base (A4) | empreinte du dump canonique identique au scénario, des deux côtés, et dumps égaux | lecture seule |
| Durée (BH-028) | médiane de 5 mesures alternées, app ≤ 2 × app-ref | seuil de la fiche (proposition § 4) |

### Commandes console (`src/Support/ConsoleDiffTestCase.php`, BH-400 à BH-460)

| Élément | Traitement | Justification |
|---|---|---|
| Code de retour (A1) | égalité stricte | contractuel (scripts planifiés) |
| stderr (A2) | présence identique + **ligne de message** du bloc d'exception égale (`ConsoleRun::exceptionMessage` : lignes de l'encadré, sans titre `[Classe]` (2.8) ni localisation `In <fichier> line <n>:` (3.4)) | classe d'exception, localisation, encadré et rappel de syntaxe : rendu du framework (§ 6.1) |
| stdout (A3) | **octet par octet** (aucune normalisation) | tableaux, séparateurs, `€`, fins de ligne contractuels (§ 6.5) |
| stdout de `list` / `help` | les deux côtés < step-01 : `Normalizer::cli()` (fins de ligne, blancs de fin) + défaut de `--month` remplacé par `<mois courant>` ; dès qu'**ACC-002** s'applique à un côté : seule la partie fleet:* (`list` : nom → description ; `help` : synopsis, options propres, aide) | rendu de l'aide ; défaut dépendant de l'horloge (§ 6.5) ; en-tête de version, options globales et mise en page hors contrat (ACC-002) |
| Journal PHP (A5) | comme pour HTTP | idem |
| Base (A4) | `fleet:report` : inchangée des deux côtés ; `fleet:demo:load` : les deux bases = empreinte de `demo`, dumps égaux | lecture seule / rechargement |
| Durée (BH-423) | médiane de 3 exécutions par cible, app ≤ 2 × app-ref | seuil de la fiche, jamais de durée absolue |

Aucune normalisation ne masque un montant, un ordre, un statut ou un libellé.

## Écarts acceptés

Registre unique côté tests : `src/Support/AcceptedChanges.php` (miroir de `docs/migration/accepted-changes.md`),
indexé par `ACC-xxx` et par étape d'application (`since`). L'étape servie par chaque cible vient des variables du
conteneur `tests` : `APP_STEP` (app) et `REF_STEP` (app-ref, `step-00` si absente). Un ACC s'applique aux attentes
d'une cible **à partir de son étape** (`AcceptedChanges::stepAtLeast` / `applies`) : la référence step-00 garde
l'attente historique. Assertions http/edge, comparaisons diff et contrôles cli consultent ce registre ; aucune
condition d'étape n'est écrite dans les tests eux-mêmes.

| ACC | Depuis | Effet sur les tests |
|---|---|---|
| ACC-001 | step-01 | `Cache-Control` attendu : `no-cache` (< step-01) ; `no-cache, private` sans session, `max-age=0, must-revalidate, private` avec session (≥ step-01). `Expires` : absent (< step-01) ; présent avec session, absent sinon (≥ step-01). Session = la réponse pose `PHPSESSID`, ou la requête l'envoyait (sauf 405, rejet par le routage) |
| ACC-002 | step-01 | `list` / `help` : seules les lignes fleet:* sont contractuelles ; goldens complets `golden/cli/help/*.txt` vérifiés seulement sur une cible < step-01 |
