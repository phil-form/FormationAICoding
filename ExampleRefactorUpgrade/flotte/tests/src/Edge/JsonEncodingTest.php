<?php

declare(strict_types=1);

namespace Flotte\Tests\Edge;

use Flotte\Tests\Support\ApiJson;
use Flotte\Tests\Support\CaseTestCase;
use Flotte\Tests\Support\Scenario;
use PHPUnit\Framework\Attributes\Group;
use PHPUnit\Framework\Attributes\Test;

/**
 * Piège : encodage JSON de l'API `GET /api/vehicules`.
 * - Symfony 2.8 `JsonResponse` : `JSON_HEX_TAG | JSON_HEX_APOS | JSON_HEX_AMP | JSON_HEX_QUOT`, sans
 *   `JSON_UNESCAPED_UNICODE` ni `JSON_UNESCAPED_SLASHES` : barre oblique échappée, non-ASCII en échappement
 *   unicode hexadécimal **minuscule** (paires de substitution pour les emoji), `< > & ' "` en échappement
 *   hexadécimal **majuscule** (003C, 003E, 0026, 0027, 0022).
 * - Types : entiers rendus en nombres JSON (PDO SQLite en PHP 8.1, DBAL 3 peuvent les rendre en chaînes).
 * - Statut inconnu → `null` (notice PHP 7, warning PHP 8).
 * Versions concernées : Symfony 3.4 → 6.4 (options par défaut de `JsonResponse`), PHP 8.1 (step-05), DBAL 3 (step-06).
 * Fiches : BH-204, BH-206, BH-207, BH-215 (tmp/tests-application.md § 4.2).
 */
#[Group('zone-api-vehicles')]
final class JsonEncodingTest extends CaseTestCase
{
    #[Test]
    #[Group('BH-204')]
    #[Scenario('demo')]
    public function bh204_integer_columns_are_json_numbers(): void
    {
        $r = $this->send('GET', ApiJson::PATH);
        $this->assertStatus(200, $r);

        self::assertSame(5, preg_match_all('/"id":\d+,/', $r->body), 'id : 5 nombres JSON');
        self::assertSame(5, preg_match_all('/"mileage":\d+,/', $r->body), 'mileage : 5 nombres JSON');
        self::assertSame(0, preg_match_all('/"(?:id|mileage)":"/', $r->body), 'aucun entier rendu en chaîne');
        $items = ApiJson::decode($r->body);
        ApiJson::assertStructure($items);
        self::assertSame([84210, 120450, 45800, 210300, 15200], array_column($items, 'mileage'));
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-206')]
    #[Scenario('edge')]
    public function bh206_hex_escapes_for_html_quotes_slashes_and_non_ascii(): void
    {
        $r = $this->send('GET', ApiJson::PATH);
        $this->assertStatus(200, $r);

        // Échappements exacts, octet par octet (casse hexadécimale comprise).
        foreach ([
            '{u003C}b{u003E}' => '< > : JSON_HEX_TAG, majuscules',
            '{u003C}\/b{u003E}' => 'barre oblique échappée dans une balise',
            '{u0022}Aircross{u0022}' => 'guillemet : JSON_HEX_QUOT',
            ' {u0026} ' => 'esperluette : JSON_HEX_AMP',
            'alert({u0027}x{u0027})' => 'apostrophe : JSON_HEX_APOS',
            'Citro{u00eb}n' => 'non ASCII : hexadécimal minuscule',
            '{u96fb}{u6c17}' => 'CJK',
            '{ud83d}{ude90}' => 'emoji : paire de substitution',
            '"29\/03\/2026 01:30"' => 'barre oblique échappée dans une date',
        ] as $fragment => $why) {
            self::assertStringContainsString(ApiJson::lit($fragment), $r->body, "Échappement attendu : $why");
        }
        self::assertStringNotContainsString('/', str_replace('\/', '', $r->body), 'aucune barre oblique non échappée');
        self::assertDoesNotMatchRegularExpression('/[\x80-\xFF]/', $r->body, 'corps 100 % ASCII');
        self::assertDoesNotMatchRegularExpression('/[<>&\']/', $r->body, 'aucun < > & apostrophe brut');
        self::assertStringNotContainsString('\\"', $r->body, 'guillemet jamais échappé par barre oblique inverse');
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-207')]
    #[Scenario('edge')]
    public function bh207_large_integers_without_exponent(): void
    {
        $r = $this->send('GET', ApiJson::PATH);
        $this->assertStatus(200, $r);

        self::assertStringContainsString('"mileage":2147483647,', $r->body);
        self::assertStringContainsString('"mileage":500000,', $r->body);
        self::assertStringContainsString('"mileage":0,', $r->body);
        self::assertDoesNotMatchRegularExpression('/"mileage":-?\d+(?:\.\d+)?[eE]/', $r->body, 'pas de notation exponentielle');
        self::assertDoesNotMatchRegularExpression('/"mileage":-?\d+\./', $r->body, 'pas de décimale');
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-215')]
    #[Scenario('anomalies')]
    public function bh215_unknown_status_is_json_null_never_empty_string(): void
    {
        $r = $this->send('GET', ApiJson::PATH);
        $this->assertStatus(200, $r);

        self::assertSame(2, substr_count($r->body, '"status":null,'), 'statuts 3 et -1 → null');
        self::assertStringNotContainsString('"status":"",', $r->body, 'jamais de chaîne vide');
        self::assertStringContainsString('"mileage":-500,', $r->body, 'entier négatif');
        self::assertStringContainsString('{"id":4,"plate":"","label":"Ford Ka",', $r->body, 'plaque vide en premier');
        self::assertStringContainsString('"label":" ",', $r->body, 'marque et modèle vides');
        $this->assertDbUnchanged();
    }
}
