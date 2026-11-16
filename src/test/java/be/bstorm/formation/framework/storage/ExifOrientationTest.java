package be.bstorm.formation.framework.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class ExifOrientationTest {

    @ParameterizedTest
    @ValueSource(ints = {1, 3, 6, 8})
    void readsOrientationInBothByteOrders(int orientation) {
        assertThat(ExifOrientation.read(TestImages.jpegWithExif(orientation, false))).isEqualTo(orientation);
        assertThat(ExifOrientation.read(TestImages.jpegWithExif(orientation, true))).isEqualTo(orientation);
    }

    @Test
    void withoutExifOrientationIsNormal() {
        assertThat(ExifOrientation.read(TestImages.encode(TestImages.redLeftBlueRight(), "jpg"))).isEqualTo(1);
    }

    @Test
    void outOfRangeValueIsIgnored() {
        assertThat(ExifOrientation.read(TestImages.jpegWithExif(42, false))).isEqualTo(1);
    }

    @Test
    void truncatedOrRandomInputNeverThrows() {
        byte[] jpeg = TestImages.jpegWithExif(6, false);
        for (int length = 0; length < 60; length++) {
            assertThat(ExifOrientation.read(Arrays.copyOf(jpeg, length))).isBetween(1, 8);
        }
        Random random = new Random(42);
        for (int i = 0; i < 200; i++) {
            byte[] noise = new byte[64];
            random.nextBytes(noise);
            noise[0] = (byte) 0xFF;
            noise[1] = (byte) 0xD8;
            assertThat(ExifOrientation.read(noise)).isBetween(1, 8);
        }
    }
}
