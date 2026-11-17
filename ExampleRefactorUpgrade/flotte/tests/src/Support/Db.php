<?php

declare(strict_types=1);

namespace Flotte\Tests\Support;

/**
 * Lecture SQLite de la base d'une cible (PDO direct, jamais le code de l'application).
 * Le dump canonique est **le même** que celui de bin/seed.php (empreinte des fichiers *.sqlite.sha256) :
 * schéma trié + lignes triées par id, valeurs passées par quote() pour conserver les types SQLite.
 */
final class Db
{
    public const TABLES = [
        'vehicle' => ['id', 'plate', 'brand', 'model', 'mileage', 'status', 'created_at'],
        'reservation' => ['id', 'vehicle_id', 'driver_name', 'start_at', 'end_at', 'status', 'start_km', 'end_km', 'cost'],
    ];

    public static function pdo(string $file): \PDO
    {
        if (!is_file($file)) {
            throw new \RuntimeException("Base absente : $file (volume monté ?)");
        }

        return new \PDO('sqlite:'.$file, null, null, [\PDO::ATTR_ERRMODE => \PDO::ERRMODE_EXCEPTION]);
    }

    public static function canonicalDump(string $file): string
    {
        $pdo = self::pdo($file);
        $out = [];
        foreach ($pdo->query("SELECT type, name, sql FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' ORDER BY type, name") as $r) {
            $out[] = "{$r['type']} {$r['name']}: ".preg_replace('/\s+/', ' ', (string) $r['sql']);
        }
        foreach (self::TABLES as $table => $cols) {
            $expr = implode(" || '|' || ", array_map(static fn (string $c): string => "quote($c)", $cols));
            foreach ($pdo->query("SELECT $expr AS line FROM $table ORDER BY id") as $r) {
                $out[] = "$table: {$r['line']}";
            }
        }

        return implode("\n", $out)."\n";
    }

    public static function fingerprintOfFile(string $file): string
    {
        return hash('sha256', self::canonicalDump($file));
    }

    /** Empreinte du contenu logique de la base de la cible. */
    public static function fingerprint(string $target): string
    {
        return self::fingerprintOfFile(Target::db($target));
    }

    public static function dump(string $target): string
    {
        return self::canonicalDump(Target::db($target));
    }

    /**
     * @param array<int|string, mixed> $params
     *
     * @return list<array<string, mixed>>
     */
    public static function query(string $target, string $sql, array $params = []): array
    {
        $stmt = self::pdo(Target::db($target))->prepare($sql);
        $stmt->execute($params);

        return $stmt->fetchAll(\PDO::FETCH_ASSOC);
    }
}
