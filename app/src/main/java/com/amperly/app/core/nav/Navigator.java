package com.amperly.app.core.nav;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.amperly.app.R;

import java.util.Locale;

/**
 * Single place that knows how to move between screens. Keeping navigation here means a flow such as
 * discovery → details → reservation → QR code works from any entry point.
 */
public final class Navigator {

    private Navigator() {
    }

    public static void open(@NonNull Fragment from, @NonNull Fragment target, String tag) {
        FragmentActivity activity = from.getActivity();
        if (activity == null) {
            return;
        }
        activity.getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, target, tag)
                .addToBackStack(tag)
                .commit();
    }

    public static void openRoot(@NonNull FragmentActivity activity, @NonNull Fragment target, String tag) {
        activity.getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, target, tag)
                .commit();
    }

    public static boolean pop(@NonNull Fragment from) {
        FragmentManager manager = from.getParentFragmentManager();
        if (manager.getBackStackEntryCount() > 0) {
            manager.popBackStack();
            return true;
        }
        return false;
    }

    /** Hand turn-by-turn navigation to the phone's maps application (works with no API key). */
    public static void navigateTo(Context context, double latitude, double longitude, String label) {
        if (context == null) {
            return;
        }
        String query = label != null && !label.isEmpty()
                ? label
                : String.format(Locale.US, "%.5f,%.5f", latitude, longitude);
        Uri geo = Uri.parse("geo:" + latitude + "," + longitude + "?q="
                + Uri.encode(latitude + "," + longitude + "(" + query + ")"));
        Intent intent = new Intent(Intent.ACTION_VIEW, geo);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (intent.resolveActivity(context.getPackageManager()) != null) {
            context.startActivity(intent);
            return;
        }
        Intent fallback = new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/maps/search/?api=1&query=" + latitude + "," + longitude));
        fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(fallback);
    }

    /** Masked calling: dial through the platform-provided masked number, never the real one. */
    public static void dial(Context context, String number) {
        if (context == null || number == null || number.trim().isEmpty()) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number.trim()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
