<?php

declare(strict_types=1);

namespace Flotte\Tests\Support;

/**
 * Exécution et vérifications communes des commandes console (tmp/tests-application.md § 6.1), partagées par
 * ConsoleTestCase (suites cli, edge) et ConsoleDiffTestCase (suite diff).
 *
 * Act : une exécution sans terminal via CONSOLE_APP_CMD / CONSOLE_REF_CMD ; si la variable de la cible est absente,
 * le test est marqué incomplet (jamais vert).
 */
trait ConsoleCommands
{
    /** Valeur substituée au défaut de --month (horloge du conteneur) dans les goldens d'aide (BH-425, § 6.5). */
    protected const MONTH_DEFAULT_PLACEHOLDER = '<mois courant>';

    /**
     * Exécute `sf-console <args>` sur la cible et relève le journal PHP ajouté pendant l'exécution.
     *
     * @param list<string> $args
     */
    protected function console(array $args, ?string $target = null): ConsoleRun
    {
        $target ??= $this->target();
        $var = Target::APP === Target::check($target) ? 'CONSOLE_APP_CMD' : 'CONSOLE_REF_CMD';
        if (null === Target::env($var)) {
            self::markTestIncomplete("Variable $var absente : commande console de « $target » inconnue (service tests de docker-compose.yml).");
        }
        $offset = PhpLog::offset($target);
        $t0 = hrtime(true);
        $r = Console::run($target, $args);
        $seconds = (hrtime(true) - $t0) / 1e9;

        return new ConsoleRun($target, $args, $r['exit'], $r['stdout'], $r['stderr'], PhpLog::signaturesSince($target, $offset), $seconds);
    }

    /**
     * Durée d'exécution seule (sans relevé du journal), pour les comparaisons de performance.
     *
     * @param list<string> $args
     */
    protected function consoleSeconds(array $args, string $target): float
    {
        $t0 = hrtime(true);
        Console::run($target, $args);

        return (hrtime(true) - $t0) / 1e9;
    }

    /**
     * A1 + A2 + A5 d'une exécution réussie : code 0, stderr vide, journal PHP attendu (vide par défaut).
     *
     * @param list<string> $log
     */
    protected function assertConsoleOk(ConsoleRun $run, array $log = []): void
    {
        self::assertSame(0, $run->exit, 'A1 code de retour — '.$run->describe());
        self::assertSame('', $run->stderr, 'A2 stderr vide — '.$run->describe());
        self::assertSame($log, $run->log, 'A5 journal PHP — '.$run->describe());
    }

    /** A1 + A2 d'un échec de la console : code attendu, stdout vide, message d'exception exact sur stderr. */
    protected function assertConsoleError(int $exit, string $message, ConsoleRun $run): void
    {
        self::assertSame($exit, $run->exit, 'A1 code de retour — '.$run->describe());
        self::assertSame($message, $run->errorMessage(), 'A2 message d\'exception sur stderr — '.$run->describe());
        self::assertSame('', $run->stdout, 'A3 stdout vide — '.$run->describe());
    }

    /** A3 : stdout comparé octet par octet au golden (aucune normalisation : fins de ligne et espaces contractuels). */
    protected function assertStdoutGolden(string $name, ConsoleRun $run): void
    {
        self::assertStringNotContainsString("\r", $run->stdout, 'A3 fins de ligne \n — '.$run->describe());
        Golden::assertMatchesGolden($name, $run->stdout, 'A3 stdout — '.$run->describe());
    }

    /**
     * Aide / liste (BH-425, BH-459) : rendu du framework, comparé après Normalizer::cli (fins de ligne, blancs de fin)
     * et remplacement du défaut de --month, qui dépend de l'horloge (proposition § 6.5).
     */
    protected static function helpText(string $stdout): string
    {
        $text = Normalizer::cli($stdout);

        return preg_replace('/(--month=MONTH\b.*\[default: ")\d{4}-\d{2}("\])/', '${1}'.self::MONTH_DEFAULT_PLACEHOLDER.'${2}', $text) ?? $text;
    }

    /**
     * Partie contractuelle de `list` / `help <commande>` depuis ACC-002 (indépendante de la version du framework) :
     * `list` → commandes fleet:* (nom → description) ; `help` → synopsis, arguments, options propres à la commande
     * (défaut de --month normalisé) et texte d'aide. Voir AcceptedChanges::fleetCommands / commandHelpContract.
     *
     * @param list<string> $args
     *
     * @return array<string, mixed>
     */
    protected static function helpContract(array $args, string $stdout): array
    {
        return 'list' === ($args[0] ?? null)
            ? AcceptedChanges::fleetCommands($stdout)
            : AcceptedChanges::commandHelpContract(self::helpText($stdout));
    }

    /**
     * Lignes de données du tableau du rapport : [plaque, réservations, km, coût], TOTAL compris, dans l'ordre.
     *
     * @return list<list<string>>
     */
    protected static function reportRows(string $stdout): array
    {
        $rows = [];
        foreach (explode("\n", $stdout) as $line) {
            if (!str_starts_with($line, '|')) {
                continue;
            }
            $cells = array_map('trim', explode('|', trim($line, '|')));
            if ('Plaque' === $cells[0]) {
                continue;
            }
            $rows[] = $cells;
        }

        return $rows;
    }

    /** @return list<string> plaques du rapport, dans l'ordre, sans la ligne TOTAL */
    protected static function reportPlates(string $stdout): array
    {
        return array_values(array_filter(array_column(self::reportRows($stdout), 0), static fn (string $p): bool => 'TOTAL' !== $p));
    }

    /** @return array{0: string, 1: string} [km, coût] de la ligne TOTAL */
    protected static function reportTotal(string $stdout): array
    {
        foreach (self::reportRows($stdout) as $row) {
            if ('TOTAL' === $row[0]) {
                return [$row[2], $row[3]];
            }
        }

        throw new \RuntimeException("Ligne TOTAL absente du rapport :\n$stdout");
    }

    /** Première ligne de la sortie (titre du rapport). */
    protected static function firstLine(string $stdout): string
    {
        return explode("\n", $stdout, 2)[0];
    }

    /** Sortie sans sa première ligne (tableau du rapport). */
    protected static function withoutFirstLine(string $stdout): string
    {
        return explode("\n", $stdout, 2)[1] ?? '';
    }

    /** Empreinte versionnée du scénario demo (A4 de fleet:demo:load). */
    protected static function demoFingerprint(): string
    {
        return trim((string) file_get_contents(Fixtures::dir().'/db/demo.sqlite.sha256'));
    }

    /** Contenu d'un golden existant, lu sans jamais l'enregistrer (comparaison à la sortie d'une autre fiche). */
    protected static function existingGolden(string $name): string
    {
        $file = Golden::dir().'/'.$name;
        if (!is_file($file)) {
            self::fail("Golden absent : golden/$name (enregistré par la fiche d'origine).");
        }

        return (string) file_get_contents($file);
    }
}
