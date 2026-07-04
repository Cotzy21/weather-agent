package no.weatheragent.web.dto;

import no.weatheragent.habit.HabitService;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** En vane slik /api/vaner leverer den: metadata + siste dagers logg + streak. */
public record HabitDto(
        UUID id,
        String name,
        String emoji,
        String unit,
        int streakDays,
        List<LogDto> logs
) {

    public record LogDto(LocalDate date, double value) {
    }

    public static HabitDto from(HabitService.HabitWithLogs h) {
        return new HabitDto(
                h.habit().getId(),
                h.habit().getName(),
                h.habit().getEmoji(),
                h.habit().getUnit(),
                h.streakDays(),
                h.recentLogs().stream()
                        .map(l -> new LogDto(l.getDate(), l.getValue()))
                        .toList());
    }
}
