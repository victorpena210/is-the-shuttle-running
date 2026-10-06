package technology.renatus.routepulse.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import technology.renatus.routepulse.support.MySqlIntegrationTestSupport;

class MigrationIntegrationTests extends MySqlIntegrationTestSupport {

    @Test
    void allMigrationsRunAgainstABlankMySqlDatabase() {
        assertNotNull(flyway.info().current());

        assertEquals(
                "35",
                flyway.info().current().getVersion().getVersion()
        );

        Integer successfulMigrations = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE success = 1
                """,
                Integer.class
        );

        Integer failedMigrations = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE success = 0
                """,
                Integer.class
        );

        assertEquals(35, successfulMigrations);
        assertEquals(0, failedMigrations);
    }
}
