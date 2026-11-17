<?php

declare(strict_types=1);

namespace Flotte\Tests\Support;

/**
 * Client HTTP minimal et neutre (flux PHP, HTTP/1.1, Connection: close) :
 * - chemin et chaîne de requête envoyés **tels quels** (aucun ré-encodage : `?status=%201`, `status%5B%5D=1`) ;
 * - aucune redirection suivie ;
 * - cookies conservés pour la durée de vie de l'instance (une instance = une session) ;
 * - Basic auth optionnelle ; lecture d'un jeton CSRF depuis un formulaire.
 */
final class HttpClient
{
    /** @var array<string, string> */
    private array $cookies = [];
    private ?string $authorization = null;

    public function __construct(private readonly string $baseUrl)
    {
    }

    public static function for(string $target): self
    {
        return new self(Target::url($target));
    }

    public function withBasicAuth(string $user, string $password): self
    {
        $this->authorization = 'Basic '.base64_encode("$user:$password");

        return $this;
    }

    public function get(string $path, array $headers = []): HttpResponse
    {
        return $this->request('GET', $path, $headers);
    }

    /**
     * @param array<string, string> $headers
     */
    public function request(string $method, string $path, array $headers = [], ?string $body = null): HttpResponse
    {
        $lines = ['Connection: close', 'User-Agent: flotte-characterization-tests'];
        // Accept par défaut, sauf si l'appelant en fournit un (négociation de contenu, BH-219) : jamais deux en-têtes.
        if (!isset(array_change_key_case($headers)['accept'])) {
            $lines[] = 'Accept: text/html,application/json;q=0.9,*/*;q=0.8';
        }
        if (null !== $this->authorization) {
            $lines[] = 'Authorization: '.$this->authorization;
        }
        if ([] !== $this->cookies) {
            $lines[] = 'Cookie: '.implode('; ', array_map(static fn (string $k, string $v): string => "$k=$v", array_keys($this->cookies), $this->cookies));
        }
        foreach ($headers as $name => $value) {
            $lines[] = "$name: $value";
        }
        if (null !== $body && !isset(array_change_key_case($headers)['content-type'])) {
            $lines[] = 'Content-Type: application/x-www-form-urlencoded';
        }
        if (null !== $body || \in_array($method, ['POST', 'PUT', 'PATCH'], true)) {
            $lines[] = 'Content-Length: '.\strlen($body ?? '');
        }

        $context = stream_context_create(['http' => [
            'method' => $method,
            'header' => implode("\r\n", $lines),
            'content' => $body ?? '',
            'ignore_errors' => true,
            'follow_location' => 0,
            'max_redirects' => 0,
            'protocol_version' => 1.1,
            'timeout' => 60,
        ]]);

        $start = hrtime(true);
        $stream = @fopen($this->baseUrl.$path, 'r', false, $context);
        if (false === $stream) {
            $error = error_get_last()['message'] ?? 'erreur inconnue';
            throw new \RuntimeException("Requête impossible : $method {$this->baseUrl}$path ($error)");
        }
        $content = 'HEAD' === $method ? '' : (string) stream_get_contents($stream);
        $meta = stream_get_meta_data($stream);
        fclose($stream);
        $duration = (hrtime(true) - $start) / 1e6;

        /** @var list<string> $raw */
        $raw = $meta['wrapper_data'] ?? [];
        [$status, $headers] = self::parseHeaders($raw);
        foreach ($headers['set-cookie'] ?? [] as $cookie) {
            $pair = trim(explode(';', $cookie, 2)[0]);
            if (str_contains($pair, '=')) {
                [$k, $v] = explode('=', $pair, 2);
                $this->cookies[$k] = $v;
            }
        }

        return new HttpResponse($method, $path, $status, $headers, $content, $duration);
    }

    /** Jeton CSRF lu dans le premier champ `*[_token]` ou `_token` d'une page. */
    public function csrfFrom(string $path): ?string
    {
        $html = $this->get($path)->body;
        if (preg_match('/name="(?:[a-z_]+\[)?_token\]?"[^>]*value="([^"]*)"/i', $html, $m)) {
            return html_entity_decode($m[1]);
        }

        return null;
    }

    /**
     * @param list<string> $raw
     *
     * @return array{0: int, 1: array<string, list<string>>}
     */
    private static function parseHeaders(array $raw): array
    {
        $status = 0;
        $headers = [];
        foreach ($raw as $line) {
            if (preg_match('#^HTTP/\S+\s+(\d{3})#', $line, $m)) {
                // Nouvelle réponse (ex. 100 Continue) : on ne garde que la dernière.
                $status = (int) $m[1];
                $headers = [];
                continue;
            }
            if (!str_contains($line, ':')) {
                continue;
            }
            [$name, $value] = explode(':', $line, 2);
            $headers[strtolower(trim($name))][] = trim($value);
        }

        return [$status, $headers];
    }
}
