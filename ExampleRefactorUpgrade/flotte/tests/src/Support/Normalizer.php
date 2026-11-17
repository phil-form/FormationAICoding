<?php

declare(strict_types=1);

namespace Flotte\Tests\Support;

/**
 * Normalisations **non métier** (chacune masque une différence non contractuelle, aucune ne masque un montant,
 * un ordre, un statut ou un libellé) :
 * - HTML : fins de ligne, indentation (blancs en début/fin de ligne), lignes vides, jetons CSRF.
 *   Les blancs **à l'intérieur** d'une ligne (texte, `<td> </td>`) sont conservés ; l'ordre des attributs aussi.
 * - JSON : rien par défaut (comparaison octet par octet) ; ré-encodage canonique seulement sur demande.
 * - CLI : fins de ligne, blancs de fin de ligne.
 */
final class Normalizer
{
    public const CSRF_PLACEHOLDER = '__CSRF__';

    public static function html(string $html): string
    {
        $html = str_replace(["\r\n", "\r"], "\n", $html);
        // Jetons CSRF (champ caché *[_token] ou _token) : valeur aléatoire par session.
        $html = preg_replace(
            '/(<input\b[^>]*\bname="(?:[^"]*\[)?_token\]?"[^>]*\bvalue=")[^"]*(")/i',
            '${1}'.self::CSRF_PLACEHOLDER.'${2}',
            $html,
        ) ?? $html;
        $html = preg_replace(
            '/(<input\b[^>]*\bvalue=")[^"]*("[^>]*\bname="(?:[^"]*\[)?_token\]?")/i',
            '${1}'.self::CSRF_PLACEHOLDER.'${2}',
            $html,
        ) ?? $html;
        $lines = array_map(static fn (string $l): string => trim($l, " \t"), explode("\n", $html));
        $lines = array_values(array_filter($lines, static fn (string $l): bool => '' !== $l));

        return implode("\n", $lines)."\n";
    }

    public static function json(string $json, bool $canonical = false): string
    {
        if (!$canonical) {
            return $json;
        }
        $decoded = json_decode($json, true, 512, JSON_THROW_ON_ERROR);

        return json_encode($decoded, JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE | JSON_PRESERVE_ZERO_FRACTION)."\n";
    }

    public static function cli(string $output): string
    {
        $output = str_replace(["\r\n", "\r"], "\n", $output);

        return implode("\n", array_map('rtrim', explode("\n", $output)));
    }

    /**
     * Ligne du journal PHP réduite à sa signature : sans horodatage, sans chemin ni numéro de ligne.
     * Ex. « [15-Jan-2026 10:00:01 Europe/Brussels] PHP Notice:  Undefined offset: 3 in /srv/app/… on line 42 »
     *   → « PHP Notice: Undefined offset: 3 ».
     */
    public static function phpLogSignature(string $line): ?string
    {
        $line = trim(preg_replace('/^\[[^\]]*\]\s*/', '', $line) ?? $line);
        if ('' === $line || str_starts_with($line, 'PHP Stack trace') || preg_match('/^PHP\s+\d+\./', $line)) {
            return null;
        }
        $line = preg_replace('/\s+in\s+\S+(?:\.php|\.twig)?(?:\(\d+\))?\s+on\s+line\s+\d+\s*$/', '', $line) ?? $line;
        $line = preg_replace('/\s+in\s+\S+:\d+$/', '', $line) ?? $line;
        $line = preg_replace('#(?:/[\w.\-]+)+/([\w.\-]+\.(?:php|twig))#', '$1', $line) ?? $line;

        return preg_replace('/\s+/', ' ', $line);
    }
}
