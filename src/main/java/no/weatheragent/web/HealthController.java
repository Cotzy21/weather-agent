package no.weatheragent.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Levende-sjekk for oppetidsovervåking (UptimeRobot o.l.) og hostens helsesjekk. Åpen og uten
 * avhengigheter (ingen database, ingen eksterne kall), så den svarer raskt og avslører ingenting om
 * oppsettet. Vi bruker bevisst ikke Spring Actuator: det åpner flere endepunkter enn vi trenger.
 */
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
