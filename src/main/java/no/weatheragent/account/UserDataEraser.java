package no.weatheragent.account;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Sletter ALT vi har lagret om én bruker (GDPR: retten til sletting). Alt skjer i én transaksjon: enten er
 * alle tabellene tømt, eller ingenting er endret.
 *
 * Listen under må dekke hver tabell med brukerdata. {@code UserDataEraserTableCoverageTest} feiler hvis en
 * ny entitet legges til uten at tabellen står her, så vi ikke glemmer den (og lagrer data vi ikke kan slette).
 * Tabell- og kolonnenavnene er faste konstanter, aldri brukerinput, så det er trygt å sette dem inn i SQL-en.
 */
@Service
public class UserDataEraser {

    /** En tabell med brukerdata og kolonnen som peker på eieren. */
    record OwnedTable(String table, String ownerColumn) {}

    /** habit_logs har ingen bruker-kolonne (den henger på vanen) og slettes først, via habits. */
    static final String HABIT_LOGS = "habit_logs";

    static final List<OwnedTable> OWNED_TABLES = List.of(
            new OwnedTable("workouts", "user_id"),
            new OwnedTable("training_plans", "user_id"),
            new OwnedTable("training_memory", "user_id"),
            new OwnedTable("training_profile", "user_id"),
            new OwnedTable("user_settings", "user_id"),
            new OwnedTable("exercise_name_map", "user_id"),
            new OwnedTable("habits", "user_id"),
            new OwnedTable("weigh_ins", "user_id"),
            new OwnedTable("nutrition_favorites", "user_id"),
            new OwnedTable("meal_entries", "user_id"),
            new OwnedTable("diet_preferences", "user_id"),
            new OwnedTable("calorie_goals", "user_id"),
            new OwnedTable("custom_foods", "owner_id"),
            new OwnedTable("custom_meals", "user_id"),
            new OwnedTable("saved_routes", "user_id"));

    private final JdbcTemplate jdbc;

    public UserDataEraser(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Alle tabellene som slettes fra (brukes av dekningstesten). */
    static Set<String> coveredTables() {
        Set<String> tables = new LinkedHashSet<>();
        tables.add(HABIT_LOGS);
        OWNED_TABLES.forEach(t -> tables.add(t.table()));
        return tables;
    }

    /** Sletter alle rader som tilhører brukeren. Returnerer hvor mange rader som ble fjernet. */
    @Transactional
    public int eraseAll(UUID userId) {
        int rows = jdbc.update(
                "delete from " + HABIT_LOGS + " where habit_id in (select id from habits where user_id = ?)", userId);
        for (OwnedTable t : OWNED_TABLES) {
            rows += jdbc.update("delete from " + t.table() + " where " + t.ownerColumn() + " = ?", userId);
        }
        return rows;
    }
}
