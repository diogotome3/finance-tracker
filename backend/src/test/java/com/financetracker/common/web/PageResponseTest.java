package com.financetracker.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;
import org.springframework.boot.test.json.JsonContent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/**
 * PageResponse is the only page shape the API exposes. These tests fix its JSON and prove none of
 * Spring Data's Page internals (pageable, sort, first, last, ...) leak into it.
 */
@JsonTest
class PageResponseTest {

    @Autowired
    private JacksonTester<PageResponse<String>> json;

    // 5 items, 2 per page, second page (0-based) -> holds items 3 and 4.
    private final Page<String> secondPage = new PageImpl<>(List.of("c", "d"), PageRequest.of(1, 2), 5);

    @Test
    void from_copiesTheFieldsFromSpringsPage() {
        PageResponse<String> response = PageResponse.from(secondPage);

        assertThat(response.content()).containsExactly("c", "d");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
    }

    @Test
    void from_worksWithAMappedPage() {
        // How controllers will use it: entities -> DTOs with Page.map, then wrap.
        PageResponse<String> response = PageResponse.from(secondPage.map(String::toUpperCase));

        assertThat(response.content()).containsExactly("C", "D");
        assertThat(response.totalElements()).isEqualTo(5);
    }

    @Test
    void emptyPage_hasEmptyContentAndZeroTotals() {
        PageResponse<String> response = PageResponse.from(Page.empty(PageRequest.of(0, 20)));

        assertThat(response.content()).isEmpty();
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.totalElements()).isZero();
        assertThat(response.totalPages()).isZero();
    }

    @Test
    void content_isImmutable() {
        List<String> source = new ArrayList<>(List.of("c", "d"));
        PageResponse<String> response = PageResponse.from(new PageImpl<>(source, PageRequest.of(1, 2), 5));

        source.add("x");  // changing the original list must not change the response

        assertThat(response.content()).containsExactly("c", "d");
        assertThatThrownBy(() -> response.content().add("y")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void isSerializedWithExactlyTheContractFields() throws Exception {
        JsonContent<PageResponse<String>> written = json.write(PageResponse.from(secondPage));

        // STRICT: no missing and no extra fields.
        assertThat(written).isStrictlyEqualToJson("""
                {
                  "content": ["c", "d"],
                  "page": 1,
                  "size": 2,
                  "totalElements": 5,
                  "totalPages": 3
                }
                """);
    }

    @Test
    void doesNotLeakSpringDataInternals() throws Exception {
        JsonContent<PageResponse<String>> written = json.write(PageResponse.from(secondPage));

        assertThat(written).doesNotHaveJsonPath("$.pageable");
        assertThat(written).doesNotHaveJsonPath("$.sort");
        assertThat(written).doesNotHaveJsonPath("$.number");
        assertThat(written).doesNotHaveJsonPath("$.first");
        assertThat(written).doesNotHaveJsonPath("$.last");
    }
}
