<?php

declare(strict_types=1);

/*
 * Chargeur de scénarios de données (conteneur "tests", PHP 8.4) — appelé par .claude/bin/seed.sh.
 *
 *   php bin/seed.php list
 *   php bin/seed.php <scenario|all> [--target app|ref|both] [--verify] [--rebuild] [--dump]
 *
 *   (sans option)  copie fixtures/db/<scenario>.sqlite dans la base de la cible (défaut : both)
 *   --rebuild      reconstruit fixtures/db/<scenario>.sqlite (+ .sha256) depuis schema.sql + <scenario>.yaml
 *   --verify       sans --target : vérifie que la base figée correspond au YAML ;
 *                  avec --target : vérifie que la base de la cible correspond au scénario
 *   --dump         affiche le dump canonique (contenu logique) de la base figée
 *
 * Neutre vis-à-vis de la version de l'application : PDO SQLite direct, aucun code applicatif.
 * Empreinte = SHA-256 du dump canonique (schéma + lignes triées par id, valeurs passées par quote() pour
 * conserver les types SQLite), pas des octets du fichier.
 */

use Symfony\Component\Yaml\Yaml;

$root = dirname(__DIR__);
$autoload = $root.'/vendor/autoload.php';
if (!is_file($autoload)) {
    fwrite(STDERR, "✘ tests/vendor absent : lancer « .claude/bin/env.sh build » puis composer install dans tests/ (voir tests/fixtures/README.md).\n");
    exit(3);
}
require $autoload;

const TABLES = [
    'vehicle' => ['id', 'plate', 'brand', 'model', 'mileage', 'status', 'created_at'],
    'reservation' => ['id', 'vehicle_id', 'driver_name', 'start_at', 'end_at', 'status', 'start_km', 'end_km', 'cost'],
];
const INT_COLUMNS = ['id', 'mileage', 'status', 'vehicle_id', 'start_km', 'end_km'];

$fixtures = getenv('FIXTURES_DIR') ?: $root.'/fixtures';
$targets = [
    'app' => getenv('APP_DB') ?: '/data/app/flotte.sqlite',
    'ref' => getenv('REF_DB') ?: '/data/ref/flotte.sqlite',
];

function fail(string $message, int $code = 1): never
{
    fwrite(STDERR, "✘ $message\n");
    exit($code);
}

function info(string $message): void
{
    fwrite(STDERR, "› $message\n");
}

/** @return list<string> */
function scenarios(string $fixtures): array
{
    $list = array_map(static fn (string $f): string => basename($f, '.yaml'), glob($fixtures.'/*.yaml') ?: []);
    sort($list);

    return $list;
}

/** @return array<string, mixed> */
function loadScenario(string $fixtures, string $name): array
{
    $file = "$fixtures/$name.yaml";
    if (!is_file($file)) {
        fail("Scénario inconnu : $name (".implode(', ', scenarios($fixtures)).')');
    }
    $data = Yaml::parseFile($file);
    if (!is_array($data) || ($data['scenario'] ?? null) !== $name) {
        fail("$file : clé « scenario: $name » attendue en tête de fichier.");
    }
    $data['vehicles'] ??= [];
    $data['reservations'] ??= [];
    if (isset($data['generate'])) {
        [$v, $r] = generate($data['generate']);
        $data['vehicles'] = array_merge($data['vehicles'], $v);
        $data['reservations'] = array_merge($data['reservations'], $r);
    }

    return $data;
}

/**
 * Génération déterministe (scénario volume) : même graine → mêmes données.
 *
 * @param array<string, mixed> $g
 *
 * @return array{0: list<array<string, mixed>>, 1: list<array<string, mixed>>}
 */
function generate(array $g): array
{
    mt_srand((int) ($g['seed'] ?? 20260115), MT_RAND_MT19937);
    $brands = $g['brands'] ?? [['Renault', 'Kangoo'], ['Peugeot', 'Partner'], ['Citroën', 'Berlingo'], ['Ford', 'Transit']];
    $drivers = $g['drivers'] ?? ['Jean Dupont', 'Marie Lambert'];
    $count = (int) ($g['vehicles'] ?? 100);
    [$minRes, $maxRes] = $g['reservations_per_vehicle'] ?? [0, 10];
    $from = new DateTimeImmutable($g['from'] ?? '2024-01-01 08:00:00');
    $now = new DateTimeImmutable($g['now'] ?? '2026-01-15 10:00:00');
    $vehicles = $reservations = [];
    $rid = (int) ($g['first_reservation_id'] ?? 1);
    $letters = 'ABCDEFGHJKLMNPRSTVWXYZ';
    for ($i = 0; $i < $count; ++$i) {
        $vid = (int) ($g['first_vehicle_id'] ?? 1) + $i;
        $plate = sprintf('%d-%s%s%s-%03d', 1 + intdiv($i, 10648) % 9, $letters[intdiv($i, 484) % 22],
            $letters[intdiv($i, 22) % 22], $letters[$i % 22], $i % 1000);
        [$brand, $model] = $brands[mt_rand(0, count($brands) - 1)];
        $km = mt_rand(0, 50000);
        $status = [0, 0, 0, 0, 0, 0, 0, 1, 2][mt_rand(0, 8)];
        $cursor = $from->modify('+'.mt_rand(0, 300).' days');
        $n = mt_rand((int) $minRes, (int) $maxRes);
        for ($j = 0; $j < $n; ++$j) {
            $start = $cursor->setTime(mt_rand(6, 10), [0, 15, 30, 45][mt_rand(0, 3)]);
            $end = $start->modify('+'.mt_rand(1, 60).' hours');
            $cursor = $end->modify('+'.mt_rand(0, 20).' days');
            $row = ['id' => $rid++, 'vehicle_id' => $vid, 'driver_name' => $drivers[mt_rand(0, count($drivers) - 1)],
                'start_at' => $start->format('Y-m-d H:i:s'), 'end_at' => $end->format('Y-m-d H:i:s'), 'start_km' => $km];
            if ($end < $now && mt_rand(0, 9) > 0) {
                $dist = mt_rand(0, 900);
                $km += $dist;
                $row += ['status' => 'terminee', 'end_km' => $km, 'cost' => number_format($dist * 0.35, 2, '.', '')];
            } else {
                $row += ['status' => $end < $now ? 'annulee' : 'confirmee', 'end_km' => null, 'cost' => null];
            }
            $reservations[] = $row;
        }
        $vehicles[] = ['id' => $vid, 'plate' => $plate, 'brand' => $brand, 'model' => $model, 'mileage' => $km,
            'status' => $status, 'created_at' => $from->modify('-'.mt_rand(0, 900).' days')->format('Y-m-d 08:00:00')];
    }

    return [$vehicles, $reservations];
}

function schemaSql(string $fixtures): string
{
    $sql = @file_get_contents("$fixtures/schema.sql");
    if (false === $sql) {
        fail("$fixtures/schema.sql absent (capturé en step-00 par /baseline).");
    }

    return preg_replace('/^--.*$/m', '', $sql) ?? '';
}

/** Construit la base d'un scénario dans $file (écrasé). */
function build(string $fixtures, string $name, string $file): void
{
    $data = loadScenario($fixtures, $name);
    @unlink($file);
    $pdo = new PDO('sqlite:'.$file, null, null, [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION]);
    $pdo->exec('PRAGMA foreign_keys = ON');
    $pdo->exec(schemaSql($fixtures));
    $pdo->beginTransaction();
    foreach (['vehicle' => $data['vehicles'], 'reservation' => $data['reservations']] as $table => $rows) {
        $cols = TABLES[$table];
        $stmt = $pdo->prepare(sprintf('INSERT INTO %s (%s) VALUES (%s)', $table, implode(', ', $cols),
            implode(', ', array_fill(0, count($cols), '?'))));
        foreach ($rows as $i => $row) {
            $unknown = array_diff(array_keys($row), $cols, ['note', 'tags']);
            if ($unknown) {
                fail("$name.yaml, $table[$i] : colonne(s) inconnue(s) ".implode(', ', $unknown));
            }
            foreach ($cols as $k => $col) {
                if (!array_key_exists($col, $row) && !in_array($col, ['end_km', 'cost'], true)) {
                    fail("$name.yaml, $table[$i] (id ".($row['id'] ?? '?').") : colonne « $col » manquante");
                }
                $v = $row[$col] ?? null;
                // Les entiers passent en INTEGER ; tout le reste en texte, comme le ferait PDO depuis Doctrine
                // (l'affinité NUMERIC de SQLite convertit alors "73.50" en 73.5, exactement comme l'application).
                $type = null === $v ? PDO::PARAM_NULL : (in_array($col, INT_COLUMNS, true) && is_int($v) ? PDO::PARAM_INT : PDO::PARAM_STR);
                $stmt->bindValue($k + 1, null === $v ? null : (PDO::PARAM_INT === $type ? $v : (string) $v), $type);
            }
            try {
                $stmt->execute();
            } catch (PDOException $e) {
                fail("$name.yaml, $table id ".($row['id'] ?? '?').' : '.$e->getMessage());
            }
        }
    }
    $pdo->commit();
    $pdo->exec('VACUUM');
}

/** Dump canonique : schéma trié + lignes triées par id, valeurs typées (quote()). */
function canonicalDump(string $file): string
{
    if (!is_file($file)) {
        fail("Base absente : $file");
    }
    $pdo = new PDO('sqlite:'.$file, null, null, [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION]);
    $out = [];
    foreach ($pdo->query("SELECT type, name, sql FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' ORDER BY type, name") as $r) {
        $out[] = "{$r['type']} {$r['name']}: ".preg_replace('/\s+/', ' ', (string) $r['sql']);
    }
    foreach (TABLES as $table => $cols) {
        $expr = implode(" || '|' || ", array_map(static fn (string $c): string => "quote($c)", $cols));
        foreach ($pdo->query("SELECT $expr AS line FROM $table ORDER BY id") as $r) {
            $out[] = "$table: {$r['line']}";
        }
    }

    return implode("\n", $out)."\n";
}

function hashOf(string $file): string
{
    return hash('sha256', canonicalDump($file));
}

function copyAtomic(string $from, string $to): void
{
    $dir = dirname($to);
    if (!is_dir($dir) || !is_writable($dir)) {
        fail("Dossier cible non accessible en écriture : $dir (volume monté ?)");
    }
    $tmp = $to.'.seed-tmp';
    if (!copy($from, $tmp)) {
        fail("Copie impossible vers $tmp");
    }
    foreach (['-journal', '-wal', '-shm'] as $suffix) {
        @unlink($to.$suffix);
    }
    rename($tmp, $to);
}

// --- Ligne de commande ----------------------------------------------------------------------------------------

$args = array_slice($argv, 1);
$name = array_shift($args) ?? 'list';
$target = null;
$verify = $rebuild = $dump = false;
while ($args) {
    $a = array_shift($args);
    match ($a) {
        '--target' => $target = array_shift($args) ?? fail('--target sans valeur'),
        '--verify' => $verify = true,
        '--rebuild' => $rebuild = true,
        '--dump' => $dump = true,
        default => fail("Option inconnue : $a"),
    };
}
if (null !== $target && !in_array($target, ['app', 'ref', 'both'], true)) {
    fail("Cible inconnue : $target (app|ref|both)");
}

if ('list' === $name) {
    foreach (scenarios($fixtures) as $s) {
        $d = loadScenario($fixtures, $s);
        printf("%-14s %5d véhicules %6d réservations  %s\n", $s, count($d['vehicles']), count($d['reservations']),
            strtok((string) ($d['description'] ?? ''), "\n"));
    }
    exit(0);
}

$names = 'all' === $name ? array_values(array_diff(scenarios($fixtures), ['volume'])) : [$name];
if ('all' === $name && null !== $target && !$verify) {
    fail('« all » ne peut pas être chargé dans une cible (une seule base par cible) : choisir un scénario.');
}
@mkdir("$fixtures/db", 0775, true);
$status = 0;

foreach ($names as $s) {
    $db = "$fixtures/db/$s.sqlite";
    $sha = "$db.sha256";

    if ($rebuild) {
        build($fixtures, $s, $db);
        file_put_contents($sha, hashOf($db)."\n");
        info("$s : base figée reconstruite (".substr(trim((string) file_get_contents($sha)), 0, 12).'…)');
        if (null === $target && !$verify && !$dump) {
            continue; // --rebuild seul : ne charge rien dans les bases des applications
        }
    }
    if (!is_file($db)) {
        // Base figée absente (ex. volume, non versionné) : construction à la volée, sans toucher l'empreinte.
        $db = sys_get_temp_dir()."/seed-$s.sqlite";
        build($fixtures, $s, $db);
        if (!is_file($sha)) {
            info("$s : pas d'empreinte de référence (lancer --rebuild pour la créer)");
        }
    }
    if ($dump) {
        echo canonicalDump($db);
        continue;
    }

    if ($verify && null === $target) {
        $tmp = sys_get_temp_dir()."/verify-$s.sqlite";
        build($fixtures, $s, $tmp);
        $expected = is_file($sha) ? trim((string) file_get_contents($sha)) : null;
        $fromYaml = hashOf($tmp);
        $frozen = hashOf($db);
        $ok = $fromYaml === $frozen && (null === $expected || $expected === $frozen);
        printf("%s %-14s %s\n", $ok ? '✔' : '✘', $s, $ok ? 'base figée conforme au YAML' : 'DÉRIVE : base figée ≠ YAML ou empreinte');
        $status |= $ok ? 0 : 1;
        continue;
    }

    foreach ('both' === ($target ?? 'both') ? ['app', 'ref'] : [$target] as $t) {
        $path = $targets[$t];
        if ($verify) {
            $expected = is_file($sha) ? trim((string) file_get_contents($sha)) : hashOf($db);
            $ok = hashOf($path) === $expected;
            printf("%s %-4s %-14s %s\n", $ok ? '✔' : '✘', $t, $s, $ok ? 'base conforme au scénario' : 'base différente du scénario');
            $status |= $ok ? 0 : 1;
        } else {
            if (!is_dir(dirname($path))) {
                info("$t : volume non monté ($path), ignoré");
                continue;
            }
            copyAtomic($db, $path);
            info("$t : scénario « $s » chargé dans $path");
        }
    }
}
exit($status);
