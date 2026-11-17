---
name: list-forms
description: Inventorie les formulaires (classes *Type et createFormBuilder dans les contrôleurs) - champs, types, options, contraintes, formats de date, protection CSRF - dans docs/migration/inventory/step-NN/forms.json.
when_to_use: Appelée par /inventory ; seule, avant d'écrire les tests de formulaires ou avant une marche qui touche au composant Form.
argument-hint: "[--step step-NN]"
context: fork
agent: cartographer
---

# Inventaire des formulaires

Arguments : `$ARGUMENTS`. Référence : `.claude/reference/conventions.md` § 5.3 (clé `block_prefix` ; `class` est un simple champ, il change en step-02).

## Sources

1. Classes `src/**/Form/*Type.php` : `buildForm()`, `configureOptions()` / `setDefaultOptions()`, `getName()` /
   `getBlockPrefix()` (le **nom du formulaire** détermine les noms de champs HTML : `reservation[startAt]` — c'est
   un élément observable).
2. Formulaires construits dans les contrôleurs (`createFormBuilder()`) : clé
   `inline:<Contrôleur>::<action>` (ex. `inline:ReservationController::returnAction`).
3. Contraintes : annotations/attributs `@Assert\…` de l'entité liée (`data_class`), contraintes personnalisées
   (`BelgianPlate`), contraintes déclarées dans le formulaire.
4. `.claude/bin/console.sh debug:form <type>` si disponible (≥ 3.3), pour confirmer options et extensions.

## Structure

```json
{
  "class": "AppBundle\\Form\\ReservationType",
  "block_prefix": "reservation",
  "data_class": "AppBundle\\Entity\\Reservation",
  "csrf": true,
  "fields": [
    { "name": "startAt", "type": "datetime", "options": { "widget": "single_text", "format": "dd/MM/yyyy HH:mm" },
      "html_name": "reservation[startAt]", "constraints": ["NotBlank"] }
  ],
  "used_by": ["reservation_new"]
}
```

- `type` : nom court normalisé (`text`, `integer`, `datetime`, `choice`…), quel que soit le style d'origine
  (`'integer'` en 2.8, `IntegerType::class` ensuite) ; garder l'original dans `type_raw`.
- Formats de date et de nombre recopiés tels quels : ce sont des pièges de migration (plan § 0).
- Tri par `block_prefix`, champs dans l'ordre de déclaration (l'ordre est visible dans le HTML).

## Étapes

1. Collecte ; écris `docs/migration/inventory/<STEP>/forms.json`.
2. Rapport : formulaires, champs, formats sensibles, formulaires sans CSRF.
