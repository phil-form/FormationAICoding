# Changements de comportement acceptés — migration flotte

## Rôle de ce fichier

Ce registre liste **les seuls écarts autorisés** entre l'application migrée et la référence `step-00`
(PHP 7.2 / Symfony 2.8.52, servie par `app-ref`). Les tests, les golden masters et les parcours de la suite `diff`
ne peuvent s'écarter de la référence **que** pour un changement consigné ici.

## Règle « bug pour bug »

- La référence fait foi, bugs compris : tout écart observé pendant une marche est par défaut une **régression** à
  corriger dans le code migré.
- Un écart n'est accepté que s'il est imposé (ou fortement induit) par la nouvelle version, que sa reproduction serait
  coûteuse ou risquée, **et** que l'utilisateur l'a explicitement décidé (gate `G-BEHAVIOR`, conventions § 7).
- Corriger un bug historique n'est pas une marche de migration : c'est une étape à part, elle aussi consignée ici.
- Ce fichier est écrit par l'auditeur, **uniquement** pour consigner une décision déjà prise par l'utilisateur ; une
  entrée n'est jamais modifiée après coup (une nouvelle décision = une nouvelle entrée qui référence l'ancienne).

## Format d'une entrée

```markdown
## ACC-NNN — titre court
- Étape : step-NN (versions avant → après)
- Fiches concernées : BH-xxx, …
- Avant (step-00) : comportement exact observé sur la référence
- Après (step-NN) : comportement exact observé sur l'application migrée
- Cause : composant et mécanisme du framework ou de PHP
- Raison de l'acceptation : …
- Impact client : …
- Décidé par : … · Date : AAAA-MM-JJ · Source : rapport de triage, gate
- Conséquence pour les tests : ce qui reste contractuel, ce qui change, ce qui ne l'est plus
```

---

## ACC-001 — `Cache-Control` / `Expires` des réponses HTTP (Symfony 3.4)

- Étape : **step-01** (Symfony 2.8.52 → 3.4.49, PHP 7.2)
- Fiches concernées : **BH-001 à BH-337** et **BH-460** (toutes les réponses HTTP de l'application).
- Avant (step-00) :
  - toutes les réponses : `Cache-Control: no-cache` ;
  - aucune réponse ne porte d'en-tête `Expires`.
- Après (step-01) :
  - réponses dont la requête n'a pas utilisé la session (ex. `GET /api/vehicules`) : `Cache-Control: no-cache, private` ;
  - réponses dont la requête a démarré ou utilisé la session (formulaires, pages avec messages flash, administration ;
    ex. `HEAD /vehicules/1/reserver`) : `Cache-Control: max-age=0, must-revalidate, private` **et** nouvel en-tête
    `Expires` égal à la date courante de la réponse.
  - Inchangé : `Set-Cookie: PHPSESSID…`, statut, corps et autres en-têtes contractuels.
- Cause : composant HttpFoundation, `ResponseHeaderBag` 3.x — le calcul de la valeur de `Cache-Control` ajoute
  systématiquement `private` en l'absence de `public`/`private` (valeur par défaut `no-cache, private`), sans API
  publique pour émettre `no-cache` seul ; composant HttpKernel, `AbstractSessionListener` (nouveau en 3.4) — toute
  réponse dont la requête a utilisé la session est rendue privée et non cacheable (`max-age=0`, `must-revalidate`,
  `Expires` à maintenant). L'option permettant de désactiver ce comportement n'existe qu'à partir de Symfony 4.1.
- Raison de l'acceptation : comportement imposé par `ResponseHeaderBag` 3.x et `AbstractSessionListener` ; sans effet
  pour un navigateur (revalidation systématique dans les deux cas) ; plus sûr pour les caches partagés, qui ne peuvent
  plus stocker les pages avec session ou messages flash ; préserver l'ancienne valeur exigerait de contourner deux
  mécanismes internes du framework sur chaque réponse, à revalider à chaque marche jusqu'à Symfony 6.4.
- Impact client : aucun impact fonctionnel pour les utilisateurs (navigateurs) ; seul un intermédiaire qui
  s'appuierait sur la valeur exacte de l'en-tête verrait une différence (aucun connu). Effet de bord favorable : les
  proxys et CDN ne conservent plus ces pages.
- Décidé par : utilisateur (formateur) · Date : 2026-11-17 · Source : `.claude/state/migration/triage/step-01.md`
  § 3 décision 1, gate `G-BEHAVIOR` (réponse « Accepter »).
- Conséquence pour les tests :
  - les assertions d'en-têtes (A2) et les golden masters attendent désormais les nouvelles valeurs de
    `Cache-Control` : `no-cache, private` sans session, `max-age=0, must-revalidate, private` avec session ;
  - `Expires` est attendu **présent** sur les réponses avec session et **absent** sinon ; sa valeur est une date non
    contractuelle (seule la présence est vérifiée, la date est normalisée) ;
  - la suite `diff` (app ↔ app-ref) déclare cet écart comme accepté pour `Cache-Control` et `Expires` ;
  - les autres en-têtes contractuels (`Allow`, `Content-Type`, `Location`, `Set-Cookie`…) restent en égalité stricte.

## ACC-002 — sortie des commandes console `list` / `help` (Symfony 3.4)

- Étape : **step-01** (Symfony 2.8.52 → 3.4.49, PHP 7.2)
- Fiches concernées : **BH-425**, **BH-459**.
- Avant (step-00) :
  - en-tête de `list` : `Symfony version 2.8.52 - app/dev/debug` ;
  - options globales affichées par `list` et `help <commande>` : dont `-s, --shell` et `--process-isolation`.
- Après (step-01) :
  - en-tête de `list` : `Symfony 3.4.49 (kernel: app, env: dev, debug: true)` ;
  - options globales `-s, --shell` et `--process-isolation` supprimées (alignement des colonnes modifié en
    conséquence) ;
  - inchangé : noms, descriptions (y compris « Vide la base et charge les donnees de demonstration » sans accents),
    options et valeurs par défaut des commandes `fleet:*`.
- Cause : composant Console / FrameworkBundle — format de l'en-tête de version de l'application console revu en 3.x,
  et shell interactif de la console (option `--shell`, `--process-isolation`) supprimé en Symfony 3.0.
- Raison de l'acceptation : reproduire l'ancien en-tête reviendrait à afficher une version du framework fausse, à
  refaire à chaque marche ; réintroduire les options exigerait de réimplémenter un composant supprimé ou d'afficher
  des options inopérantes. Partie de la sortie produite par le framework, hors comportement métier.
- Impact client : `app/console -s` (shell interactif) n'est plus disponible ; aucun usage connu (fiches, README).
  Aucun impact sur les commandes métier `fleet:*`.
- Décidé par : utilisateur (formateur) · Date : 2026-11-17 · Source : `.claude/state/migration/triage/step-01.md`
  § 3 décision 2, gate `G-BEHAVIOR` (réponse « Accepter »).
- Conséquence pour les tests :
  - seules les lignes propres aux commandes `fleet:*` restent contractuelles : nom, description, options et leurs
    valeurs par défaut (ainsi que le code de sortie) ;
  - la partie produite par le framework (en-tête de version, options globales, mise en page et alignement) n'est plus
    vérifiée, ni dans les assertions, ni dans les golden masters, ni dans la suite `diff`.
