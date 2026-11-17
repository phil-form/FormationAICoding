<?php

declare(strict_types=1);

namespace Flotte\Tests\Support;

/**
 * Journal PHP d'une cible (/var/log/php/error.log dans le conteneur applicatif). Le conteneur tests n'a pas le
 * volume des journaux : lecture par `docker exec <conteneur> …` (socket Docker monté).
 */
final class PhpLog
{
    public const FILE = '/var/log/php/error.log';

    /** Taille actuelle du journal (0 s'il n'existe pas encore). */
    public static function offset(string $target): int
    {
        [$code, $out] = self::exec($target, ['sh', '-c', 'if [ -f '.self::FILE.' ]; then stat -c %s '.self::FILE.'; else echo 0; fi']);
        if (0 !== $code || !preg_match('/^\d+$/', trim($out))) {
            throw new \RuntimeException("Journal PHP de « $target » illisible : $out");
        }

        return (int) trim($out);
    }

    /** Lignes ajoutées depuis $offset (octets). */
    public static function since(string $target, int $offset): string
    {
        [$code, $out] = self::exec($target, ['sh', '-c', 'if [ -f '.self::FILE.' ]; then tail -c +'.($offset + 1).' '.self::FILE.'; fi']);
        if (0 !== $code) {
            throw new \RuntimeException("Journal PHP de « $target » illisible : $out");
        }

        return $out;
    }

    /**
     * Signatures (type + message, sans horodatage ni chemin) des entrées ajoutées depuis $offset.
     *
     * @return list<string>
     */
    public static function signaturesSince(string $target, int $offset): array
    {
        $signatures = [];
        foreach (explode("\n", self::since($target, $offset)) as $line) {
            $sig = Normalizer::phpLogSignature($line);
            if (null !== $sig && '' !== $sig) {
                $signatures[] = $sig;
            }
        }

        return $signatures;
    }

    /**
     * @param list<string> $command
     *
     * @return array{0: int, 1: string}
     */
    private static function exec(string $target, array $command): array
    {
        $cmd = array_merge(['docker', 'exec', Target::container($target)], $command);
        $proc = proc_open($cmd, [1 => ['pipe', 'w'], 2 => ['pipe', 'w']], $pipes);
        if (!\is_resource($proc)) {
            throw new \RuntimeException('docker exec impossible (proc_open)');
        }
        $out = (string) stream_get_contents($pipes[1]);
        $err = (string) stream_get_contents($pipes[2]);
        fclose($pipes[1]);
        fclose($pipes[2]);
        $code = proc_close($proc);

        return [$code, 0 === $code ? $out : $out.$err];
    }
}
