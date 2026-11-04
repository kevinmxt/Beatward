package dev.beatward.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class MonitorOwnershipMigrationIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @AfterEach
    void resetSchema() throws Exception {
        try (Connection connection = open();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA public CASCADE");
            statement.execute("CREATE SCHEMA public");
        }
    }

    @Test
    void backfillsExistingMonitorsToTheActiveAdmin() throws Exception {
        migrateTo("14");

        long adminId;
        try (Connection connection = open()) {
            adminId = insertAdmin(connection, "legacy-admin", "ACTIVE");
            insertMonitor(connection, "legacy-monitor");
        }

        migrateToLatest();

        try (Connection connection = open();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT user_id FROM monitor WHERE name = 'legacy-monitor'")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getLong("user_id")).isEqualTo(adminId);
        }

        try (Connection connection = open()) {
            DatabaseMetaData metadata = connection.getMetaData();
            try (ResultSet columns = metadata.getColumns(null, "public", "monitor", "user_id")) {
                assertThat(columns.next()).isTrue();
                assertThat(columns.getInt("NULLABLE")).isEqualTo(DatabaseMetaData.columnNullable);
            }
        }

        assertThat(queryInt("SELECT count(*) FROM pg_indexes "
                + "WHERE schemaname = 'public' AND indexname = 'idx_monitor_user_enabled'"))
                .isEqualTo(1);
        assertThat(queryInt("""
                SELECT count(*)
                FROM pg_constraint
                WHERE conrelid = 'monitor'::regclass
                  AND contype = 'f'
                  AND confrelid = 'app_user'::regclass
                """))
                .isEqualTo(1);
    }

    @Test
    void failsLoudlyWhenMonitorsExistWithoutAnActiveAdmin() throws Exception {
        migrateTo("14");
        try (Connection connection = open()) {
            insertAdmin(connection, "disabled-admin", "DISABLED");
            insertMonitor(connection, "unowned-monitor");
        }

        assertThatThrownBy(MonitorOwnershipMigrationIT::migrateToLatest)
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("requires an active ADMIN");
    }

    @Test
    void backfillsToTheEarliestActiveAdmin() throws Exception {
        migrateTo("14");
        long expectedAdminId;
        try (Connection connection = open()) {
            insertAdmin(connection, "disabled-admin", "DISABLED");
            expectedAdminId = insertAdmin(connection, "first-active-admin", "ACTIVE");
            insertAdmin(connection, "second-active-admin", "ACTIVE");
            insertMonitor(connection, "multi-admin-monitor");
        }

        migrateToLatest();

        try (Connection connection = open();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT user_id FROM monitor WHERE name = 'multi-admin-monitor'")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getLong("user_id")).isEqualTo(expectedAdminId);
        }
    }

    private static void migrateTo(String version) {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(version))
                .load()
                .migrate();
    }

    private static void migrateToLatest() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private static Connection open() throws Exception {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static int queryInt(String sql) throws Exception {
        try (Connection connection = open();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getInt(1);
        }
    }

    private static long insertAdmin(Connection connection, String username, String status)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO app_user (username, password_hash, role, status, created_at, updated_at)
                VALUES (?, '{bcrypt}$2a$12$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ01234',
                        'ADMIN', ?, NOW(), NOW())
                RETURNING id
                """)) {
            statement.setString(1, username);
            statement.setString(2, status);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getLong("id");
            }
        }
    }

    private static void insertMonitor(Connection connection, String name) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO monitor (name, type, target_url, method, interval_seconds,
                                     connect_timeout_ms, response_timeout_ms,
                                     expected_status_min, expected_status_max, enabled,
                                     current_status, next_check_at, created_at, updated_at)
                VALUES (?, 'HTTP', 'https://example.com/health', 'GET', 60,
                        3000, 10000, 200, 299, true,
                        'UNKNOWN', NOW(), NOW(), NOW())
                """)) {
            statement.setString(1, name);
            statement.executeUpdate();
        }
    }
}
