package com.amperly.app.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.amperly.app.R;
import com.amperly.app.ui.content.NavItem;

import java.util.ArrayList;
import java.util.List;

/** Builds role-specific tabs at runtime so the shell never shows unusable destinations. */
public class BottomNavView extends LinearLayout {

    public interface OnItemSelectedListener {
        void onItemSelected(int index, NavItem item);
    }

    private final List<NavItem> items = new ArrayList<>();
    private final List<View> itemViews = new ArrayList<>();
    private OnItemSelectedListener listener;
    private int selectedIndex = -1;

    public BottomNavView(Context context) {
        this(context, null);
    }

    public BottomNavView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setClipToPadding(false);
        setPadding(6, 2, 6, 4);
    }

    public void setItems(List<NavItem> newItems, OnItemSelectedListener newListener) {
        this.items.clear();
        this.items.addAll(newItems);
        this.listener = newListener;
        this.selectedIndex = -1;

        removeAllViews();
        itemViews.clear();
        setWeightSum(items.size());

        LayoutInflater inflater = LayoutInflater.from(getContext());
        for (int i = 0; i < items.size(); i++) {
            final int index = i;
            final NavItem item = items.get(i);
            View view = inflater.inflate(R.layout.item_bottom_nav, this, false);
            LayoutParams params = new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f);
            view.setLayoutParams(params);

            TextView label = view.findViewById(R.id.navLabel);
            ImageView icon = view.findViewById(R.id.navIcon);
            label.setText(item.label);
            icon.setImageResource(item.iconRes);
            view.setContentDescription(item.label);
            view.setOnClickListener(v -> select(index, true));

            addView(view);
            itemViews.add(view);
        }
    }

    public void select(int index, boolean notify) {
        if (index < 0 || index >= itemViews.size()) {
            return;
        }
        selectedIndex = index;
        for (int i = 0; i < itemViews.size(); i++) {
            applyState(itemViews.get(i), i == index);
        }
        if (notify && listener != null) {
            listener.onItemSelected(index, items.get(index));
        }
    }

    public void clearSelection() {
        selectedIndex = -1;
        for (View view : itemViews) {
            applyState(view, false);
        }
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    private void applyState(View view, boolean selected) {
        FrameLayout iconWrap = view.findViewById(R.id.navIconWrap);
        ImageView icon = view.findViewById(R.id.navIcon);
        TextView label = view.findViewById(R.id.navLabel);

        iconWrap.setBackground(selected
                ? ContextCompat.getDrawable(getContext(), R.drawable.bg_nav_selected)
                : null);
        icon.setColorFilter(ContextCompat.getColor(getContext(),
                selected ? R.color.ink : R.color.muted));
        label.setTextColor(ContextCompat.getColor(getContext(),
                selected ? R.color.ink : R.color.muted));
        label.setAlpha(selected ? 1f : 0.9f);
        view.setSelected(selected);
    }
}
