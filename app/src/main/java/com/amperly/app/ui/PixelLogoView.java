package com.amperly.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/** Crisp pixel-art "A" mark. No bitmap assets required. */
public class PixelLogoView extends View {

    private static final String[] PATTERN = {
            "..###..",
            ".##.##.",
            "##...##",
            "##...##",
            "#######",
            "##...##",
            "##...##"
    };

    private final Paint tilePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pixelPaint = new Paint();

    public PixelLogoView(Context context) {
        this(context, null);
    }

    public PixelLogoView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        // Dark mode inverts the mark: a lime tile keeps the lock-up legible on the
        // near-black shell, and the pixel "A" is punched out in the ink colour.
        tilePaint.setColor(Color.parseColor("#C6F94C"));
        pixelPaint.setColor(Color.parseColor("#0E1410"));
        setContentDescription("Amperly");
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight());
        if (size <= 0) {
            return;
        }

        float inset = size * 0.015f;
        float radius = size * 0.28f;
        canvas.drawRoundRect(new RectF(inset, inset, size - inset, size - inset), radius, radius, tilePaint);

        float grid = size * 0.66f;
        float cell = grid / PATTERN.length;
        float left = (size - grid) / 2f;
        float top = (size - grid) / 2f;
        float gap = Math.max(0.8f, cell * 0.13f);

        for (int row = 0; row < PATTERN.length; row++) {
            String line = PATTERN[row];
            for (int col = 0; col < line.length(); col++) {
                if (line.charAt(col) != '#') {
                    continue;
                }
                float x = left + col * cell;
                float y = top + row * cell;
                canvas.drawRect(x + gap, y + gap, x + cell - gap, y + cell - gap, pixelPaint);
            }
        }
    }
}
