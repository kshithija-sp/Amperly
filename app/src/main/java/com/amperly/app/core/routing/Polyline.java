package com.amperly.app.core.routing;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Decoder for Google's encoded polyline format — the compact representation a Directions response
 * uses for road geometry.
 *
 * <p>Kept free of Android and network access so it can be unit tested directly. The encoding is
 * easy to get subtly wrong (zig-zag sign handling, five-bit chunks, the 1e5 scale), and a mistake
 * puts the drawn route in the wrong hemisphere without any visible error.
 */
public final class Polyline {

    private static final double SCALE = 1e5;

    private Polyline() {
    }

    /**
     * Decodes an encoded polyline.
     *
     * @return latitude/longitude pairs as {@code {lat, lng}}, in order. Empty when the input is
     *         blank.
     */
    @NonNull
    public static List<double[]> decode(@NonNull String encoded) {
        List<double[]> points = new ArrayList<>();
        if (encoded.isEmpty()) {
            return points;
        }
        int index = 0;
        int latitude = 0;
        int longitude = 0;
        int length = encoded.length();

        while (index < length) {
            int[] latResult = decodeValue(encoded, index);
            index = latResult[1];
            latitude += latResult[0];

            int[] lngResult = decodeValue(encoded, index);
            index = lngResult[1];
            longitude += lngResult[0];

            points.add(new double[]{latitude / SCALE, longitude / SCALE});
        }
        return points;
    }

    /** Decodes one zig-zag varint. Returns {@code {value, nextIndex}}. */
    private static int[] decodeValue(@NonNull String encoded, int startIndex) {
        int index = startIndex;
        int shift = 0;
        int result = 0;
        int b;
        do {
            if (index >= encoded.length()) {
                return new int[]{0, index};
            }
            b = encoded.charAt(index++) - 63;
            result |= (b & 0x1f) << shift;
            shift += 5;
        } while (b >= 0x20);
        int value = (result & 1) != 0 ? ~(result >> 1) : (result >> 1);
        return new int[]{value, index};
    }
}
