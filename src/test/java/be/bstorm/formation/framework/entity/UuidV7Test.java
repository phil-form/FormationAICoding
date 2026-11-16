package be.bstorm.formation.framework.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UuidV7Test {

    @Test
    void versionVariantAndTimestamp() {
        long millis = Instant.parse("2026-11-16T09:00:00Z").toEpochMilli();
        UUID uuid = UuidV7.next(millis);

        assertThat(uuid.version()).isEqualTo(7);
        assertThat(uuid.variant()).isEqualTo(2);
        assertThat(uuid.getMostSignificantBits() >>> 16).isEqualTo(millis);
    }

    @Test
    void orderedByTimeAndUnique() {
        assertThat(UuidV7.next(1_000).compareTo(UuidV7.next(2_000))).isNegative();
        Set<UUID> ids = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            ids.add(UuidV7.next());
        }
        assertThat(ids).hasSize(10_000);
    }
}
