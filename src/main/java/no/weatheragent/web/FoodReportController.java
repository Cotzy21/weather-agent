package no.weatheragent.web;

import jakarta.validation.Valid;
import no.weatheragent.nutrition.FoodReportService;
import no.weatheragent.security.AdminGuard;
import no.weatheragent.web.dto.ReportFoodRequest;
import no.weatheragent.web.dto.ReportedFoodDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Rapportering av offentlig delte matvarer (alle innloggede) og enkel moderering (bare administratorer, se
 * {@link AdminGuard}). Administratorlisten kommer fra konfigurasjon, ikke fra noe klienten kan sende.
 */
@RestController
public class FoodReportController {

    private final FoodReportService reports;
    private final AdminGuard admin;

    public FoodReportController(FoodReportService reports, AdminGuard admin) {
        this.reports = reports;
        this.admin = admin;
    }

    @PostMapping("/api/kosthold/egne-matvarer/{id}/rapport")
    public ResponseEntity<Void> report(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                       @Valid @RequestBody ReportFoodRequest request) {
        reports.report(UUID.fromString(jwt.getSubject()), id, request.reason(), request.note());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/admin/rapporter")
    public List<ReportedFoodDto> reported(@AuthenticationPrincipal Jwt jwt) {
        admin.require(jwt);
        return reports.listReported().stream().map(ReportedFoodDto::from).toList();
    }

    @DeleteMapping("/api/admin/matvarer/{id}")
    public ResponseEntity<Void> deleteFood(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        admin.require(jwt);
        return reports.deleteFood(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/api/admin/rapporter/{foodId}")
    public ResponseEntity<Void> dismiss(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID foodId) {
        admin.require(jwt);
        reports.dismiss(foodId);
        return ResponseEntity.noContent().build();
    }
}
