package com.amperly.app.ui.content;

public class NavItem {

    public final String pageId;
    public final String label;
    public final int iconRes;

    public NavItem(String pageId, String label, int iconRes) {
        this.pageId = pageId;
        this.label = label;
        this.iconRes = iconRes;
    }
}
