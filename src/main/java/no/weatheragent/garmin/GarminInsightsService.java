package no.weatheragent.garmin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import no.weatheragent.nutrition.MealEntryRepository;
import no.weatheragent.training.WorkoutRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Service
public class GarminInsightsService {
    private static final TypeReference<Map<String, Double>> METRICS = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final WorkoutRepository workouts;
    private final MealEntryRepository meals;

    public GarminInsightsService(JdbcTemplate jdbc, ObjectMapper mapper, WorkoutRepository workouts, MealEntryRepository meals) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.workouts = workouts;
        this.meals = meals;
    }

    @Transactional
    public int save(UUID user, Map<LocalDate, Map<String, Double>> days) {
        if (days.isEmpty()) return 0;
        var rows = days.entrySet().stream().map(entry ->
                new Object[]{user, entry.getKey(), json(entry.getValue())}).toList();
        jdbc.batchUpdate("""
                insert into garmin_daily_metrics (user_id, day, metrics) values (?, ?, cast(? as jsonb))
                on conflict (user_id, day) do update
                    set metrics = garmin_daily_metrics.metrics || excluded.metrics
                """, rows);
        return days.size();
    }

    public List<Day> list(UUID user, LocalDate from, LocalDate to) {
        var days = new TreeMap<LocalDate, Map<String, Double>>();
        from.datesUntil(to.plusDays(1)).forEach(day -> days.put(day, new LinkedHashMap<>()));
        jdbc.query("select day, metrics from garmin_daily_metrics where user_id = ? and day between ? and ?",
                row -> {
                    days.put(row.getObject("day", LocalDate.class), parse(row.getString("metrics")));
                }, user, from, to);
        // Preserve the source. Do not count an unlogged food day as zero, or combine two food logs.
        meals.findByUserIdAndDateBetween(user, from, to).forEach(meal -> {
            var metrics = days.computeIfAbsent(meal.getDate(), ignored -> new LinkedHashMap<>());
            metrics.merge("appFoodKcal", meal.getKcal(), Double::sum);
            metrics.merge("appProteinG", meal.getProteinG(), Double::sum);
            metrics.merge("appCarbG", meal.getCarbG(), Double::sum);
            metrics.merge("appFatG", meal.getFatG(), Double::sum);
            if ("KVELDS".equals(meal.getMeal())) metrics.merge("appEveningKcal", meal.getKcal(), Double::sum);
        });
        workouts.findByUserIdAndDateBetween(user, from, to).forEach(workout -> {
            var metrics = days.computeIfAbsent(workout.getDate(), ignored -> new LinkedHashMap<>());
            metrics.merge("workouts", 1.0, Double::sum);
            var minutes = workout.getContent().path("durationMin");
            if (minutes.isNumber() && minutes.asDouble() > 0) metrics.merge("workoutMin", minutes.asDouble(), Double::sum);
        });
        return days.entrySet().stream().map(e -> new Day(e.getKey(), e.getValue())).toList();
    }

    @Transactional
    public void delete(UUID user) {
        jdbc.update("delete from garmin_daily_metrics where user_id = ?", user);
    }

    private String json(Map<String, Double> metrics) {
        try {
            return mapper.writeValueAsString(metrics);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not encode Garmin metrics");
        }
    }

    private Map<String, Double> parse(String json) {
        try {
            return mapper.readValue(json, METRICS);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not read Garmin metrics");
        }
    }

    public record Day(LocalDate date, Map<String, Double> metrics) {}
}
