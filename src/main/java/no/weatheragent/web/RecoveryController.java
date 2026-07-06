package no.weatheragent.web;

import no.weatheragent.recovery.RecoveryService;
import no.weatheragent.web.dto.RecoveryReportDto;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Restitusjon og skadeforebygging: rapport basert på brukerens treningslogg.
 * Kun lesing - all analyse skjer i {@link RecoveryService}.
 */
@RestController
public class RecoveryController {

    private final RecoveryService recovery;

    public RecoveryController(RecoveryService recovery) {
        this.recovery = recovery;
    }

    /** {@code ?lang=en} gir råd/varsler på engelsk; ellers norsk. */
    @GetMapping("/api/recovery")
    public RecoveryReportDto report(@AuthenticationPrincipal Jwt jwt,
                                    @RequestParam(value = "lang", required = false) String lang) {
        boolean english = "en".equalsIgnoreCase(lang);
        return RecoveryReportDto.from(recovery.report(UUID.fromString(jwt.getSubject()), english));
    }
}
