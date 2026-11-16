package be.bstorm.formation.framework.openbao;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.vault.core.lease.SecretLeaseContainer;
import org.springframework.vault.core.lease.domain.RequestedSecret;
import org.springframework.vault.core.lease.event.LeaseListener;
import org.springframework.vault.core.lease.event.SecretLeaseCreatedEvent;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RotatingDatabaseCredentialsTest {

    private final SecretLeaseContainer container = mock(SecretLeaseContainer.class);

    @Test
    void registersRotatingSecretForTheDatabaseRole() {
        RotatingDatabaseCredentials credentials = new RotatingDatabaseCredentials(container, "app");

        verify(container).register(org.mockito.ArgumentMatchers.argThat(
                (RequestedSecret s) -> s.getPath().equals("database/creds/app")
                        && s.getMode() == RequestedSecret.Mode.ROTATE), any(LeaseListener.class));
        assertThat(credentials.current()).isNull();
    }

    @Test
    void obtainStartsContainerAndReturnsFirstCredentials() {
        RotatingDatabaseCredentials credentials = new RotatingDatabaseCredentials(container, "app");
        doAnswer(inv -> {
            credentials.apply(Map.of("username", "v-app-1", "password", "p1"));
            return null;
        }).when(container).start();

        assertThat(credentials.obtain()).isEqualTo(new RotatingDatabaseCredentials.Credentials("v-app-1", "p1"));
        verify(container).start();
    }

    @Test
    void obtainFailsWhenOpenBaoReturnsNothing() {
        RotatingDatabaseCredentials credentials = new RotatingDatabaseCredentials(container, "app");
        when(container.isRunning()).thenReturn(true);

        assertThatIllegalStateException().isThrownBy(credentials::obtain).withMessageContaining("database/creds/app");
    }

    @Test
    void rotationUpdatesBoundPoolCredentials() {
        RotatingDatabaseCredentials credentials = new RotatingDatabaseCredentials(container, "app");
        credentials.apply(Map.of("username", "v-app-1", "password", "p1"));
        try (HikariDataSource hikari = new HikariDataSource()) {
            hikari.setUsername("v-app-1");
            credentials.bind(hikari);

            credentials.onLeaseEvent(new SecretLeaseCreatedEvent(RequestedSecret.rotating("database/creds/app"),
                    null, Map.of("username", "v-app-2", "password", "p2")));

            assertThat(hikari.getUsername()).isEqualTo("v-app-2");
            assertThat(hikari.getPassword()).isEqualTo("p2");
            assertThat(credentials.current().username()).isEqualTo("v-app-2");
        }
    }

    @Test
    void passwordNeverAppearsInToString() {
        assertThat(new RotatingDatabaseCredentials.Credentials("v-app-1", "s3cr3t").toString())
                .doesNotContain("s3cr3t").contains("***");
    }
}
