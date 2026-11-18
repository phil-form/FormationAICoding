package be.bstorm.formation.framework.crypto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Contrat du {@link PasswordPepper}, exécuté sur l'implémentation en mémoire et sur OpenBao Transit. */
abstract class PasswordPepperContract {

    protected static final String PASSWORD = "correct horse battery staple";

    protected abstract PasswordPepper pepper();

    /** Rotation de la clé de poivre (opération d'administration). */
    protected abstract void rotateKey();

    @Test
    void pepperIsDeterministicDistinctAndOpaque() {
        PasswordPepper.Pepper first = pepper().pepper(PASSWORD);

        assertThat(pepper().pepper(PASSWORD)).isEqualTo(first);
        assertThat(pepper().pepper(PASSWORD + "!").value()).isNotEqualTo(first.value());
        assertThat(first.value()).doesNotContain(PASSWORD).doesNotContain(":");
        assertThat(first.keyVersion()).isPositive();
        assertThat(first.toString()).doesNotContain(first.value());
    }

    @Test
    void rotationKeepsOldVersionsUsable() {
        PasswordPepper.Pepper before = pepper().pepper(PASSWORD);

        rotateKey();
        PasswordPepper.Pepper after = pepper().pepper(PASSWORD);

        assertThat(after.keyVersion()).isEqualTo(before.keyVersion() + 1);
        assertThat(after.value()).isNotEqualTo(before.value());
        assertThat(pepper().pepper(PASSWORD, before.keyVersion())).isEqualTo(before);
    }

    @Test
    void invalidInputsAreRejectedWithoutLeakingThePassword() {
        assertThatThrownBy(() -> pepper().pepper("")).isInstanceOf(CryptoException.class);
        assertThatThrownBy(() -> pepper().pepper(null)).isInstanceOf(CryptoException.class);
        assertThatThrownBy(() -> pepper().pepper(PASSWORD, 0)).isInstanceOf(CryptoException.class)
                .message().doesNotContain(PASSWORD);
        assertThatThrownBy(() -> pepper().pepper(PASSWORD, 999)).isInstanceOf(CryptoException.class)
                .message().doesNotContain(PASSWORD);
    }
}
