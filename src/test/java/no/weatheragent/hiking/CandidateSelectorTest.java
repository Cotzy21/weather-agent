package no.weatheragent.hiking;

import no.weatheragent.geo.Location;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class CandidateSelectorTest {

    private static Peak peak(String name, double lat, double lon, Double ele) {
        return new Peak(new Location(name, lat, lon), ele);
    }

    @Test
    void keepsHighestPeakPerCell() {
        // A og B ligger i samme 0.25-rute; B er høyest -> B vinner.
        Peak a = peak("A", 62.20, 7.60, 1000.0);
        Peak b = peak("B", 62.22, 7.62, 1500.0);
        // C ligger i en annen rute.
        Peak c = peak("C", 63.00, 8.00, 800.0);

        List<Peak> reps = CandidateSelector.representativePeaks(List.of(a, b, c), 0.25, 0);

        assertEquals(2, reps.size());
        assertSame(b, reps.get(0)); // sortert høyeste først
        assertSame(c, reps.get(1));
    }

    @Test
    void dropsPeaksBelowElevationFloor() {
        Peak low = peak("Lav", 62.20, 7.60, 400.0);
        Peak high = peak("Hoy", 63.00, 8.00, 900.0);

        List<Peak> reps = CandidateSelector.representativePeaks(List.of(low, high), 0.25, 600);

        assertEquals(1, reps.size());
        assertSame(high, reps.getFirst());
    }

    @Test
    void dropsPeaksWithoutElevation() {
        Peak unknown = peak("Ukjent", 62.20, 7.60, null);
        Peak known = peak("Kjent", 63.00, 8.00, 700.0);

        List<Peak> reps = CandidateSelector.representativePeaks(List.of(unknown, known), 0.25, 0);

        assertEquals(1, reps.size());
        assertSame(known, reps.getFirst());
    }
}
