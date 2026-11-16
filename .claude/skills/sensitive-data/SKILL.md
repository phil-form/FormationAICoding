---
name: sensitive-data
description: Inventorie chaque donnée d'une analyse de feature (entités, DTO, fichiers, paramètres de requête), la classe selon la grille du projet et signale les données sensibles. Toute donnée sensible est chiffrée via le CryptoService (OpenBao Transit) - exigences SD-xx et critères testables AC-SD-xx ajoutés à l'analyse (§ 7.5), contrat technique et migration ajustés. Appelé systématiquement par analyse-feature.
when_to_use: Automatiquement à chaque /analyse-feature ; manuellement après une modification du modèle de données d'une analyse, ou pour reclasser une feature existante.
argument-hint: <docs/analyse/features/NNN-slug.md>
context: fork
agent: analyst
background: false
---

# Données sensibles : détection et chiffrement

Cible : `$ARGUMENTS` (une analyse dans `docs/analyse/features/`).

## Principe

**Toute donnée classée sensible (`S2`) est chiffrée, sans exception ni dérogation implicite.** La skill ne décide pas
de « ne pas chiffrer » : si le chiffrement d'une donnée `S2` pose un problème (recherche partielle, tri, volume),
elle le signale dans « Questions ouvertes » comme **bloquant** avec une alternative — l'utilisateur tranche.

## Démarche

1. Lis l'analyse cible, `CLAUDE.md` (section « Données sensibles »), [classification.md](classification.md),
   l'analyse client (dont « Réponses du client ») et `framework/crypto/SensitiveDataType.java` (types déjà
   disponibles). Ne lis jamais `src/test/`.
2. **Inventaire** — relève **toutes** les données manipulées par la feature : champs d'entités (§ 5.3), champs des
   DTO de requête et de réponse (§ 5.2), paramètres de route et de requête, fichiers téléversés, champs texte libre,
   données dérivées (exports, statistiques), données écrites dans les logs ou les erreurs.
3. **Classification** — attribue à chaque donnée une classe de [classification.md](classification.md) (`S0`, `S1`,
   `S2`, `SECRET`), avec la raison en quelques mots. En cas de doute entre deux classes : la plus haute.
   Un **texte libre** (description, remarque, motif) susceptible de contenir des données de santé ou d'infraction est
   `S1` + exigence de minimisation (aide à la saisie, mention « pas de donnée médicale »), sauf si sa finalité le
   rend `S2` (ex. description médicale) → `S2`.
4. **Traitement par classe** — pour chaque donnée `S2`, rédige les exigences (§ « Mesures » de la grille) :
   - type `SensitiveDataType` (existant ou **nouveau** : nom, clé de chiffrement `<nom>`, clé d'index `<nom>-index`
     si recherche / unicité) ;
   - colonne(s) : `<champ>_enc text not null` (chiffré `vault:vN:…`) et, si besoin, `<champ>_index text` (unique si
     la donnée est unique) ; **aucune colonne en clair** ;
   - contexte de chiffrement = identifiant **immuable** de la ligne (UUID connu avant `encrypt`) ;
   - recherche = index aveugle puis égalité ; **pas** de `LIKE`, de tri ni de recherche partielle sur la donnée ;
   - exposition : réponse **masquée** par défaut ; valeur en clair seulement via un endpoint ou un rôle explicitement
     autorisé, consultation **journalisée** (qui, quand, quelle ligne — jamais la valeur) ;
   - jamais dans les logs, les messages d'erreur, les `ProblemDetail`, les URL (pas de donnée `S2` en paramètre de
     route ou de requête : POST de recherche avec corps) ;
   - rotation : une implémentation de `framework.crypto.RewrapTarget` ;
   - infrastructure : clés dans `docker/openbao/configure.sh`, droits `encrypt` / `decrypt` / `rewrap` / `hmac` dans
     `docker/openbao/policies/api.hcl` (à signaler comme **prérequis** : hors du périmètre de l'agent code-writer si
     ces fichiers ne sont pas encore prévus — l'indiquer dans « Dépend de ») ;
   - fichier `S2` : chiffrement **par enveloppe** (clé de données Transit `datakey`, contenu chiffré AES-GCM, clé de
     données chiffrée stockée avec les métadonnées) — prérequis socle (plan Delvaux 1.3) à signaler s'il manque.
   Pour `SECRET` : hachage (mots de passe : Argon2id + pepper, ADR 0002 ; jetons : empreinte SHA-256), jamais de
   chiffrement réversible, jamais renvoyé. Pour `S1` : minimisation, contrôle d'accès, masquage dans les logs.
5. **Critères testables** — au moins un `AC-SD-xx` par exigence `SD-xx`, vérifiable depuis l'API **ou** en base
   (les tests d'intégration lisent la table) :
   - la colonne en base ne contient pas la valeur en clair (préfixe `vault:` / chiffré ≠ valeur) ;
   - deux lignes avec la même valeur ont des chiffrés différents (contexte par ligne) ;
   - la réponse JSON standard ne contient pas la valeur (masquée ou absente) ;
   - la recherche exacte retrouve la ligne ; une valeur en double → `409` (si unique) ;
   - une erreur de validation ne renvoie pas la valeur soumise.
6. **Écriture dans l'analyse** :
   - ajoute ou remplace la sous-section **« 7.5 Données sensibles »** (format ci-dessous) ;
   - répercute les exigences dans le **contrat technique** (§ 5.2 DTO masqués / `@ValidXxx`, § 5.3 colonnes
     `_enc` / `_index`, § 5.5 méthodes de service, § 5.7 erreurs) et la **migration** (§ 6) — sans contredire les
     identifiants existants ;
   - en-tête : ajoute la ligne `| Données sensibles | oui — <liste S2> |` ou `| Données sensibles | non |` ; s'il y a
     au moins une donnée `S2` ou `SECRET`, le champ `Sécurité` passe à `oui` ;
   - mets à jour la colonne « Données sensibles » de `docs/analyse/features/INDEX.md` si la feature y figure ;
   - ajoute une ligne d'historique.

```markdown
### 7.5 Données sensibles

Grille : `.claude/skills/sensitive-data/classification.md`.

| Donnée | Où (entité.champ / DTO / fichier) | Classe | Raison | Traitement |
|---|---|---|---|---|
| NISS | `User.niss` / `UserCreateRequest.niss` | S2 | identifiant national (RGPD art. 87) | chiffré `NISS` + index unique, masqué |

- **SD-01** — … *(classe S2 ; ADR 0001 D6)*
- **AC-SD-01** (SD-01) — *Given* … *When* … *Then* la colonne `niss_enc` commence par `vault:` et ne contient pas les 11 chiffres.

Prérequis d'infrastructure : … (ou « aucun : type `NISS` déjà disponible »).
```

## Réponse finale

Tableau court des données `S2` / `SECRET` détectées (donnée → traitement), nouveaux `SensitiveDataType` et
prérequis OpenBao à prévoir, questions bloquantes éventuelles. Si aucune donnée sensible : le dire en une ligne.
