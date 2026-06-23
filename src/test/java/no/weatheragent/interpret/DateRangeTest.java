package no.weatheragent.interpret;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DateRangeTest {

    @Test
    void daysAreInclusiveOfBothEnds() {
        DateRange r = new DateRange(LocalDate.of(2026, 6, 27), LocalDate.of(2026, 6, 28));

        assertEquals(2, r.days().size());
        assertEquals(LocalDate.of(2026, 6, 27), r.days().getFirst());
        assertEquals(LocalDate.of(2026, 6, 28), r.days().getLast());
    }

    @Test
    void singleIsOneDay() {
        DateRange r = DateRange.single(LocalDate.of(2026, 6, 27));

        assertEquals(1, r.days().size());
    }

    @Test
    void rejectsReversedRange() {
        assertThrows(IllegalArgumentException.class,
                () -> new DateRange(LocalDate.of(2026, 6, 28), LocalDate.of(2026, 6, 27)));
    }
}
