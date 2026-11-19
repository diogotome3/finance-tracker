package com.financetracker.user;

import static com.financetracker.support.TestData.anAppUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import com.financetracker.common.config.JpaAuditingConfig;
import com.financetracker.support.TestcontainersConfiguration;

// @DataJpaTest loads only the JPA slice and does not pick up @Configuration classes,
// so auditing has to be imported explicitly or the timestamps stay null.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class AppUserRepositoryIT {

    @Autowired
    private AppUserRepository repository;

    @Test
    void save_setsIdAndAuditTimestamps() {
        AppUser user = anAppUser().build();

        AppUser saved = repository.saveAndFlush(user);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void save_rejectsDuplicateEmail() {
        repository.saveAndFlush(anAppUser().withEmail("same@test.com").build());
        AppUser duplicate = anAppUser().withEmail("same@test.com").build();

        // saveAndFlush forces the INSERT now, so the unique constraint fires inside the assertion
        assertThatThrownBy(() -> repository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
