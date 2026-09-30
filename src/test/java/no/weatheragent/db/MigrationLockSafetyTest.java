package no.weatheragent.db;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * En migrering som gjør DDL på {@code flyway_schema_history} henger seg selv: Flyway holder en lås på tabellen mens
 * migreringen kjører, så {@code alter table} venter på seg selv til databasen avbryter den («canceling statement due to
 * statement timeout») og hele oppstarten feiler. V16 gjorde dette (RLS-løkke over alle tabeller) og stoppet en deploy på
 * Render 2026-09-30. Denne testen hindrer at det skjer igjen. H2/psql fanger det ikke, fordi de bruker én tilkobling.
 */
class MigrationLockSafetyTest {

    private static final Pattern DDL_ON_HISTORY = Pattern.compile(
            "(alter|drop|truncate)\\s+table\\s+(only\\s+)?(public\\.)?\"?flyway_schema_history", Pattern.CASE_INSENSITIVE);

    private static Resource[] migrations() throws IOException {
        return new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/*.sql");
    }

    private static String read(Resource r) throws IOException {
        try (var in = r.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void noMigrationRunsDdlDirectlyOnTheFlywayHistoryTable() throws IOException {
        for (Resource r : migrations()) {
            assertFalse(DDL_ON_HISTORY.matcher(read(r)).find(), r.getFilename() + " gjør DDL på flyway_schema_history");
        }
    }

    @Test
    void migrationsThatLoopOverAllTablesSkipTheFlywayHistoryTable() throws IOException {
        int loops = 0;
        for (Resource r : migrations()) {
            String sql = read(r).toLowerCase();
            if (sql.contains("from pg_tables") && sql.contains("execute format('alter table")) {
                loops++;
                assertTrue(sql.contains("tablename <> 'flyway_schema_history'"),
                        r.getFilename() + " løper over alle tabeller uten å hoppe over flyway_schema_history");
            }
        }
        assertTrue(loops >= 1, "forventet minst én migrering med en løkke over tabellene (V16)");
    }
}
