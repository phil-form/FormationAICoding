<?php

declare(strict_types=1);

namespace Flotte\Tests\Support;

/**
 * Cible d'exécution : "app" (étape en cours) ou "ref" (référence step-00 figée).
 * Choisie par la variable TARGET (défaut : app) ; les tests diff utilisent les deux.
 */
final class Target
{
    public const APP = 'app';
    public const REF = 'ref';

    public static function current(): string
    {
        return self::check((string) (self::env('TARGET') ?? self::APP));
    }

    public static function check(string $target): string
    {
        if (!\in_array($target, [self::APP, self::REF], true)) {
            throw new \InvalidArgumentException("Cible inconnue : « $target » (app|ref).");
        }

        return $target;
    }

    /** URL de base, sans barre oblique finale. */
    public static function url(string $target): string
    {
        $default = self::APP === self::check($target) ? 'http://app:8080' : 'http://app-ref:8080';

        return rtrim((string) (self::env(self::APP === $target ? 'APP_URL' : 'REF_URL') ?? $default), '/');
    }

    /** Chemin de la base SQLite de la cible (volume monté dans le conteneur tests). */
    public static function db(string $target): string
    {
        $default = self::APP === self::check($target) ? '/data/app/flotte.sqlite' : '/data/ref/flotte.sqlite';

        return (string) (self::env(self::APP === $target ? 'APP_DB' : 'REF_DB') ?? $default);
    }

    /** Nom du conteneur Docker (lecture du journal PHP, console). */
    public static function container(string $target): string
    {
        $default = self::APP === self::check($target) ? 'flotte-mig-app' : 'flotte-mig-app-ref';

        return (string) (self::env(self::APP === $target ? 'APP_CONTAINER' : 'REF_CONTAINER') ?? $default);
    }

    public static function env(string $name): ?string
    {
        $value = getenv($name);
        if (false === $value || '' === $value) {
            $value = $_ENV[$name] ?? $_SERVER[$name] ?? null;
        }

        return null === $value || '' === $value ? null : (string) $value;
    }

    /** Racine du projet de tests (tests/). */
    public static function testsRoot(): string
    {
        return \dirname(__DIR__, 2);
    }

    /** Résout un chemin relatif à tests/. */
    public static function path(string $relativeOrAbsolute): string
    {
        return str_starts_with($relativeOrAbsolute, '/') ? $relativeOrAbsolute : self::testsRoot().'/'.$relativeOrAbsolute;
    }
}
