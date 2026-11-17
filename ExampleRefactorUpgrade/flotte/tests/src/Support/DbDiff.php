<?php

declare(strict_types=1);

namespace Flotte\Tests\Support;

/**
 * A4 des routes qui écrivent (tmp/tests-application.md § 3.1) : diff **exact** de la base entre deux instantanés.
 * Valeurs passées par quote() (types SQLite conservés : `35` INTEGER ≠ `35.0` REAL ≠ `'35'` TEXT).
 *
 * Format d'une ligne de diff (ordre : tables de Db::TABLES, puis id, puis colonnes) :
 *   + reservation: 11|1|'Jean Test'|'2026-02-20 08:00:00'|'2026-02-20 18:00:00'|'confirmee'|10000|NULL|NULL
 *   ~ reservation 1 status: 'confirmee' → 'terminee'
 *   - vehicle: 3|'1-BOK-003'|…
 */
final class DbDiff
{
    /**
     * @return array<string, array<string, array<string, string>>> table → id → colonne → valeur quote()
     */
    public static function snapshot(string $target): array
    {
        $pdo = Db::pdo(Target::db($target));
        $out = [];
        foreach (Db::TABLES as $table => $cols) {
            $out[$table] = [];
            $select = implode(', ', array_map(static fn (string $c): string => "quote($c) AS $c", $cols));
            foreach ($pdo->query("SELECT $select FROM $table ORDER BY id") as $row) {
                $values = [];
                foreach ($cols as $c) {
                    $values[$c] = (string) $row[$c];
                }
                $out[$table][$values['id']] = $values;
            }
        }

        return $out;
    }

    /**
     * @param array<string, array<string, array<string, string>>> $before
     * @param array<string, array<string, array<string, string>>> $after
     *
     * @return list<string>
     */
    public static function between(array $before, array $after): array
    {
        $lines = [];
        foreach (array_keys(Db::TABLES) as $table) {
            $b = $before[$table] ?? [];
            $a = $after[$table] ?? [];
            $ids = array_unique(array_merge(array_keys($b), array_keys($a)));
            usort($ids, static fn ($x, $y): int => ((int) $x <=> (int) $y) ?: strcmp((string) $x, (string) $y));
            foreach ($ids as $id) {
                if (!isset($b[$id])) {
                    $lines[] = "+ $table: ".implode('|', $a[$id]);
                } elseif (!isset($a[$id])) {
                    $lines[] = "- $table: ".implode('|', $b[$id]);
                } else {
                    foreach ($b[$id] as $col => $value) {
                        if ($a[$id][$col] !== $value) {
                            $lines[] = "~ $table $id $col: $value → {$a[$id][$col]}";
                        }
                    }
                }
            }
        }

        return $lines;
    }
}
