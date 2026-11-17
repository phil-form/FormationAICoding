<?php

declare(strict_types=1);

namespace Flotte\Tests\Edge;

use Flotte\Tests\Support\Db;
use Flotte\Tests\Support\FormTestCase;
use Flotte\Tests\Support\HttpResponse;
use Flotte\Tests\Support\Scenario;
use PHPUnit\Framework\Attributes\Group;
use PHPUnit\Framework\Attributes\Test;

/**
 * Pièges de saisie de `reservation_new` (tmp/tests-application.md § 3.2) : analyse des dates par ICU
 * (`dd/MM/yyyy HH:mm`, non « lenient » mais tolérante à l'année sur 2 chiffres, aux chiffres uniques et aux secondes),
 * calendrier julien et heure solaire avant 1892, changements d'heure, longueur non contrôlée, POST sans corps
 * (`isValid()` sur un formulaire non soumis : exception à partir de Symfony 4).
 * Chaque test vérifie la valeur **réellement enregistrée** (texte, longueur, type SQLite) ou l'absence d'écriture.
 */
#[Group('zone-reservation-new')]
#[Scenario('booking')]
final class ReservationInputParsingTest extends FormTestCase
{
    private const PATH = '/vehicules/1/reserver';

    private function book(string $driver, string $start, string $end): HttpResponse
    {
        $client = $this->session();
        [, $token] = $this->openForm($client, self::PATH);

        return $this->postForm($client, self::PATH, [
            'reservation[driverName]' => $driver,
            'reservation[startAt]' => $start,
            'reservation[endAt]' => $end,
            'reservation[_token]' => $token,
        ]);
    }

    /** @return array<string, mixed>|null réservation créée (id 11), types SQLite compris */
    private function created(): ?array
    {
        return Db::query(
            $this->target(),
            'SELECT driver_name, length(driver_name) AS len, start_at, end_at, typeof(start_at) AS t_start, status FROM reservation WHERE id = 11',
        )[0] ?? null;
    }

    private function assertDates(string $start, string $end, string $why): void
    {
        $row = $this->created();
        self::assertNotNull($row, "réservation 11 attendue — $why");
        self::assertSame([$start, $end, 'text'], [$row['start_at'], $row['end_at'], $row['t_start']], $why);
    }

    /** @return array<string, list<string>> */
    private function dateErrors(HttpResponse $r): array
    {
        return $this->fieldErrors($r, 'reservation');
    }

    #[Test]
    #[Group('BH-118')]
    public function bh118_driver_name_longer_than_column_is_not_truncated(): void
    {
        $r = $this->book(str_repeat('é', 101), '20/02/2026 08:00', '20/02/2026 18:00');

        $this->assertStatus(302, $r);
        $row = $this->created();
        self::assertSame(101, $row['len'] ?? null, 'VARCHAR(100) non contrôlé par SQLite, aucune contrainte Length (DBAL 3 / validation : à surveiller)');
        self::assertSame(str_repeat('é', 101), $row['driver_name']);
    }

    #[Test]
    #[Group('BH-120')]
    public function bh120_iso_format_is_rejected_by_icu_pattern(): void
    {
        $r = $this->book('Jean Test', '2026-02-20 08:00', '2026-02-20 18:00');

        $this->assertStatus(200, $r);
        self::assertSame(['reservation_startAt' => ['This value is not valid.'], 'reservation_endAt' => ['This value is not valid.']], $this->dateErrors($r));
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-121')]
    public function bh121_non_lenient_parsing_rejects_impossible_dates(): void
    {
        foreach (['31/02/2026 08:00', '20/02/2026 25:00', '20/02/2026 8'] as $date) {
            $r = $this->book('Jean Test', $date, $date);
            $this->assertStatus(200, $r);
            self::assertCount(2, $this->dateErrors($r), "« $date » : erreur sur les deux champs (ICU non lenient)");
        }
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-122')]
    public function bh122_two_digit_year_becomes_julian_year_26_with_brussels_solar_time(): void
    {
        $r = $this->book('X', '20/02/26 08:00', '20/02/26 18:00');

        $this->assertStatus(302, $r);
        $this->assertDates('0026-02-18 07:42:30', '0026-02-18 17:42:30', 'an 26, −2 jours (calendrier julien), −17 min 30 s (heure solaire de Bruxelles avant 1892)');
    }

    #[Test]
    #[Group('BH-123')]
    public function bh123_single_digit_day_month_hour_are_accepted(): void
    {
        $r = $this->book('Jean Test', '1/2/2026 8:05', '1/2/2026 9:05');

        $this->assertStatus(302, $r);
        $this->assertDates('2026-02-01 08:05:00', '2026-02-01 09:05:00', 'chiffres uniques acceptés par ICU');
    }

    #[Test]
    #[Group('BH-124')]
    public function bh124_trailing_seconds_are_accepted_and_dropped(): void
    {
        $r = $this->book('Jean Test', '20/02/2026 08:00:30', '20/02/2026 18:00:30');

        $this->assertStatus(302, $r);
        $this->assertDates('2026-02-20 08:00:00', '2026-02-20 18:00:00', 'secondes ignorées par le motif HH:mm');
    }

    #[Test]
    #[Group('BH-125')]
    public function bh125_nonexistent_spring_forward_hour_is_rejected(): void
    {
        $r = $this->book('Jean Test', '29/03/2026 02:30', '29/03/2026 03:30');

        $this->assertStatus(200, $r);
        self::assertSame(['reservation_startAt' => ['This value is not valid.']], $this->dateErrors($r), '02:30 n\'existe pas le 29/03/2026 (Europe/Brussels)');
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-126')]
    public function bh126_ambiguous_fall_back_hour_is_stored_naively(): void
    {
        $r = $this->book('Jean Test', '25/10/2026 02:30', '25/10/2026 02:45');

        $this->assertStatus(302, $r);
        $this->assertDates('2026-10-25 02:30:00', '2026-10-25 02:45:00', 'heure ambiguë enregistrée telle quelle (heure naïve)');
    }

    #[Test]
    #[Group('BH-127')]
    public function bh127_year_9999_is_rejected(): void
    {
        $r = $this->book('Jean Test', '31/12/9999 08:00', '31/12/9999 18:00');

        $this->assertStatus(200, $r);
        self::assertCount(2, $this->dateErrors($r), 'année 9999 refusée par ICU sur les deux champs');
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-133')]
    public function bh133_post_without_body_never_fails(): void
    {
        $client = $this->session();
        $this->openForm($client, self::PATH);
        $r = $this->postForm($client, self::PATH, null);

        $this->assertStatus(200, $r);
        self::assertSame([], $this->globalErrors($r, 'reservation'), 'formulaire non soumis : aucune erreur (Symfony 4+ : exception si isSubmitted() n\'est pas vérifié)');
        self::assertSame([], $this->dateErrors($r));
        $this->assertDbUnchanged();
    }
}
