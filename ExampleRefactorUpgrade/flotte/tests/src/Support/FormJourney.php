<?php

declare(strict_types=1);

namespace Flotte\Tests\Support;

use PHPUnit\Framework\Assert;

/**
 * Parcours d'un formulaire protégé par CSRF (tmp/tests-application.md § 3.1) :
 * client avec session → GET du formulaire (jeton) → POST urlencoded des champs **exacts** de la fiche →
 * GET de `Location` avec la même session pour le message flash. A4 = diff exact de la base (DbDiff).
 *
 * Utilisé par FormTestCase (suites http, edge) et FormDiffTestCase (suite diff). Aucune classe de l'application.
 */
trait FormJourney
{
    /** Instantané de la base pris juste après la restauration (avant toute requête), par cible. */
    private array $initialSnapshots = [];

    /** Client neuf (session vide) pour une cible. */
    protected function session(?string $target = null): HttpClient
    {
        return HttpClient::for($target ?? $this->target());
    }

    /**
     * GET du formulaire et extraction du jeton CSRF (`<form>[_token]`).
     *
     * @return array{0: HttpResponse, 1: string}
     */
    protected function openForm(HttpClient $client, string $path): array
    {
        $r = $client->get($path);
        Assert::assertSame(200, $r->status, 'GET du formulaire — '.$r->describe());
        if (!preg_match('/name="[a-z_]+\[_token\]"[^>]*value="([^"]*)"/i', $r->body, $m)) {
            Assert::fail('Jeton CSRF introuvable — '.$r->describe());
        }

        return [$r, html_entity_decode($m[1])];
    }

    /**
     * POST `application/x-www-form-urlencoded` : champs envoyés tels quels, dans l'ordre donné.
     * $fields = null : POST **sans corps** (aucun Content-Type, Content-Length: 0).
     *
     * @param array<string, string>|null $fields
     */
    protected function postForm(HttpClient $client, string $path, ?array $fields, string $method = 'POST'): HttpResponse
    {
        if (null === $fields) {
            return $client->request($method, $path);
        }
        $pairs = [];
        foreach ($fields as $name => $value) {
            $pairs[] = rawurlencode((string) $name).'='.rawurlencode($value);
        }

        return $client->request($method, $path, [], implode('&', $pairs));
    }

    /** Suit une redirection 302 avec la même session (GET de Location). */
    protected function follow(HttpClient $client, HttpResponse $r): HttpResponse
    {
        $location = $r->header('Location');
        Assert::assertNotNull($location, 'Location attendu — '.$r->describe());

        return $client->get($location);
    }

    // --- A3 : lecture du formulaire et des messages ------------------------------------------------------------

    /** Messages flash de succès affichés par le layout. */
    protected function flashes(HttpResponse $r): array
    {
        return $r->crawler()->filter('p.flash-success')->each(static fn ($p): string => $p->text('', false));
    }

    /** Erreurs globales du formulaire (`<div id="<form>"><ul><li>…`), en tête du formulaire. */
    protected function globalErrors(HttpResponse $r, string $form): array
    {
        return $r->crawler()->filter("#$form > ul > li")->each(static fn ($li): string => $li->text('', false));
    }

    /**
     * Erreurs de champ : identifiant du champ → messages (dans le `<div>` du champ, avant l'`<input>`).
     * Seuls les champs portant une erreur figurent dans le résultat.
     *
     * @return array<string, list<string>>
     */
    protected function fieldErrors(HttpResponse $r, string $form): array
    {
        $out = [];
        $r->crawler()->filter("#$form > div")->each(static function ($div) use (&$out): void {
            $input = $div->filter('input');
            if (0 === $input->count()) {
                return;
            }
            $errors = $div->filter('ul > li')->each(static fn ($li): string => $li->text('', false));
            if ([] !== $errors) {
                $out[(string) $input->attr('id')] = $errors;
            }
        });

        return $out;
    }

    /** Valeur réaffichée d'un champ (`value="…"`), null si absente. */
    protected function fieldValue(HttpResponse $r, string $id): ?string
    {
        $input = $r->crawler()->filter("#$id");

        return 0 === $input->count() ? null : $input->attr('value');
    }

    // --- A4 : diff exact de la base ---------------------------------------------------------------------------

    /** Instantané initial (pris au premier appel, normalement juste après la restauration). */
    protected function initialSnapshot(?string $target = null): array
    {
        $target ??= $this->target();

        return $this->initialSnapshots[$target] ??= DbDiff::snapshot($target);
    }

    /** Diff de la base de la cible depuis l'instantané initial. */
    protected function dbDiff(?string $target = null): array
    {
        $target ??= $this->target();

        return DbDiff::between($this->initialSnapshot($target), DbDiff::snapshot($target));
    }

    /** A4 : la base contient exactement ces différences, et rien d'autre. */
    protected function assertDbDiff(array $expected, ?string $target = null): void
    {
        Assert::assertSame($expected, $this->dbDiff($target), 'A4 diff exact de la base ('.($target ?? $this->target()).')');
    }

    /** Restaure à nouveau le scénario (variante suivante d'une même fiche), instantané initial compris. */
    protected function restoreScenarioAgain(): void
    {
        $scenario = $this->scenario();
        Assert::assertNotNull($scenario, 'Aucun #[Scenario] à restaurer');
        foreach ($this->targets() as $target) {
            $this->expectedFingerprints[$target] = Fixtures::restore($scenario, $target);
            unset($this->initialSnapshots[$target]);
            $this->initialSnapshot($target);
        }
    }
}
