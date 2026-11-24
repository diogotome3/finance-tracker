package com.financetracker.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

/**
 * The top of the generated OpenAPI spec (/v3/api-docs) and of Swagger UI (/swagger-ui.html).
 * springdoc builds the paths and schemas from the controllers; this bean only adds what it can't infer.
 */
@Configuration
public class OpenApiConfig {

    static final String API_VERSION = "1.0";

    @Bean
    OpenAPI financeTrackerOpenApi(AppProperties properties) {
        return new OpenAPI().info(new Info()
                .title("Finance Tracker API")
                .version(API_VERSION)
                .description(description(properties.currency().getCurrencyCode())));
    }

    // Swagger UI renders the description as Markdown.
    private static String description(String currency) {
        return """
                REST API for a personal finance tracker: categories, income and expense transactions, \
                monthly budgets and spending statistics.

                **Conventions**
                - **Amounts** are plain decimal numbers in %s, e.g. `1000.00`.
                - **Dates** are ISO-8601, e.g. `2026-11-21`. **Timestamps** are ISO-8601 in UTC, \
                e.g. `2026-11-21T10:15:30Z`.
                - **Lists** are paginated with `?page=0&size=20`. Pages are **0-based**, and each response holds \
                `content`, `page`, `size`, `totalElements` and `totalPages`.
                - **Errors** are [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) Problem Details \
                (`application/problem+json`). Validation errors add an `errors` list of `{field, message}`.
                """.formatted(currency);
    }
}
