package com.financetracker;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.financetracker.support.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class FinanceTrackerApplicationIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

	@Test
	void contextLoads() {
        // Passes if the applicationContext starts:
        // config, Security, Flyway and Hibernate validation all succeeded
	}

    @Test
    void flywayCreatesAppUsersTable() {
        Integer tables = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name = 'app_users'
                """, Integer.class);

        assertThat(tables).isEqualTo(1);
    }

    @Test
    void flywayRecordsV1AsSuccessful() {
        Boolean success = jdbcTemplate.queryForObject("""
                SELECT success
                FROM flyway_schema_history
                WHERE version = '1'
                """, Boolean.class);

        assertThat(success).isTrue();
    }

}
