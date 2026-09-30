package no.weatheragent.web.dto;

import no.weatheragent.nutrition.FoodReportService.ReportedFood;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** En rapportert vare for administratoren: varen, hvor mange som har rapportert den, og hvorfor. */
public record ReportedFoodDto(
        String id,
        String name,
        String brand,
        double kcalPer100g,
        double proteinPer100g,
        double fatPer100g,
        double carbPer100g,
        long reports,
        Map<String, Long> reasons,
        List<String> notes,
        Instant latestReportAt
) {
    public static ReportedFoodDto from(ReportedFood r) {
        return new ReportedFoodDto(
                r.food().getId().toString(), r.food().getName(), r.food().getBrand(),
                r.food().getKcalPer100g(), r.food().getProteinG(), r.food().getFatG(), r.food().getCarbG(),
                r.reports(),
                r.reasons().entrySet().stream().collect(Collectors.toMap(e -> e.getKey().name(), Map.Entry::getValue)),
                r.notes(), r.latestReportAt());
    }
}
