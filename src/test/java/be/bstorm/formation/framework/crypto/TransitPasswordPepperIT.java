package be.bstorm.formation.framework.crypto;

import be.bstorm.formation.framework.openbao.OpenBaoTestEnvironment;
import org.junit.jupiter.api.Test;
import org.springframework.vault.core.VaultTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrat du {@link PasswordPepper} contre OpenBao Transit réel, avec un token de l'AppRole {@code api}
 * (politique de production) ; les rotations sont faites avec le client administrateur.
 */
class TransitPasswordPepperIT extends PasswordPepperContract {

    private final VaultTemplate admin = OpenBaoTestEnvironment.admin();
    private final VaultTemplate api = OpenBaoTestEnvironment.api();
    private final TransitPasswordPepper pepper = new TransitPasswordPepper(api);

    @Override
    protected PasswordPepper pepper() {
        return pepper;
    }

    @Override
    protected void rotateKey() {
        admin.opsForTransit().rotate(TransitPasswordPepper.KEY);
    }

    @Test
    void parsesTransitHmac() {
        assertThat(TransitPasswordPepper.parse("vault:v3:YWJj")).isEqualTo(new PasswordPepper.Pepper(3, "YWJj"));
    }

    @Test
    void applicationCannotRotateOrExportThePepperKey() {
        assertThatThrownBy(() -> api.opsForTransit().rotate(TransitPasswordPepper.KEY))
                .hasMessageContaining("permission denied");
        assertThatThrownBy(() -> api.read("transit/export/hmac-key/" + TransitPasswordPepper.KEY))
                .hasMessageContaining("permission denied");
    }
}
