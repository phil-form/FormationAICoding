package be.bstorm.formation.framework.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Active {@code @CreatedDate} / {@code @LastModifiedDate} sur {@code BaseEntity}. */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
