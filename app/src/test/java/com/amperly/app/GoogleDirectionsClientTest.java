package com.amperly.app;

import com.amperly.app.core.routing.DirectionsRoute;
import com.amperly.app.core.routing.GoogleDirectionsClient;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The Google Directions response parser.
 *
 * <p>Worth testing without a network: metres/seconds conversions, the traffic duration preference
 * and the HTML markup in the instructions are all easy to get subtly wrong, and each one would
 * show up only as a wrong number or a mangled turn on the road.
 */
public class GoogleDirectionsClientTest {

    private static final String OK_RESPONSE = "{"
            + "\"status\":\"OK\","
            + "\"routes\":[{"
            + "\"overview_polyline\":{\"points\":\"_p~iF~ps|U_ulLnnqC_mqNvxq`@\"},"
            + "\"legs\":[{"
            + "\"start_address\":\"Mysore, Karnataka\","
            + "\"end_address\":\"Mangalore, Karnataka\","
            + "\"distance\":{\"value\":12345,\"text\":\"12.3 km\"},"
            + "\"duration\":{\"value\":900,\"text\":\"15 mins\"},"
            + "\"duration_in_traffic\":{\"value\":1200,\"text\":\"20 mins\"},"
            + "\"steps\":["
            + "{\"html_instructions\":\"Head <b>north</b> on Main St\","
            + "\"distance\":{\"value\":100,\"text\":\"0.1 km\"},"
            + "\"duration\":{\"value\":20,\"text\":\"1 min\"},"
            + "\"maneuver\":\"turn-left\","
            + "\"start_location\":{\"lat\":12.30,\"lng\":76.60}},"
            + "{\"html_instructions\":\"Turn right toward the campus gate\","
            + "\"distance\":{\"value\":200,\"text\":\"0.2 km\"},"
            + "\"duration\":{\"value\":40,\"text\":\"1 min\"},"
            + "\"start_location\":{\"lat\":12.31,\"lng\":76.61}}"
            + "]}]}],"
            + "\"geocoded_waypoints\":[]}";

    @Test
    public void convertsMetresAndSecondsAndPrefersTheTrafficDuration() {
        DirectionsRoute route = GoogleDirectionsClient.parseRoute(
                GoogleDirectionsClient.TravelMode.DRIVING, OK_RESPONSE);
        assertEquals(12.345, route.getDistanceKm(), 1e-6);
        assertEquals(15, route.getDurationMinutes());
        assertEquals(20, route.getDurationInTrafficMinutes());
        assertEquals("traffic-aware time is the one to plan with", 20,
                route.getEffectiveDurationMinutes());
        assertFalse("a real Directions response is never approximate", route.isApproximate());
    }

    @Test
    public void readsTheRouteGeometryAsLatitudeThenLongitude() {
        DirectionsRoute route = GoogleDirectionsClient.parseRoute(
                GoogleDirectionsClient.TravelMode.DRIVING, OK_RESPONSE);
        assertEquals(3, route.getPoints().size());
        assertEquals(38.5, route.getOriginLat(), 1e-5);
        assertEquals(-120.2, route.getOriginLng(), 1e-5);
        assertEquals(43.252, route.getDestinationLat(), 1e-5);
        assertEquals(-126.453, route.getDestinationLng(), 1e-5);
    }

    @Test
    public void stripsHtmlFromTurnInstructions() {
        DirectionsRoute route = GoogleDirectionsClient.parseRoute(
                GoogleDirectionsClient.TravelMode.DRIVING, OK_RESPONSE);
        List<DirectionsRoute.Step> steps = route.getSteps();
        assertEquals(2, steps.size());
        assertEquals("Head north on Main St", steps.get(0).getInstruction());
        assertEquals("turn-left", steps.get(0).getManeuver());
        assertEquals("0.1 km", steps.get(0).getDistanceText());
        assertEquals(12.31, steps.get(1).getStartLat(), 1e-6);
    }

    @Test
    public void keepsTheStartAndEndAddressesForDisplay() {
        DirectionsRoute route = GoogleDirectionsClient.parseRoute(
                GoogleDirectionsClient.TravelMode.DRIVING, OK_RESPONSE);
        assertEquals("Mysore, Karnataka", route.getStartAddress());
        assertEquals("Mangalore, Karnataka", route.getEndAddress());
    }

    @Test
    public void aDeniedKeyIsReportedAsAConfigurationProblem() {
        try {
            GoogleDirectionsClient.parseRoute(GoogleDirectionsClient.TravelMode.DRIVING,
                    "{\"status\":\"REQUEST_DENIED\",\"error_message\":\"\"}");
            fail("expected a failure for a denied request");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Directions API"));
        }
    }

    @Test
    public void zeroResultsIsReportedAsNoRoute() {
        try {
            GoogleDirectionsClient.parseRoute(GoogleDirectionsClient.TravelMode.DRIVING,
                    "{\"status\":\"ZERO_RESULTS\",\"routes\":[]}");
            fail("expected a failure when Google finds no route");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().toLowerCase().contains("no route"));
        }
    }

    @Test
    public void aRouteWithNoGeometryIsRejected() {
        String noGeometry = "{\"status\":\"OK\",\"routes\":[{\"legs\":[{\"distance\":{\"value\":1},"
                + "\"duration\":{\"value\":1}}],\"overview_polyline\":{\"points\":\"\"}}]}";
        try {
            GoogleDirectionsClient.parseRoute(GoogleDirectionsClient.TravelMode.DRIVING, noGeometry);
            fail("expected a failure for a route with no geometry");
        } catch (IllegalStateException expected) {
            // handled
        }
    }

    @Test
    public void theStraightLineFallbackIsAlwaysLabelledApproximate() {
        DirectionsRoute route = DirectionsRoute.straightLineEstimate(12.30, 76.60, 12.40, 76.70, 45.0);
        assertTrue(route.isApproximate());
        assertTrue(route.getPoints().size() > 2);
        assertTrue(route.getDistanceKm() > 0);
        assertEquals(12.30, route.getOriginLat(), 1e-4);
        assertEquals(12.40, route.getDestinationLat(), 1e-4);
        assertTrue(route.getEffectiveDurationMinutes() > 0);
    }
}
