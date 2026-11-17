<?php

declare(strict_types=1);

namespace Flotte\Tests\Edge;

use Flotte\Tests\Support\CaseTestCase;
use Flotte\Tests\Support\Scenario;
use PHPUnit\Framework\Attributes\Group;
use PHPUnit\Framework\Attributes\Test;

/**
 * Piège : auto-échappement HTML de Twig (1 → 3) — entités produites (`&#039;` pour l'apostrophe, `&quot;`),
 * Unicode et emoji non transformés.
 * Version concernée : Twig 2/3 (step-01 à step-06), `htmlspecialchars` (ENT_QUOTES par défaut en PHP 8.1).
 * Fiche : BH-021 (liste, scénario edge).
 */
#[Group('zone-vehicles-list')]
final class HtmlEscapingTest extends CaseTestCase
{
    #[Test]
    #[Group('BH-021')]
    #[Scenario('edge')]
    public function bh021_brand_and_model_escaped_exactly(): void
    {
        $r = $this->send('GET', '/vehicules');
        $this->assertStatus(200, $r);
        self::assertStringContainsString(
            '<td>Citroën &lt;b&gt;C3&lt;/b&gt; &quot;Aircross&quot; &amp; &lt;script&gt;alert(&#039;x&#039;)&lt;/script&gt;</td>',
            $r->body,
            'Échappement historique changé (entités exactes &quot; &#039; &amp;)',
        );
        self::assertStringContainsString('<td>Škoda Énergie Ölçü Ñandú 電気 🚐</td>', $r->body, 'Unicode/emoji altérés');
        self::assertStringNotContainsString('<script>', $r->body, 'Contenu non échappé');
        $this->assertDbUnchanged();
    }
}
