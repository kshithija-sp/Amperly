package com.amperly.app.data.remote;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.Nullable;

import com.amperly.app.core.common.Formatters;
import com.amperly.app.data.local.AmperlyDatabase;
import com.google.gson.Gson;

import java.util.List;

/**
 * The app's connection to Supabase.
 *
 * <p>Postgres is the source of truth. A save writes straight to the server through
 * {@link #save}, {@link #patch}, {@link #delete} or {@link #call}; there is no offline queue,
 * so a write either reaches the database or is reported. Room keeps the copy that was read
 * back.
 *
 * <p>{@link #syncNow} is the read direction. It pages each table forward from its per-table
 * cursor and upserts the changed rows into Room, so another device's changes — and the rows
 * the settlement functions write on the server — appear in the app.
 */
public final class SyncEngine {

    private static final String TAG = "SyncEngine";
    private static final String CURSOR_PREFS = "amperly_sync_cursors";
    private static final String KEY_LAST_SYNC_MS = "__lastSyncMs";
    private static final String KEY_LAST_ERROR = "__lastError";

    private static final int MAX_PULL_PAGES = 20;
    private static final Gson GSON = new Gson();

    private SyncEngine() {
    }

    /** The outcome of one pull, for the UI to report honestly. */
    public static final class Result {
        public final boolean ran;
        public final int pulled;
        @Nullable
        public final String message;

        Result(boolean ran, int pulled, @Nullable String message) {
            this.ran = ran;
            this.pulled = pulled;
            this.message = message;
        }

        static Result skipped(String message) {
            return new Result(false, 0, message);
        }

        public String summary() {
            if (!ran) {
                return message == null ? "Sync did not run." : message;
            }
            return pulled + " pulled.";
        }
    }

    public static boolean isEnabled() {
        return SupabaseRest.isReady();
    }

    // ------------------------------------------------------------------- write
    // These run on the repository's write thread, in the same task as the Room write, so a
    // save is sent as soon as it is made. Failures propagate to the caller, which records
    // them for the account screen rather than queueing the change for later.

    /** Inserts or replaces one row. */
    public static void save(String tableName, Object entity) {
        if (!isEnabled() || entity == null) {
            return;
        }
        SyncTables.Table table = SyncTables.byName(tableName);
        if (table == null) {
            return;
        }
        SupabaseRest.upsertJson(table.name, GSON.toJson(entity, table.type));
    }

    /**
     * Updates columns of an existing row, addressed by its primary key.
     *
     * <p>A PATCH, not an upsert: an upsert is an INSERT ... ON CONFLICT and is also checked
     * against the table's INSERT policy, which a partial change cannot satisfy.
     */
    public static void patch(String tableName, String rowId, Object payload) {
        if (!isEnabled() || payload == null || rowId == null || rowId.isEmpty()) {
            return;
        }
        SyncTables.Table table = SyncTables.byName(tableName);
        if (table == null) {
            return;
        }
        SupabaseRest.updateById(table.name, rowId, GSON.toJson(payload));
    }

    public static void delete(String tableName, String rowId) {
        if (!isEnabled() || rowId == null || rowId.isEmpty()) {
            return;
        }
        SyncTables.Table table = SyncTables.byName(tableName);
        if (table == null) {
            return;
        }
        SupabaseRest.deleteById(table.name, rowId);
    }

    /** Runs a Postgres function, for the settlement transitions. */
    public static void call(String functionName, @Nullable Object args) {
        if (!isEnabled() || functionName == null || functionName.isEmpty()) {
            return;
        }
        SupabaseRest.rpc(functionName, args);
    }

    // -------------------------------------------------------------------- pull

    public static Result syncNow(Context context, AmperlyDatabase db) {
        if (!SupabaseRest.isReady()) {
            return Result.skipped("Supabase is not configured — running on the device only.");
        }
        if (!SupabaseAuth.isSignedIn()) {
            return Result.skipped("Not signed in to Supabase, so nothing was pulled.");
        }

        int pulled;
        try {
            pulled = pull(context, db);
        } catch (SupabaseRest.RestException restException) {
            recordError(context, restException.getMessage());
            return new Result(true, 0, "Sync stopped: " + restException.getMessage());
        } catch (RuntimeException unexpected) {
            recordError(context, unexpected.getMessage());
            return new Result(true, 0, "Sync stopped unexpectedly: " + unexpected.getMessage());
        }

        recordSuccess(context);
        return new Result(true, pulled, null);
    }

    /** Pages every table forward from its stored cursor. */
    private static int pull(Context context, AmperlyDatabase db) {
        SharedPreferences cursors = cursors(context);
        int pulled = 0;

        for (SyncTables.Table table : SyncTables.ALL) {
            String cursor = cursors.getString(table.name, null);
            for (int page = 0; page < MAX_PULL_PAGES; page++) {
                SupabaseRest.SyncPage<?> result =
                        SupabaseRest.selectChanged(table.name, table.type, cursor, SupabaseRest.PAGE_SIZE);
                if (result.rows.isEmpty() && result.cursor == null) {
                    break;
                }
                final List<?> rows = result.rows;
                if (!rows.isEmpty()) {
                    db.runInTransaction(() -> {
                        for (Object row : rows) {
                            SyncTables.write(db, table, row);
                        }
                    });
                    pulled += rows.size();
                }
                if (result.cursor != null) {
                    cursor = result.cursor;
                    cursors.edit().putString(table.name, cursor).apply();
                }
                if (!result.isFullPage(SupabaseRest.PAGE_SIZE)) {
                    break;
                }
            }
        }
        return pulled;
    }

    // ------------------------------------------------------------------- state

    /** Human-readable state for the UI: when the last pull worked, and what broke. */
    public static String statusSummary(Context context, AmperlyDatabase db) {
        if (!isEnabled()) {
            return "Device only — no Supabase project configured.";
        }
        if (!SupabaseAuth.isSignedIn()) {
            return "Supabase is configured but nobody is signed in.";
        }
        long lastSync = cursors(context).getLong(KEY_LAST_SYNC_MS, 0L);
        String lastError = cursors(context).getString(KEY_LAST_ERROR, null);

        StringBuilder summary = new StringBuilder();
        summary.append(lastSync > 0
                ? "Synced " + Formatters.agoFromNow(lastSync)
                : "Not synced yet");
        if (lastError != null && !lastError.isEmpty()) {
            summary.append(" · ").append(lastError);
        }
        return summary.toString();
    }

    /**
     * Records a failed write so the account screen can say so instead of leaving the user
     * believing a save reached the database when it did not. The local copy stays in Room.
     */
    public static void recordWriteError(Context context, @Nullable String message) {
        String text = message == null || message.trim().isEmpty()
                ? "Last save did not reach the server." : message;
        Log.w(TAG, text);
        recordError(context, text);
    }

    /** Clears the pull cursors — used on sign-out so the next account reads from scratch. */
    public static void reset(Context context) {
        cursors(context).edit().clear().apply();
    }

    private static void recordSuccess(Context context) {
        cursors(context).edit()
                .putLong(KEY_LAST_SYNC_MS, System.currentTimeMillis())
                .remove(KEY_LAST_ERROR)
                .apply();
    }

    private static void recordError(Context context, @Nullable String message) {
        cursors(context).edit()
                .putString(KEY_LAST_ERROR, message)
                .apply();
    }

    private static SharedPreferences cursors(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(CURSOR_PREFS, Context.MODE_PRIVATE);
    }
}
