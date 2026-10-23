package com.amperly.app.ui.dialog;

import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.amperly.app.R;
import com.amperly.app.engine.meter.MeterSimulator;

/**
 * Host-side meter evidence capture. The prototype always labels readings as simulated or manually
 * entered because there is no live charger integration.
 */
public final class MeterEvidenceDialog {

    public interface OnSubmit {
        void onSubmit(double startReading, double endReading, String note);
    }

    private MeterEvidenceDialog() {
    }

    public static void show(Fragment fragment, String stationName, String stationId,
                           double suggestedStart, OnSubmit onSubmit) {
        Context context = fragment.getContext();
        if (context == null) {
            return;
        }
        int padding = (int) (16 * context.getResources().getDisplayMetrics().density);
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(padding, padding, padding, padding);

        TextView header = new TextView(context);
        header.setText(stationName + "\nCurrent demo meter reading: "
                + String.format(java.util.Locale.US, "%.2f kWh", MeterSimulator.currentReading(stationId))
                + "\nReadings are simulated or manually entered — Amperly does not read live hardware.");
        container.addView(header);

        TextView startLabel = new TextView(context);
        startLabel.setText(R.string.session_start_meter);
        container.addView(startLabel);

        EditText startEdit = new EditText(context);
        startEdit.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        startEdit.setText(String.format(java.util.Locale.US, "%.2f", suggestedStart));
        container.addView(startEdit);

        TextView endLabel = new TextView(context);
        endLabel.setText(R.string.session_end_meter);
        container.addView(endLabel);

        EditText endEdit = new EditText(context);
        endEdit.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        container.addView(endEdit);

        TextView noteLabel = new TextView(context);
        noteLabel.setText("Session note (optional)");
        container.addView(noteLabel);

        EditText noteEdit = new EditText(context);
        noteEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        container.addView(noteEdit);

        new AlertDialog.Builder(context)
                .setTitle(R.string.host_record_readings)
                .setView(container)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_save, (dialog, which) -> {
                    double start = parse(startEdit.getText().toString(), suggestedStart);
                    double end = parse(endEdit.getText().toString(), start);
                    if (onSubmit != null) {
                        onSubmit.onSubmit(start, end, noteEdit.getText().toString().trim());
                    }
                })
                .show();
    }

    private static double parse(String value, double fallback) {
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
