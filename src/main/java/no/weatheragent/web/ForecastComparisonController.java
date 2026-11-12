package no.weatheragent.web;

import no.weatheragent.weather.ForecastComparisonService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ForecastComparisonController {
    private final ForecastComparisonService service;

    public ForecastComparisonController(ForecastComparisonService service) {
        this.service = service;
    }

    @GetMapping("/api/vaer/sammenlign")
    public ForecastComparisonService.Comparison compare(@RequestParam(defaultValue = "alesund") String place) {
        return service.compare(place);
    }
}
