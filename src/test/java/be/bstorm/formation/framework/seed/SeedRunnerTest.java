package be.bstorm.formation.framework.seed;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.support.TransactionOperations;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeedRunnerTest {

    private final List<String> calls = new ArrayList<>();

    @Test
    void runsSeedersByOrderThenName() {
        runner(dev(), seeder("users", 10, true), seeder("courses", 20, true), seeder("roles", 10, true)).run(null);

        assertThat(calls).containsExactly("roles", "users", "courses");
    }

    @Test
    void skipsSeedersWhoseDataIsAlreadyPresent() {
        runner(dev(), seeder("present", 0, false), seeder("missing", 1, true)).run(null);

        assertThat(calls).containsExactly("missing");
    }

    @Test
    void refusesToRunWithProdProfile() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod", SeedRunner.PROFILE);

        assertThatIllegalStateException().isThrownBy(() -> runner(env, seeder("any", 0, true)).run(null))
                .withMessageContaining("prod");
        assertThat(calls).isEmpty();
    }

    @Test
    void failurePropagatesAndStopsFollowingSeeders() {
        DataSeeder failing = new TestSeeder("failing", 1, true) {
            @Override
            public void seed() {
                throw new IllegalArgumentException("boom");
            }
        };

        assertThatThrownBy(() -> runner(dev(), seeder("first", 0, true), failing, seeder("after", 2, true)).run(null))
                .hasMessage("boom");
        assertThat(calls).containsExactly("first");
    }

    private SeedRunner runner(MockEnvironment env, DataSeeder... seeders) {
        return new SeedRunner(List.of(seeders), TransactionOperations.withoutTransaction(), env);
    }

    private static MockEnvironment dev() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev", SeedRunner.PROFILE);
        return env;
    }

    private DataSeeder seeder(String name, int order, boolean shouldRun) {
        return new TestSeeder(name, order, shouldRun);
    }

    private class TestSeeder implements DataSeeder {
        private final String name;
        private final int order;
        private final boolean shouldRun;

        TestSeeder(String name, int order, boolean shouldRun) {
            this.name = name;
            this.order = order;
            this.shouldRun = shouldRun;
        }

        @Override
        public int order() {
            return order;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public boolean shouldRun() {
            return shouldRun;
        }

        @Override
        public void seed() {
            calls.add(name);
        }
    }
}
