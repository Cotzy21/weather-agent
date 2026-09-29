package no.weatheragent.hiking;

/**
 * Bygger Overpass QL-spørringer for område-oppslag. Ren og testbar uten nettverk
 * - samme mønster som parserne.
 *
 * To varianter per oppslag:
 * <ul>
 *   <li><b>Land-scopet</b> (foretrukket når tolkeren fant en landkode): finn
 *       landet via {@code ISO3166-1}-taggen, let etter området KUN innenfor
 *       landet, og gjør treffene om til areas med {@code map_to_area}. Unngår
 *       navnekollisjoner på tvers av land ("Stranda" finnes også i Italia).</li>
 *   <li><b>Globalt navnesøk</b> (fallback): match området på navn hvor som
 *       helst, slik den gamle Norge-spørringen gjorde.</li>
 * </ul>
 *
 * Admin-nivåene 4-8 dekker de vanlige regionale inndelingene i de fleste land
 * (Norge: fylke=4/kommune=7; Østerrike: delstat=4/kommune=8; Italia:
 * regione=4/provincia=6/comune=8; USA: stat=4/county=6, osv.).
 *
 * Områdenavnet kommer fra LLM-tolkningen (brukerstyrt tekst) og escapes derfor
 * alltid før det settes inn i spørringen.
 */
final class OverpassQueries {

    private static final String ADMIN_LEVELS = "^(4|5|6|7|8)$";

    private OverpassQueries() {
    }

    /** Topper i et navngitt område. {@code countryCode} = null gir globalt navnesøk. */
    static String peaks(String areaName, String countryCode) {
        return header(90)
                + areaSelector(areaName, countryCode)
                + """
                node(area.omr)["natural"="peak"]["name"];
                out;
                """;
    }

    /** Turruter/stier i et navngitt område. {@code countryCode} = null gir globalt navnesøk. */
    static String trailsInArea(String areaName, String countryCode) {
        return header(120)
                + areaSelector(areaName, countryCode)
                + """
                way(area.omr)["highway"~"path|footway"]->.w;
                (
                  rel(bw.w)["route"~"hiking|foot"]["name"];
                  way.w["name"];
                );
                out center tags 600;
                """;
    }

    /** Maks antall elementer (stier/ruter) vi ber om geometri for. */
    static final int MAX_GEOMETRY_ELEMENTS = 60;

    /**
     * Navngitte turruter/stier rundt ETT punkt, MED linjegeometri - til å tegne
     * og highlighte stiene på kartet. Geometrien klippes til en boks rundt
     * punktet ({@code out geom(s,w,n,e)}): uten klipping ville en lang rute
     * (f.eks. E1) gitt megabytes med koordinater for noe som stort sett ligger
     * utenfor kartutsnittet. Koordinater formateres med punktum (Locale.ROOT).
     */
    static String trailGeometriesNear(double lat, double lon, int radiusMeters) {
        double dLat = radiusMeters / 111_320.0;
        double dLon = radiusMeters / (111_320.0 * Math.cos(Math.toRadians(lat)));
        return String.format(java.util.Locale.ROOT, """
                [out:json][timeout:60];
                (
                  relation(around:%d,%.6f,%.6f)["route"~"hiking|foot"]["name"];
                  way(around:%d,%.6f,%.6f)["highway"~"path|footway"]["name"];
                );
                out geom(%.6f,%.6f,%.6f,%.6f) %d;
                """, radiusMeters, lat, lon, radiusMeters, lat, lon,
                lat - dLat, lon - dLon, lat + dLat, lon + dLon, MAX_GEOMETRY_ELEMENTS);
    }

    private static String header(int timeoutSeconds) {
        return "[out:json][timeout:" + timeoutSeconds + "];\n";
    }

    /** Velger området (administrativt eller nasjonalpark) inn i settet {@code .omr}. */
    private static String areaSelector(String areaName, String countryCode) {
        String name = escape(areaName);
        if (countryCode == null) {
            return """
                    (
                      area["name"="%s"]["boundary"="administrative"]["admin_level"~"%s"];
                      area["name"="%s"]["boundary"="national_park"];
                    )->.omr;
                    """.formatted(name, ADMIN_LEVELS, name);
        }
        return """
                area["ISO3166-1"="%s"]["admin_level"="2"]->.land;
                (
                  rel(area.land)["name"="%s"]["boundary"="administrative"]["admin_level"~"%s"];
                  rel(area.land)["name"="%s"]["boundary"="national_park"];
                );
                map_to_area->.omr;
                """.formatted(escape(countryCode), name, ADMIN_LEVELS, name);
    }

    /**
     * Escaper en verdi for bruk i en Overpass QL-streng med doble anførselstegn,
     * så bruker-/LLM-tekst ikke kan bryte ut av spørringen. Kontrolltegn fjernes.
     */
    static String escape(String value) {
        return value
                .replaceAll("\\p{Cntrl}", " ")
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
