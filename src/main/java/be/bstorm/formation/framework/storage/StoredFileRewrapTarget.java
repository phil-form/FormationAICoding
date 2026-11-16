package be.bstorm.formation.framework.storage;

import be.bstorm.formation.framework.crypto.RewrapTarget;
import be.bstorm.formation.framework.crypto.SensitiveDataType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Rotation de la clé Transit {@code file} : re-chiffre les clés de données ({@code stored_file.wrapped_key}), pas les
 * contenus. SQL natif volontaire : les fichiers supprimés logiquement suivent aussi la rotation.
 */
@Component
@RequiredArgsConstructor
public class StoredFileRewrapTarget implements RewrapTarget {

    private final JdbcClient jdbc;

    @Override
    public String name() {
        return "stored_file.wrapped_key";
    }

    @Override
    public SensitiveDataType type() {
        return SensitiveDataType.FILE;
    }

    @Override
    public List<EncryptedValue> findOlderThan(int version, int limit) {
        return jdbc.sql("""
                        SELECT id, wrapped_key FROM stored_file
                        WHERE substring(split_part(wrapped_key, ':', 2) FROM 2)::int < :version
                        ORDER BY id LIMIT :limit""")
                .param("version", version)
                .param("limit", limit)
                .query((rs, n) -> new EncryptedValue(rs.getString("id"), rs.getString("wrapped_key"), rs.getString("id")))
                .list();
    }

    @Override
    public void update(String id, String newCiphertext) {
        jdbc.sql("UPDATE stored_file SET wrapped_key = :key WHERE id = :id")
                .param("key", newCiphertext)
                .param("id", UUID.fromString(id))
                .update();
    }
}
