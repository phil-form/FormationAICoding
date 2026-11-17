<?php

declare(strict_types=1);

namespace Flotte\Tests\Support;

use PHPUnit\Framework\Assert;

/**
 * Parcours du formulaire d'administration `admin_vehicle_new` (tmp/tests-application.md § 5.1) : authentification
 * HTTP Basic envoyée au GET du formulaire et au POST exactement comme le dit la fiche, jeton CSRF, POST urlencoded,
 * A4 = diff exact de la table vehicle avec `created_at` réduit à sa date (horloge du conteneur qui avance).
 *
 * À utiliser dans une classe qui utilise déjà FormJourney (FormTestCase, FormDiffTestCase). Aucune classe de
 * l'application n'est référencée.
 */
trait AdminVehicleForm
{
    protected const ADMIN_PATH = '/admin/vehicules/nouveau';
    protected const ADMIN_FORM = 'vehicle';
    protected const ADMIN_TOKEN = 'vehicle[_token]';
    protected const ADMIN_USER = 'gestionnaire';

    /** Mot de passe du compte de test : variable d'environnement FLEET_ADMIN_PASSWORD, sinon valeur de step-00. */
    protected static function adminPassword(): string
    {
        $password = getenv('FLEET_ADMIN_PASSWORD');

        return false === $password || '' === $password ? 'flotte2016' : $password;
    }

    /** Session neuve authentifiée en Basic (l'en-tête est envoyé à toutes les requêtes du client). */
    protected function adminSession(?string $target = null, string $user = self::ADMIN_USER): HttpClient
    {
        return $this->session($target)->withBasicAuth($user, self::adminPassword());
    }

    /**
     * Champs du POST : entrée par défaut du § 5.2 (`1-XYZ-789`, Renault, Master, 1500, statut 0), surchargée champ par
     * champ ; une valeur null retire le champ du POST (BH-324). Ordre : plate, brand, model, mileage, status, extras.
     *
     * @param array<string, string|null> $overrides clés courtes (`plate`…) ou noms complets (`vehicle[id]`)
     *
     * @return array<string, string>
     */
    protected function vehicleFields(array $overrides = []): array
    {
        $values = ['plate' => '1-XYZ-789', 'brand' => 'Renault', 'model' => 'Master', 'mileage' => '1500', 'status' => '0'];
        $fields = [];
        foreach ($values as $name => $default) {
            $value = \array_key_exists($name, $overrides) ? $overrides[$name] : $default;
            unset($overrides[$name]);
            if (null !== $value) {
                $fields["vehicle[$name]"] = $value;
            }
        }
        foreach ($overrides as $name => $value) {
            if (null !== $value) {
                $fields[$name] = $value;
            }
        }

        return $fields;
    }

    /**
     * GET du formulaire (avec l'authentification du client) puis POST des champs + jeton valide (sauf $token false,
     * ou chaîne = jeton imposé).
     *
     * @param array<string, string>|null $fields null : POST sans corps
     *
     * @return array{0: HttpClient, 1: HttpResponse}
     */
    protected function submitVehicle(?array $fields, bool|string $token = true, ?HttpClient $client = null): array
    {
        $client ??= $this->adminSession();
        [, $csrf] = $this->openForm($client, self::ADMIN_PATH);
        if (null !== $fields && false !== $token) {
            $fields[self::ADMIN_TOKEN] = true === $token ? $csrf : $token;
        }

        return [$client, $this->postForm($client, self::ADMIN_PATH, $fields)];
    }

    /** Ligne de diff attendue pour un véhicule créé (created_at réduit à sa date, cf. maskCreatedAt()). */
    protected static function addedVehicle(int $id, string $plate, string $brand = 'Renault', string $model = 'Master', string $mileage = '1500', string $status = '0'): string
    {
        $q = static fn (string $s): string => "'".str_replace("'", "''", $s)."'";

        return "+ vehicle: $id|{$q($plate)}|{$q($brand)}|{$q($model)}|$mileage|$status|'2026-01-15 hh:mm:ss'";
    }

    /**
     * `created_at` d'une ligne de véhicule (diff `+ vehicle:` ou dump `vehicle:`) : le format `Y-m-d H:i:s` est
     * vérifié, la date conservée, l'heure remplacée par `hh:mm:ss` (jamais assertée, § 5.1). Les autres lignes
     * sont rendues telles quelles.
     */
    protected static function maskCreatedAt(string $line): string
    {
        if (!preg_match('/^(?:\+ )?vehicle: /', $line)) {
            return $line;
        }
        Assert::assertMatchesRegularExpression("/\\|'\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}'$/", $line, 'A4 created_at au format Y-m-d H:i:s');

        return (string) preg_replace("/'(\\d{4}-\\d{2}-\\d{2}) \\d{2}:\\d{2}:\\d{2}'$/", "'\$1 hh:mm:ss'", $line);
    }

    /** A4 : diff exact de la base (created_at des véhicules ajoutés réduit à sa date). */
    protected function assertVehicleDiff(array $expected, ?string $target = null): void
    {
        Assert::assertSame($expected, array_map(self::maskCreatedAt(...), $this->dbDiff($target)), 'A4 diff exact de la base ('.($target ?? $this->target()).')');
    }

    // --- A3 ---------------------------------------------------------------------------------------------------

    /**
     * Erreurs du formulaire vehicle : 'global' → erreurs en tête du formulaire, puis id du champ (input ou select)
     * → messages. Seules les entrées non vides figurent.
     *
     * @return array<string, list<string>>
     */
    protected function vehicleErrors(HttpResponse $r): array
    {
        $out = [];
        $global = $this->globalErrors($r, self::ADMIN_FORM);
        if ([] !== $global) {
            $out['global'] = $global;
        }
        $r->crawler()->filter('#'.self::ADMIN_FORM.' > div')->each(static function ($div) use (&$out): void {
            $control = $div->filter('input, select');
            if (0 === $control->count()) {
                return;
            }
            $errors = $div->filter('ul > li')->each(static fn ($li): string => $li->text('', false));
            if ([] !== $errors) {
                $out[(string) $control->attr('id')] = $errors;
            }
        });

        return $out;
    }
}
