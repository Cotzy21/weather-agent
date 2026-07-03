package no.weatheragent.training;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GarminCsvParserTest {

    private static final String NORWEGIAN = """
            Aktivitetstype,Dato,Favoritt,Tittel,Distanse,Kalorier,Tid,Gjennomsnittlig puls,Stigning totalt
            Løping,2026-07-01 18:23:45,false,"Ålesund løping","5,02","412","00:28:15","156","64"
            Styrketrening,2026-06-30 17:00:00,false,"Push A","0,00","310","00:52:10","--","--"
            Bassengsvømming,2026-06-28 08:10:00,false,"Morgensvøm","1500","350","00:41:00","--","--"
            """;

    private static final String ENGLISH = """
            Activity Type,Date,Favorite,Title,Distance,Calories,Time,Avg HR,Total Ascent
            Running,2026-07-01 18:23:45,false,"Evening Run","5.02","1,234","00:28:15","156","64"
            Hiking,2026-06-29 10:00:00,false,"Sukkertoppen","6.40","702","02:15:30","--","512"
            """;

    @Test
    void parsesNorwegianCsvWithDecimalCommas() {
        List<GarminActivity> acts = GarminCsvParser.parse(NORWEGIAN);

        assertEquals(3, acts.size());
        GarminActivity run = acts.getFirst();
        assertEquals(LocalDate.of(2026, 7, 1), run.date());
        assertEquals("Ålesund løping", run.title());
        assertEquals("LØPING", run.type());
        assertEquals(5.02, run.distanceKm(), 0.001);
        assertEquals(412, run.kcal(), 0.001);
        assertEquals(28.25, run.durationMin(), 0.001);
        assertEquals(64, run.ascentM(), 0.001);
    }

    @Test
    void missingValuesBecomeNull() {
        GarminActivity strength = GarminCsvParser.parse(NORWEGIAN).get(1);

        assertEquals("STYRKE", strength.type());
        assertNull(strength.ascentM()); // «--»
    }

    @Test
    void poolSwimDistanceInMetersIsConvertedToKm() {
        GarminActivity swim = GarminCsvParser.parse(NORWEGIAN).get(2);

        assertEquals("SVØMMING", swim.type());
        assertEquals(1.5, swim.distanceKm(), 0.001);
    }

    @Test
    void parsesEnglishCsvWithThousandsSeparator() {
        List<GarminActivity> acts = GarminCsvParser.parse(ENGLISH);

        assertEquals(2, acts.size());
        assertEquals(1234, acts.getFirst().kcal(), 0.001);
        assertEquals("HIKING", acts.get(1).type());
        assertEquals(512, acts.get(1).ascentM(), 0.001);
        assertEquals(135.5, acts.get(1).durationMin(), 0.001);
    }

    @Test
    void skipsRowsWithoutValidDate() {
        String csv = """
                Aktivitetstype,Dato,Tittel
                Løping,ikke en dato,"Tulleøkt"
                Løping,2026-07-01 10:00:00,"Ekte økt"
                """;

        List<GarminActivity> acts = GarminCsvParser.parse(csv);

        assertEquals(1, acts.size());
        assertEquals("Ekte økt", acts.getFirst().title());
    }

    @Test
    void unknownActivityTypeBecomesFristil() {
        assertEquals("FRISTIL", GarminCsvParser.mapType("Yoga"));
        assertEquals("SYKKEL", GarminCsvParser.mapType("Indoor Cycling"));
        assertEquals("BULDRING", GarminCsvParser.mapType("Bouldering"));
    }

    @Test
    void nonGarminCsvGivesEmptyList() {
        assertTrue(GarminCsvParser.parse("a,b,c\n1,2,3\n").isEmpty());
        assertTrue(GarminCsvParser.parse("").isEmpty());
    }
}
