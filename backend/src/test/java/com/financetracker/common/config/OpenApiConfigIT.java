package com.financetracker.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.financetracker.support.TestcontainersConfiguration;

/**
 * Checks what the API docs contain. That they are reachable without credentials is SecurityConfigIT's job.
 * No authentication is sent here either, so these requests would also fail if the docs stopped being public.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiConfigIT {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void spec_hasOurInfo() {
        assertThat(mvc.get().uri("/v3/api-docs"))
                .hasStatusOk()
                .bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$.info.title").isEqualTo("Finance Tracker API");
                    assertThat(json).extractingPath("$.info.version").isEqualTo(OpenApiConfig.API_VERSION);
                });
    }

    @Test
    void spec_descriptionStatesTheConventions() {
        assertThat(mvc.get().uri("/v3/api-docs"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.info.description").asString()
                .contains("EUR")                // the currency comes from app.currency
                .contains("0-based")
                .contains("RFC 9457");
    }

    @Test
    void spec_isOpenApi3() {
        assertThat(mvc.get().uri("/v3/api-docs"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.openapi").asString().startsWith("3.");
    }

    @Test
    void spec_describesEveryCategoryEndpoint() {
        assertThat(mvc.get().uri("/v3/api-docs"))
                .hasStatusOk()
                .bodyJson()
                .satisfies(json -> {
                    // All five operations exist, under the Categories tag, each with a summary.
                    for (String operation : new String[] {
                            "['/api/categories'].get", "['/api/categories'].post",
                            "['/api/categories/{id}'].get", "['/api/categories/{id}'].put",
                            "['/api/categories/{id}'].delete"}) {
                        assertThat(json).extractingPath("$.paths" + operation + ".tags[0]").isEqualTo("Categories");
                        assertThat(json).extractingPath("$.paths" + operation + ".summary").asString().isNotBlank();
                    }
                    // Status codes springdoc can't infer from the method signature.
                    assertThat(json).extractingPath("$.paths['/api/categories'].post.responses").asMap()
                            .containsKeys("201", "400", "409").doesNotContainKey("200");
                    assertThat(json).extractingPath("$.paths['/api/categories/{id}'].get.responses").asMap()
                            .containsKeys("200", "404");
                    assertThat(json).extractingPath("$.paths['/api/categories/{id}'].put.responses").asMap()
                            .containsKeys("200", "400", "404", "409");
                    assertThat(json).extractingPath("$.paths['/api/categories/{id}'].delete.responses").asMap()
                            .containsKeys("204", "404", "409");
                    assertThat(json).extractingPath("$.paths['/api/categories'].get.responses").asMap()
                            .containsKeys("200", "400");
                });
    }

    @Test
    void swaggerUi_shortcutRedirectsToTheUi() {
        assertThat(mvc.get().uri("/swagger-ui.html"))
                .hasStatus3xxRedirection()
                .headers().hasValue("Location", "/swagger-ui/index.html");
    }

    @Test
    void swaggerUi_pageLoads() {
        assertThat(mvc.get().uri("/swagger-ui/index.html"))
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.TEXT_HTML)
                .bodyText().contains("Swagger UI");
    }
}
