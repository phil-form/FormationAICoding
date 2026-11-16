package be.bstorm.formation.framework.storage;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class FileStorageTest {

    @ParameterizedTest(name = "« {0} » → {1}")
    @CsvSource(delimiter = '|', value = {
            "photo.JPG                    | photo.jpg",
            "../../etc/passwd             | passwd.jpg",
            "C:\\Users\\x\\IMG_0001.jpeg  | IMG_0001.jpg",
            "sinistre.html                | sinistre.jpg",
            "archive.tar.gz               | archive.tar.jpg",
            "'a\"b\u0007c.jpg'             | abc.jpg",
            "..                           | file.jpg",
            "''                           | file.jpg",
    })
    void safeNameKeepsOnlyABaseNameWithTheRealExtension(String original, String expected) {
        assertThat(FileStorage.safeName(original, ImageType.JPEG)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"200, 104", "100, 104", "99, 103"})
    void safeNameIsBounded(int length, int expected) {
        assertThat(FileStorage.safeName("x".repeat(length) + ".png", ImageType.PNG)).hasSize(expected);
    }
}
