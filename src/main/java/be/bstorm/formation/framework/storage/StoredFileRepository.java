package be.bstorm.formation.framework.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {
}
