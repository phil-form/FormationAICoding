package be.bstorm.formation.framework.entity;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;

/**
 * UUID version 7 (RFC 9562) : 48 bits d'horodatage en millisecondes puis 74 bits aléatoires — ordonné dans le temps
 * (bon pour les index), non devinable.
 */
public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7() {
    }

    public static UUID next() {
        return next(Clock.systemUTC().millis());
    }

    static UUID next(long epochMillis) {
        long randA = RANDOM.nextInt(1 << 12);
        long most = (epochMillis & 0xFFFF_FFFF_FFFFL) << 16 | 0x7000L | randA;
        long least = RANDOM.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL | 0x8000_0000_0000_0000L;
        return new UUID(most, least);
    }
}
