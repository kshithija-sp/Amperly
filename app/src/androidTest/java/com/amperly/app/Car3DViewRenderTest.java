package com.amperly.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.amperly.app.ui.Car3DView;
import com.amperly.app.vehicle.BodyTemplate;
import com.amperly.app.vehicle.BodyType;
import com.amperly.app.vehicle.SpecConfidence;
import com.amperly.app.vehicle.SpecResolver;
import com.amperly.app.vehicle.VehicleSpecs;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertTrue;

/**
 * Draws the vehicle popup's model on a real device renderer and checks that a shaded car - not
 * an empty stage - comes out for every body template and for a resolved database car.
 */
@RunWith(AndroidJUnit4.class)
public class Car3DViewRenderTest {

    private static final int WIDTH = 900;
    private static final int HEIGHT = 420;

    @Test
    public void everyBodyTemplateRendersAShadedCar() {
        Context context = ApplicationProvider.getApplicationContext();
        for (BodyTemplate template : BodyTemplate.all()) {
            Stats stats = stats(render(context, templateSpecs(template)), template.bodyType.name());

            assertTrue(stats.label, stats.solidPixels > 16000);
            assertTrue(stats.label, stats.distinctColors > 150);
            assertTrue(stats.label, stats.litPixels > 3000);
            assertTrue(stats.label, stats.darkPixels > 600);
            assertTrue(stats.label, stats.width > WIDTH * 0.4f);
            assertTrue(stats.label, stats.height > HEIGHT * 0.2f);
            assertTrue(stats.label, Math.abs(stats.centerX - WIDTH / 2f) < WIDTH * 0.15f);
            assertTrue(stats.label, stats.centerY > HEIGHT * 0.25f
                    && stats.centerY < HEIGHT * 0.85f);
        }
    }

    @Test
    public void aResolvedDatabaseCarRendersWithItsPaint() {
        Context context = ApplicationProvider.getApplicationContext();
        VehicleSpecs specs = SpecResolver.getInstance(context)
                .resolve("Tata", "Nexon EV", "Empowered+");
        Stats stats = stats(render(context, specs), specs.displayName());
        assertTrue(stats.label, stats.solidPixels > 16000);
        assertTrue(stats.label, stats.distinctColors > 150);
        assertTrue(stats.label, stats.litPixels > 3000);
    }

    @Test
    public void theModelChangesWithTheBodyType() {
        Context context = ApplicationProvider.getApplicationContext();
        Stats coupe = stats(render(context, templateSpecs(BodyTemplate.of(BodyType.COUPE))), "coupe");
        Stats suv = stats(render(context, templateSpecs(BodyTemplate.of(BodyType.SUV))), "suv");
        assertTrue("an SUV renders taller than a coupe", suv.height > coupe.height);
    }

    private static VehicleSpecs templateSpecs(BodyTemplate template) {
        return VehicleSpecs.builder("Test", template.bodyType.name(), template.bodyType)
                .dimensionsMm(template.referenceLengthMm, template.referenceWidthMm,
                        template.referenceHeightMm)
                .wheelbaseMm(template.referenceWheelbaseMm)
                .groundClearanceMm(template.referenceGroundClearanceMm)
                .tyre(template.referenceTyreWidthMm, template.referenceTyreAspectPercent,
                        template.referenceRimDiameterIn)
                .confidence(SpecConfidence.HIGH)
                .build();
    }

    private static Bitmap render(Context context, VehicleSpecs specs) {
        // Views, ScaleGestureDetector and ValueAnimator all need a Looper, and the JUnit
        // instrumentation thread is not the UI thread. Render on the main looper.
        Bitmap[] result = new Bitmap[1];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Car3DView view = new Car3DView(context);
            view.setVehicle(specs);
            view.setAutoRotate(false);
            view.setAnimated(false);
            view.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
            view.layout(0, 0, WIDTH, HEIGHT);

            Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
            view.draw(new Canvas(bitmap));
            result[0] = bitmap;
        });
        return result[0];
    }

    private static Stats stats(Bitmap bitmap, String label) {
        return Stats.of(bitmap, WIDTH, HEIGHT, label);
    }

    private static final class Stats {
        final String label;
        int solidPixels;
        int litPixels;
        int darkPixels;
        int distinctColors;
        float width;
        float height;
        float centerX;
        float centerY;

        Stats(String label) {
            this.label = label;
        }

        static Stats of(Bitmap bitmap, int w, int h, String label) {
            Stats stats = new Stats(label);
            Set<Integer> colors = new HashSet<>();
            int minX = w;
            int minY = h;
            int maxX = -1;
            int maxY = -1;
            int[] row = new int[w];

            for (int y = 0; y < h; y++) {
                bitmap.getPixels(row, 0, w, 0, y, w, 1);
                for (int x = 0; x < w; x++) {
                    int color = row[x];
                    int alpha = (color >>> 24) & 0xFF;
                    if (alpha < 200) {
                        continue;
                    }
                    stats.solidPixels++;
                    colors.add(color & 0xFFFFFF);
                    int lum = (299 * ((color >> 16) & 0xFF)
                            + 587 * ((color >> 8) & 0xFF)
                            + 114 * (color & 0xFF)) / 1000;
                    if (lum > 150) {
                        stats.litPixels++;
                    } else if (lum < 120) {
                        stats.darkPixels++;
                    }
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }

            stats.distinctColors = colors.size();
            if (maxX >= 0) {
                stats.width = maxX - minX;
                stats.height = maxY - minY;
                stats.centerX = (minX + maxX) / 2f;
                stats.centerY = (minY + maxY) / 2f;
            }
            return stats;
        }
    }
}
