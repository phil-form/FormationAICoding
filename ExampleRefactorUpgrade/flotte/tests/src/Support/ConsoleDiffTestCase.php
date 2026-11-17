<?php

declare(strict_types=1);

namespace Flotte\Tests\Support;

/**
 * Variante différentielle des commandes console (suite diff, gabarit § 1.2.3 et § 6.1) : même scénario restauré sur
 * app-ref et app, même commande sur les deux (app-ref d'abord), comparaison sans valeur attendue propre.
 *
 * Comparé : A1 code de retour ; A2 message d'exception de stderr (classe et rappel de syntaxe non contractuels) ;
 * A3 stdout octet par octet (aide et liste : Normalizer::cli avant step-01, partie fleet:* seule dès qu'ACC-002
 * s'applique à un côté) ; A5 signatures du journal PHP. A4 : voir
 * assertBothDbUnchanged() (fleet:report) et assertBothDbAreDemo() (fleet:demo:load).
 */
abstract class ConsoleDiffTestCase extends DiffTestCase
{
    use ConsoleCommands;

    /**
     * @param list<string> $args
     *
     * @return array{ref: ConsoleRun, app: ConsoleRun}
     */
    protected function assertConsoleSameAsReference(array $args, bool $help = false): array
    {
        $ref = $this->console($args, Target::REF);
        $app = $this->console($args, Target::APP);
        $where = 'sf-console '.implode(' ', $args);

        self::assertSame($ref->exit, $app->exit, "A1 code de retour app ≠ app-ref — $where\n".$app->describe());
        self::assertSame($ref->errorMessage(), $app->errorMessage(), "A2 message stderr app ≠ app-ref — $where");
        self::assertSame('' === $ref->stderr, '' === $app->stderr, "A2 présence de stderr app ≠ app-ref — $where");
        if ($help && AcceptedChanges::appliesToComparison('ACC-002')) {
            // ACC-002 : seule la partie propre aux commandes fleet:* est comparée (en-tête de version, options globales
            // et mise en page produits par le framework : hors contrat).
            self::assertSame(self::helpContract($args, $ref->stdout), self::helpContract($args, $app->stdout), "A3 aide app ≠ app-ref (ACC-002 : partie fleet:*) — $where");
        } elseif ($help) {
            self::assertSame(self::helpText($ref->stdout), self::helpText($app->stdout), "A3 aide app ≠ app-ref — $where");
        } else {
            self::assertSame($ref->stdout, $app->stdout, "A3 stdout app ≠ app-ref (octet par octet) — $where");
        }
        self::assertSame($ref->log, $app->log, "A5 journal PHP app ≠ app-ref — $where");

        return ['ref' => $ref, 'app' => $app];
    }

    /** A4 de fleet:demo:load : les deux bases égales au scénario demo. */
    protected function assertBothDbAreDemo(): void
    {
        foreach ([Target::REF, Target::APP] as $target) {
            self::assertSame(self::demoFingerprint(), Db::fingerprint($target), "A4 base de « $target » ≠ scénario demo");
        }
    }
}
