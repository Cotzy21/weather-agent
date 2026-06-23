package no.weatheragent.assist;

import no.weatheragent.geo.Location;
import no.weatheragent.hiking.CandidateSelector;
import no.weatheragent.hiking.OverpassClient;
import no.weatheragent.hiking.Peak;
import no.weatheragent.interpret.Interpretation;
import no.weatheragent.interpret.QueryInterpreter;
import no.weatheragent.interpret.TripType;
import no.weatheragent.ranking.BestWeatherFinder;
import no.weatheragent.ranking.RankedPlaceOverPeriod;
import no.weatheragent.ranking.ScoreWeights;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tur-assistentens kjerne: fra et fritekst-spørsmål til en rangering av steder
 * med best vær over perioden. Orkestrerer hele kjeden - tolkning (LLM) ->
 * turdata (OSM) -> vær/scoring (MET) - uten en eneste hardkodet region eller dato.
 *
 * Dette er bruksområdet (use case) et framtidig REST-endepunkt vil kalle;
 * CLI-/demo-runnerne er bare tynne skall rundt denne.
 */
@Component
public class TurvaerService {

    // Større rute = færre kandidater = færre vær-oppslag (raskere).
    private static final double CELL_DEGREES = 0.40;

    // Høydegulv for kandidat-utvelgelse. Fjelltur = bare høye topper; ellers
    // tar vi med alt (havniva-korreksjonen i scoringen hindrer at lave knauser
    // vinner på ren varme, HANDOFF §7).
    private static final double FJELL_MIN_ELEVATION_M = 800;
    private static final double DEFAULT_MIN_ELEVATION_M = 0;

    private final QueryInterpreter interpreter;
    private final OverpassClient overpassClient;
    private final BestWeatherFinder bestWeatherFinder;

    public TurvaerService(QueryInterpreter interpreter,
                          OverpassClient overpassClient,
                          BestWeatherFinder bestWeatherFinder) {
        this.interpreter = interpreter;
        this.overpassClient = overpassClient;
        this.bestWeatherFinder = bestWeatherFinder;
    }

    /** Finn beste vær med normal vekt på alle faktorer. */
    public TurResult finnBesteVaer(String query) {
        return finnBesteVaer(query, ScoreWeights.DEFAULT);
    }

    public TurResult finnBesteVaer(String query, ScoreWeights weights) {
        Interpretation tolkning = interpreter.interpret(query);
        if (!tolkning.hasRegion()) {
            return new TurResult(tolkning, List.of());
        }

        double minElevation = tolkning.tripType() == TripType.FJELLTUR
                ? FJELL_MIN_ELEVATION_M : DEFAULT_MIN_ELEVATION_M;

        List<Peak> peaks = overpassClient.peaksInCounty(tolkning.region());
        List<Location> candidates = CandidateSelector
                .representativePeaks(peaks, CELL_DEGREES, minElevation)
                .stream()
                .map(Peak::location)
                .toList();

        List<RankedPlaceOverPeriod> ranking =
                bestWeatherFinder.rankOverPeriod(candidates, tolkning.dates().days(), weights);

        return new TurResult(tolkning, ranking);
    }
}
