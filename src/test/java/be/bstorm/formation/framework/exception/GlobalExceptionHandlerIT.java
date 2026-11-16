package be.bstorm.formation.framework.exception;

import be.bstorm.formation.TestcontainersConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;

/** Format {@code ProblemDetail} de toutes les erreurs, sans fuite d'information technique. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, GlobalExceptionHandlerIT.ProbeController.class})
class GlobalExceptionHandlerIT {

    @Autowired
    MockMvcTester mvc;

    @Test
    @WithMockUser
    void validationErrorListsFieldsWithoutEchoingValues() {
        MvcTestResult result = mvc.post().uri("/api/test/probe").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}").exchange();

        assertThat(result).hasStatus(400).hasContentType("application/problem+json");
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Validation Failed");
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("name");
    }

    @Test
    @WithMockUser
    void unknownJsonFieldIsRejected() {
        assertThat(mvc.post().uri("/api/test/probe").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"ok\",\"role\":\"ADMIN\"}")).hasStatus(400);
    }

    @Test
    @WithMockUser
    void businessExceptionKeepsItsStatusAndTitle() {
        MvcTestResult result = mvc.get().uri("/api/test/conflict").exchange();

        assertThat(result).hasStatus(409);
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Already Exists");
    }

    @Test
    @WithMockUser
    void unexpectedErrorHidesInternalsAndReturnsErrorId() throws Exception {
        MvcTestResult result = mvc.get().uri("/api/test/boom").exchange();

        assertThat(result).hasStatus(500);
        assertThat(result).bodyJson().extractingPath("$.errorId").isNotNull();
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("secret-internal-detail").doesNotContain("IllegalStateException")
                .doesNotContain("at be.bstorm");
    }

    @Test
    void anonymousCallerIsUnauthorized() {
        assertThat(mvc.get().uri("/api/test/admin")).hasStatus(401);
    }

    @Test
    @WithMockUser(roles = "USER")
    void authenticatedCallerWithoutRoleIsForbidden() {
        MvcTestResult result = mvc.get().uri("/api/test/admin").exchange();

        assertThat(result).hasStatus(403);
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Forbidden");
    }

    record ProbeRequest(@NotBlank @Size(max = 50) String name) {}

    @TestConfiguration
    @RestController
    static class ProbeController {

        @PostMapping("/api/test/probe")
        ProbeRequest probe(@Valid @RequestBody ProbeRequest request) {
            return request;
        }

        @GetMapping("/api/test/conflict")
        void conflict() {
            throw new ConflictException("Already Exists", "The resource already exists.");
        }

        @GetMapping("/api/test/boom")
        void boom() {
            throw new IllegalStateException("secret-internal-detail");
        }

        @GetMapping("/api/test/admin")
        @PreAuthorize("hasRole('ADMIN')")
        void admin() {
        }
    }
}
