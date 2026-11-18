package be.bstorm.formation.framework.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Active les tâches planifiées ({@code @Scheduled}), ex. purge des sessions expirées (feature 001, RG-17). */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfig {
}
