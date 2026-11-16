package be.bstorm.formation.framework.type;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;

/**
 * Numéro d'identification de la sécurité sociale belge (NISS / numéro de registre national ou numéro BIS).
 * <p>
 * Format : {@code YYMMDD SSS CC} (11 chiffres) — date de naissance, numéro d'ordre (001–998), clé de contrôle
 * {@code 97 - (YYMMDDSSS mod 97)}, calculée sur {@code 2YYMMDDSSS} pour les naissances à partir de 2000.
 * Numéro BIS : mois augmenté de 20 (sexe inconnu) ou 40 (sexe connu). Mois ou jour {@code 00} : date inconnue.
 * <p>
 * <b>Donnée personnelle sensible</b> : {@link #toString()} est masqué ; ne jamais la journaliser ni la renvoyer
 * en clair sans besoin explicite. Stockage : chiffrée ({@code CryptoService}) + index aveugle sur {@link #digits()}.
 */
public final class Niss {

    private static final Pattern SEPARATORS = Pattern.compile("[\\s.\\-/]");
    private static final Pattern ELEVEN_DIGITS = Pattern.compile("\\d{11}");

    private final String digits;
    private final LocalDate birthDate;
    private final boolean bis;

    private Niss(String digits, LocalDate birthDate, boolean bis) {
        this.digits = digits;
        this.birthDate = birthDate;
        this.bis = bis;
    }

    /**
     * Normalise ({@code 85.07.30-033.28} → {@code 85073003328}) puis valide.
     *
     * @throws InvalidNissException si le numéro est invalide (le message ne contient jamais la valeur)
     */
    public static Niss of(String raw) {
        if (raw == null) {
            throw new InvalidNissException("NISS absent");
        }
        String digits = SEPARATORS.matcher(raw).replaceAll("");
        if (!ELEVEN_DIGITS.matcher(digits).matches()) {
            throw new InvalidNissException("Le NISS doit comporter 11 chiffres");
        }
        long base = Long.parseLong(digits.substring(0, 9));
        int check = Integer.parseInt(digits.substring(9));
        int century;
        if (checksum(base) == check) {
            century = 1900;
        } else if (checksum(2_000_000_000L + base) == check) {
            century = 2000;
        } else {
            throw new InvalidNissException("Clé de contrôle du NISS invalide");
        }

        int sequence = Integer.parseInt(digits.substring(6, 9));
        if (sequence < 1 || sequence > 998) {
            throw new InvalidNissException("Numéro d'ordre du NISS invalide");
        }

        int year = century + Integer.parseInt(digits.substring(0, 2));
        int rawMonth = Integer.parseInt(digits.substring(2, 4));
        int day = Integer.parseInt(digits.substring(4, 6));
        boolean bis = rawMonth >= 20;
        int month = rawMonth >= 40 ? rawMonth - 40 : rawMonth >= 20 ? rawMonth - 20 : rawMonth;
        if (month > 12 || day > 31) {
            throw new InvalidNissException("Date de naissance du NISS invalide");
        }

        LocalDate birthDate = null;
        if (month != 0 && day != 0) {
            try {
                birthDate = LocalDate.of(year, month, day);
            } catch (DateTimeException e) {
                throw new InvalidNissException("Date de naissance du NISS invalide");
            }
            if (birthDate.isAfter(LocalDate.now())) {
                throw new InvalidNissException("Date de naissance du NISS dans le futur");
            }
        }
        return new Niss(digits, birthDate, bis);
    }

    public static boolean isValid(String raw) {
        try {
            of(raw);
            return true;
        } catch (InvalidNissException e) {
            return false;
        }
    }

    /**
     * NISS valide aléatoire (registre national, non BIS) — données de test et de seed uniquement.
     */
    public static Niss random(RandomGenerator random) {
        LocalDate start = LocalDate.of(1930, 1, 1);
        LocalDate date = start.plusDays(random.nextLong(ChronoUnit.DAYS.between(start, LocalDate.now().minusYears(1))));
        int sequence = 1 + random.nextInt(997);
        String prefix = "%02d%02d%02d%03d".formatted(date.getYear() % 100, date.getMonthValue(),
                date.getDayOfMonth(), sequence);
        long base = Long.parseLong(prefix);
        int check = checksum(date.getYear() >= 2000 ? 2_000_000_000L + base : base);
        return of(prefix + "%02d".formatted(check));
    }

    private static int checksum(long value) {
        return (int) (97 - value % 97);
    }

    /** Les 11 chiffres, sans séparateur (valeur à chiffrer et à indexer). */
    public String digits() {
        return digits;
    }

    /** Format d'affichage officiel : {@code YY.MM.DD-SSS.CC}. */
    public String formatted() {
        return "%s.%s.%s-%s.%s".formatted(digits.substring(0, 2), digits.substring(2, 4), digits.substring(4, 6),
                digits.substring(6, 9), digits.substring(9));
    }

    /** Forme masquée pour l'affichage partiel : {@code **.**.**-***.CC}. */
    public String masked() {
        return "**.**.**-***." + digits.substring(9);
    }

    /** Date de naissance, absente si le mois ou le jour est inconnu ({@code 00}). */
    public Optional<LocalDate> birthDate() {
        return Optional.ofNullable(birthDate);
    }

    /** Numéro BIS (personne non inscrite au registre national). */
    public boolean isBis() {
        return bis;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Niss niss && digits.equals(niss.digits);
    }

    @Override
    public int hashCode() {
        return digits.hashCode();
    }

    /** Masqué : un NISS ne doit jamais apparaître dans un log ou un message d'erreur. */
    @Override
    public String toString() {
        return "Niss[" + masked() + "]";
    }
}
