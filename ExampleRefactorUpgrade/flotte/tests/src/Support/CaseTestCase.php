<?php

declare(strict_types=1);

namespace Flotte\Tests\Support;

use PHPUnit\Framework\TestCase;

/**
 * Classe de base des tests boîte noire (gabarit tmp/tests-*.md § 2) :
 * - Arrange : restauration du scénario déclaré par #[Scenario('…')] sur la cible (empreinte vérifiée) ;
 * - Act : une requête, client neuf (sans cookie, sans redirection suivie) ;
 * - Assert : statut, en-têtes contractuels, corps normalisé (golden + assertions ciblées), base inchangée.
 *
 * Aucune classe de l'application n'est référencée : l'application n'est vue que par HTTP, la base et le journal.
 */
abstract class CaseTestCase extends TestCase
{
    /** En-têtes communs à toutes les réponses HTML de l'application (observés en step-00). */
    protected const FLEET_VERSION = '1.4.2';
    protected const HTML_CONTENT_TYPE = 'text/html; charset=UTF-8';

    /** @var array<string, string> empreinte attendue de la base, par cible restaurée */
    protected array $expectedFingerprints = [];

    /** Cibles à restaurer avant chaque test (les tests diff restaurent les deux). */
    protected function targets(): array
    {
        return [Target::current()];
    }

    protected function setUp(): void
    {
        parent::setUp();
        $scenario = $this->scenario();
        if (null !== $scenario) {
            foreach ($this->targets() as $target) {
                $this->expectedFingerprints[$target] = Fixtures::restore($scenario, $target);
            }
        }
    }

    protected function target(): string
    {
        return Target::current();
    }

    protected function scenario(): ?string
    {
        $method = new \ReflectionMethod($this, $this->name());
        foreach ([$method, new \ReflectionClass($this)] as $reflector) {
            foreach ($reflector->getAttributes(Scenario::class) as $attribute) {
                return $attribute->newInstance()->name;
            }
        }

        return null;
    }

    protected function client(?string $target = null): HttpClient
    {
        return HttpClient::for($target ?? $this->target());
    }

    /** Une requête, client neuf. */
    protected function send(string $method, string $path, ?array $basicAuth = null, ?string $target = null): HttpResponse
    {
        $client = $this->client($target);
        if (null !== $basicAuth) {
            $client->withBasicAuth($basicAuth[0], $basicAuth[1]);
        }

        return $client->request($method, $path);
    }

    // --- A1 / A2 ----------------------------------------------------------------------------------------------

    protected function assertStatus(int $expected, HttpResponse $r): void
    {
        self::assertSame($expected, $r->status, 'A1 statut — '.$r->describe());
    }

    /**
     * X-Fleet-Version, Cache-Control, Content-Type : présents sur toutes les réponses (200, 302, 401, 404, 405).
     * Cache-Control / Expires : attente selon l'étape de la cible (ACC-001, AcceptedChanges).
     */
    protected function assertCommonHeaders(HttpResponse $r): void
    {
        self::assertSame(self::FLEET_VERSION, $r->header('X-Fleet-Version'), 'A2 X-Fleet-Version — '.$r->describe());
        $this->assertCacheHeaders($r);
        self::assertSame(self::HTML_CONTENT_TYPE, $r->header('Content-Type'), 'A2 Content-Type — '.$r->describe());
    }

    /** Cache-Control exact et présence d'Expires (ACC-001 à partir de step-01, « no-cache » sans Expires avant). */
    protected function assertCacheHeaders(HttpResponse $r): void
    {
        AcceptedChanges::assertCacheHeaders($r);
    }

    /** Cookie de session PHPSESSID posé (path=/, HttpOnly) — jamais sa valeur. */
    protected function assertSessionCookie(HttpResponse $r): void
    {
        self::assertSame(['PHPSESSID; path=/; httponly'], $r->cookieShapes(), 'A2 Set-Cookie — '.$r->describe());
    }

    protected function assertNoCookie(HttpResponse $r): void
    {
        self::assertSame([], $r->cookieShapes(), 'A2 Set-Cookie absent — '.$r->describe());
    }

    // --- A3 -----------------------------------------------------------------------------------------------

    protected function html(HttpResponse $r): string
    {
        return Normalizer::html($r->body);
    }

    protected function assertGolden(string $name, HttpResponse $r): void
    {
        Golden::assertMatchesGolden($name, $this->html($r), 'A3 corps — '.$r->describe());
    }

    /** Texte des liens de plaque des lignes du tableau de la liste, dans l'ordre affiché. */
    protected function listPlates(HttpResponse $r): array
    {
        return $r->crawler()->filter('table tbody tr td:first-child a')->each(static fn ($a): string => $a->text('', false));
    }

    /** Lignes du tableau de la liste (texte brut des 4 cellules). */
    protected function listRows(HttpResponse $r): array
    {
        return $r->crawler()->filter('table tbody tr')->each(
            static fn ($tr): array => $tr->filter('td')->each(static fn ($td): string => $td->text('', false)),
        );
    }

    /**
     * Fiche véhicule : texte des <li> de la n-ième liste (0 = à venir, 1 = historique).
     * $raw = false : espaces normalisés ; true : texte brut (espaces conservés).
     */
    protected function showItems(HttpResponse $r, int $list, bool $raw = false): array
    {
        return $r->crawler()->filter('ul')->eq($list)->filter('li')->each(static fn ($li): string => $li->text('', !$raw));
    }

    /** Valeurs des options du filtre de statut portant l'attribut selected. */
    protected function selectedOptions(HttpResponse $r): array
    {
        return $r->crawler()->filter('select[name="status"] option[selected]')->each(static fn ($o): string => (string) $o->attr('value'));
    }

    // --- A4 -----------------------------------------------------------------------------------------------

    protected function assertDbUnchanged(?string $target = null): void
    {
        $target ??= $this->target();
        self::assertArrayHasKey($target, $this->expectedFingerprints, "Aucun scénario restauré sur « $target »");
        self::assertSame($this->expectedFingerprints[$target], Db::fingerprint($target), "A4 base modifiée sur « $target » (route en lecture seule)");
    }
}
