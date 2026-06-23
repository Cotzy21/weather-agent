package no.weatheragent.hiking;

/**
 * En merket turrute fra OpenStreetMap nær et sted - enten en navngitt
 * fot-/turrute-relasjon (route=hiking/foot) eller en navngitt sti (highway=path).
 * Koordinaten er senterpunktet (fra Overpass "out center"), nok til å plassere
 * en markør på kartet.
 *
 * @param name     rutenavn (vi tar bare med navngitte ruter)
 * @param network  OSM-nettverksnivå: lwn=lokal, rwn=regional, nwn=nasjonal ("" hvis ukjent)
 * @param operator hvem som drifter ruta, f.eks. "Den Norske Turistforening" ("" hvis ukjent)
 */
public record Trail(String name, String network, String operator, double latitude, double longitude) {
}
