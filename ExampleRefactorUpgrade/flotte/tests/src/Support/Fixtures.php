<?php

declare(strict_types=1);

namespace Flotte\Tests\Support;

/**
 * Restauration d'un scénario figé dans la base d'une cible, puis vérification de son empreinte.
 * Bases figées : fixtures/db/<scenario>.sqlite (+ .sha256), cf. fixtures/README.md.
 * Un scénario sans base figée versionnée (volume) est construit par bin/seed.php à partir de son YAML.
 */
final class Fixtures
{
    /** @var array<string, string> base construite à la volée, par scénario (durée du processus) */
    private static array $built = [];

    public static function dir(): string
    {
        return Target::path(Target::env('FIXTURES_DIR') ?? 'fixtures');
    }

    /**
     * Copie le scénario dans la base de la cible et renvoie l'empreinte attendue (contenu logique).
     */
    public static function restore(string $scenario, string $target): string
    {
        Target::check($target);
        $frozen = self::dir()."/db/$scenario.sqlite";
        $shaFile = "$frozen.sha256";
        $expected = is_file($shaFile) ? trim((string) file_get_contents($shaFile)) : null;

        $source = is_file($frozen) ? $frozen : self::buildOnTheFly($scenario);
        $expected ??= Db::fingerprintOfFile($source);

        $dest = Target::db($target);
        if (!is_dir(\dirname($dest)) || !is_writable(\dirname($dest))) {
            throw new \RuntimeException("Base de la cible « $target » non accessible en écriture : $dest");
        }
        $tmp = $dest.'.restore-tmp';
        if (!copy($source, $tmp)) {
            throw new \RuntimeException("Copie impossible : $source → $tmp");
        }
        foreach (['-journal', '-wal', '-shm'] as $suffix) {
            @unlink($dest.$suffix);
        }
        rename($tmp, $dest);

        $actual = Db::fingerprint($target);
        if ($actual !== $expected) {
            throw new \RuntimeException("Scénario « $scenario » restauré sur « $target » : empreinte $actual ≠ attendue $expected");
        }

        return $expected;
    }

    /** Construit une base non versionnée (ex. volume) via bin/seed.php, une seule fois par processus. */
    private static function buildOnTheFly(string $scenario): string
    {
        if (isset(self::$built[$scenario]) && is_file(self::$built[$scenario])) {
            return self::$built[$scenario];
        }
        $file = sys_get_temp_dir()."/fixtures-$scenario-".getmypid().'.sqlite';
        // seed.php construit la base dans sys_get_temp_dir()/seed-<s>.sqlite puis la copie dans la cible : on la
        // charge dans une cible fictive (fichier temporaire) pour ne garder que la base construite.
        $cmd = [PHP_BINARY, Target::path('bin/seed.php'), $scenario, '--target', 'app'];
        $env = getenv() + ['FIXTURES_DIR' => self::dir()];
        $env['APP_DB'] = $file;
        $proc = proc_open($cmd, [1 => ['pipe', 'w'], 2 => ['pipe', 'w']], $pipes, Target::testsRoot(), $env);
        if (!\is_resource($proc)) {
            throw new \RuntimeException("Construction du scénario « $scenario » impossible (proc_open)");
        }
        $out = stream_get_contents($pipes[1]).stream_get_contents($pipes[2]);
        fclose($pipes[1]);
        fclose($pipes[2]);
        if (0 !== proc_close($proc) || !is_file($file)) {
            throw new \RuntimeException("Construction du scénario « $scenario » en échec : $out");
        }

        return self::$built[$scenario] = $file;
    }
}
