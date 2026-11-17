# Suite `diff` — normalisations

Chaque test `diff` restaure **le même scénario** sur `app` et `app-ref`, envoie la même requête (client neuf,
redirections non suivies) et compare. Implémentation : `src/Support/DiffTestCase.php`.

| Élément | Traitement | Justification |
|---|---|---|
| Statut | égalité stricte | contractuel |
| `Location`, `Content-Type`, `X-Fleet-Version`, `Cache-Control`, `Allow`, `WWW-Authenticate` | égalité stricte | contractuels (gabarit § 1) |
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
| stderr (A2) | présence identique + **message** du bloc d'exception (lignes après `[Classe]`, rognées) égal | classe d'exception, encadré et rappel de syntaxe : rendu du framework (§ 6.1) |
| stdout (A3) | **octet par octet** (aucune normalisation) | tableaux, séparateurs, `€`, fins de ligne contractuels (§ 6.5) |
| stdout de `list` / `help` | `Normalizer::cli()` (fins de ligne, blancs de fin) + défaut de `--month` remplacé par `<mois courant>` | rendu de l'aide ; défaut dépendant de l'horloge (§ 6.5) |
| Journal PHP (A5) | comme pour HTTP | idem |
| Base (A4) | `fleet:report` : inchangée des deux côtés ; `fleet:demo:load` : les deux bases = empreinte de `demo`, dumps égaux | lecture seule / rechargement |
| Durée (BH-423) | médiane de 3 exécutions par cible, app ≤ 2 × app-ref | seuil de la fiche, jamais de durée absolue |

Aucune normalisation ne masque un montant, un ordre, un statut ou un libellé. Écarts acceptés : aucun
(`accepted.yaml` non créé ; ajout uniquement en mode `--fix` sur décision consignée dans
`docs/migration/accepted-changes.md`).
