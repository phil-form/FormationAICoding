<?php

declare(strict_types=1);

namespace Flotte\Tests\Support;

use Symfony\Component\DomCrawler\Crawler;

/**
 * Réponse HTTP brute : statut, en-têtes (noms en minuscules, valeurs dans l'ordre reçu), corps, durée ; plus deux
 * faits sur la requête utiles aux attentes dépendant de l'étape (AcceptedChanges) : cible interrogée et envoi du
 * cookie de session.
 */
final class HttpResponse
{
    /**
     * @param array<string, list<string>> $headers
     */
    public function __construct(
        public readonly string $method,
        public readonly string $path,
        public readonly int $status,
        public readonly array $headers,
        public readonly string $body,
        public readonly float $durationMs,
        public readonly ?string $target = null,
        public readonly bool $sessionCookieSent = false,
    ) {
    }

    public function header(string $name): ?string
    {
        return $this->headers[strtolower($name)][0] ?? null;
    }

    /** @return list<string> */
    public function headerValues(string $name): array
    {
        return $this->headers[strtolower($name)] ?? [];
    }

    public function hasHeader(string $name): bool
    {
        return [] !== $this->headerValues($name);
    }

    /**
     * Cookie posé par la réponse : attributs normalisés (nom, attributs en minuscules, sans la valeur).
     *
     * @return list<string> ex. ["PHPSESSID; path=/; httponly"]
     */
    public function cookieShapes(): array
    {
        $shapes = [];
        foreach ($this->headerValues('set-cookie') as $line) {
            $parts = array_map('trim', explode(';', $line));
            $name = strstr((string) array_shift($parts), '=', true) ?: '?';
            $attrs = array_map(static fn (string $a): string => strtolower($a), array_filter($parts, static fn (string $a): bool => '' !== $a));
            $shapes[] = implode('; ', array_merge([$name], $attrs));
        }

        return $shapes;
    }

    public function crawler(): Crawler
    {
        return new Crawler($this->body, 'http://localhost'.$this->path);
    }

    public function describe(): string
    {
        return "$this->method $this->path → $this->status";
    }
}
