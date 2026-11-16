package be.bstorm.formation.framework.storage;

import be.bstorm.formation.TestcontainersConfig;
import be.bstorm.formation.framework.crypto.CryptoException;
import be.bstorm.formation.framework.crypto.CryptoService;
import be.bstorm.formation.framework.crypto.InMemoryCryptoService;
import be.bstorm.formation.framework.crypto.KeyRotationService;
import be.bstorm.formation.framework.crypto.SensitiveDataType;
import be.bstorm.formation.framework.entity.SoftDeleter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static be.bstorm.formation.framework.storage.TestImages.SECRET;
import static be.bstorm.formation.framework.storage.TestImages.contains;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Stockage chiffré au repos (enveloppe), sur une vraie base et un vrai dossier ; crypto en mémoire. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class FileStorageIT {

    @Autowired
    FileStorage storage;

    @Autowired
    StoredFileRepository files;

    @Autowired
    CryptoService crypto;

    @Autowired
    KeyRotationService rotation;

    @Autowired
    StoredFileRewrapTarget rewrapTarget;

    @Autowired
    SoftDeleter softDeleter;

    @Autowired
    FileStorageProperties properties;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcClient jdbc;

    private StoredFile store(String name, byte[] content) {
        return tx.execute(s -> storage.store(name, content));
    }

    private FileContent read(UUID id) {
        return tx.execute(s -> storage.read(files.findById(id).orElseThrow()));
    }

    @Test
    void contentIsEncryptedAtRestAndReadBackSanitized() throws IOException {
        StoredFile file = store("../../DCIM/IMG_0001.JPG", TestImages.jpegWithExif(1, false));

        assertThat(file.getOriginalName()).isEqualTo("IMG_0001.jpg");
        assertThat(file.getContentType()).isEqualTo("image/jpeg");
        assertThat(file.getSha256()).hasSize(64);
        assertThat(file.getWrappedKey()).startsWith("local:v");

        byte[] onDisk = Files.readAllBytes(storage.path(file.getId()));
        assertThat(onDisk[0]).isEqualTo((byte) 1);
        assertThat(contains(onDisk, "JFIF")).isFalse();
        assertThat(contains(onDisk, SECRET)).isFalse();

        FileContent content = read(file.getId());
        assertThat(content.content()).hasSize((int) file.getSizeBytes());
        assertThat(contains(content.content(), SECRET)).isFalse();
        assertThat(ImageIO.read(new ByteArrayInputStream(content.content())).getWidth()).isEqualTo(16);
        assertThat(content.fileName()).isEqualTo("IMG_0001.jpg");
        assertThat(content.toString()).doesNotContain("[B@");
    }

    @Test
    void rolledBackUploadLeavesNoFileAndNoRow() {
        AtomicReference<UUID> id = new AtomicReference<>();

        tx.executeWithoutResult(s -> {
            id.set(storage.store("photo.png", TestImages.encode(TestImages.redLeftBlueRight(), "png")).getId());
            assertThat(storage.path(id.get())).exists();
            s.setRollbackOnly();
        });

        assertThat(storage.path(id.get())).doesNotExist();
        assertThat(files.existsById(id.get())).isFalse();
        try (var stream = Files.list(properties.root())) {
            assertThat(stream.map(Path::toString)).noneMatch(p -> p.endsWith(".tmp"));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void tamperedContentIsRejected() throws IOException {
        StoredFile file = store("photo.png", TestImages.encode(TestImages.redLeftBlueRight(), "png"));
        Path path = storage.path(file.getId());
        byte[] data = Files.readAllBytes(path);
        data[data.length / 2] ^= 0x01;
        Files.write(path, data);

        assertThatThrownBy(() -> read(file.getId())).isInstanceOf(CryptoException.class);
    }

    @Test
    void contentCannotBeMovedToAnotherFile() throws IOException {
        StoredFile a = store("a.png", TestImages.encode(TestImages.redLeftBlueRight(), "png"));
        StoredFile b = store("b.png", TestImages.encode(TestImages.redLeftBlueRight(), "png"));
        Files.copy(storage.path(a.getId()), storage.path(b.getId()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        assertThatThrownBy(() -> read(b.getId())).isInstanceOf(CryptoException.class);
    }

    @Test
    void wrappedKeyIsBoundToItsFile() {
        StoredFile a = store("a.png", TestImages.encode(TestImages.redLeftBlueRight(), "png"));
        StoredFile b = store("b.png", TestImages.encode(TestImages.redLeftBlueRight(), "png"));
        jdbc.sql("UPDATE stored_file SET wrapped_key = :key WHERE id = :id")
                .param("key", a.getWrappedKey()).param("id", b.getId()).update();

        assertThatThrownBy(() -> read(b.getId())).isInstanceOf(CryptoException.class);
    }

    @Test
    void oversizedOrEmptyFilesAreRefusedBeforeDecoding() {
        byte[] tooBig = new byte[(int) properties.maxFileSize().toBytes() + 1];

        assertThatThrownBy(() -> store("big.jpg", tooBig))
                .isInstanceOfSatisfying(InvalidFileException.class, e -> assertThat(e.getStatus().value()).isEqualTo(413));
        assertThatThrownBy(() -> store("empty.jpg", new byte[0]))
                .isInstanceOfSatisfying(InvalidFileException.class, e -> assertThat(e.getStatus().value()).isEqualTo(400));
    }

    @Test
    void keyRotationRewrapsDataKeysIncludingDeletedFiles() {
        StoredFile kept = store("kept.png", TestImages.encode(TestImages.redLeftBlueRight(), "png"));
        StoredFile deleted = store("deleted.png", TestImages.encode(TestImages.redLeftBlueRight(), "png"));
        tx.executeWithoutResult(s -> softDeleter.delete(files.findById(deleted.getId()).orElseThrow()));

        ((InMemoryCryptoService) crypto).rotate(SensitiveDataType.FILE);
        int latest = crypto.latestKeyVersion(SensitiveDataType.FILE);
        rotation.rewrapAll(rewrapTarget);

        for (UUID id : new UUID[]{kept.getId(), deleted.getId()}) {
            String key = jdbc.sql("SELECT wrapped_key FROM stored_file WHERE id = :id").param("id", id)
                    .query(String.class).single();
            assertThat(CryptoService.keyVersion(key)).isEqualTo(latest);
        }
        assertThat(read(kept.getId()).content()).hasSize((int) kept.getSizeBytes());
    }
}
