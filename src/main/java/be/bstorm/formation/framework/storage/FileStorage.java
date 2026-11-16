package be.bstorm.formation.framework.storage;

import be.bstorm.formation.framework.crypto.CryptoException;
import be.bstorm.formation.framework.crypto.CryptoService;
import be.bstorm.formation.framework.crypto.SensitiveDataType;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Stockage des fichiers téléversés, <b>tous chiffrés au repos</b> (chiffrement par enveloppe) :
 * <ol>
 *   <li>taille contrôlée, image neutralisée ({@link ImageSanitizer}) ;</li>
 *   <li>id UUID v7 attribué d'avance ({@code assignId}) : il sert de contexte de chiffrement ;</li>
 *   <li>clé de données AES-256 aléatoire, chiffrée par OpenBao Transit (clé dérivée {@code file}, contexte = id),
 *       puis métadonnées persistées ({@link StoredFile}) ;</li>
 *   <li>contenu chiffré AES-256-GCM (id en données associées) écrit de façon atomique dans {@code <root>/<id>.bin}.</li>
 * </ol>
 * Le fichier écrit est supprimé si la transaction est annulée. La clé de données ne quitte jamais la mémoire en
 * clair ; sa rotation passe par {@link StoredFileRewrapTarget} (le contenu n'est pas re-chiffré).
 * Le nombre de fichiers par ressource (ex. 10 photos par sinistre) est une règle de la feature.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileStorage {

    private static final byte FORMAT_V1 = 1;
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StoredFileRepository repository;
    private final CryptoService crypto;
    private final ImageSanitizer sanitizer;
    private final FileStorageProperties properties;

    /** Au démarrage : le dossier (volume) doit exister ou être créable, et accessible en écriture. */
    @PostConstruct
    void checkRoot() {
        try {
            Files.createDirectories(properties.root());
        } catch (IOException e) {
            throw new UncheckedIOException("Dossier de stockage impossible à créer : " + properties.root(), e);
        }
        if (!Files.isWritable(properties.root())) {
            throw new IllegalStateException("Dossier de stockage non accessible en écriture : " + properties.root()
                    + " (volume uploads monté ?)");
        }
    }

    /** Neutralise, chiffre et enregistre une image ; à appeler dans la transaction de la feature. */
    @Transactional
    public StoredFile store(String originalName, byte[] content) {
        if (content == null || content.length == 0) {
            throw InvalidFileException.empty();
        }
        if (content.length > properties.maxFileSize().toBytes()) {
            throw InvalidFileException.tooLarge();
        }
        ImageSanitizer.SanitizedImage image = sanitizer.sanitize(content);
        StoredFile file = new StoredFile(safeName(originalName, image.type()), image.type().mediaType(),
                image.content().length, sha256(image.content()));
        UUID id = file.assignId();
        byte[] key = new byte[KEY_BYTES];
        RANDOM.nextBytes(key);
        try {
            file.setWrappedKey(crypto.encrypt(SensitiveDataType.FILE, Base64.getEncoder().encodeToString(key),
                    id.toString()));
            file = repository.save(file);
            write(id, encrypt(key, id, image.content()));
        } finally {
            Arrays.fill(key, (byte) 0);
        }
        log.info("Fichier stocké id={} type={} taille={}", file.getId(), file.getContentType(), file.getSizeBytes());
        return file;
    }

    /** Déchiffre un fichier et vérifie son intégrité ; le contrôle d'accès (RM9) est fait par l'appelant. */
    public FileContent read(StoredFile file) {
        byte[] data;
        try {
            data = Files.readAllBytes(path(file.getId()));
        } catch (IOException e) {
            throw new UncheckedIOException("Contenu introuvable pour le fichier " + file.getId(), e);
        }
        byte[] key = Base64.getDecoder().decode(
                crypto.decrypt(SensitiveDataType.FILE, file.getWrappedKey(), file.getId().toString()));
        byte[] content;
        try {
            content = decrypt(key, file.getId(), data);
        } finally {
            Arrays.fill(key, (byte) 0);
        }
        if (!sha256(content).equals(file.getSha256())) {
            throw new CryptoException("Intégrité du fichier " + file.getId() + " non vérifiée");
        }
        return new FileContent(file.getOriginalName(), file.getContentType(), content);
    }

    Path path(UUID id) {
        return properties.root().resolve(id + ".bin");
    }

    private void write(UUID id, byte[] data) {
        Path target = path(id);
        Path temp = properties.root().resolve(id + ".tmp");
        try {
            if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
                Files.createFile(temp, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            }
            Files.write(temp, data, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            deleteQuietly(temp);
            throw new UncheckedIOException("Écriture du fichier " + id + " impossible", e);
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        deleteQuietly(target);
                    }
                }
            });
        }
    }

    /** Format : version (1 octet) ‖ IV (12) ‖ chiffré AES-256-GCM avec étiquette (16) ; AAD = id du fichier. */
    private static byte[] encrypt(byte[] key, UUID id, byte[] content) {
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, key, id, iv);
            byte[] encrypted = cipher.doFinal(content);
            return ByteBuffer.allocate(1 + IV_BYTES + encrypted.length).put(FORMAT_V1).put(iv).put(encrypted).array();
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Chiffrement du fichier " + id + " impossible", e);
        }
    }

    private static byte[] decrypt(byte[] key, UUID id, byte[] data) {
        if (data.length < 1 + IV_BYTES + TAG_BITS / 8 || data[0] != FORMAT_V1) {
            throw new CryptoException("Format du fichier " + id + " invalide");
        }
        try {
            Cipher cipher = cipher(Cipher.DECRYPT_MODE, key, id, Arrays.copyOfRange(data, 1, 1 + IV_BYTES));
            return cipher.doFinal(data, 1 + IV_BYTES, data.length - 1 - IV_BYTES);
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Déchiffrement du fichier " + id + " impossible (altéré ou autre fichier)", e);
        }
    }

    private static Cipher cipher(int mode, byte[] key, UUID id, byte[] iv) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
        cipher.updateAAD(id.toString().getBytes(StandardCharsets.US_ASCII));
        return cipher;
    }

    /** Dernier segment du nom, sans caractère de contrôle ni guillemet, 100 caractères max, extension du type réel. */
    static String safeName(String originalName, ImageType type) {
        String name = originalName == null ? "" : originalName;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = name.replaceAll("[\\p{Cntrl}\"]", "").strip();
        int dot = name.lastIndexOf('.');
        String base = (dot > 0 ? name.substring(0, dot) : name).strip();
        if (base.isEmpty() || base.chars().allMatch(c -> c == '.')) {
            base = "file";
        }
        if (base.length() > 100) {
            base = base.substring(0, 100);
        }
        return base + "." + type.extension();
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Suppression de {} impossible", path.getFileName(), e);
        }
    }
}
