<?php

declare(strict_types=1);

namespace Flotte\Tests\Support;

use PHPUnit\Framework\Assert;

/**
 * Registre **unique**, côté tests, des changements de comportement acceptés (docs/migration/accepted-changes.md).
 *
 * Un changement accepté s'applique aux attentes **à partir de son étape** : une cible dont l'étape servie est
 * antérieure (app-ref = step-00) garde l'attente historique ; une cible à cette étape ou au-delà reçoit l'attente
 * acceptée. L'étape servie par chaque cible vient de l'environnement du conteneur tests : APP_STEP (app) et REF_STEP
 * (app-ref, step-00 par définition si absente).
 *
 * Toute assertion ou normalisation liée à un ACC passe par cette classe : aucune condition d'étape éparpillée dans
 * les tests. Une normalisation ne touche **que** les valeurs couvertes par l'ACC ; tout le reste (statut, corps,
 * autres en-têtes, base, journal PHP) reste comparé strictement.
 */
final class AcceptedChanges
{
    /**
     * @var array<string, array{since: string, title: string, fiches: string, scope: string}>
     */
    public const REGISTRY = [
        'ACC-001' => [
            'since' => 'step-01',
            'title' => 'Cache-Control / Expires des réponses HTTP (Symfony 3.4)',
            'fiches' => 'BH-001 à BH-337, BH-460',
            'scope' => 'En-têtes Cache-Control et Expires de toute réponse HTTP (suites http, edge, diff).',
        ],
        'ACC-002' => [
            'since' => 'step-01',
            'title' => 'Sortie des commandes console list / help (Symfony 3.4)',
            'fiches' => 'BH-425, BH-459',
            'scope' => 'Partie de list / help produite par le framework (en-tête de version, options globales, mise en page).',
        ],
    ];

    // --- Étapes ---------------------------------------------------------------------------------------------------

    /** Étape servie par la cible (« step-NN »). */
    public static function step(string $target): string
    {
        if (Target::REF === Target::check($target)) {
            return self::checkStep(Target::env('REF_STEP') ?? 'step-00', 'REF_STEP');
        }
        $step = Target::env('APP_STEP');
        if (null === $step) {
            throw new \RuntimeException('APP_STEP absente : étape servie par app inconnue (service tests de docker-compose.yml).');
        }

        return self::checkStep($step, 'APP_STEP');
    }

    /** La cible sert-elle l'étape $step ou une étape ultérieure ? */
    public static function stepAtLeast(string $target, string $step): bool
    {
        return self::stepNumber(self::step($target)) >= self::stepNumber(self::checkStep($step, '$step'));
    }

    /** Le changement accepté $id s'applique-t-il aux attentes de la cible ? */
    public static function applies(string $id, string $target): bool
    {
        if (!isset(self::REGISTRY[$id])) {
            throw new \InvalidArgumentException("Changement accepté inconnu : $id (docs/migration/accepted-changes.md).");
        }

        return self::stepAtLeast($target, self::REGISTRY[$id]['since']);
    }

    /** Comparaison app ↔ app-ref : l'ACC s'applique-t-il à au moins un des deux côtés ? */
    public static function appliesToComparison(string $id): bool
    {
        return self::applies($id, Target::REF) || self::applies($id, Target::APP);
    }

    private static function checkStep(string $step, string $source): string
    {
        if (!preg_match('/^step-\d{2}$/', $step)) {
            throw new \InvalidArgumentException("Étape invalide dans $source : « $step » (attendu step-NN).");
        }

        return $step;
    }

    private static function stepNumber(string $step): int
    {
        return (int) substr($step, 5);
    }

    // --- ACC-001 : Cache-Control / Expires --------------------------------------------------------------------------

    /** Avant step-01 : toutes les réponses. */
    public const CACHE_CONTROL_LEGACY = 'no-cache';
    /** À partir de step-01 : requête qui n'a pas utilisé la session. */
    public const CACHE_CONTROL_NO_SESSION = 'no-cache, private';
    /** À partir de step-01 : requête qui a démarré ou utilisé la session (+ Expires présent, valeur non vérifiée). */
    public const CACHE_CONTROL_SESSION = 'max-age=0, must-revalidate, private';

    /**
     * La requête a-t-elle démarré ou utilisé la session ? Déterminé par des faits observables **autres** que les
     * en-têtes de cache (eux-mêmes vérifiés par ailleurs) :
     * - la réponse pose le cookie de session (Set-Cookie PHPSESSID) : session démarrée ;
     * - sinon, la requête portait le cookie de session : session utilisée, sauf rejet par le routage (405), qui
     *   intervient avant toute lecture de la session (observé sur app, step-01).
     */
    public static function sessionUsed(HttpResponse $r): bool
    {
        foreach ($r->cookieShapes() as $shape) {
            if (str_starts_with($shape, 'PHPSESSID')) {
                return true;
            }
        }

        return $r->sessionCookieSent && 405 !== $r->status;
    }

    /** Valeur exacte de Cache-Control attendue pour cette réponse, selon l'étape de sa cible. */
    public static function expectedCacheControl(HttpResponse $r): string
    {
        if (!self::applies('ACC-001', self::targetOf($r))) {
            return self::CACHE_CONTROL_LEGACY;
        }

        return self::sessionUsed($r) ? self::CACHE_CONTROL_SESSION : self::CACHE_CONTROL_NO_SESSION;
    }

    /** Expires attendu ? Jamais avant step-01 ; à partir de step-01, sur les réponses avec session uniquement. */
    public static function expectsExpires(HttpResponse $r): bool
    {
        return self::applies('ACC-001', self::targetOf($r)) && self::sessionUsed($r);
    }

    /** A2 : Cache-Control exact et présence / absence d'Expires (valeur d'Expires jamais comparée). */
    public static function assertCacheHeaders(HttpResponse $r): void
    {
        $acc = self::applies('ACC-001', self::targetOf($r)) ? ' (ACC-001, '.self::step(self::targetOf($r)).')' : '';
        Assert::assertSame(self::expectedCacheControl($r), $r->header('Cache-Control'), "A2 Cache-Control$acc — ".$r->describe());
        if (self::expectsExpires($r)) {
            Assert::assertTrue($r->hasHeader('Expires'), "A2 Expires présent$acc — ".$r->describe());
        } else {
            Assert::assertFalse($r->hasHeader('Expires'), "A2 Expires absent$acc — ".$r->describe());
        }
    }

    /**
     * Suite diff : en-têtes Cache-Control et Expires sous une forme comparable entre app et app-ref.
     * Sur une cible où ACC-001 s'applique, une valeur **conforme** à l'ACC est ramenée à son équivalent step-00
     * (Cache-Control « no-cache », Expires absent) ; une valeur non conforme est laissée telle quelle et fait échouer
     * la comparaison. Sur une cible antérieure à step-01 : valeurs brutes (Expires réduit à sa présence).
     *
     * @return array{cache-control: list<string>, expires: list<string>}
     */
    public static function comparableCacheHeaders(HttpResponse $r): array
    {
        $cacheControl = $r->headerValues('Cache-Control');
        $expires = $r->hasHeader('Expires') ? ['<présent>'] : [];
        if (!self::applies('ACC-001', self::targetOf($r))) {
            return ['cache-control' => $cacheControl, 'expires' => $expires];
        }
        if ([self::expectedCacheControl($r)] === $cacheControl) {
            $cacheControl = [self::CACHE_CONTROL_LEGACY];
        }
        if ($r->hasHeader('Expires') === self::expectsExpires($r)) {
            $expires = [];
        } elseif ([] === $expires) {
            $expires = ['<absent alors que ACC-001 l\'exige>'];
        }

        return ['cache-control' => $cacheControl, 'expires' => $expires];
    }

    private static function targetOf(HttpResponse $r): string
    {
        return $r->target ?? Target::current();
    }

    // --- ACC-002 : list / help -------------------------------------------------------------------------------------

    /**
     * Options globales de l'application console (partie produite par le framework, hors contrat depuis ACC-002),
     * toutes versions de Symfony visées (2.8 → 6.4). Identifiées par leur nom long.
     */
    public const GLOBAL_CONSOLE_OPTIONS = [
        'help', 'quiet', 'verbose', 'version', 'ansi', 'no-ansi', 'no-interaction', 'shell', 'process-isolation',
        'env', 'no-debug', 'profile', 'silent',
    ];

    /**
     * `list` : lignes propres aux commandes fleet:* — nom → description (texte exact, sans la mise en page).
     *
     * @return array<string, string>
     */
    public static function fleetCommands(string $listStdout): array
    {
        $out = [];
        foreach (explode("\n", Normalizer::cli($listStdout)) as $line) {
            if (preg_match('/^\s+(fleet:\S+)(?:\s+(.*))?$/', $line, $m)) {
                $out[$m[1]] = trim($m[2] ?? '');
            }
        }

        return $out;
    }

    /**
     * `help <commande fleet:*>` : partie propre à la commande — synopsis, arguments et options de la commande (hors
     * options globales), texte d'aide. Colonnes séparées par « | », espaces d'alignement réduits.
     * $helpText : sortie déjà passée par ConsoleCommands::helpText() (défaut de --month normalisé).
     *
     * @return array{usage: string, arguments: list<string>, options: list<string>, help: string}
     */
    public static function commandHelpContract(string $helpText): array
    {
        $sections = [];
        $current = null;
        foreach (explode("\n", Normalizer::cli($helpText)) as $line) {
            if (preg_match('/^(\S[^:]*):$/', $line, $m)) {
                $current = $m[1];
                $sections[$current] = [];
                continue;
            }
            if (null !== $current && '' !== trim($line)) {
                $sections[$current][] = trim($line);
            }
        }

        $columns = static fn (string $line): array => preg_split('/\s{2,}/', $line, 2) ?: [$line];
        $canonical = static function (array $cols): string {
            $spec = preg_replace('/\s+/', ' ', trim($cols[0])) ?? $cols[0];

            return isset($cols[1]) ? $spec.' | '.trim($cols[1]) : $spec;
        };

        $options = [];
        foreach ($sections['Options'] ?? [] as $line) {
            $cols = $columns($line);
            preg_match_all('/--([a-z][a-z0-9-]*)/', $cols[0], $names);
            if ([] !== array_intersect($names[1], self::GLOBAL_CONSOLE_OPTIONS)) {
                continue;
            }
            $options[] = $canonical($cols);
        }
        $arguments = [];
        foreach ($sections['Arguments'] ?? [] as $line) {
            $cols = $columns($line);
            if ('command' !== trim($cols[0])) {
                $arguments[] = $canonical($cols);
            }
        }

        return [
            'usage' => preg_replace('/\s+/', ' ', $sections['Usage'][0] ?? '') ?? '',
            'arguments' => $arguments,
            'options' => $options,
            'help' => implode("\n", $sections['Help'] ?? []),
        ];
    }
}
