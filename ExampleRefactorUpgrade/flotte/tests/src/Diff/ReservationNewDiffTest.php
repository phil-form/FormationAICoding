<?php

declare(strict_types=1);

namespace Flotte\Tests\Diff;

use Flotte\Tests\Support\FormDiffTestCase;
use Flotte\Tests\Support\HttpClient;
use Flotte\Tests\Support\Scenario;
use PHPUnit\Framework\Attributes\Group;
use PHPUnit\Framework\Attributes\Test;

/**
 * Variantes comparatives app ↔ app-ref, route `reservation_new` (fiches « sensibles à la migration » du § 3.2) :
 * même parcours (GET du formulaire → POST → GET de Location) sur les deux cibles, même scénario restauré des deux
 * côtés ; réponses normalisées, diffs de base et journal PHP comparés.
 */
#[Group('zone-reservation-new')]
#[Scenario('booking')]
final class ReservationNewDiffTest extends FormDiffTestCase
{
    private const TOKEN = 'reservation[_token]';

    /** @return array<string, string> */
    private function fields(string $driver, string $start, string $end): array
    {
        return ['reservation[driverName]' => $driver, 'reservation[startAt]' => $start, 'reservation[endAt]' => $end];
    }

    /** @param array<string, string>|null $fields null : POST sans corps */
    private function book(int $vehicle, ?array $fields, string $label, bool $withToken = true): void
    {
        $this->assertJourneySameAsReference($this->submitJourney("/vehicules/$vehicle/reserver", $fields, self::TOKEN, $withToken), $label);
    }

    #[Test]
    #[Group('BH-100')]
    public function bh100_booking_form_same_as_reference(): void
    {
        $this->assertSameAsReference('GET', '/vehicules/1/reserver');
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-101')]
    public function bh101_valid_booking_same_as_reference(): void
    {
        $this->book(1, $this->fields('Jean Test', '20/02/2026 08:00', '20/02/2026 18:00'), 'BH-101');
    }

    #[Test]
    #[Group('BH-106')]
    public function bh106_slot_touching_start_same_as_reference(): void
    {
        $this->book(2, $this->fields('Jean Test', '10/02/2026 08:00', '10/02/2026 10:00'), 'BH-106');
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-107')]
    public function bh107_slot_touching_end_same_as_reference(): void
    {
        $this->book(2, $this->fields('Jean Test', '10/02/2026 12:00', '10/02/2026 14:00'), 'BH-107');
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-116')]
    public function bh116_blank_driver_same_as_reference(): void
    {
        foreach (['', '   '] as $driver) {
            $this->book(1, $this->fields($driver, '20/02/2026 08:00', '20/02/2026 18:00'), "BH-116 « $driver »");
        }
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-117')]
    public function bh117_trimmed_driver_same_as_reference(): void
    {
        $this->book(1, $this->fields('  Paul Martin  ', '20/02/2026 08:00', '20/02/2026 18:00'), 'BH-117');
    }

    #[Test]
    #[Group('BH-118')]
    public function bh118_long_driver_same_as_reference(): void
    {
        $this->book(1, $this->fields(str_repeat('A', 101), '20/02/2026 08:00', '20/02/2026 18:00'), 'BH-118');
    }

    #[Test]
    #[Group('BH-119')]
    public function bh119_html_driver_same_as_reference(): void
    {
        $this->book(1, $this->fields('<script>alert(1)</script> Émile 🚐', '20/02/2026 08:00', '20/02/2026 18:00'), 'BH-119');
    }

    #[Test]
    #[Group('BH-120')]
    public function bh120_iso_dates_same_as_reference(): void
    {
        $this->book(1, $this->fields('Jean Test', '2026-02-20 08:00', '2026-02-20 18:00'), 'BH-120');
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-121')]
    public function bh121_impossible_dates_same_as_reference(): void
    {
        foreach (['31/02/2026 08:00', '20/02/2026 25:00', '20/02/2026 8'] as $date) {
            $this->book(1, $this->fields('Jean Test', $date, $date), "BH-121 « $date »");
        }
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-122')]
    public function bh122_two_digit_year_same_as_reference(): void
    {
        $this->book(1, $this->fields('X', '20/02/26 08:00', '20/02/26 18:00'), 'BH-122');
    }

    #[Test]
    #[Group('BH-123')]
    public function bh123_single_digits_same_as_reference(): void
    {
        $this->book(1, $this->fields('Jean Test', '1/2/2026 8:05', '1/2/2026 9:05'), 'BH-123');
    }

    #[Test]
    #[Group('BH-124')]
    public function bh124_seconds_same_as_reference(): void
    {
        $this->book(1, $this->fields('Jean Test', '20/02/2026 08:00:30', '20/02/2026 18:00:30'), 'BH-124');
    }

    #[Test]
    #[Group('BH-125')]
    public function bh125_nonexistent_hour_same_as_reference(): void
    {
        $this->book(1, $this->fields('Jean Test', '29/03/2026 02:30', '29/03/2026 03:30'), 'BH-125');
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-126')]
    public function bh126_ambiguous_hour_same_as_reference(): void
    {
        $this->book(1, $this->fields('Jean Test', '25/10/2026 02:30', '25/10/2026 02:45'), 'BH-126');
    }

    #[Test]
    #[Group('BH-127')]
    public function bh127_year_9999_same_as_reference(): void
    {
        $this->book(1, $this->fields('Jean Test', '31/12/9999 08:00', '31/12/9999 18:00'), 'BH-127');
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-129')]
    public function bh129_csrf_same_as_reference(): void
    {
        $fields = $this->fields('Jean Test', '20/02/2026 08:00', '20/02/2026 18:00');
        $this->book(1, $fields, 'BH-129 sans jeton', false);
        $this->book(1, $fields + [self::TOKEN => 'faux-jeton'], 'BH-129 jeton faux', false);
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-130')]
    public function bh130_extra_field_same_as_reference(): void
    {
        $this->book(1, $this->fields('Jean Test', '20/02/2026 08:00', '20/02/2026 18:00') + ['reservation[status]' => 'terminee'], 'BH-130');
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-132')]
    public function bh132_token_only_same_as_reference(): void
    {
        $this->book(1, [], 'BH-132');
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-133')]
    public function bh133_post_without_body_same_as_reference(): void
    {
        $this->book(1, null, 'BH-133');
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-136')]
    public function bh136_unknown_vehicle_same_as_reference(): void
    {
        $this->assertJourneySameAsReference(
            fn (HttpClient $c): array => [$this->postForm($c, '/vehicules/999/reserver', $this->fields('Jean Test', '20/02/2026 08:00', '20/02/2026 18:00'))],
            'BH-136 POST 999',
        );
        $this->assertSameAsReference('GET', '/vehicules/abc/reserver');
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-138')]
    public function bh138_vehicle_page_after_booking_same_as_reference(): void
    {
        $journey = $this->submitJourney('/vehicules/1/reserver', $this->fields('Jean Test', '20/02/2026 08:00', '20/02/2026 18:00'), self::TOKEN);
        $this->assertJourneySameAsReference(
            static fn (HttpClient $c): array => [...$journey($c), $c->get('/vehicules/1')],
            'BH-138',
        );
    }
}
