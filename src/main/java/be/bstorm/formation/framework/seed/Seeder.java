package be.bstorm.formation.framework.seed;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Déclare un {@link DataSeeder} : bean Spring créé uniquement avec le profil {@code seed}.
 *
 * <pre>{@code
 * @Seeder
 * @RequiredArgsConstructor
 * class CourseSeeder implements DataSeeder {
 *     private final CourseRepository courses;
 *     private final Faker faker;   // net.datafaker, déterministe (SeedFakerConfig)
 *     public int order() { return 10; }
 *     public boolean shouldRun() { return courses.count() == 0; }
 *     public void seed() {
 *         courses.saveAll(IntStream.range(0, 20)
 *                 .mapToObj(i -> Course.builder().name(faker.educator().course()).build()).toList());
 *     }
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
@Profile(SeedRunner.PROFILE)
public @interface Seeder {
}
