# Grille de classification des données

Référence pour `/sensitive-data`. En cas de doute : la classe **la plus haute**. Base légale de référence : RGPD
(art. 4, 9, 10, 87) et loi belge du 30 juillet 2018.

| Classe | Définition | Exemples | Traitement obligatoire |
|---|---|---|---|
| `S0` — publique / interne | Donnée sans lien avec une personne, ou référentiel | nom d'un dépôt, type de véhicule, immatriculation d'un véhicule de société, kilométrage, coûts agrégés | validation des entrées |
| `S1` — personnelle | Identifie une personne ou s'y rattache, sans risque élevé en cas de fuite | nom, prénom, e-mail et téléphone **professionnels**, rôle, catégorie de permis, réservations, pleins d'un conducteur | contrôle d'accès (RM9 / BOLA), minimisation (ne renvoyer que le nécessaire), jamais dans les logs au-delà de l'identifiant technique |
| `S2` — sensible | Fuite = préjudice grave pour la personne (usurpation, discrimination, fraude) | voir liste ci-dessous | **chiffrement `CryptoService`** + mesures ci-dessous |
| `SECRET` | Sert à s'authentifier ou à signer | mot de passe, jeton de rafraîchissement, code de réinitialisation, clé d'API | **hachage** (jamais chiffrement réversible), jamais renvoyé ni journalisé |

## Données `S2` (liste non exhaustive)

- **Identifiants nationaux** : NISS / numéro de registre national, numéro BIS, numéro de carte d'identité, de
  passeport, de titre de séjour, **numéro** de permis de conduire (≠ catégorie, qui est `S1`).
- **Données financières personnelles** : IBAN / numéro de compte, numéro de carte de paiement (à éviter : jamais
  stocké, déléguer au prestataire), salaire.
- **Catégories particulières (RGPD art. 9)** : santé (aptitude médicale, handicap, arrêt maladie), origine, opinions,
  religion, appartenance syndicale, biométrie, vie sexuelle.
- **Infractions et condamnations (RGPD art. 10)** : amendes, retraits de permis, procès-verbaux nominatifs.
- **Coordonnées privées** : adresse du domicile, téléphone et e-mail **personnels**, date de naissance complète.
- **Géolocalisation précise** d'une personne (traces GPS, position à un instant).
- **Fichiers** contenant l'une de ces données : scan de pièce d'identité ou de permis, certificat médical, photo
  permettant d'identifier une personne (visage) **lorsque c'est leur finalité**.

## Mesures pour `S2`

1. Chiffrement Transit AES-256-GCM96 **dérivé**, contexte = identifiant immuable de la ligne (`CryptoService.encrypt`).
2. Index aveugle HMAC (`CryptoService.blindIndex`) seulement si recherche exacte ou unicité nécessaire.
3. Aucune colonne en clair, aucune copie en clair (cache, export, journal d'audit, seed affiché).
4. Réponses masquées par défaut ; accès en clair restreint à un rôle / endpoint explicite et journalisé.
5. Jamais dans les logs, erreurs, `ProblemDetail`, URL ; `toString()` masqué (type valeur dédié comme `Niss`).
6. Rotation : `RewrapTarget` ; clés dans `configure.sh`, droits dans la politique `api`.
7. Fichiers : chiffrement par enveloppe (Transit `datakey`), nom non devinable, téléchargement contrôlé.

## Cas limites

- **Texte libre** (motif, remarque, description de sinistre) : `S1` + minimisation, sauf finalité `S2`.
- **Photo de sinistre** : `S1` si elle montre un véhicule (EXIF supprimé) ; `S2` si sa finalité est d'identifier une
  personne ou si elle documente une blessure (santé).
- **Donnée dérivée** d'une donnée `S2` (date de naissance tirée du NISS, âge) : `S2`, sauf agrégat anonyme.
- **Identifiant technique** (UUID) : `S0` ; il peut figurer dans les logs.
