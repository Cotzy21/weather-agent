package no.weatheragent.hiking;

import com.fasterxml.jackson.databind.JsonNode;
import no.weatheragent.route.RoutePoint;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.*;

/** Parses GML 3.2 in EPSG:4326 (latitude, longitude), without external entities. */
final class KartverketTrailParser {
    private KartverketTrailParser() {}
    static List<Trail> parse(String xml) {
        if (xml == null) throw new IllegalStateException("Tomt turrutesvar fra Kartverket");
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            if (!"FeatureCollection".equals(doc.getDocumentElement().getLocalName())) {
                throw new IllegalStateException("Ugyldig turrutesvar fra Kartverket");
            }
            List<Trail> result = new ArrayList<>();
            NodeList features = doc.getElementsByTagNameNS("*", "Fotrute");
            for (int i = 0; i < features.getLength(); i++) {
                Element feature = (Element) features.item(i);
                String name = text(feature, "rutenavn");
                String number = text(feature, "rutenummer");
                if (name.isBlank()) name = "Fotrute " + (number.isBlank() ? feature.getAttributeNS("http://www.opengis.net/gml/3.2", "id") : number);
                String operator = text(feature, "vedlikeholdsansvarlig");
                List<List<RoutePoint>> lines = new ArrayList<>();
                NodeList positions = feature.getElementsByTagNameNS("http://www.opengis.net/gml/3.2", "posList");
                for (int n = 0; n < positions.getLength(); n++) {
                    Element pos = (Element) positions.item(n);
                    int dimension = pos.hasAttribute("srsDimension") ? Integer.parseInt(pos.getAttribute("srsDimension")) : 2;
                    if (dimension != 2 && dimension != 3) continue;
                    String[] values = pos.getTextContent().strip().split("\\s+");
                    if (values.length < dimension * 2 || values.length % dimension != 0) continue;
                    int step = Math.max(1, (int) Math.ceil((values.length / (double) dimension) / 500));
                    List<RoutePoint> line = new ArrayList<>();
                    for (int j = 0; j < values.length; j += dimension * step) {
                        addPoint(line, values, j);
                    }
                    addPoint(line, values, values.length - dimension);
                    if (line.size() >= 2) lines.add(List.copyOf(line));
                }
                if (lines.isEmpty()) continue;
                List<RoutePoint> longest = lines.stream().max(Comparator.comparingInt(List::size)).orElseThrow();
                RoutePoint center = longest.get(longest.size() / 2);
                result.add(new Trail(name.strip(), "", operator.strip(), center.lat(), center.lon(), List.copyOf(lines)));
            }
            return List.copyOf(result);
        } catch (IllegalStateException e) { throw e; }
        catch (Exception e) { throw new IllegalStateException("Kan ikke lese turrutedata fra Kartverket", e); }
    }
    private static void addPoint(List<RoutePoint> points, String[] values, int i) {
        double lat = Double.parseDouble(values[i]), lon = Double.parseDouble(values[i + 1]);
        if (!Double.isFinite(lat) || !Double.isFinite(lon) || lat < -90 || lat > 90 || lon < -180 || lon > 180) return;
        RoutePoint p = new RoutePoint(lat, lon);
        if (points.isEmpty() || !points.getLast().equals(p)) points.add(p);
    }
    private static String text(Element e, String name) {
        NodeList nodes = e.getElementsByTagNameNS("*", name);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent().strip();
    }
    static boolean inside(JsonNode geometry, double x, double y) {
        JsonNode coordinates = geometry.path("coordinates");
        if ("Polygon".equals(geometry.path("type").asText())) return polygon(coordinates, x, y);
        if ("MultiPolygon".equals(geometry.path("type").asText())) {
            for (JsonNode p : coordinates) if (polygon(p, x, y)) return true;
        }
        return false;
    }
    private static boolean polygon(JsonNode rings, double x, double y) {
        if (!ring(rings.path(0), x, y)) return false;
        for (int i = 1; i < rings.size(); i++) if (ring(rings.path(i), x, y)) return false;
        return true;
    }
    private static boolean ring(JsonNode points, double x, double y) {
        boolean inside = false;
        for (int i = 0, j = points.size() - 1; i < points.size(); j = i++) {
            double xi = points.path(i).path(0).asDouble(), yi = points.path(i).path(1).asDouble();
            double xj = points.path(j).path(0).asDouble(), yj = points.path(j).path(1).asDouble();
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside;
        }
        return inside;
    }
}
