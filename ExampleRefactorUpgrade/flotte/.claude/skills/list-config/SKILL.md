---
name: list-config
description: Inventorie la configuration effective (paramètres, framework, sécurité, Doctrine, Twig, valeurs métier) sous forme de clés pointées, valeurs sensibles masquées, dans docs/migration/inventory/step-NN/config.json.
when_to_use: Appelée par /inventory (en premier) ; seule, avant une marche qui réorganise la configuration (step-02 Flex, step-06 sécurité).
argument-hint: "[--step step-NN]"
context: fork
agent: cartographer
---

# Inventaire de la configuration

Arguments : `$ARGUMENTS`. Référence : `.claude/reference/conventions.md` § 5.3 (clé `key` pointée).

## Sources

1. Fichiers : `app/config/*.yml` (≤ 3.4) ou `config/packages/**/*.yaml`, `config/services.yaml`, `.env*` (**noms
   de variables uniquement**, jamais les valeurs : `.env` est illisible).
2. Configuration résolue : `.claude/bin/console.sh debug:config <extension>` (≥ 2.8 ; ex. `framework`, `security`,
   `doctrine`, `twig`) et `.claude/bin/console.sh debug:container --parameters --format=json` (≥ 3.x ; en 2.8,
   `container:debug --parameters`).
3. Valeurs métier : paramètres `app.*` (`app.version`, `app.cost_per_km`).

## Structure

Tableau trié par `key` :

```json
[
  { "key": "parameters.app.cost_per_km", "value": 0.35, "source": "app/config/config.yml", "observable": true },
  { "key": "parameters.secret", "value": "<redacted>", "source": "parameters.yml", "observable": false },
  { "key": "security.firewalls.main.http_basic", "value": {}, "source": "security.yml", "observable": true },
  { "key": "security.access_control.0", "value": { "path": "^/admin", "roles": ["ROLE_ADMIN"] }, "source": "security.yml", "observable": true },
  { "key": "security.encoders.Symfony\\Component\\Security\\Core\\User\\User", "value": "plaintext", "source": "security.yml", "observable": false }
]
```

- `observable: true` pour ce qui change un comportement visible (locale, CSRF, firewalls, access_control,
  paramètres métier, `strict_variables`, session, format de dates). C'est ce que `/inventory --diff` surveille.
- Les clés de framework renommées par la migration (`encoders` → `password_hashers`, `templating` supprimé) sont
  attendues : les noter dans `renamed_to` lors de l'étape où elles changent si l'équivalence est certaine.
- Valeurs sensibles (`secret`, mots de passe, DSN avec identifiants) → `"<redacted>"`, avec mention dans le rapport
  (elles relèvent de `/vuln-audit`, configuration).

## Étapes

1. Collecte ; écris `docs/migration/inventory/<STEP>/config.json`.
2. Rapport : nombre de clés, clés observables, secrets en clair repérés (sans valeur).
