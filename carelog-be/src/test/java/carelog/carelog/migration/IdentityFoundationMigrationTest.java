package carelog.carelog.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 전체 Flyway Migration이 (1) 신규 빈 DB, (2) 기존(ddl-auto:update로 만들어진) Legacy Schema
 * 양쪽에서 안전한지 raw JDBC로 직접 검증한다. Spring 컨텍스트 없이 Flyway Java API + Testcontainers만
 * 사용해 Migration 자체의 정합성에만 집중한다(JUnit Jupiter Testcontainers 확장 모듈 없이 수동 lifecycle 관리).
 */
class IdentityFoundationMigrationTest {

    static PostgreSQLContainer<?> POSTGRES;

    @BeforeAll
    static void startContainer() {
        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
        POSTGRES.start();
    }

    @AfterAll
    static void stopContainer() {
        POSTGRES.stop();
    }

    @DisplayName("신규 빈 DB는 V1부터 전체 Migration이 예외 없이 적용된다")
    @Test
    void freshEmptyDatabase_migratesAllVersionsCleanly() throws Exception {
        String jdbcUrl = createDatabase("fresh_db");

        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, POSTGRES.getUsername(), POSTGRES.getPassword())
                .load();
        var result = flyway.migrate();

        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isEqualTo(8);

        try (Connection conn = DriverManager.getConnection(jdbcUrl, POSTGRES.getUsername(), POSTGRES.getPassword())) {
            assertTableExists(conn, "users");
            assertTableExists(conn, "refresh_token");
            assertTableExists(conn, "relations");
            assertTableExists(conn, "journal_templates");
            assertTableExists(conn, "relation_journals");
            assertTableExists(conn, "platform_accounts");
            assertTableExists(conn, "password_credentials");
            assertTableExists(conn, "external_identities");
            assertTableExists(conn, "product_clients");
            assertTableExists(conn, "customer_events");
            assertColumnExists(conn, "users", "account_id");
            assertColumnExists(conn, "users", "customer_memo");
            assertColumnIsNullable(conn, "users", "customer_memo");
            assertColumnExists(conn, "refresh_token", "account_id");
        }
    }

    @DisplayName("기존(V1 상당) Legacy Schema에 실제 데이터가 있을 때 baseline-on-migrate로 V2 이후만 적용되고 " +
            "credential-bearing MANAGER만 Backfill되며 CUSTOMER는 제외된다")
    @Test
    void legacySchemaWithData_baselinesThenBackfillsCredentialBearingManagersOnly() throws Exception {
        String jdbcUrl = createDatabase("legacy_db");

        // 1) "기존 운영 DB"를 시뮬레이션: V1만 raw로 적용(ddl-auto:update가 만들어 둔 것과 동일한 스키마)
        applyV1Directly(jdbcUrl);

        UUID managerPublicId = UUID.randomUUID();
        UUID managerOrgId = UUID.randomUUID();
        UUID customerPublicId = UUID.randomUUID();
        UUID customerOrgId = UUID.randomUUID();
        String bcryptHash = "$2a$10$abcdefghijklmnopqrstuv.wxyzABCDEFGHIJKLMNOPQRSTUVWXYZ01";

        try (Connection conn = DriverManager.getConnection(jdbcUrl, POSTGRES.getUsername(), POSTGRES.getPassword())) {
            insertLegacyUser(conn, managerPublicId, managerOrgId, "legacy-manager", "legacy-manager@example.com",
                    bcryptHash, "MANAGER", "PHYSICAL_THERAPIST");
            insertLegacyUser(conn, customerPublicId, customerOrgId, null, null, null, "CUSTOMER", null);
        }

        // 2) baseline-on-migrate: 기존 비어있지 않은 DB를 V1 기준선으로 인식시키고 V2 이후만 적용
        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, POSTGRES.getUsername(), POSTGRES.getPassword())
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .load();
        var result = flyway.migrate();

        assertThat(result.success).isTrue();
        // baseline이 V1을 흡수하므로 실행되는 건 V2~V8 7개뿐이어야 한다.
        assertThat(result.migrationsExecuted).isEqualTo(7);

        try (Connection conn = DriverManager.getConnection(jdbcUrl, POSTGRES.getUsername(), POSTGRES.getPassword())) {
            // MANAGER Account 수 == Credential 수 == 1, CUSTOMER Account 수 == 0
            assertThat(countRows(conn, "SELECT COUNT(*) FROM platform_accounts")).isEqualTo(1);
            assertThat(countRows(conn, "SELECT COUNT(*) FROM password_credentials")).isEqualTo(1);

            // 기존 MANAGER publicId == platformAccount.id
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, status, primary_email FROM platform_accounts WHERE id = ?")) {
                ps.setObject(1, managerPublicId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getObject("id", UUID.class)).isEqualTo(managerPublicId);
                    assertThat(rs.getString("status")).isEqualTo("ACTIVE");
                    assertThat(rs.getString("primary_email")).isEqualTo("legacy-manager@example.com");
                }
            }

            // password_credentials.login_id == users.user_id, password_hash == 기존 BCrypt Hash 그대로
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT login_id, password_hash FROM password_credentials WHERE account_id = ?")) {
                ps.setObject(1, managerPublicId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("login_id")).isEqualTo("legacy-manager");
                    assertThat(rs.getString("password_hash")).isEqualTo(bcryptHash);
                }
            }

            // users.account_id는 MANAGER에만 연결, CUSTOMER는 null
            assertThat(getUuidColumn(conn, managerPublicId, "account_id")).isEqualTo(managerPublicId);
            assertThat(getUuidColumn(conn, customerPublicId, "account_id")).isNull();

            // V7은 기존 Customer의 식별자·이름을 보존하고 메모리는 backfill하지 않는다.
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT public_id, name, customer_memo FROM users WHERE public_id = ?")) {
                ps.setObject(1, customerPublicId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getObject("public_id", UUID.class)).isEqualTo(customerPublicId);
                    assertThat(rs.getString("name")).isEqualTo("Legacy Test " + customerPublicId);
                    assertThat(rs.getString("customer_memo")).isNull();
                }
            }

            // orphan account/credential 없음: platform_accounts 수 == password_credentials 수 == users.account_id 연결 수
            assertThat(countRows(conn,
                    "SELECT COUNT(*) FROM platform_accounts pa LEFT JOIN password_credentials pc ON pc.account_id = pa.id WHERE pc.account_id IS NULL"))
                    .isEqualTo(0);
            assertThat(countRows(conn,
                    "SELECT COUNT(*) FROM users u WHERE u.account_id IS NOT NULL AND u.account_id NOT IN (SELECT id FROM platform_accounts)"))
                    .isEqualTo(0);
        }
    }

    @DisplayName("V6은 CARELOG WEB·MOBILE 기본 Client를 한 번 등록하고 clientId·enum 제약을 강제한다")
    @Test
    void productClientRegistryMigration_seedsCarelogWebAndMobileAndEnforcesConstraints() throws Exception {
        String jdbcUrl = createDatabase("product_client_registry_db");
        Flyway.configure().dataSource(jdbcUrl, POSTGRES.getUsername(), POSTGRES.getPassword()).load().migrate();

        OffsetDateTime now = OffsetDateTime.now();
        try (Connection conn = DriverManager.getConnection(jdbcUrl, POSTGRES.getUsername(), POSTGRES.getPassword())) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT product, channel, enabled FROM product_clients WHERE client_id = ?")) {
                ps.setString(1, "carelog-web");
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("product")).isEqualTo("CARELOG");
                    assertThat(rs.getString("channel")).isEqualTo("WEB");
                    assertThat(rs.getBoolean("enabled")).isTrue();
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT product, channel, enabled FROM product_clients WHERE client_id = ?")) {
                ps.setString(1, "carelog-mobile");
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("product")).isEqualTo("CARELOG");
                    assertThat(rs.getString("channel")).isEqualTo("MOBILE");
                    assertThat(rs.getBoolean("enabled")).isTrue();
                }
            }

            assertThatThrownBy(() -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO product_clients (client_id, product, channel, enabled, created_at, updated_at) " +
                                "VALUES (?, ?, ?, ?, ?, ?)")) {
                    ps.setString(1, "carelog-web");
                    ps.setString(2, "CARELOG");
                    ps.setString(3, "WEB");
                    ps.setBoolean(4, true);
                    ps.setObject(5, now);
                    ps.setObject(6, now);
                    ps.executeUpdate();
                }
            }).isInstanceOfSatisfying(SQLException.class, exception ->
                    assertThat(exception.getSQLState()).isEqualTo("23505"));

            assertThatThrownBy(() -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO product_clients (client_id, product, channel, enabled, created_at, updated_at) " +
                                "VALUES (?, ?, ?, ?, ?, ?)")) {
                    ps.setString(1, "invalid-channel");
                    ps.setString(2, "CARELOG");
                    ps.setString(3, "DESKTOP");
                    ps.setBoolean(4, true);
                    ps.setObject(5, now);
                    ps.setObject(6, now);
                    ps.executeUpdate();
                }
            }).isInstanceOfSatisfying(SQLException.class, exception ->
                    assertThat(exception.getSQLState()).isEqualTo("23514"));

            assertThatThrownBy(() -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO product_clients (client_id, product, channel, enabled, created_at, updated_at) " +
                                "VALUES (?, ?, ?, ?, ?, ?)")) {
                    ps.setString(1, "invalid client id");
                    ps.setString(2, "CARELOG");
                    ps.setString(3, "WEB");
                    ps.setBoolean(4, true);
                    ps.setObject(5, now);
                    ps.setObject(6, now);
                    ps.executeUpdate();
                }
            }).isInstanceOfSatisfying(SQLException.class, exception ->
                    assertThat(exception.getSQLState()).isEqualTo("23514"));
        }
    }

    @DisplayName("V5는 refresh_token.user_id(loginId)로 account_id를 백필하고, 고아 행을 삭제하며 " +
            "account_id를 필수 키로 강제한다")
    @Test
    void v5_backfillsMappedRowsDeletesOrphansAndRequiresAccountId() throws Exception {
        String jdbcUrl = createDatabase("refresh_token_backfill_db");
        applyV1Directly(jdbcUrl);

        UUID managerPublicId = UUID.randomUUID();
        UUID managerOrgId = UUID.randomUUID();
        try (Connection conn = DriverManager.getConnection(jdbcUrl, POSTGRES.getUsername(), POSTGRES.getPassword())) {
            insertLegacyUser(conn, managerPublicId, managerOrgId, "legacy-manager", "legacy-manager@example.com",
                    "hash1", "MANAGER", "PHYSICAL_THERAPIST");
        }

        // V4까지 적용해 password_credentials(login_id="legacy-manager", account_id=managerPublicId)를 만든 뒤,
        // V5 적용 전 상태의 refresh_token 두 행을 raw INSERT로 시뮬레이션한다.
        Flyway flywayToV4 = Flyway.configure()
                .dataSource(jdbcUrl, POSTGRES.getUsername(), POSTGRES.getPassword())
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .target("4")
                .load();
        flywayToV4.migrate();

        OffsetDateTime now = OffsetDateTime.now();
        try (Connection conn = DriverManager.getConnection(jdbcUrl, POSTGRES.getUsername(), POSTGRES.getPassword())) {
            // 매핑 가능: user_id가 실제 password_credentials.login_id와 일치
            insertLegacyRefreshToken(conn, "mapped-refresh-token", "legacy-manager", now.plusDays(7));
            // 매핑 불가(고아 행): user_id가 어떤 login_id와도 일치하지 않음(예: 탈퇴/오염된 과거 세션)
            insertLegacyRefreshToken(conn, "orphan-refresh-token", "deleted-user-no-longer-exists", now.plusDays(7));
        }

        Flyway flywayFull = Flyway.configure()
                .dataSource(jdbcUrl, POSTGRES.getUsername(), POSTGRES.getPassword())
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .load();
        var result = flywayFull.migrate();

        assertThat(result.success).isTrue();

        try (Connection conn = DriverManager.getConnection(jdbcUrl, POSTGRES.getUsername(), POSTGRES.getPassword())) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT account_id FROM refresh_token WHERE refresh_token = ?")) {
                ps.setString(1, "mapped-refresh-token");
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getObject("account_id", UUID.class)).isEqualTo(managerPublicId);
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM refresh_token WHERE refresh_token = ?")) {
                ps.setString(1, "orphan-refresh-token");
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isZero();
                }
            }

            // account_id 없는 신규 세션은 DB 불변식 위반으로 저장되면 안 된다.
            assertThatThrownBy(() -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO refresh_token (refresh_token, token_expires_at, created_at, updated_at) " +
                                "VALUES (?, ?, ?, ?)")) {
                    ps.setString(1, "missing-account-id-refresh-token");
                    ps.setObject(2, now.plusDays(7));
                    ps.setObject(3, now);
                    ps.setObject(4, now);
                    ps.executeUpdate();
                }
            }).isInstanceOf(Exception.class);

            // user_id의 NOT NULL은 해제되지만 account_id가 있으면 신규 세션은 저장 가능해야 한다.
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO refresh_token (refresh_token, account_id, token_expires_at, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, ?)")) {
                ps.setString(1, "no-login-id-refresh-token");
                ps.setObject(2, managerPublicId);
                ps.setObject(3, now.plusDays(7));
                ps.setObject(4, now);
                ps.setObject(5, now);
                ps.executeUpdate();
            }

            // 존재하지 않는 accountId는 FK가 차단하고, account_id 조회 인덱스는 유지돼야 한다.
            assertThatThrownBy(() -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO refresh_token (refresh_token, account_id, token_expires_at, created_at, updated_at) " +
                                "VALUES (?, ?, ?, ?, ?)")) {
                    ps.setString(1, "unknown-account-refresh-token");
                    ps.setObject(2, UUID.randomUUID());
                    ps.setObject(3, now.plusDays(7));
                    ps.setObject(4, now);
                    ps.setObject(5, now);
                    ps.executeUpdate();
                }
            }).isInstanceOf(Exception.class);
            assertThat(countRows(conn,
                    "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'public' " +
                            "AND tablename = 'refresh_token' AND indexname = 'idx_refresh_token_account_id'"))
                    .isEqualTo(1);
        }
    }

    @DisplayName("중복 login_id가 존재하면 V4 제약조건 부여 단계에서 Migration이 실패한다")
    @Test
    void duplicateLoginId_failsMigrationAtConstraintStep() throws Exception {
        String jdbcUrl = createDatabase("dup_loginid_db");
        applyV1Directly(jdbcUrl);

        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID org1 = UUID.randomUUID();
        UUID org2 = UUID.randomUUID();
        // 방어적 시나리오 검증용: 동일 user_id를 가진 두 MANAGER 행이 있었다고 가정(users.user_id unique라 실제로는
        // 불가능하지만, backfill 이후 login_id 유일성 제약이 실제로 걸린다는 것 자체를 검증하기 위해
        // password_credentials에 직접 중복을 주입해 V4가 이를 막아세우는지 확인한다).
        try (Connection conn = DriverManager.getConnection(jdbcUrl, POSTGRES.getUsername(), POSTGRES.getPassword())) {
            insertLegacyUser(conn, p1, org1, "dup-user", "dup1@example.com", "hash1", "MANAGER", "PHYSICAL_THERAPIST");
            insertLegacyUser(conn, p2, org2, "dup-user-2", "dup2@example.com", "hash2", "MANAGER", "PHYSICAL_THERAPIST");
        }

        Flyway flywayToV3 = Flyway.configure()
                .dataSource(jdbcUrl, POSTGRES.getUsername(), POSTGRES.getPassword())
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .target("3")
                .load();
        flywayToV3.migrate();

        // V3 적용 후, 의도적으로 두 번째 credential의 login_id를 첫 번째와 동일하게 만들어 중복을 주입한다.
        try (Connection conn = DriverManager.getConnection(jdbcUrl, POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement st = conn.createStatement()) {
            st.executeUpdate("UPDATE password_credentials SET login_id = 'dup-user' WHERE account_id = '" + p2 + "'");
        }

        Flyway flywayFull = Flyway.configure()
                .dataSource(jdbcUrl, POSTGRES.getUsername(), POSTGRES.getPassword())
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .load();

        assertThatThrownBy(flywayFull::migrate)
                .as("중복 login_id가 있으면 V4의 unique 제약 추가가 실패해야 한다")
                .isInstanceOf(Exception.class);
    }

    private void insertLegacyRefreshToken(
            Connection conn, String refreshToken, String userId, OffsetDateTime expiresAt
    ) throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO refresh_token (refresh_token, user_id, token_expires_at, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, refreshToken);
            ps.setString(2, userId);
            ps.setObject(3, expiresAt);
            ps.setObject(4, now);
            ps.setObject(5, now);
            ps.executeUpdate();
        }
    }

    private String createDatabase(String dbName) throws Exception {
        try (Connection admin = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement st = admin.createStatement()) {
            st.executeUpdate("CREATE DATABASE " + dbName);
        }
        String base = POSTGRES.getJdbcUrl();
        return base.substring(0, base.lastIndexOf('/') + 1) + dbName;
    }

    private void applyV1Directly(String jdbcUrl) throws Exception {
        String sql = new String(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(
                "src/main/resources/db/migration/V1__baseline_current_schema.sql")));
        try (Connection conn = DriverManager.getConnection(jdbcUrl, POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    private void insertLegacyUser(
            Connection conn, UUID publicId, UUID organizationId, String userId, String email,
            String password, String role, String managerType
    ) throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO users (public_id, organization_id, user_id, email, password, name, role, manager_type, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setObject(1, publicId);
            ps.setObject(2, organizationId);
            ps.setString(3, userId);
            ps.setString(4, email);
            ps.setString(5, password);
            ps.setString(6, "Legacy Test " + publicId);
            ps.setString(7, role);
            ps.setString(8, managerType);
            ps.setObject(9, now);
            ps.setObject(10, now);
            ps.executeUpdate();
        }
    }

    private void assertTableExists(Connection conn, String table) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM information_schema.tables WHERE table_name = ?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as("table %s should exist", table).isTrue();
            }
        }
    }

    private void assertColumnExists(Connection conn, String table, String column) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM information_schema.columns WHERE table_name = ? AND column_name = ?")) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as("column %s.%s should exist", table, column).isTrue();
            }
        }
    }

    private void assertColumnIsNullable(Connection conn, String table, String column) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT is_nullable FROM information_schema.columns WHERE table_name = ? AND column_name = ?")) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("is_nullable")).isEqualTo("YES");
            }
        }
    }

    private int countRows(Connection conn, String sql) throws Exception {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private UUID getUuidColumn(Connection conn, UUID publicId, String column) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT " + column + " FROM users WHERE public_id = ?")) {
            ps.setObject(1, publicId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return (UUID) rs.getObject(column);
            }
        }
    }
}
