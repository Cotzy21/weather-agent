package no.weatheragent.hiking;

/**
 * En merket turrute fra OpenStreetMap (relation route=hiking) nær et sted.
 * Koordinaten er rutas senterpunkt (fra Overpass "out center"), nok til å
 * plassere en markør på kartet.
 *
 * @param name     rutenavn (vi tar bare med navngitte ruter)
 * @param network  OSM-nettverksnivå: lwn=lokal, rwn=regional, nwn=nasjonal ("" hvis ukjent)
 * @param operator hvem som drifter ruta, f.eks. "Den Norske Turistforening" ("" hvis ukjent)
 */
public record Trail(String name, String network, String operator, double latitude, double longitude) {
}
