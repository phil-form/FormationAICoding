<?php

declare(strict_types=1);

namespace Flotte\Tests\Http;

use Flotte\Tests\Support\FormTestCase;
use Flotte\Tests\Support\HttpClient;
use Flotte\Tests\Support\HttpResponse;
use Flotte\Tests\Support\Scenario;
use PHPUnit\Framework\Attributes\Group;
use PHPUnit\Framework\Attributes\Test;

/**
 * ReservationController, route `reservation_new` — `GET|POST /vehicules/{id}/reserver`.
 * Fiches : tmp/tests-application.md § 3.2 (parcours § 3.1 : GET du formulaire → jeton → POST → GET de Location).
 * Scénario booking (date de référence 15/01/2026 10:00). A4 = diff exact de la base (types SQLite par quote()).
 * Pages 404/405 : corps non contractuel (proposition § 2.5, en attente de décision). A5 comparé en suite diff.
 */
#[Group('zone-reservation-new')]
#[Scenario('booking')]
final class ReservationNewTest extends FormTestCase
{
    private const GOLDEN = 'http/reservation-new/';
    private const FORM = 'reservation';
    private const CREATED = 'Réservation enregistrée.';
    private const UNAVAILABLE = "Ce véhicule n'est pas disponible.";
    private const END_BEFORE_START = 'La date de fin doit être postérieure à la date de début.';
    private const OVERLAP = 'Ce véhicule est déjà réservé sur cette période.';
    private const DRIVER_REQUIRED = 'Le nom du conducteur est obligatoire.';
    private const NOT_VALID = 'This value is not valid.';
    private const NOT_BLANK = 'This value should not be blank.';
    private const CSRF_INVALID = 'The CSRF token is invalid. Please try to resubmit the form.';
    private const EXTRA_FIELDS = 'This form should not contain extra fields.';

    /** Kilométrage des véhicules du scénario booking (start_km attendu à la création). */
    private const KM = [1 => 10000, 2 => 20000, 3 => 30000, 4 => 40000, 5 => 50000, 6 => 60000, 7 => 70000, 8 => 0];

    /** @return array<string, string> */
    private function fields(string $driver, string $start, string $end): array
    {
        return ['reservation[driverName]' => $driver, 'reservation[startAt]' => $start, 'reservation[endAt]' => $end];
    }

    /**
     * Parcours complet : GET du formulaire, POST des champs + jeton valide (sauf $token = false).
     *
     * @param array<string, string> $fields
     *
     * @return array{0: HttpClient, 1: HttpResponse}
     */
    private function submit(int $vehicle, array $fields, bool $token = true, ?HttpClient $client = null): array
    {
        $client ??= $this->session();
        $path = "/vehicules/$vehicle/reserver";
        [, $csrf] = $this->openForm($client, $path);
        if ($token) {
            $fields['reservation[_token]'] = $csrf;
        }

        return [$client, $this->postForm($client, $path, $fields)];
    }

    /** Ligne ajoutée attendue (format DbDiff). */
    private function added(int $id, int $vehicle, string $driver, string $start, string $end): string
    {
        $km = self::KM[$vehicle];

        return "+ reservation: $id|$vehicle|'".str_replace("'", "''", $driver)."'|'$start'|'$end'|'confirmee'|$km|NULL|NULL";
    }

    /** 302 vers la fiche véhicule, puis flash « Réservation enregistrée. » (une seule fois). */
    private function assertCreated(HttpClient $client, HttpResponse $r, int $vehicle): HttpResponse
    {
        $this->assertStatus(302, $r);
        $this->assertCommonHeaders($r);
        self::assertSame("/vehicules/$vehicle", $r->header('Location'), 'A2 Location relatif');
        $page = $this->follow($client, $r);
        $this->assertStatus(200, $page);
        self::assertSame([self::CREATED], $this->flashes($page), 'A3 flash après redirection');

        return $page;
    }

    /** 200 : formulaire réaffiché. */
    private function assertRedisplayed(HttpResponse $r): void
    {
        $this->assertStatus(200, $r);
        $this->assertCommonHeaders($r);
        self::assertSame(1, $r->crawler()->filter('form[name="reservation"]')->count(), 'formulaire réaffiché');
    }

    private function assertRefusedWithGlobalError(HttpResponse $r, string $message): void
    {
        $this->assertRedisplayed($r);
        self::assertSame([$message], $this->globalErrors($r, self::FORM), 'A3 erreur globale en tête du formulaire');
        self::assertSame([], $this->fieldErrors($r, self::FORM), 'A3 aucune erreur de champ');
    }

    // --- BH-100 à BH-138 ------------------------------------------------------------------------------------

    #[Test]
    #[Group('BH-100')]
    public function bh100_booking_form_has_three_required_fields_and_no_button(): void
    {
        $r = $this->send('GET', '/vehicules/1/reserver');

        $this->assertStatus(200, $r);
        $this->assertCommonHeaders($r);
        $this->assertSessionCookie($r);
        $this->assertGolden(self::GOLDEN.'bh100.html', $r);
        $c = $r->crawler();
        self::assertSame('Réserver 1-BOK-001', $c->filter('h1')->text());
        self::assertSame('Format des dates : JJ/MM/AAAA HH:MM', $c->filter('p')->first()->text());
        self::assertSame(
            ['reservation[driverName]', 'reservation[startAt]', 'reservation[endAt]'],
            $c->filter('form[name="reservation"] input[required]')->each(static fn ($i): string => (string) $i->attr('name')),
        );
        self::assertSame(1, $c->filter('input[type="hidden"][name="reservation[_token]"]')->count(), 'jeton caché');
        self::assertSame(0, $c->filter('button, input[type="submit"]')->count(), 'aucun bouton de soumission');
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-101')]
    public function bh101_valid_booking_is_created_and_redirects_with_flash(): void
    {
        [$client, $r] = $this->submit(1, $this->fields('Jean Test', '20/02/2026 08:00', '20/02/2026 18:00'));

        $page = $this->assertCreated($client, $r, 1);
        $this->assertGolden(self::GOLDEN.'bh101-after-redirect.html', $page);
        self::assertSame([], $this->flashes($client->get('/vehicules/1')), 'le flash ne s\'affiche qu\'une fois');
        $this->assertDbDiff([$this->added(11, 1, 'Jean Test', '2026-02-20 08:00:00', '2026-02-20 18:00:00')]);
    }

    #[Test]
    #[Group('BH-102')]
    public function bh102_unavailable_vehicle_is_refused(): void
    {
        foreach ([3 => 'maintenance', 4 => 'out-of-service'] as $vehicle => $variant) {
            [, $r] = $this->submit($vehicle, $this->fields('Jean Test', '20/02/2026 08:00', '20/02/2026 18:00'));
            $this->assertRefusedWithGlobalError($r, self::UNAVAILABLE);
            $this->assertGolden(self::GOLDEN."bh102-$variant.html", $r);
        }
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-103')]
    public function bh103_end_equal_to_start_is_refused(): void
    {
        [, $r] = $this->submit(1, $this->fields('Jean Test', '20/02/2026 08:00', '20/02/2026 08:00'));

        $this->assertRefusedWithGlobalError($r, self::END_BEFORE_START);
        $this->assertGolden(self::GOLDEN.'bh103.html', $r);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-104')]
    public function bh104_end_before_start_is_refused(): void
    {
        [, $r] = $this->submit(1, $this->fields('Jean Test', '20/02/2026 18:00', '20/02/2026 08:00'));

        $this->assertRefusedWithGlobalError($r, self::END_BEFORE_START);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-105')]
    public function bh105_slot_inside_existing_booking_is_refused(): void
    {
        [, $r] = $this->submit(2, $this->fields('Jean Test', '10/02/2026 10:30', '10/02/2026 11:30'));

        $this->assertRefusedWithGlobalError($r, self::OVERLAP);
        $this->assertGolden(self::GOLDEN.'bh105.html', $r);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-106')]
    public function bh106_slot_ending_exactly_at_existing_start_is_refused(): void
    {
        [, $r] = $this->submit(2, $this->fields('Jean Test', '10/02/2026 08:00', '10/02/2026 10:00'));

        $this->assertRefusedWithGlobalError($r, self::OVERLAP);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-107')]
    public function bh107_slot_starting_exactly_at_existing_end_is_refused(): void
    {
        [, $r] = $this->submit(2, $this->fields('Jean Test', '10/02/2026 12:00', '10/02/2026 14:00'));

        $this->assertRefusedWithGlobalError($r, self::OVERLAP);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-108')]
    public function bh108_slot_enclosing_existing_booking_is_refused(): void
    {
        [, $r] = $this->submit(2, $this->fields('Jean Test', '10/02/2026 09:00', '10/02/2026 13:00'));

        $this->assertRefusedWithGlobalError($r, self::OVERLAP);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-109')]
    public function bh109_one_minute_gap_is_enough(): void
    {
        [$client, $r] = $this->submit(2, $this->fields('Jean Avant', '10/02/2026 08:00', '10/02/2026 09:59'));
        $this->assertCreated($client, $r, 2);
        [$client, $r] = $this->submit(2, $this->fields('Jean Après', '10/02/2026 12:01', '10/02/2026 14:00'));
        $this->assertCreated($client, $r, 2);

        $this->assertDbDiff([
            $this->added(11, 2, 'Jean Avant', '2026-02-10 08:00:00', '2026-02-10 09:59:00'),
            $this->added(12, 2, 'Jean Après', '2026-02-10 12:01:00', '2026-02-10 14:00:00'),
        ]);
    }

    #[Test]
    #[Group('BH-110')]
    public function bh110_finished_and_cancelled_bookings_do_not_block(): void
    {
        [$client, $r] = $this->submit(5, $this->fields('Sur annulée', '10/02/2026 09:00', '10/02/2026 13:00'));
        $this->assertCreated($client, $r, 5);
        [$client, $r] = $this->submit(5, $this->fields('Sur terminée', '12/01/2026 09:00', '12/01/2026 13:00'));
        $this->assertCreated($client, $r, 5);

        $this->assertDbDiff([
            $this->added(11, 5, 'Sur annulée', '2026-02-10 09:00:00', '2026-02-10 13:00:00'),
            $this->added(12, 5, 'Sur terminée', '2026-01-12 09:00:00', '2026-01-12 13:00:00'),
        ]);
    }

    #[Test]
    #[Group('BH-111')]
    public function bh111_slot_over_cancelled_booking_is_accepted(): void
    {
        [$client, $r] = $this->submit(2, $this->fields('Jean Test', '20/02/2026 09:00', '20/02/2026 10:00'));

        $this->assertCreated($client, $r, 2);
        $this->assertDbDiff([$this->added(11, 2, 'Jean Test', '2026-02-20 09:00:00', '2026-02-20 10:00:00')]);
    }

    #[Test]
    #[Group('BH-112')]
    public function bh112_slot_during_ongoing_booking_is_refused(): void
    {
        [, $r] = $this->submit(6, $this->fields('Jean Test', '15/01/2026 12:00', '15/01/2026 14:00'));

        $this->assertRefusedWithGlobalError($r, self::OVERLAP);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-113')]
    public function bh113_overdue_booking_still_blocks_its_past_slot(): void
    {
        [, $r] = $this->submit(7, $this->fields('Jean Test', '05/01/2026 12:00', '05/01/2026 14:00'));

        $this->assertRefusedWithGlobalError($r, self::OVERLAP);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-114')]
    public function bh114_booking_in_the_past_is_accepted(): void
    {
        [$client, $r] = $this->submit(1, $this->fields('Jean Test', '01/12/2025 08:00', '01/12/2025 10:00'));

        $this->assertCreated($client, $r, 1);
        $this->assertDbDiff([$this->added(11, 1, 'Jean Test', '2025-12-01 08:00:00', '2025-12-01 10:00:00')]);
    }

    #[Test]
    #[Group('BH-115')]
    public function bh115_zero_mileage_vehicle_gives_zero_start_km(): void
    {
        [$client, $r] = $this->submit(8, $this->fields('Jean Test', '20/02/2026 08:00', '20/02/2026 18:00'));

        $this->assertCreated($client, $r, 8);
        $this->assertDbDiff([$this->added(11, 8, 'Jean Test', '2026-02-20 08:00:00', '2026-02-20 18:00:00')]);
    }

    #[Test]
    #[Group('BH-116')]
    public function bh116_blank_driver_name_is_refused_after_trim(): void
    {
        foreach (['' => 'empty', '   ' => 'spaces'] as $driver => $variant) {
            [, $r] = $this->submit(1, $this->fields((string) $driver, '20/02/2026 08:00', '20/02/2026 18:00'));
            $this->assertRedisplayed($r);
            self::assertSame([], $this->globalErrors($r, self::FORM));
            self::assertSame(['reservation_driverName' => [self::DRIVER_REQUIRED]], $this->fieldErrors($r, self::FORM), "conducteur « $driver »");
            $this->assertGolden(self::GOLDEN."bh116-$variant.html", $r);
        }
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-117')]
    public function bh117_driver_name_is_trimmed(): void
    {
        [$client, $r] = $this->submit(1, $this->fields('  Paul Martin  ', '20/02/2026 08:00', '20/02/2026 18:00'));

        $this->assertCreated($client, $r, 1);
        $this->assertDbDiff([$this->added(11, 1, 'Paul Martin', '2026-02-20 08:00:00', '2026-02-20 18:00:00')]);
    }

    #[Test]
    #[Group('BH-118')]
    public function bh118_driver_name_of_101_characters_is_stored_in_full(): void
    {
        $driver = str_repeat('A', 101);
        [$client, $r] = $this->submit(1, $this->fields($driver, '20/02/2026 08:00', '20/02/2026 18:00'));

        $this->assertCreated($client, $r, 1);
        $this->assertDbDiff([$this->added(11, 1, $driver, '2026-02-20 08:00:00', '2026-02-20 18:00:00')]);
    }

    #[Test]
    #[Group('BH-119')]
    public function bh119_html_and_unicode_driver_name_stored_raw_and_displayed_escaped(): void
    {
        $driver = '<script>alert(1)</script> Émile 🚐';
        [$client, $r] = $this->submit(1, $this->fields($driver, '20/02/2026 08:00', '20/02/2026 18:00'));

        $page = $this->assertCreated($client, $r, 1);
        $this->assertGolden(self::GOLDEN.'bh119-after-redirect.html', $page);
        self::assertStringContainsString(': &lt;script&gt;alert(1)&lt;/script&gt; Émile 🚐', $page->body, 'affiché échappé');
        self::assertStringNotContainsString('<script>alert(1)</script>', $page->body);
        $this->assertDbDiff([$this->added(11, 1, $driver, '2026-02-20 08:00:00', '2026-02-20 18:00:00')]);
    }

    #[Test]
    #[Group('BH-120')]
    public function bh120_iso_dates_are_invalid_on_both_fields(): void
    {
        [, $r] = $this->submit(1, $this->fields('Jean Test', '2026-02-20 08:00', '2026-02-20 18:00'));

        $this->assertRedisplayed($r);
        self::assertSame([], $this->globalErrors($r, self::FORM));
        self::assertSame(
            ['reservation_startAt' => [self::NOT_VALID], 'reservation_endAt' => [self::NOT_VALID]],
            $this->fieldErrors($r, self::FORM),
        );
        self::assertSame('2026-02-20 08:00', $this->fieldValue($r, 'reservation_startAt'), 'saisie réaffichée');
        $this->assertGolden(self::GOLDEN.'bh120.html', $r);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-121')]
    public function bh121_impossible_or_incomplete_dates_are_invalid(): void
    {
        foreach (['31/02/2026 08:00' => 'feb-31', '20/02/2026 25:00' => 'hour-25', '20/02/2026 8' => 'no-minutes'] as $date => $variant) {
            [, $r] = $this->submit(1, $this->fields('Jean Test', $date, $date));
            $this->assertRedisplayed($r);
            self::assertSame(
                ['reservation_startAt' => [self::NOT_VALID], 'reservation_endAt' => [self::NOT_VALID]],
                $this->fieldErrors($r, self::FORM),
                "date « $date »",
            );
            $this->assertGolden(self::GOLDEN."bh121-$variant.html", $r);
        }
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-122')]
    public function bh122_two_digit_year_is_stored_as_year_26_shifted(): void
    {
        [$client, $r] = $this->submit(1, $this->fields('X', '20/02/26 08:00', '20/02/26 18:00'));

        $page = $this->assertCreated($client, $r, 1);
        $this->assertGolden(self::GOLDEN.'bh122-two-digit-year.html', $page);
        self::assertSame(['18/02/0026 07:42 → 18/02/0026 17:42 : X (retour)'], $this->showItems($page, 0));
        $this->assertDbDiff([$this->added(11, 1, 'X', '0026-02-18 07:42:30', '0026-02-18 17:42:30')]);
    }

    #[Test]
    #[Group('BH-123')]
    public function bh123_single_digit_date_parts_are_accepted(): void
    {
        [$client, $r] = $this->submit(1, $this->fields('Jean Test', '1/2/2026 8:05', '1/2/2026 9:05'));

        $this->assertCreated($client, $r, 1);
        $this->assertDbDiff([$this->added(11, 1, 'Jean Test', '2026-02-01 08:05:00', '2026-02-01 09:05:00')]);
    }

    #[Test]
    #[Group('BH-124')]
    public function bh124_seconds_are_accepted_and_dropped(): void
    {
        [$client, $r] = $this->submit(1, $this->fields('Jean Test', '20/02/2026 08:00:30', '20/02/2026 18:00:30'));

        $this->assertCreated($client, $r, 1);
        $this->assertDbDiff([$this->added(11, 1, 'Jean Test', '2026-02-20 08:00:00', '2026-02-20 18:00:00')]);
    }

    #[Test]
    #[Group('BH-125')]
    public function bh125_nonexistent_dst_hour_is_invalid_on_start_only(): void
    {
        [, $r] = $this->submit(1, $this->fields('Jean Test', '29/03/2026 02:30', '29/03/2026 03:30'));

        $this->assertRedisplayed($r);
        self::assertSame(['reservation_startAt' => [self::NOT_VALID]], $this->fieldErrors($r, self::FORM));
        $this->assertGolden(self::GOLDEN.'bh125.html', $r);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-126')]
    public function bh126_ambiguous_dst_hour_is_stored_as_is(): void
    {
        [$client, $r] = $this->submit(1, $this->fields('Jean Test', '25/10/2026 02:30', '25/10/2026 02:45'));

        $this->assertCreated($client, $r, 1);
        $this->assertDbDiff([$this->added(11, 1, 'Jean Test', '2026-10-25 02:30:00', '2026-10-25 02:45:00')]);
    }

    #[Test]
    #[Group('BH-127')]
    public function bh127_year_9999_is_invalid_on_both_fields(): void
    {
        [, $r] = $this->submit(1, $this->fields('Jean Test', '31/12/9999 08:00', '31/12/9999 18:00'));

        $this->assertRedisplayed($r);
        self::assertSame(
            ['reservation_startAt' => [self::NOT_VALID], 'reservation_endAt' => [self::NOT_VALID]],
            $this->fieldErrors($r, self::FORM),
        );
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-128')]
    public function bh128_any_minute_and_one_minute_duration_accepted(): void
    {
        [$client, $r] = $this->submit(1, $this->fields('Jean Test', '20/02/2026 08:07', '20/02/2026 08:08'));

        $this->assertCreated($client, $r, 1);
        $this->assertDbDiff([$this->added(11, 1, 'Jean Test', '2026-02-20 08:07:00', '2026-02-20 08:08:00')]);
    }

    #[Test]
    #[Group('BH-129')]
    public function bh129_missing_or_wrong_csrf_token_is_refused(): void
    {
        $fields = $this->fields('Jean Test', '20/02/2026 08:00', '20/02/2026 18:00');
        [, $r] = $this->submit(1, $fields, false);
        $this->assertRefusedWithGlobalError($r, self::CSRF_INVALID);
        $this->assertGolden(self::GOLDEN.'bh129-missing.html', $r);

        [, $r] = $this->submit(1, $fields + ['reservation[_token]' => 'faux-jeton'], false);
        $this->assertRefusedWithGlobalError($r, self::CSRF_INVALID);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-130')]
    public function bh130_extra_field_inside_form_is_refused_without_assignment(): void
    {
        [, $r] = $this->submit(1, $this->fields('Jean Test', '20/02/2026 08:00', '20/02/2026 18:00') + ['reservation[status]' => 'terminee']);

        $this->assertRefusedWithGlobalError($r, self::EXTRA_FIELDS);
        $this->assertGolden(self::GOLDEN.'bh130.html', $r);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-131')]
    public function bh131_extra_field_outside_form_is_ignored(): void
    {
        [$client, $r] = $this->submit(1, $this->fields('Jean Test', '20/02/2026 08:00', '20/02/2026 18:00') + ['foo' => 'bar']);

        $this->assertCreated($client, $r, 1);
        $this->assertDbDiff([$this->added(11, 1, 'Jean Test', '2026-02-20 08:00:00', '2026-02-20 18:00:00')]);
    }

    #[Test]
    #[Group('BH-132')]
    public function bh132_token_only_gives_three_mixed_language_errors(): void
    {
        [, $r] = $this->submit(1, []);

        $this->assertRedisplayed($r);
        self::assertSame([], $this->globalErrors($r, self::FORM));
        self::assertSame([
            'reservation_driverName' => [self::DRIVER_REQUIRED],
            'reservation_startAt' => [self::NOT_BLANK],
            'reservation_endAt' => [self::NOT_BLANK],
        ], $this->fieldErrors($r, self::FORM));
        $this->assertGolden(self::GOLDEN.'bh132.html', $r);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-133')]
    public function bh133_post_without_body_redisplays_form_without_error(): void
    {
        $client = $this->session();
        $this->openForm($client, '/vehicules/1/reserver');
        $r = $this->postForm($client, '/vehicules/1/reserver', null);

        $this->assertRedisplayed($r);
        self::assertSame([], $this->globalErrors($r, self::FORM));
        self::assertSame([], $this->fieldErrors($r, self::FORM));
        $this->assertGolden(self::GOLDEN.'bh133.html', $r);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-134')]
    public function bh134_same_post_twice_creates_a_single_booking(): void
    {
        $client = $this->session();
        [, $token] = $this->openForm($client, '/vehicules/1/reserver');
        $fields = $this->fields('Jean Test', '20/02/2026 08:00', '20/02/2026 18:00') + ['reservation[_token]' => $token];

        $first = $this->postForm($client, '/vehicules/1/reserver', $fields);
        $this->assertStatus(302, $first);
        $second = $this->postForm($client, '/vehicules/1/reserver', $fields);
        $this->assertRefusedWithGlobalError($second, self::OVERLAP);
        $this->assertDbDiff([$this->added(11, 1, 'Jean Test', '2026-02-20 08:00:00', '2026-02-20 18:00:00')]);
    }

    #[Test]
    #[Group('BH-135')]
    public function bh135_form_of_unavailable_vehicle_is_displayed(): void
    {
        $r = $this->send('GET', '/vehicules/3/reserver');

        $this->assertStatus(200, $r);
        $this->assertCommonHeaders($r);
        $this->assertGolden(self::GOLDEN.'bh135.html', $r);
        self::assertSame('Réserver 1-BOK-003', $r->crawler()->filter('h1')->text());
        self::assertSame([], $this->globalErrors($r, self::FORM));
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-136')]
    public function bh136_unknown_or_invalid_vehicle_is_not_found(): void
    {
        $client = $this->session();
        $r = $this->postForm($client, '/vehicules/999/reserver', $this->fields('Jean Test', '20/02/2026 08:00', '20/02/2026 18:00'));
        $this->assertStatus(404, $r);
        $this->assertCommonHeaders($r);

        $r = $this->send('GET', '/vehicules/abc/reserver');
        $this->assertStatus(404, $r);
        $this->assertCommonHeaders($r);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-137')]
    public function bh137_put_is_method_not_allowed(): void
    {
        $r = $this->send('PUT', '/vehicules/1/reserver');

        $this->assertStatus(405, $r);
        $this->assertCommonHeaders($r);
        self::assertSame('GET, POST, HEAD', $r->header('Allow'));
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-138')]
    public function bh138_created_booking_appears_in_upcoming_list_with_return_link(): void
    {
        [$client, $r] = $this->submit(1, $this->fields('Jean Test', '20/02/2026 08:00', '20/02/2026 18:00'));
        $this->assertCreated($client, $r, 1);

        $page = $client->get('/vehicules/1');
        $this->assertStatus(200, $page);
        $this->assertGolden(self::GOLDEN.'bh138-vehicle-page.html', $page);
        self::assertSame(['20/02/2026 08:00 → 20/02/2026 18:00 : Jean Test (retour)'], $this->showItems($page, 0));
        self::assertSame('/reservations/11/retour', $page->crawler()->filter('ul')->eq(0)->filter('a')->attr('href'));
        self::assertSame([], $this->showItems($page, 1, true), 'historique vide');
    }
}
