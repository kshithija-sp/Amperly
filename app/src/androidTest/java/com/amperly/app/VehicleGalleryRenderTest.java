package com.amperly.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.appcompat.view.ContextThemeWrapper;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.amperly.app.feature.consumer.VehicleGalleryAdapter;
import com.amperly.app.vehicle.SpecResolver;
import com.amperly.app.vehicle.VehicleSpecs;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The debug gallery must lay out and draw all twelve catalogue cars with real meshes. */
@RunWith(AndroidJUnit4.class)
public class VehicleGalleryRenderTest {

    @Test
    public void everyCatalogueCardRendersItsModelAndDimensions() {
        Context themed = new ContextThemeWrapper(ApplicationProvider.getApplicationContext(),
                R.style.Theme_Amperly);
        List<VehicleSpecs> catalogue = SpecResolver.getInstance(themed).catalogue();
        assertEquals("catalogue size", 12, catalogue.size());

        List<CardResult> results = new ArrayList<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            VehicleGalleryAdapter adapter = new VehicleGalleryAdapter();
            adapter.submit(catalogue);
            assertEquals(12, adapter.getItemCount());

            FrameLayout parent = new FrameLayout(themed);
            for (int position = 0; position < catalogue.size(); position++) {
                VehicleGalleryAdapter.Holder holder = adapter.onCreateViewHolder(parent, 0);
                adapter.onBindViewHolder(holder, position);
                View card = holder.itemView;
                card.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.AT_MOST));
                card.layout(0, 0, card.getMeasuredWidth(), card.getMeasuredHeight());

                TextView placeholder = card.findViewById(R.id.galleryReferencePlaceholder);
                Bitmap bitmap = Bitmap.createBitmap(card.getMeasuredWidth(),
                        card.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
                card.draw(new Canvas(bitmap));

                int distinct = 0;
                int previous = 0;
                for (int y = 0; y < bitmap.getHeight(); y += 7) {
                    for (int x = 0; x < bitmap.getWidth(); x += 7) {
                        int color = bitmap.getPixel(x, y);
                        if (color != previous) {
                            distinct++;
                            previous = color;
                        }
                    }
                }
                results.add(new CardResult(catalogue.get(position).displayName(),
                        card.getMeasuredWidth(), card.getMeasuredHeight(), distinct,
                        placeholder != null && placeholder.getVisibility() == View.VISIBLE));
            }
        });

        assertEquals(12, results.size());
        for (CardResult result : results) {
            assertTrue(result.name + " has width", result.width > 0);
            assertTrue(result.name + " has height", result.height > 0);
            assertTrue(result.name + " draws content", result.distinct > 20);
            assertTrue("no bundled reference photo for " + result.name,
                    result.placeholderVisible);
        }
    }

    private static final class CardResult {
        final String name;
        final int width;
        final int height;
        final int distinct;
        final boolean placeholderVisible;

        CardResult(String name, int width, int height, int distinct, boolean placeholderVisible) {
            this.name = name;
            this.width = width;
            this.height = height;
            this.distinct = distinct;
            this.placeholderVisible = placeholderVisible;
        }
    }
}
