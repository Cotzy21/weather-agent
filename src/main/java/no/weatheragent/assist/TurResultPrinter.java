package no.weatheragent.assist;

import no.weatheragent.hiking.Trail;
import no.weatheragent.interpret.Interpretation;
import no.weatheragent.ranking.RankedPlaceOverPeriod;

import java.util.List;

/**
 * Formaterer et {@link TurResult} til lesbar terminal-tekst. Ren og testbar -
 * ingen I/O her, bare strenger inn/ut.
 */
public final class TurResultPrinter {

    private TurResultPrinter() {
    }

    public static String format(TurResult result) {
        Interpretation r = result.interpretation();
        StringBuilder sb = new StringBuilder();

        sb.append("Tolket : region=").append(r.hasRegion() ? r.region() : "(ukjent)")
          .append(", ").append(r.when())
          .append(" (").append(r.dates().from()).append("->").append(r.dates().to()).append(")")
          .append(", ").append(r.tripType()).append('\n');

        if (!r.hasRegion()) {
            return sb.append("Fant ingen region i spørsmålet.").toString();
        }
        if (!result.hasAnswer()) {
            return sb.append("Ingen værdata for perioden (kanskje for langt fram?).").toString();
        }

        List<RankedPlaceOverPeriod> ranking = result.ranking();
        ranking.stream().limit(10).forEach(p -> sb.append(String.format(
                "  %-20s %5.0f moh  snitt %4.1f°C  %4.1f mm  %4.1f m/s   (score %.1f)%n",
                p.location().name(), p.elevationMeters(),
                p.avgMaxTempC(), p.avgPrecipMm(), p.avgWindMs(), p.score())));

        RankedPlaceOverPeriod best = ranking.getFirst();
        sb.append(String.format("=> Finest vær %s->%s: %s (%.0f moh, snitt %.1f°C, %.1f mm regn/dag)",
                r.dates().from(), r.dates().to(), best.location().name(),
                best.elevationMeters(), best.avgMaxTempC(), best.avgPrecipMm()));

        List<Trail> trails = result.trails();
        if (!trails.isEmpty()) {
            sb.append("\n\nMerkede turer i området:");
            trails.forEach(t -> {
                sb.append("\n  - ").append(t.name());
                if (!t.operator().isBlank()) {
                    sb.append(" (").append(t.operator()).append(')');
                }
            });
        }

        List<String> clothing = result.clothing();
        if (!clothing.isEmpty()) {
            sb.append("\n\nKlær & utstyr:");
            clothing.forEach(c -> sb.append("\n  - ").append(c));
        }
        return sb.toString();
    }
}
