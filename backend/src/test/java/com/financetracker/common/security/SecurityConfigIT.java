package com.financetracker.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;

import com.financetracker.support.TestcontainersConfiguration;

/**
 * Sends requests through the real security filter chain (MockMvc, no server).
 * /api/categories doesn't exist yet: CORS preflights are answered by the CORS filter before any controller lookup.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigIT {

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";   // app.cors.allowed-origins default
    private static final String OTHER_ORIGIN = "http://evil.example";

    @Autowired
    private MockMvcTester mvc;

    // --- CORS ---

    @Test
    void preflightFromAllowedOrigin_isAccepted() {
        assertThat(preflight(ALLOWED_ORIGIN, "POST"))
                .hasStatusOk()
                .hasHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN);
    }

    @Test
    void preflightFromOtherOrigin_isRejected() {
        assertThat(preflight(OTHER_ORIGIN, "POST"))
                .hasStatus(HttpStatus.FORBIDDEN)
                .doesNotContainHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
    }

    @Test
    void preflightForMethodNotAllowed_isRejected() {
        // Only GET, POST, PUT and DELETE are allowed.
        assertThat(preflight(ALLOWED_ORIGIN, "PATCH"))
                .hasStatus(HttpStatus.FORBIDDEN)
                .doesNotContainHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
    }

    @Test
    void preflightForHeaderNotAllowed_isRejected() {
        // Only Content-Type is allowed.
        assertThat(preflight(ALLOWED_ORIGIN, "POST").header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-Custom"))
                .hasStatus(HttpStatus.FORBIDDEN)
                .doesNotContainHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
    }

    // --- Open endpoints: reachable without credentials ---

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/health", "/v3/api-docs", "/swagger-ui/index.html"})
    void publicEndpoints_areReachableWithoutCredentials(String path) {
        assertThat(mvc.get().uri(path)).hasStatusOk();
    }

    @Test
    void swaggerUiShortcut_redirectsToTheUi() {
        assertThat(mvc.get().uri("/swagger-ui.html")).hasStatus3xxRedirection();
    }

    @Test
    void apiPaths_passSecurity() {
        // 404, not 401/403: the request got through security; there's just no endpoint there yet.
        assertThat(mvc.get().uri("/api/does-not-exist")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void writesToApi_needNoCsrfToken() {
        // With CSRF enabled this POST would be rejected with 403 for missing a token.
        assertThat(mvc.post().uri("/api/does-not-exist").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void errorPage_isNotBlocked() {
        // Exceptions are forwarded to /error. If security blocked it, every 500 would be turned into a 403.
        assertThat(mvc.get().uri("/error"))
                .satisfies(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403));
    }

    @Test
    void deniedRequests_doNotCreateASession() {
        // Without STATELESS, Spring Security saves a denied request in a new HTTP session (to resume it after a
        // login). Stateless means no session is ever created, so no JSESSIONID cookie.
        assertThat(mvc.get().uri("/some/other/path"))
                .satisfies(result -> assertThat(result.getRequest().getSession(false)).isNull());
    }

    // --- Everything else is closed ---

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/env", "/some/other/path"})
    void everythingElse_isDenied(String path) {
        assertThat(mvc.get().uri(path)).hasStatus(HttpStatus.FORBIDDEN);
    }

    private MockMvcRequestBuilder preflight(String origin, String method) {
        return mvc.options().uri("/api/categories")
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method);
    }
}
