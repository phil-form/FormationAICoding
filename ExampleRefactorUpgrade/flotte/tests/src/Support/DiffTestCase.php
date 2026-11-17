<?php

declare(strict_types=1);

namespace Flotte\Tests\Support;

/**
 * Base des tests différentiels (suite diff, gabarit § 2.3) : même scénario restauré sur app ET app-ref, même requête
 * sur les deux, comparaison normalisée — sans valeur attendue propre : « identique à la référence ».
 *
 * Comparé : A1 statut ; A2 en-têtes contractuels (Location, Content-Type, X-Fleet-Version, Cache-Control, Allow,
 * WWW-Authenticate, forme des Set-Cookie) ; A3 corps HTML normalisé (sauf 3xx et pages d'erreur ≥ 400 : corps non
 * contractuel, proposition § 4) ; A4 base inchangée des deux côtés ; A5 signatures du journal PHP.
 * Normalisations : voir tests/src/Diff/README.md.
 */
abstract class DiffTestCase extends CaseTestCase
{
    private const CONTRACT_HEADERS = ['location', 'content-type', 'x-fleet-version', 'cache-control', 'allow', 'www-authenticate'];

    /** @var list<string> signatures du journal PHP de app-ref pour la dernière requête comparée */
    protected array $lastRefLog = [];

    protected function targets(): array
    {
        return [Target::REF, Target::APP];
    }

    /**
     * Rejoue la requête sur app-ref puis app (clients neufs) et compare.
     *
     * @return array{ref: HttpResponse, app: HttpResponse}
     */
    protected function assertSameAsReference(string $method, string $path, ?array $basicAuth = null): array
    {
        $responses = $logs = [];
        foreach ([Target::REF, Target::APP] as $target) {
            $offset = PhpLog::offset($target);
            $responses[$target] = $this->send($method, $path, $basicAuth, $target);
            $logs[$target] = PhpLog::signaturesSince($target, $offset);
        }
        $ref = $responses[Target::REF];
        $app = $responses[Target::APP];
        $where = "$method $path";

        self::assertSame($ref->status, $app->status, "A1 statut app ≠ app-ref — $where");
        self::assertSame($this->contractHeaders($ref), $this->contractHeaders($app), "A2 en-têtes app ≠ app-ref — $where");
        if ($ref->status < 300) {
            self::assertSame(Normalizer::html($ref->body), Normalizer::html($app->body), "A3 corps normalisé app ≠ app-ref — $where");
        }
        self::assertSame($logs[Target::REF], $logs[Target::APP], "A5 journal PHP app ≠ app-ref — $where");
        $this->lastRefLog = $logs[Target::REF];

        return ['ref' => $ref, 'app' => $app];
    }

    /** A4 : bases intactes des deux côtés et identiques entre elles. */
    protected function assertBothDbUnchanged(): void
    {
        $this->assertDbUnchanged(Target::REF);
        $this->assertDbUnchanged(Target::APP);
        self::assertSame(Db::dump(Target::REF), Db::dump(Target::APP), 'A4 dump canonique app ≠ app-ref');
    }

    /** @return array<string, list<string>> */
    private function contractHeaders(HttpResponse $r): array
    {
        $out = [];
        foreach (self::CONTRACT_HEADERS as $name) {
            $out[$name] = $r->headerValues($name);
        }
        $out['set-cookie'] = $r->cookieShapes();

        return $out;
    }
}
