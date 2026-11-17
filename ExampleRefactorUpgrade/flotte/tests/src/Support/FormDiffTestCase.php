<?php

declare(strict_types=1);

namespace Flotte\Tests\Support;

/**
 * Variante différentielle d'un **parcours** de formulaire (§ 1.2.3 + § 3.1) : le même parcours (GET du formulaire,
 * POST, GET de Location…) est joué sur app-ref puis sur app, chacun avec une session neuve, après restauration du
 * même scénario des deux côtés. Comparés à chaque étape : A1 statut, A2 en-têtes contractuels et forme des cookies,
 * A3 corps HTML normalisé (jeton CSRF masqué ; sauf 3xx et ≥ 400, non contractuels) ; puis A4 diff de la base
 * (identique des deux côtés) et A5 signatures du journal PHP.
 */
abstract class FormDiffTestCase extends DiffTestCase
{
    use FormJourney;

    private const CONTRACT_HEADERS = ['location', 'content-type', 'x-fleet-version', 'cache-control', 'allow', 'www-authenticate'];

    protected function setUp(): void
    {
        parent::setUp();
        if (null !== $this->scenario()) {
            foreach ($this->targets() as $target) {
                $this->initialSnapshot($target);
            }
        }
    }

    /**
     * @param \Closure(HttpClient): list<HttpResponse> $journey parcours joué avec une session neuve, renvoie les
     *                                                          réponses à comparer, dans l'ordre
     *
     * @return array{ref: list<HttpResponse>, app: list<HttpResponse>}
     */
    protected function assertJourneySameAsReference(\Closure $journey, string $label): array
    {
        $responses = $logs = [];
        foreach ([Target::REF, Target::APP] as $target) {
            $offset = PhpLog::offset($target);
            $responses[$target] = $journey($this->session($target));
            $logs[$target] = PhpLog::signaturesSince($target, $offset);
        }
        $ref = $responses[Target::REF];
        $app = $responses[Target::APP];
        self::assertCount(\count($ref), $app, "Nombre d'étapes app ≠ app-ref — $label");

        foreach ($ref as $i => $r) {
            $a = $app[$i];
            $where = "$label, étape ".($i + 1).' ('.$r->describe().')';
            self::assertSame($r->status, $a->status, "A1 statut app ≠ app-ref — $where");
            self::assertSame($this->journeyHeaders($r), $this->journeyHeaders($a), "A2 en-têtes app ≠ app-ref — $where");
            if ($r->status < 300) {
                self::assertSame(Normalizer::html($r->body), Normalizer::html($a->body), "A3 corps normalisé app ≠ app-ref — $where");
            }
        }
        self::assertSame($this->dbDiff(Target::REF), $this->dbDiff(Target::APP), "A4 diff de la base app ≠ app-ref — $label");
        self::assertSame(Db::dump(Target::REF), Db::dump(Target::APP), "A4 dump canonique app ≠ app-ref — $label");
        self::assertSame($logs[Target::REF], $logs[Target::APP], "A5 journal PHP app ≠ app-ref — $label");
        $this->lastRefLog = $logs[Target::REF];

        return ['ref' => $ref, 'app' => $app];
    }

    /**
     * Parcours type : GET du formulaire, POST des champs (+ jeton valide si $withToken), GET de Location si 302.
     *
     * @param array<string, string>|null $fields
     *
     * @return \Closure(HttpClient): list<HttpResponse>
     */
    protected function submitJourney(string $path, ?array $fields, string $tokenField, bool $withToken = true): \Closure
    {
        return function (HttpClient $c) use ($path, $fields, $tokenField, $withToken): array {
            [$form, $token] = $this->openForm($c, $path);
            if (null !== $fields && $withToken) {
                $fields[$tokenField] = $token;
            }
            $post = $this->postForm($c, $path, $fields);
            $out = [$form, $post];
            if (302 === $post->status) {
                $out[] = $this->follow($c, $post);
            }

            return $out;
        };
    }

    /** @return array<string, list<string>> */
    private function journeyHeaders(HttpResponse $r): array
    {
        $out = [];
        foreach (self::CONTRACT_HEADERS as $name) {
            $out[$name] = $r->headerValues($name);
        }
        $out['set-cookie'] = $r->cookieShapes();

        return $out;
    }
}
