package no.weatheragent.hiking;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reduserer hundrevis av topper til et håndterbart, geografisk spredt
 * kandidatsett - så vi slipper å hente vær for hver eneste topp.
 *
 * Idé: været er romlig korrelert (naboTopper har nesten likt vær), så vi
 * deler fylket i et rutenett og beholder den HØYESTE navngitte toppen i hver
 * rute. Det gir dekning over hele området med få vær-oppslag.
 *
 * Ren logikk uten nettverk - derfor lett å teste.
 */
public final class CandidateSelector {

    private CandidateSelector() {
    }

    /**
     * @param peaks             alle toppene i området
     * @param cellDegrees       rutestørrelse i grader (større = færre kandidater)
     * @param minElevationMeters høydegulv; topper under dette ignoreres
     * @return én representativ topp per rute, høyeste først
     */
    public static List<Peak> representativePeaks(List<Peak> peaks, double cellDegrees, double minElevationMeters) {
        Map<String, Peak> highestPerCell = new LinkedHashMap<>();

        for (Peak peak : peaks) {
            Double elevation = peak.elevationMeters();
            if (elevation == null || elevation < minElevationMeters) {
                continue; // ukjent høyde eller for lav - hopp over
            }

            String cell = cellOf(peak, cellDegrees);
            Peak current = highestPerCell.get(cell);
            if (current == null || elevation > current.elevationMeters()) {
                highestPerCell.put(cell, peak);
            }
        }

        return highestPerCell.values().stream()
                .sorted(Comparator.comparingDouble(Peak::elevationMeters).reversed())
                .toList();
    }

    /** Rute-id som "latIndeks:lonIndeks" - alle topper i samme rute får samme id. */
    private static String cellOf(Peak peak, double cellDegrees) {
        long latIndex = Math.round(peak.location().latitude() / cellDegrees);
        long lonIndex = Math.round(peak.location().longitude() / cellDegrees);
        return latIndex + ":" + lonIndex;
    }
}
