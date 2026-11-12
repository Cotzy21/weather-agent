package no.weatheragent.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Isolated schemas only. SECURITY_QA_JDBC_URL must never point at production. */
@EnabledIfEnvironmentVariable(named = "SECURITY_QA_JDBC_URL", matches = "jdbc:postgresql://127\\.0\\.0\\.1:55439/postgres")
class AccountPrivacyIntegrationTest {
    private DriverManagerDataSource ds;
    private JdbcTemplate jdbc;
    private JdbcTemplate admin;
    private TransactionTemplate tx;
    private String schema;
    private final UUID owner = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    @BeforeEach void setup() throws Exception {
        String url = System.getenv("SECURITY_QA_JDBC_URL");
        admin = new JdbcTemplate(new DriverManagerDataSource(url, "postgres", ""));
        schema = "privacy_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("create schema " + schema);
        ds = new DriverManagerDataSource(url + "?currentSchema=" + schema, "postgres", "");
        jdbc = new JdbcTemplate(ds);
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        for (var table : UserDataEraser.OWNED_TABLES) {
            jdbc.execute("create table " + table.table() + "(id uuid primary key, "
                    + table.ownerColumn() + " uuid not null, note text)");
        }
        jdbc.execute("create table habit_logs(id uuid primary key, habit_id uuid references habits(id), note text)");
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/V21__account_revocation.sql")));
    }

    @AfterEach void cleanup() {
        if (admin != null && schema != null) admin.execute("drop schema " + schema + " cascade");
    }

    private void insert(String table, String column, UUID user) {
        jdbc.update("insert into " + table + "(id," + column + ",note) values (?,?,?)", UUID.randomUUID(), user, user.toString());
    }
    private void erase() { tx.executeWithoutResult(s -> new UserDataEraser(jdbc).eraseAll(owner)); }

    @Test void exportAndDeleteAreScopedAcrossEveryOwnedTable() throws Exception {
        for (var table : UserDataEraser.OWNED_TABLES) {
            insert(table.table(), table.ownerColumn(), owner);
            insert(table.table(), table.ownerColumn(), other);
        }
        var output = new ByteArrayOutputStream();
        tx.executeWithoutResult(s -> {
            try { new UserDataExporter(jdbc, new ObjectMapper()).write(owner, "owner@example.test", output); }
            catch (Exception e) { throw new RuntimeException(e); }
        });
        var exported = new ObjectMapper().readTree(output.toByteArray());
        assertFalse(output.toString().contains(other.toString()));
        for (var table : UserDataEraser.OWNED_TABLES) assertEquals(1, exported.path("data").path(table.table()).size());
        erase();
        for (var table : UserDataEraser.OWNED_TABLES) {
            assertEquals(1, jdbc.queryForObject("select count(*) from " + table.table(), Integer.class));
            assertThrows(org.springframework.dao.DataAccessException.class, () -> insert(table.table(), table.ownerColumn(), owner));
        }
    }

    @Test void writeFinishingBeforeDeletionIsIncludedInErasure() throws Exception {
        var written = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var writer = executor.submit(() -> tx.executeWithoutResult(s -> {
                insert("workouts", "user_id", owner);
                written.countDown();
                await(release);
            }));
            assertTrue(written.await(5, TimeUnit.SECONDS));
            var deletion = executor.submit(this::erase);
            assertThrows(TimeoutException.class, () -> deletion.get(150, TimeUnit.MILLISECONDS));
            release.countDown();
            writer.get(5, TimeUnit.SECONDS);
            deletion.get(5, TimeUnit.SECONDS);
            assertEquals(0, jdbc.queryForObject("select count(*) from workouts", Integer.class));
        } finally { release.countDown(); }
    }

    @Test void writeWaitingBehindDeletionCannotRecreateData() throws Exception {
        insert("workouts", "user_id", owner);
        var erased = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var deletion = executor.submit(() -> tx.executeWithoutResult(s -> {
                new UserDataEraser(jdbc).eraseAll(owner);
                erased.countDown();
                await(release);
            }));
            assertTrue(erased.await(5, TimeUnit.SECONDS));
            var writer = executor.submit(() -> insert("workouts", "user_id", owner));
            assertThrows(TimeoutException.class, () -> writer.get(150, TimeUnit.MILLISECONDS));
            release.countDown();
            deletion.get(5, TimeUnit.SECONDS);
            assertThrows(ExecutionException.class, () -> writer.get(5, TimeUnit.SECONDS));
            assertEquals(0, jdbc.queryForObject("select count(*) from workouts", Integer.class));
        } finally { release.countDown(); }
    }

    @Test void failedDeletionRollsBackTheRevocation() {
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(s -> {
            new UserDataEraser(jdbc).eraseAll(owner);
            throw new IllegalStateException("simulated failure");
        }));
        insert("workouts", "user_id", owner);
        assertEquals(1, jdbc.queryForObject("select count(*) from workouts", Integer.class));
    }

    @Test void failedAuthRemovalsDoNotStarveOtherAccountsAcrossRetryInstances() {
        var remover = org.mockito.Mockito.mock(AuthUserRemover.class);
        for (int i = 0; i < 25; i++) {
            jdbc.update("insert into revoked_accounts(user_id, revoked_at) values (?, current_timestamp)", UUID.randomUUID());
        }
        new AuthDeletionRetry(jdbc, remover).retryPending();
        new AuthDeletionRetry(jdbc, remover).retryPending();
        var attempts = org.mockito.ArgumentCaptor.forClass(UUID.class);
        org.mockito.Mockito.verify(remover, org.mockito.Mockito.times(25)).remove(attempts.capture());
        assertEquals(25, new java.util.HashSet<>(attempts.getAllValues()).size());
        assertEquals(25, jdbc.queryForObject("select count(*) from revoked_accounts where auth_delete_attempted_at is not null", Integer.class));
    }

    @Test void habitLogsAreExportedAndDeletedOnlyForTheirOwner() throws Exception {
        UUID mine = UUID.randomUUID(), theirs = UUID.randomUUID();
        jdbc.update("insert into habits(id,user_id) values (?,?),(?,?)", mine, owner, theirs, other);
        jdbc.update("insert into habit_logs(id,habit_id,note) values (?,?,?),(?,?,?)",
                UUID.randomUUID(), mine, "own log", UUID.randomUUID(), theirs, "other log");
        var output = new ByteArrayOutputStream();
        tx.executeWithoutResult(s -> {
            try { new UserDataExporter(jdbc, new ObjectMapper()).write(owner, "owner@example.test", output); }
            catch (Exception e) { throw new RuntimeException(e); }
        });
        var logs = new ObjectMapper().readTree(output.toByteArray()).path("data").path("habit_logs");
        assertEquals(1, logs.size());
        assertEquals("own log", logs.get(0).path("note").asText());
        erase();
        assertEquals("other log", jdbc.queryForObject("select note from habit_logs", String.class));
    }

    @Test void recordOwnershipCannotBeChanged() {
        insert("workouts", "user_id", owner);
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("update workouts set user_id = ? where user_id = ?", other, owner));
        assertEquals(owner, jdbc.queryForObject("select user_id from workouts", UUID.class));
    }

    @Test void provenanceMigrationDoesNotGuessThatUnmarkedHistoricalDataIsManual() throws Exception {
        jdbc.execute("alter table workouts add column notes text, add column content jsonb not null default '{}'::jsonb");
        insert("workouts", "user_id", owner);
        insert("workouts", "user_id", other);
        jdbc.update("update workouts set notes = 'Importert fra Garmin (FIT)' where user_id = ?", other);
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/V22__workout_provenance.sql")));
        assertEquals("UNKNOWN", jdbc.queryForObject("select source from workouts where user_id = ?", String.class, owner));
        assertEquals("GARMIN", jdbc.queryForObject("select source from workouts where user_id = ?", String.class, other));
        UUID newOwner = UUID.randomUUID();
        insert("workouts", "user_id", newOwner);
        assertEquals("MANUAL", jdbc.queryForObject("select source from workouts where user_id = ?", String.class, newOwner));
    }

    @Test void aiPermissionIsExplicitOwnerScopedAndRevocable() {
        jdbc.execute("alter table user_settings alter column id set default gen_random_uuid(), "
                + "add column key varchar(40), add column value jsonb, add column updated_at timestamptz, "
                + "add unique (user_id, key)");
        var permission = new TrainingAiPermission(jdbc);
        assertFalse(permission.get(owner).allowed());
        assertThrows(TrainingAiPermission.ConsentRequired.class, () -> permission.require(owner, "en"));
        int version = permission.get(owner).version();
        permission.record(owner, version, true);
        assertTrue(permission.get(owner).allowed());
        assertFalse(permission.get(other).allowed());
        permission.record(owner, version, false);
        assertFalse(permission.get(owner).allowed());
        assertThrows(TrainingAiPermission.ConsentRequired.class, () -> permission.require(owner, "en"));
        assertEquals(Boolean.TRUE, jdbc.queryForObject("select value ->> 'grantedAt' is not null "
                + "and value ->> 'withdrawnAt' is not null from user_settings where user_id = ?", Boolean.class, owner));
        erase();
        assertEquals(0, jdbc.queryForObject("select count(*) from user_settings", Integer.class));
        assertThrows(org.springframework.dao.DataAccessException.class, () -> permission.record(owner, version, true));
    }

    private static void await(CountDownLatch latch) {
        try { if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("timed out"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }
}
