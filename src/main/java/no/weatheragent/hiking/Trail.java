package no.weatheragent.hiking;

import no.weatheragent.route.RoutePoint;

import java.util.List;

/**
 * En merket turrute fra OpenStreetMap nær et sted - enten en navngitt
 * fot-/turrute-relasjon (route=hiking/foot) eller en navngitt sti (highway=path).
 * Koordinaten er senterpunktet, nok til å plassere en markør på kartet.
 *
 * @param name     rutenavn (vi tar bare med navngitte ruter)
 * @param network  OSM-nettverksnivå: lwn=lokal, rwn=regional, nwn=nasjonal ("" hvis ukjent)
 * @param operator hvem som drifter ruta, f.eks. "Den Norske Turistforening" ("" hvis ukjent)
 * @param lines    linjegeometri (én eller flere polylinjer, uttynnet) når den er
 *                 hentet med {@code out geom}; tom når vi bare har senterpunktet
 */
public record Trail(String name, String network, String operator, double latitude, double longitude,
                    List<List<RoutePoint>> lines) {

    /** Uten geometri (senterpunkt-oppslagene). */
    public Trail(String name, String network, String operator, double latitude, double longitude) {
        this(name, network, operator, latitude, longitude, List.of());
    }
}
