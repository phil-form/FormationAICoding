package be.bstorm.formation.framework.type;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.Random;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Vecteurs calculés indépendamment de l'implémentation (clé = 97 - base mod 97, base préfixée de 2 après 2000). */
class NissTest {

    @Test
    void validBefore2000() {
        Niss niss = Niss.of("85073003328");

        assertThat(niss.digits()).isEqualTo("85073003328");
        assertThat(niss.birthDate()).contains(LocalDate.of(1985, 7, 30));
        assertThat(niss.isBis()).isFalse();
    }

    @Test
    void validFrom2000UsesPrefixedChecksum() {
        assertThat(Niss.of("05031212459").birthDate()).contains(LocalDate.of(2005, 3, 12));
    }

    @Test
    void bisNumberWithMonthPlus40() {
        Niss niss = Niss.of("90411500276");

        assertThat(niss.isBis()).isTrue();
        assertThat(niss.birthDate()).contains(LocalDate.of(1990, 1, 15));
    }

    @Test
    void unknownBirthDateIsAllowed() {
        assertThat(Niss.of("60000001185").birthDate()).isEmpty();
        assertThat(Niss.of("70200000553").birthDate()).isEmpty();
        assertThat(Niss.of("70200000553").isBis()).isTrue();
    }

    @Test
    void separatorsAreNormalized() {
        assertThat(Niss.of("85.07.30-033.28").digits()).isEqualTo("85073003328");
        assertThat(Niss.of(" 850730 033 28 ").digits()).isEqualTo("85073003328");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "85073003329",   // clé de contrôle fausse
            "8507300332",    // 10 chiffres
            "850730033281",  // 12 chiffres
            "8507300332A",   // lettre
            "85073000061",   // numéro d'ordre 000
            "99123199940",   // numéro d'ordre 999
            "85023000125",   // 30 février
            "99010100147",   // 1er janvier 2099 : futur
            ""
    })
    void invalidNumbersAreRejected(String raw) {
        assertThat(Niss.isValid(raw)).isFalse();
        assertThatThrownBy(() -> Niss.of(raw)).isInstanceOf(InvalidNissException.class);
    }

    @Test
    void errorMessagesNeverContainTheValue() {
        assertThatThrownBy(() -> Niss.of("85073003329")).hasMessageNotContaining("85073003329")
                .hasMessageNotContaining("850730033");
    }

    @Test
    void toStringIsMaskedButFormattedIsAvailableOnDemand() {
        Niss niss = Niss.of("85073003328");

        assertThat(niss.toString()).isEqualTo("Niss[**.**.**-***.28]").doesNotContain("850730");
        assertThat(niss.formatted()).isEqualTo("85.07.30-033.28");
        assertThat(niss.masked()).isEqualTo("**.**.**-***.28");
    }

    @Test
    void equalityIsBasedOnDigits() {
        assertThat(Niss.of("85.07.30-033.28")).isEqualTo(Niss.of("85073003328"))
                .hasSameHashCodeAs(Niss.of("85073003328"));
    }

    @Test
    void randomGeneratesValidAndReproducibleNumbers() {
        Random random = new Random(42);
        IntStream.range(0, 1_000).forEach(i -> assertThat(Niss.isValid(Niss.random(random).digits())).isTrue());

        assertThat(Niss.random(new Random(7))).isEqualTo(Niss.random(new Random(7)));
    }
}
