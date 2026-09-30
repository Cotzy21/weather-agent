package no.weatheragent.body;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WeighInServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 30);

    private final WeighInRepository repository = mock(WeighInRepository.class);
    private final WeighInService service = new WeighInService(repository);

    @Test
    void logsNewWeighInRoundedToOneDecimal() {
        when(repository.findByUserIdAndDate(USER, TODAY)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WeighIn w = service.log(USER, TODAY, 82.456, TODAY);

        assertEquals(82.5, w.getWeightKg());
        assertEquals(TODAY, w.getDate());
    }

    @Test
    void secondWeighInSameDayOverwrites() {
        WeighIn existing = new WeighIn(USER, TODAY, 83.0);
        when(repository.findByUserIdAndDate(USER, TODAY)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WeighIn w = service.log(USER, TODAY, 82.2, TODAY);

        assertSame(existing, w);
        assertEquals(82.2, w.getWeightKg());
    }

    @Test
    void refusesUnrealisticWeights() {
        assertThrows(IllegalArgumentException.class, () -> service.log(USER, TODAY, 5, TODAY));
        assertThrows(IllegalArgumentException.class, () -> service.log(USER, TODAY, 900, TODAY));
        assertThrows(IllegalArgumentException.class, () -> service.log(USER, TODAY, Double.NaN, TODAY));
    }

    @Test
    void refusesFutureDates() {
        assertThrows(IllegalArgumentException.class, () -> service.log(USER, TODAY.plusDays(1), 80, TODAY));
    }
}
