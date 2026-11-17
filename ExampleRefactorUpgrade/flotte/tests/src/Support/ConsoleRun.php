<?php

declare(strict_types=1);

namespace Flotte\Tests\Support;

/**
 * Résultat d'une exécution de commande console (tmp/tests-application.md § 6.1) :
 * A1 code de retour, A2 stderr, A3 stdout (octets bruts), A5 signatures du journal PHP ajoutées, durée mesurée.
 */
final class ConsoleRun
{
    /**
     * @param list<string> $args
     * @param list<string> $log
     */
    public function __construct(
        public readonly string $target,
        public readonly array $args,
        public readonly int $exit,
        public readonly string $stdout,
        public readonly string $stderr,
        public readonly array $log,
        public readonly float $seconds,
    ) {
    }

    /** Ligne du message d'exception de stderr (seule partie contractuelle de A2) ; '' si stderr est vide. */
    public function errorMessage(): string
    {
        return self::exceptionMessage($this->stderr);
    }

    /**
     * Extrait le message d'un bloc d'exception de la console : les lignes qui suivent la ligne « [Classe] » jusqu'à la
     * fin du bloc (ligne blanche), rognées et jointes par une espace. Sans ligne « [Classe] » (autre format de
     * console), premier bloc non vide. La classe d'exception et le rappel de syntaxe ne sont pas contractuels.
     */
    public static function exceptionMessage(string $stderr): string
    {
        $lines = explode("\n", str_replace(["\r\n", "\r"], "\n", $stderr));
        $start = null;
        foreach ($lines as $i => $line) {
            if (preg_match('/^\s*\[[^\]]+\]\s*$/', $line)) {
                $start = $i + 1;
                break;
            }
        }
        if (null === $start) {
            $start = 0;
            while ($start < \count($lines) && '' === trim($lines[$start])) {
                ++$start;
            }
        }
        $message = [];
        for ($i = $start; $i < \count($lines) && '' !== trim($lines[$i]); ++$i) {
            $message[] = trim($lines[$i]);
        }

        return implode(' ', $message);
    }

    public function describe(): string
    {
        return sprintf(
            "%s : sf-console %s → code %d\n--- stdout ---\n%s--- stderr ---\n%s",
            $this->target,
            implode(' ', array_map(static fn (string $a): string => '' === $a || preg_match('/\s/', $a) ? "'$a'" : $a, $this->args)),
            $this->exit,
            $this->stdout,
            $this->stderr,
        );
    }
}
