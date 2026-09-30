package no.weatheragent.training;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProgramProgressTest {

    private static final LocalDate START = LocalDate.of(2026, 9, 1);

    @Test
    void dayTwelveOfEightWeeks() {
        ProgramProgress p = ProgramProgress.of(START, 8, START.plusDays(11));
        assertEquals(12, p.dayNumber());
        assertEquals(56, p.totalDays());
        assertEquals(2, p.weekNumber());
        assertEquals("ACTIVE", p.status());
    }

    @Test
    void firstAndLastDayAreActive() {
        assertEquals(1, ProgramProgress.of(START, 8, START).dayNumber());
        ProgramProgress last = ProgramProgress.of(START, 8, START.plusDays(55));
        assertEquals(56, last.dayNumber());
        assertEquals("ACTIVE", last.status());
        assertEquals(8, last.weekNumber());
    }

    @Test
    void beforeStartAndAfterEnd() {
        assertEquals("NOT_STARTED", ProgramProgress.of(START, 8, START.minusDays(1)).status());
        ProgramProgress done = ProgramProgress.of(START, 8, START.plusDays(56));
        assertEquals("FINISHED", done.status());
        assertEquals(56, done.dayNumber());
    }

    @Test
    void rejectsBadLengthOrMissingDate() {
        assertThrows(IllegalArgumentException.class, () -> ProgramProgress.of(START, 0, START));
        assertThrows(IllegalArgumentException.class, () -> ProgramProgress.of(START, 53, START));
        assertThrows(IllegalArgumentException.class, () -> ProgramProgress.of(null, 8, START));
    }
}
