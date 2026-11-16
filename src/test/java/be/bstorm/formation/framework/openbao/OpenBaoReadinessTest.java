package be.bstorm.formation.framework.openbao;

import org.junit.jupiter.api.Test;
import org.springframework.vault.VaultException;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.core.VaultSysOperations;
import org.springframework.vault.support.VaultHealth;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenBaoReadinessTest {

    private final VaultOperations vault = mock(VaultOperations.class);
    private final VaultSysOperations sys = mock(VaultSysOperations.class);
    private final VaultHealth health = mock(VaultHealth.class);
    private final OpenBaoReadiness readiness = new OpenBaoReadiness(vault, URI.create("https://openbao:8200"));

    OpenBaoReadinessTest() {
        when(vault.opsForSys()).thenReturn(sys);
    }

    @Test
    void readyWhenInitializedAndUnsealed() {
        when(sys.health()).thenReturn(health);
        when(health.isInitialized()).thenReturn(true);
        when(health.isSealed()).thenReturn(false);

        assertThatCode(readiness::assertReady).doesNotThrowAnyException();
    }

    @Test
    void sealedVaultPreventsStartupWithUnsealHint() {
        when(sys.health()).thenReturn(health);
        when(health.isInitialized()).thenReturn(true);
        when(health.isSealed()).thenReturn(true);

        assertThatIllegalStateException().isThrownBy(readiness::assertReady)
                .withMessageContaining("SCELLÉ").withMessageContaining("unseal.sh");
    }

    @Test
    void uninitializedVaultPreventsStartupWithBootstrapHint() {
        when(sys.health()).thenReturn(health);
        when(health.isInitialized()).thenReturn(false);

        assertThatIllegalStateException().isThrownBy(readiness::assertReady)
                .withMessageContaining("bootstrap.sh");
    }

    @Test
    void unreachableVaultPreventsStartupWithAddress() {
        when(sys.health()).thenThrow(new VaultException("Connection refused"));

        assertThatIllegalStateException().isThrownBy(readiness::assertReady)
                .withMessageContaining("injoignable").withMessageContaining("https://openbao:8200");
    }
}
