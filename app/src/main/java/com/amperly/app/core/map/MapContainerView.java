package com.amperly.app.core.map;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The frame a Google map is dropped into.
 *
 * <p>Most Amperly screens put the map inside a {@code ScrollView}. Without this, the scroll gesture
 * wins the touch and the map silently refuses to pan. Asking the scrolling ancestor not to
 * intercept while a finger is on the map keeps both usable: drag on the map to move it, drag
 * anywhere else to scroll the screen.
 */
public class MapContainerView extends FrameLayout {

    public MapContainerView(@NonNull Context context) {
        super(context);
    }

    public MapContainerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public MapContainerView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        return false;
    }
}
