<?php

declare(strict_types=1);

namespace Flotte\Tests\Support;

/**
 * Exécution d'une commande console de la cible (cf. skill write-cli-tests) :
 * CONSOLE_APP_CMD / CONSOLE_REF_CMD (ex. « docker exec -i flotte-mig-app sf-console »).
 */
final class Console
{
    /**
     * @param list<string> $args
     *
     * @return array{exit: int, stdout: string, stderr: string}
     */
    public static function run(string $target, array $args, ?string $stdin = null): array
    {
        $prefix = Target::env(Target::APP === Target::check($target) ? 'CONSOLE_APP_CMD' : 'CONSOLE_REF_CMD')
            ?? 'docker exec -i '.Target::container($target).' sf-console';
        $cmd = array_merge(preg_split('/\s+/', trim($prefix)) ?: [], $args);
        $proc = proc_open($cmd, [0 => ['pipe', 'r'], 1 => ['pipe', 'w'], 2 => ['pipe', 'w']], $pipes);
        if (!\is_resource($proc)) {
            throw new \RuntimeException('Console : proc_open impossible');
        }
        if (null !== $stdin) {
            fwrite($pipes[0], $stdin);
        }
        fclose($pipes[0]);
        $stdout = (string) stream_get_contents($pipes[1]);
        $stderr = (string) stream_get_contents($pipes[2]);
        fclose($pipes[1]);
        fclose($pipes[2]);

        return ['exit' => proc_close($proc), 'stdout' => $stdout, 'stderr' => $stderr];
    }
}
