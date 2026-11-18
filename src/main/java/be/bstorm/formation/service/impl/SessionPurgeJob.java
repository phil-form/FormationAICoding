package be.bstorm.formation.service.impl;

import be.bstorm.formation.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** RG-17 : purge planifiée des familles de jetons expirées ou révoquées depuis plus de la rétention. */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionPurgeJob {

    private final AuthService authService;

    @Scheduled(cron = "${app.auth.refresh.purge-cron}", zone = "Europe/Brussels")
    public void purge() {
        int purged = authService.purgeExpiredSessions();
        log.info("Purge des sessions : {} famille(s) supprimée(s)", purged);
    }
}
