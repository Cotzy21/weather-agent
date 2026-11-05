package no.weatheragent.garmin;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

@Component
public class GarminWellnessReader {
    private final GarminPersonalClient client;

    public GarminWellnessReader(GarminPersonalClient client) {
        this.client = client;
    }

    public enum Group { WELLNESS, BODY, FOOD, DETAIL }

    public Map<LocalDate, Map<String, Double>> read(String key, Group group, LocalDate from, LocalDate to) {
        var dates = Map.of("startDate", from, "endDate", to);
        var days = new TreeMap<LocalDate, Map<String, Double>>();
        String token = client.accessToken(key);
        switch (group) {
            case WELLNESS -> {
                for (JsonNode row : get(token, "/usersummary-service/v2/dailysummaries", dates).path("data")) {
                    collect(days, row.path("calendarDate").asText(), from, to, daily(row));
                }
                for (JsonNode row : get(token, "/sleep-service/sleep/dailySleepSummariesByDate", dates)) {
                    LocalDateTime wake = localTimestamp(row.path("sleepEndTimestampLocal"));
                    String date = wake != null ? wake.toLocalDate().toString() : row.path("calendarDate").asText();
                    collect(days, date, from, to, sleep(row, wake));
                }
                for (JsonNode row : get(token, "/hrv-service/hrv/daily/" + from + "/" + to, Map.of()).path("hrvSummaries")) {
                    var metrics = new LinkedHashMap<String, Double>();
                    fields(metrics, row, "hrv", "lastNightAvg", "weeklyHrv", "weeklyAvg", "hrvPeak", "lastNight5MinHigh");
                    collect(days, row.path("calendarDate").asText(), from, to, metrics);
                }
            }
            case BODY -> {
                for (JsonNode row : get(token, "/weight-service/weight/range/" + from + "/" + to, Map.of("includeAll", false))
                        .path("dailyWeightSummaries")) {
                    var metrics = new LinkedHashMap<String, Double>();
                    fields(metrics, row.path("latestWeight"), "weightKg", "weight", "bmi", "bmi",
                            "bodyFat", "bodyFat", "bodyWater", "bodyWater", "muscleMass", "muscleMass", "boneMass", "boneMass",
                            "visceralFat", "visceralFat", "metabolicAge", "metabolicAge");
                    collect(days, row.path("summaryDate").asText(), from, to, metrics);
                }
                for (JsonNode row : get(token, "/usersummary-service/stats/hydration/daily/" + from + "/" + to, Map.of())) {
                    var metrics = new LinkedHashMap<String, Double>();
                    fields(metrics, row, "waterMl", "valueInML", "waterGoalMl", "goalInML", "sweatLossMl", "sweatLossInML");
                    collect(days, row.path("calendarDate").asText(), from, to, metrics);
                }
            }
            case DETAIL -> {
                var metrics = new LinkedHashMap<String, Double>();
                JsonNode pulse = get(token, "/wellness-service/wellness/dailyHeartRate", Map.of("date", to));
                fields(metrics, pulse, "restingHr", "restingHeartRate", "dailyMaxHr", "maxHeartRate",
                        "dailyMinHr", "minHeartRate", "weeklyRestingHr", "lastSevenDaysAvgRestingHeartRate");
                JsonNode oxygen = get(token, "/wellness-service/wellness/daily/spo2acclimation/" + to, Map.of());
                fields(metrics, oxygen, "spo2", "averageSpO2", "spo2Low", "lowestSpO2",
                        "weeklySpo2", "lastSevenDaysAvgSpO2");
                JsonNode age = get(token, "/fitnessage-service/fitnessage/v2/" + to, Map.of("fullHistoryScan", false));
                if (age.isArray()) fields(metrics, age.path(0), "fitnessAge", "fitnessAge", "fitnessAgeGoal", "achievableFitnessAge");
                collect(days, to.toString(), from, to, metrics);
            }
            case FOOD -> {
                for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
                    JsonNode food = get(token, "/nutrition-service/food/logs/" + day,
                            Map.of("includeNutrition", true, "includeMedia", false));
                    boolean logged = !food.path("loggedFoodsWithServingSizes").isEmpty();
                    for (JsonNode meal : food.path("mealDetails")) logged |= !meal.path("loggedFoods").isEmpty();
                    if (!logged) continue;
                    var metrics = new LinkedHashMap<String, Double>();
                    fields(metrics, food.path("dailyNutritionContent"), "garminFoodKcal", "calories",
                            "garminProteinG", "protein", "garminCarbG", "carbs", "garminFatG", "fat");
                    collect(days, day.toString(), from, to, metrics);
                }
            }
        }
        return days;
    }

    private JsonNode get(String token, String path, Map<String, ?> query) {
        try {
            JsonNode data = client.wellnessData(token, path, query);
            return data == null ? com.fasterxml.jackson.databind.node.MissingNode.getInstance() : data;
        } catch (HttpClientErrorException.NotFound e) {
            return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
        }
    }

    static Map<String, Double> daily(JsonNode row) {
        var metrics = new LinkedHashMap<String, Double>();
        JsonNode movement = row.path("movement");
        metric(metrics, "steps", movement.path("steps"), 1);
        fields(metrics, movement.path("steps"), "steps", "value", "stepGoal", "goal");
        fields(metrics, movement.path("floorsAscended"), "floors", "value");
        fields(metrics, row.path("calories"), "activeKcal", "burnedActive", "restingKcal", "burnedResting");
        fields(metrics, row.path("heartRate"), "restingHr", "restingValue", "dailyMaxHr", "maxValue", "dailyMinHr", "minValue");
        fields(metrics, row.path("intensityMinutes"), "moderateMin", "moderate", "vigorousMin", "vigorous");
        fields(metrics, row.path("stress"), "stress", "avgLevel", "stressMax", "maxLevel");
        fields(metrics, row.path("bodyBattery"), "bodyBattery", "endOfDayValue", "bodyBatteryHigh", "maxValue", "bodyBatteryLow", "minValue",
                "bodyBatteryCharged", "chargedValue", "bodyBatteryDrained", "drainedValue");
        if (!metrics.containsKey("bodyBattery")) fields(metrics, row.path("bodyBattery"), "bodyBattery", "latestValue");
        fields(metrics, row.path("hydration"), "waterMl", "consumedInFractionalMl", "waterGoalMl", "goalInMl");
        fields(metrics, row.path("respiration"), "respiration", "avgValue");
        fields(metrics, row.path("pulseOx"), "spo2", "avgValue");
        return metrics;
    }

    static Map<String, Double> sleep(JsonNode row, LocalDateTime wake) {
        var metrics = new LinkedHashMap<String, Double>();
        if (row.path("sleepTimeSeconds").asDouble() <= 0) return metrics;
        metric(metrics, "sleepMin", row.path("sleepTimeSeconds"), 60);
        metric(metrics, "deepSleepMin", row.path("deepSleepSeconds"), 60);
        metric(metrics, "lightSleepMin", row.path("lightSleepSeconds"), 60);
        metric(metrics, "remSleepMin", row.path("remSleepSeconds"), 60);
        metric(metrics, "awakeMin", row.path("awakeSleepSeconds"), 60);
        metric(metrics, "napMin", row.path("napTimeSeconds"), 60);
        fields(metrics, row.path("sleepScores").path("overall"), "sleepScore", "value");
        fields(metrics, row, "sleepHr", "avgHeartRate", "sleepRespiration", "averageRespirationValue",
                "sleepSpo2", "averageSpO2Value", "awakenings", "awakeCount");
        LocalDateTime start = localTimestamp(row.path("sleepStartTimestampLocal"));
        if (start != null && wake != null && start.isBefore(wake) && !start.isBefore(wake.minusHours(24))) {
            metrics.put("bedtime", (double) start.toLocalTime().toSecondOfDay() / 60);
            metrics.put("wakeTime", (double) wake.toLocalTime().toSecondOfDay() / 60);
        }
        return metrics;
    }

    static LocalDateTime localTimestamp(JsonNode node) {
        String value = node.asText("");
        try {
            if (value.matches("\\d{12,14}")) {
                // Garmin encodes local wall-clock time as epoch milliseconds with the local offset applied.
                return LocalDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(value)), ZoneOffset.UTC);
            }
            if (value.length() >= 16) return LocalDateTime.parse(value.replace(' ', 'T').substring(0, Math.min(value.length(), 19)));
        } catch (DateTimeParseException ignored) {
            // Keep the associated date and leave the clock time blank.
        }
        return null;
    }

    private static void collect(Map<LocalDate, Map<String, Double>> days, String date, LocalDate from, LocalDate to,
                                Map<String, Double> metrics) {
        if (metrics.isEmpty()) return;
        try {
            LocalDate day = LocalDate.parse(date);
            if (!day.isBefore(from) && !day.isAfter(to)) days.computeIfAbsent(day, ignored -> new LinkedHashMap<>()).putAll(metrics);
        } catch (DateTimeParseException ignored) {
            // An undated result cannot be linked to a specific day.
        }
    }

    private static void fields(Map<String, Double> metrics, JsonNode row, String... keys) {
        for (int i = 0; i < keys.length; i += 2) metric(metrics, keys[i], row.path(keys[i + 1]), 1);
    }

    private static void metric(Map<String, Double> metrics, String key, JsonNode node, double scale) {
        if (!node.isNumber()) return;
        double number = node.doubleValue();
        if (Double.isFinite(number) && number >= 0) metrics.put(key, number / scale);
    }
}
