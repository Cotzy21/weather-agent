package no.weatheragent.hiking;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KartverketTrailParserTest {
    private static String collection(String points) { return """
        <wfs:FeatureCollection xmlns:wfs="http://www.opengis.net/wfs/2.0" xmlns:app="https://example.org" xmlns:gml="http://www.opengis.net/gml/3.2">
        <wfs:member><app:Fotrute gml:id="fotrute.1"><app:rutenummer>F_1</app:rutenummer>
        <app:vedlikeholdsansvarlig>Testlaget</app:vedlikeholdsansvarlig><app:senterlinje><gml:LineString srsName="urn:ogc:def:crs:EPSG::4326">
        <gml:posList>""" + points + "</gml:posList></gml:LineString></app:senterlinje></app:Fotrute></wfs:member></wfs:FeatureCollection>"; }
    @Test void latitudeAndLongitudeAreNotSwapped() {
        var trails = KartverketTrailParser.parse(collection("59 6 59.1 6.1 59.2 6.2"));
        var trail = trails.getFirst();
        assertEquals(59.1, trail.latitude()); assertEquals(6.1, trail.longitude());
        assertEquals("Fotrute F_1", trail.name()); assertEquals("Testlaget", trail.operator());
        assertEquals(3, trail.lines().getFirst().size());
    }
    @Test void largeGeometryIsThinnedButRetainsEndpoints() {
        StringBuilder points = new StringBuilder();
        for (int i = 0; i < 2000; i++) points.append(59 + i * .000001).append(" 6 ");
        var line = KartverketTrailParser.parse(collection(points.toString())).getFirst().lines().getFirst();
        assertTrue(line.size() <= 501); assertEquals(59, line.getFirst().lat()); assertEquals(59.001999, line.getLast().lat(), .0000001);
    }
    @Test void externalEntitiesAndServiceExceptionsAreRejected() {
        assertThrows(IllegalStateException.class, () -> KartverketTrailParser.parse("<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///etc/passwd'>]><x>&e;</x>"));
        assertThrows(IllegalStateException.class, () -> KartverketTrailParser.parse("<ExceptionReport/>"));
    }
    @Test void exactRegionPolygonExcludesItsHolesAndNeighbors() throws Exception {
        var geometry = new ObjectMapper().readTree("""
          {"type":"MultiPolygon","coordinates":[[[[5,58],[7,58],[7,60],[5,60],[5,58]],[[5.5,58.5],[6.5,58.5],[6.5,59.5],[5.5,59.5],[5.5,58.5]]]]}
          """);
        assertTrue(KartverketTrailParser.inside(geometry, 5.2, 58.2));
        assertFalse(KartverketTrailParser.inside(geometry, 6, 59));
        assertFalse(KartverketTrailParser.inside(geometry, 8, 59));
    }
}
