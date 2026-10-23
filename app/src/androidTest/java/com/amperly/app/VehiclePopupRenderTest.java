package com.amperly.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.view.ContextThemeWrapper;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.amperly.app.ui.Car3DView;
import com.amperly.app.vehicle.SpecResolver;
import com.amperly.app.vehicle.VehicleSpecs;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Inflates the real vehicle popup and checks the 3D model is drawn inside its stage, below the
 * header and clear of the labels, using the same layout the shell uses.
 */
@RunWith(AndroidJUnit4.class)
public class VehiclePopupRenderTest {

    @Test
    public void thePopupDrawsTheModelInsideTheStage() {
        Context themed = new ContextThemeWrapper(ApplicationProvider.getApplicationContext(),
                R.style.Theme_Amperly);

        int[] measured = new int[2];
        int[] modelSize = new int[2];
        boolean[] belowHeader = new boolean[1];
        int[] sampledAndLit = new int[2];
        Set<Integer> colors = new HashSet<>();

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            View card = LayoutInflater.from(themed)
                    .inflate(R.layout.view_vehicle_popup, null, false);
            ((TextView) card.findViewById(R.id.vehicleName)).setText("Tesla Model 3");
            ((TextView) card.findViewById(R.id.vehicleSpec))
                    .setText("Long Range AWD · 82 kWh · CCS");
            ((TextView) card.findViewById(R.id.vehicleBattery)).setText("78%");

            Car3DView car = card.findViewById(R.id.car3d);
            assertNotNull(car);
            VehicleSpecs specs = SpecResolver.getInstance(themed)
                    .resolve("Tata", "Nexon EV", "Empowered+");
            car.setVehicle(specs);
            car.setAnimated(false);
            car.setAutoRotate(false);

            int width = 1000;
            card.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.AT_MOST));
            card.layout(0, 0, width, card.getMeasuredHeight());

            measured[0] = card.getMeasuredWidth();
            measured[1] = card.getMeasuredHeight();
            modelSize[0] = car.getWidth();
            modelSize[1] = car.getHeight();

            int carLeft = 0;
            int carTop = 0;
            View walker = car;
            while (walker != card && walker != null) {
                carLeft += walker.getLeft();
                carTop += walker.getTop();
                walker = walker.getParent() instanceof View ? (View) walker.getParent() : null;
            }

            View header = card.findViewById(R.id.vehicleHeader);
            belowHeader[0] = carTop >= header.getBottom() - header.getTop();

            Bitmap bitmap = Bitmap.createBitmap(measured[0], measured[1],
                    Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.WHITE);
            card.draw(canvas);

            int lit = 0;
            int sampled = 0;
            for (int y = carTop; y < carTop + car.getHeight(); y += 2) {
                for (int x = carLeft; x < carLeft + car.getWidth(); x += 2) {
                    sampled++;
                    int color = bitmap.getPixel(x, y);
                    colors.add(color & 0xFFFFFF);
                    int lum = (299 * ((color >> 16) & 0xFF) + 587 * ((color >> 8) & 0xFF)
                            + 114 * (color & 0xFF)) / 1000;
                    if (lum > 120) {
                        lit++;
                    }
                }
            }
            sampledAndLit[0] = sampled;
            sampledAndLit[1] = lit;
        });

        assertTrue("the popup measures", measured[0] > 0 && measured[1] > 0);
        assertTrue("the model stage has size", modelSize[0] > 0 && modelSize[1] > 0);
        assertTrue("model stage sits below the header", belowHeader[0]);
        assertTrue("the stage is not empty", sampledAndLit[0] > 0);
        assertTrue("the model lights up the stage",
                sampledAndLit[1] > sampledAndLit[0] * 0.03f);
        assertTrue("the model is shaded, not a flat fill", colors.size() > 100);
    }
}
