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
     * Extrait **la seule ligne de message** d'un bloc d'exception de la console (tmp/tests-application.md § 6.1),
     * indépendamment de l'habillage produit par le framework, qui n'est pas contractuel :
     * - Symfony 2.8 : encadré « ligne de remplissage / [Classe] / message / remplissage » ;
     * - Symfony 3.4 : « In <fichier> line <n>: » (exceptions PHP) puis encadré « remplissage / message / remplissage » ;
     * - rappel de syntaxe après le bloc.
     * Règle : lignes de l'encadré (entre la première ligne de remplissage — blancs seuls, non vide — et la ligne blanche
     * suivante), sans titre « [Classe] », rognées et jointes par une espace. Sans encadré : premier bloc non vide, sans
     * titre « [Classe] » ni localisation « In … line N: ».
     */
    public static function exceptionMessage(string $stderr): string
    {
        $lines = explode("\n", str_replace(["\r\n", "\r"], "\n", $stderr));
        $isTitle = static fn (string $text): bool => 1 === preg_match('/^\[[^\]]+\]$/', $text);
        $isLocation = static fn (string $text): bool => 1 === preg_match('/^In \S.* line \d+:$/', $text);

        foreach ($lines as $i => $line) {
            if ('' !== $line && '' === trim($line)) {
                $message = [];
                for ($j = $i + 1; $j < \count($lines) && '' !== trim($lines[$j]); ++$j) {
                    $text = trim($lines[$j]);
                    if (!$isTitle($text)) {
                        $message[] = $text;
                    }
                }
                if ([] !== $message) {
                    return implode(' ', $message);
                }
                break;
            }
        }

        $message = [];
        foreach ($lines as $line) {
            $text = trim($line);
            if ('' === $text) {
                if ([] !== $message) {
                    break;
                }
                continue;
            }
            if (!$isTitle($text) && !$isLocation($text)) {
                $message[] = $text;
            }
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
