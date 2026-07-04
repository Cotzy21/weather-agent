package no.weatheragent.web.dto;

import no.weatheragent.recovery.InjuryWarning;
import no.weatheragent.recovery.RecoveryAdvice;
import no.weatheragent.recovery.RecoveryService;

import java.util.List;

/** Restitusjonsrapporten slik /api/recovery leverer den til frontenden. */
public record RecoveryReportDto(List<AdviceDto> advice,
                                List<WarningDto> warnings,
                                int streakDays,
                                boolean restDaySuggested) {

    public record AdviceDto(String type, String when, List<String> steps) {
        static AdviceDto from(RecoveryAdvice a) {
            return new AdviceDto(a.type(), a.when(), a.steps());
        }
    }

    public record WarningDto(String type, String level, int percentAboveNormal, String message) {
        static WarningDto from(InjuryWarning w) {
            return new WarningDto(w.type(), w.level().name(), w.percentAboveNormal(), w.message());
        }
    }

    public static RecoveryReportDto from(RecoveryService.RecoveryReport report) {
        return new RecoveryReportDto(
                report.advice().stream().map(AdviceDto::from).toList(),
                report.warnings().stream().map(WarningDto::from).toList(),
                report.streakDays(),
                report.restDaySuggested());
    }
}
