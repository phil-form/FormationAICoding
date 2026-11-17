<?php

declare(strict_types=1);

namespace Flotte\Tests\Support;

use PHPUnit\Framework\Assert;

/**
 * Outils de l'API JSON `GET /api/vehicules` (tmp/tests-application.md § 4.1) :
 * - A3 octet par octet : golden `.json` **sans aucune normalisation** + empreinte SHA-256 (`.json.sha256`) ;
 * - assertions structurelles après json_decode : clés exactes et ordonnées, types, ordre des plaques.
 * Aucune classe de l'application n'est référencée.
 */
final class ApiJson
{
    public const PATH = '/api/vehicules';
    public const CONTENT_TYPE = 'application/json';
    public const KEYS = ['id', 'plate', 'label', 'mileage', 'status', 'nextReservation'];
    public const GOLDEN_DIR = 'http/api-vehicles/';

    /**
     * Fragment JSON littéral, avec `{uXXXX}` remplacé par la séquence d'échappement JSON « barre oblique inverse,
     * u, XXXX » (casse conservée). Les sources ne contiennent ainsi jamais ces séquences telles quelles : l'outil
     * d'écriture des fichiers les décode silencieusement (ce qui avait cassé la syntaxe PHP).
     * Ex. lit('Citro{u00eb}n') → les 12 octets ASCII que l'API envoie pour « Citroën ».
     */
    public static function lit(string $fragment): string
    {
        return (string) preg_replace('/\{u([0-9A-Fa-f]{4})\}/', '\\\\u$1', $fragment);
    }

    /** Corps comparé octet par octet au golden, puis empreinte SHA-256 comparée à son golden. */
    public static function assertMatchesGolden(string $name, string $body, string $message = ''): void
    {
        Golden::assertMatchesGolden(self::GOLDEN_DIR.$name.'.json', $body, $message);
        Golden::assertMatchesGolden(self::GOLDEN_DIR.$name.'.json.sha256', hash('sha256', $body)."\n", $message.' (empreinte)');
    }

    /** @return list<array<string, mixed>> */
    public static function decode(string $body): array
    {
        $data = json_decode($body, true, 512, JSON_THROW_ON_ERROR);
        Assert::assertIsArray($data, 'A3 : la racine doit être un tableau JSON');
        Assert::assertTrue(array_is_list($data), 'A3 : la racine doit être une liste');

        return $data;
    }

    /**
     * Clés exactes dans l'ordre et types JSON de chaque élément : id et mileage entiers ; plate, label et
     * nextReservation chaînes ; status chaîne ou null.
     *
     * @param list<array<string, mixed>> $items
     */
    public static function assertStructure(array $items): void
    {
        foreach ($items as $i => $item) {
            Assert::assertSame(self::KEYS, array_keys($item), "A3 clés de l'élément $i (ordre compris)");
            Assert::assertIsInt($item['id'], "A3 type de id (élément $i)");
            Assert::assertIsString($item['plate'], "A3 type de plate (élément $i)");
            Assert::assertIsString($item['label'], "A3 type de label (élément $i)");
            Assert::assertIsInt($item['mileage'], "A3 type de mileage (élément $i)");
            Assert::assertTrue(null === $item['status'] || \is_string($item['status']), "A3 type de status (élément $i)");
            Assert::assertIsString($item['nextReservation'], "A3 type de nextReservation (élément $i)");
        }
    }

    /**
     * @param list<array<string, mixed>> $items
     *
     * @return list<string>
     */
    public static function plates(array $items): array
    {
        return array_map(static fn (array $v): string => $v['plate'], $items);
    }

    /**
     * Élément par plaque brute.
     *
     * @param list<array<string, mixed>> $items
     *
     * @return array<string, mixed>
     */
    public static function byPlate(array $items, string $plate): array
    {
        foreach ($items as $item) {
            if ($item['plate'] === $plate) {
                return $item;
            }
        }
        Assert::fail("A3 : plaque « $plate » absente de la réponse");
    }

    /** Le corps ne contient aucun blanc hors des chaînes JSON (encodage compact). */
    public static function whitespaceOutsideStrings(string $body): string
    {
        $outside = preg_replace('/"(?:[^"\\\\]|\\\\.)*"/s', '""', $body) ?? $body;

        return (string) preg_replace('/[^\s]/', '', $outside);
    }
}
