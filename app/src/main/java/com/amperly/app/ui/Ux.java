package com.amperly.app.ui;

import android.content.Context;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.snackbar.Snackbar;

public final class Ux {

    private Ux() {
    }

    public static void toast(@Nullable Fragment fragment, String message) {
        if (fragment == null || fragment.getContext() == null || message == null) {
            return;
        }
        Toast.makeText(fragment.getContext(), message, Toast.LENGTH_LONG).show();
    }

    public static void toast(Context context, String message) {
        if (context == null || message == null) {
            return;
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }

    public static void snack(@Nullable View anchor, String message) {
        if (anchor == null || message == null) {
            return;
        }
        Snackbar.make(anchor, message, Snackbar.LENGTH_LONG).show();
    }

    public static String formatMoney(double amount) {
        return com.amperly.app.core.common.Formatters.money(amount);
    }
}
