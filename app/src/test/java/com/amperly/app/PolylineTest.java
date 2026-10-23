package com.amperly.app;

import com.amperly.app.core.routing.Polyline;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The encoded-polyline decoder used for Google road geometry.
 *
 * <p>The canonical Google example is used so a mistake in the zig-zag sign handling or the 1e5
 * scale cannot pass unnoticed — a wrong decode puts the drawn route in the wrong place with no
 * visible error.
 */
public class PolylineTest {

    @Test
    public void decodesTheCanonicalGoogleExample() {
        List<double[]> points = Polyline.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@");
        assertEquals(3, points.size());
        assertEquals(38.5, points.get(0)[0], 1e-5);
        assertEquals(-120.2, points.get(0)[1], 1e-5);
        assertEquals(40.7, points.get(1)[0], 1e-5);
        assertEquals(-120.95, points.get(1)[1], 1e-5);
        assertEquals(43.252, points.get(2)[0], 1e-5);
        assertEquals(-126.453, points.get(2)[1], 1e-5);
    }

    @Test
    public void anEmptyStringDecodesToNoPoints() {
        assertTrue(Polyline.decode("").isEmpty());
    }

    @Test
    public void aTwoPointLineKeepsItsEndpoints() {
        List<double[]> points = Polyline.decode("_p~iF~ps|U_ulLnnqC");
        assertEquals(2, points.size());
        assertEquals(38.5, points.get(0)[0], 1e-5);
        assertEquals(40.7, points.get(1)[0], 1e-5);
    }
}
