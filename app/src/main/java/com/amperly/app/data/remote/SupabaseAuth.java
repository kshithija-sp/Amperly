package com.amperly.app.data.remote;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.security.GeneralSecurityException;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * Supabase Auth (GoTrue) session handling.
 *
 * <p>Deliberately synchronous: the whole app already runs its work through
 * {@code AmperlyRepository.background(...)}, so blocking here keeps the calling style
 * consistent instead of stacking callbacks on top of an executor.
 *
 * <p>SESSION STORAGE: access and refresh tokens are bearer credentials — whoever holds them
 * is the account. They are therefore written to {@link EncryptedSharedPreferences}, whose
 * key lives in the Android Keystore (AES-256-GCM for values, AES-256-SIV for keys), rather
 * than to a plain XML file that a backup or a rooted device could read. If the Keystore is
 * unavailable the app fails closed and asks the user to sign in again; it never silently
 * downgrades to plaintext.
 */
public final class SupabaseAuth {

    /** Encrypted store. The name is new so a legacy plaintext file is migrated, not reused. */
    private static final String PREFS = "amperly_supabase_session_secure";

    /** The pre-encryption file name, read once so existing installs stay signed in. */
    private static final String LEGACY_PREFS = "amperly_supabase_session";

    private static final String TAG = "SupabaseAuth";

    private static final Object PREFS_LOCK = new Object();

    /** Serialises token renewal: Supabase rotates the refresh token on every use. */
    private static final Object RENEW_LOCK = new Object();

    /** Remembered so a request can renew an expiring token without a screen passing it in. */
    @Nullable
    private static volatile Context appContext;

    private static volatile SharedPreferences cachedPrefs;
    private static final String KEY_ACCESS = "access_token";
    private static final String KEY_REFRESH = "refresh_token";
    private static final String KEY_EXPIRES_AT = "expires_at_ms";
    private static final String KEY_USER_ID = "auth_user_id";
    private static final String KEY_EMAIL = "email";

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    /** Refresh this long before the token actually expires. */
    private static final long REFRESH_MARGIN_MS = 60_000L;

    private SupabaseAuth() {
    }

    /** A rejected request: bad credentials, a missing account, a policy denial. */
    public static class Failure extends Exception {
        public Failure(String message) {
            super(message);
        }
    }

    /** A transport-level problem. Nothing was changed on the server. */
    public static class NetworkFailure extends RuntimeException {
        public NetworkFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** A signed-in Supabase account. */
    public static class Session {
        public final String accessToken;
        public final String refreshToken;
        public final String authUserId;
        public final String email;
        public final long expiresAtMs;

        Session(String accessToken, String refreshToken, String authUserId, String email,
                long expiresAtMs) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.authUserId = authUserId;
            this.email = email;
            this.expiresAtMs = expiresAtMs;
        }

        public boolean needsRefresh(long nowMs) {
            return expiresAtMs > 0 && nowMs + REFRESH_MARGIN_MS >= expiresAtMs;
        }
    }

    private static final class HttpResult {
        final int code;
        final String body;

        HttpResult(int code, @Nullable String body) {
            this.code = code;
            this.body = body == null ? "" : body;
        }

        boolean isSuccessful() {
            return code >= 200 && code < 300;
        }

        JsonObject json() {
            if (body.isEmpty()) {
                return new JsonObject();
            }
            JsonElement parsed = JsonParser.parseString(body);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
        }
    }

    private interface CallSupplier {
        Response<ResponseBody> get() throws IOException;
    }

    // ------------------------------------------------------------------ state

    public static boolean isConfigured() {
        return SupabaseClient.isReady();
    }

    /** Remembers the application context for {@link #ensureFreshSession()}. */
    public static void attach(Context context) {
        if (context != null) {
            appContext = context.getApplicationContext();
        }
    }

    /** True once a session has been loaded into {@link SupabaseClient}. */
    public static boolean isSignedIn() {
        return SupabaseClient.authHeader() != null;
    }

    @Nullable
    public static String authUserId() {
        return SupabaseClient.appUserId();
    }

    // ------------------------------------------------------------------ sign in

    public static Session signIn(Context context, String email, String password)
            throws Failure {
        requireConfigured();
        attach(context);
        JsonObject body = new JsonObject();
        body.addProperty("email", email == null ? "" : email.trim());
        body.addProperty("password", password == null ? "" : password);

        HttpResult result = execute(() ->
                SupabaseClient.goTrue().grant("password", toJsonBody(body)).execute());
        if (!result.isSuccessful()) {
            throw new Failure(describe(result, "Sign-in failed."));
        }
        Session session = parseSession(result, null, null);
        persist(context, session);
        apply(session);
        return session;
    }

    /** Creates an account. Returns null when the project requires email confirmation. */
    @Nullable
    public static Session signUp(Context context, String email, String password)
            throws Failure {
        requireConfigured();
        attach(context);
        JsonObject body = new JsonObject();
        body.addProperty("email", email == null ? "" : email.trim());
        body.addProperty("password", password == null ? "" : password);

        HttpResult result = execute(() ->
                SupabaseClient.goTrue().signUp(toJsonBody(body)).execute());
        if (!result.isSuccessful()) {
            throw new Failure(describe(result, "Sign-up failed."));
        }
        if (!result.json().has("access_token")) {
            // Projects with email confirmation enabled return the bare user record.
            return null;
        }
        Session session = parseSession(result, null, null);
        persist(context, session);
        apply(session);
        return session;
    }

    // ------------------------------------------------------------------ session

    public static boolean hasStoredSession(Context context) {
        try {
            if (context == null) return false;
            SharedPreferences prefs = prefs(context);
            if (prefs == null) return false;
            String access = prefs.getString(KEY_ACCESS, null);
            return access != null && !access.trim().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /** Loads a stored session and refreshes it when it is close to expiring. */
    @Nullable
    public static Session restoreSession(Context context) {
        if (!SupabaseClient.isReady()) {
            return null;
        }
        attach(context);
        SharedPreferences prefs = prefs(context);
        String access = prefs.getString(KEY_ACCESS, null);
        if (access == null || access.isEmpty()) {
            return null;
        }
        String storedUserId = prefs.getString(KEY_USER_ID, null);
        String storedEmail = prefs.getString(KEY_EMAIL, null);
        Session session = new Session(access, prefs.getString(KEY_REFRESH, null),
                storedUserId, storedEmail, prefs.getLong(KEY_EXPIRES_AT, 0L));

        if (session.needsRefresh(System.currentTimeMillis())) {
            try {
                return refresh(context);
            } catch (Failure failure) {
                // The refresh token is no longer usable. Require a fresh sign-in rather
                // than leaving a dead token attached to every request.
                clear(context);
                return null;
            }
        }
        apply(session);
        return session;
    }

    public static Session refresh(Context context) throws Failure {
        requireConfigured();
        attach(context);
        SharedPreferences prefs = prefs(context);
        String refreshToken = prefs.getString(KEY_REFRESH, null);
        if (refreshToken == null || refreshToken.isEmpty()) {
            throw new Failure("No stored session to refresh. Please sign in again.");
        }
        JsonObject body = new JsonObject();
        body.addProperty("refresh_token", refreshToken);

        HttpResult result = execute(() ->
                SupabaseClient.goTrue().grant("refresh_token", toJsonBody(body)).execute());
        if (!result.isSuccessful()) {
            throw new Failure(describe(result, "Your session expired. Please sign in again."));
        }
        Session session = parseSession(result, prefs.getString(KEY_USER_ID, null),
                prefs.getString(KEY_EMAIL, null));
        persist(context, session);
        apply(session);
        return session;
    }

    public static void signOut(Context context) {
        try {
            String header = SupabaseClient.authHeader();
            if (SupabaseClient.isReady() && header != null) {
                SupabaseClient.goTrue().logout(header).execute();
            }
        } catch (IOException | RuntimeException ignored) {
            // Signing out locally is what matters; a failed server call must not leave
            // the user stuck in a signed-in state.
        }
        clear(context);
    }

    /**
     * Renews the access token when it is about to expire.
     *
     * <p>A Supabase session lasts one hour. Without this, every write made after that hour
     * was refused with HTTP 401 until the app was restarted, because the only place that
     * refreshed was session restore at launch. Safe to call before any request: it does
     * nothing while the token is still valid, and it never throws.
     */
    public static void ensureFreshSession() {
        renewSession(false);
    }

    /**
     * Renews after the server refused the current token, even when the stored expiry says
     * it should still be valid (a token can be revoked or rotated elsewhere).
     *
     * @return true when a fresh token was obtained and the request is worth retrying once.
     */
    public static boolean renewAfterRejection() {
        return renewSession(true);
    }

    private static boolean renewSession(boolean forced) {
        Context context = appContext;
        if (context == null || !SupabaseClient.isReady()) {
            return false;
        }
        synchronized (RENEW_LOCK) {
            try {
                SharedPreferences prefs = prefs(context);
                String refreshToken = prefs.getString(KEY_REFRESH, null);
                if (refreshToken == null || refreshToken.isEmpty()) {
                    return false;
                }
                long expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L);
                if (!forced && (expiresAt <= 0
                        || System.currentTimeMillis() + REFRESH_MARGIN_MS < expiresAt)) {
                    return false;
                }
                refresh(context);
                Log.i(TAG, "Access token renewed before expiring.");
                return true;
            } catch (Failure failure) {
                // A dead refresh token means the user has to sign in again; the caller's
                // own request will then fail with the server's 401 and say so.
                Log.w(TAG, "Could not renew the session: " + failure.getMessage());
                return false;
            } catch (RuntimeException e) {
                Log.w(TAG, "Could not renew the session: " + e, e);
                return false;
            }
        }
    }

    // ------------------------------------------------------------------ internal

    private static void apply(Session session) {
        SupabaseClient.setAccessToken(session.accessToken);
        SupabaseClient.setAppUserId(session.authUserId);
    }

    private static void persist(Context context, Session session) {
        prefs(context).edit()
                .putString(KEY_ACCESS, session.accessToken)
                .putString(KEY_REFRESH, session.refreshToken)
                .putString(KEY_USER_ID, session.authUserId)
                .putString(KEY_EMAIL, session.email)
                .putLong(KEY_EXPIRES_AT, session.expiresAtMs)
                .apply();
    }

    private static void clear(Context context) {
        try {
            prefs(context).edit().clear().apply();
        } catch (RuntimeException ignored) {
            // A broken Keystore must not trap the user in a signed-in state.
        }
        // Also remove the pre-encryption file, if this device still has one.
        try {
            context.getApplicationContext()
                    .getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
                    .edit().clear().apply();
        } catch (RuntimeException ignored) {
        }
        SupabaseClient.setAccessToken(null);
        SupabaseClient.setAppUserId(null);
    }

    /**
     * The Keystore-backed preference file, created once and cached.
     *
     * <p>Throws rather than returning a plaintext file when the Keystore cannot be used:
     * storing a bearer token unencrypted after a security failure is worse than asking the
     * user to sign in again.
     */
    private static SharedPreferences prefs(Context context) {
        SharedPreferences existing = cachedPrefs;
        if (existing != null) {
            return existing;
        }
        synchronized (PREFS_LOCK) {
            if (cachedPrefs != null) {
                return cachedPrefs;
            }
            Context app = context.getApplicationContext();
            try {
                MasterKey masterKey = new MasterKey.Builder(app)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build();
                SharedPreferences secure = EncryptedSharedPreferences.create(
                        app,
                        PREFS,
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
                migrateLegacySession(app, secure);
                cachedPrefs = secure;
                Log.i(TAG, "Session storage is encrypted with a Keystore-backed key.");
                return secure;
            } catch (GeneralSecurityException | IOException e) {
                // Never fall back to a plaintext file: a bearer token written in the clear
                // after a security failure is worse than requiring a fresh sign-in.
                Log.e(TAG, "Secure session storage unavailable; refusing to store tokens.", e);
                throw new IllegalStateException(
                        "Secure session storage is unavailable on this device.", e);
            }
        }
    }

    /**
     * Copies a session written by an older build into the encrypted store, then wipes the
     * plaintext copy. Without this, updating the app would sign everyone out.
     */
    private static void migrateLegacySession(Context app, SharedPreferences secure) {
        if (secure.getString(KEY_ACCESS, null) != null) {
            return;
        }
        try {
            SharedPreferences legacy = app.getSharedPreferences(LEGACY_PREFS,
                    Context.MODE_PRIVATE);
            String access = legacy.getString(KEY_ACCESS, null);
            if (access == null || access.isEmpty()) {
                return;
            }
            secure.edit()
                    .putString(KEY_ACCESS, access)
                    .putString(KEY_REFRESH, legacy.getString(KEY_REFRESH, null))
                    .putString(KEY_USER_ID, legacy.getString(KEY_USER_ID, null))
                    .putString(KEY_EMAIL, legacy.getString(KEY_EMAIL, null))
                    .putLong(KEY_EXPIRES_AT, legacy.getLong(KEY_EXPIRES_AT, 0L))
                    .apply();
            legacy.edit().clear().apply();
        } catch (RuntimeException ignored) {
            // A migration failure leaves the legacy file for the next attempt.
        }
    }

    private static void requireConfigured() throws Failure {
        if (!SupabaseConfig.isConfigured()) {
            throw new Failure("Supabase is not configured. Add SUPABASE_URL and "
                    + "SUPABASE_PUBLISHABLE_KEY to local.properties and rebuild.");
        }
        if (SupabaseConfig.looksLikeSecretKey()) {
            throw new Failure("The configured Supabase key is a secret key. Replace it with the "
                    + "publishable key — a secret key must never ship inside an app.");
        }
    }

    private static Session parseSession(HttpResult result, @Nullable String fallbackUserId,
                                        @Nullable String fallbackEmail) throws Failure {
        JsonObject json = result.json();
        String access = optString(json, "access_token");
        if (access == null) {
            throw new Failure("The authentication response did not contain an access token.");
        }
        String refresh = optString(json, "refresh_token");
        long expiresInSeconds = 3600L;
        JsonElement expiresIn = json.get("expires_in");
        if (expiresIn != null && expiresIn.isJsonPrimitive()) {
            expiresInSeconds = expiresIn.getAsLong();
        }

        String userId = fallbackUserId;
        String email = fallbackEmail;
        JsonElement userElement = json.get("user");
        if (userElement != null && userElement.isJsonObject()) {
            JsonObject user = userElement.getAsJsonObject();
            String parsedId = optString(user, "id");
            String parsedEmail = optString(user, "email");
            if (parsedId != null) {
                userId = parsedId;
            }
            if (parsedEmail != null) {
                email = parsedEmail;
            }
        }
        long expiresAt = System.currentTimeMillis() + (expiresInSeconds * 1000L);
        return new Session(access, refresh, userId, email, expiresAt);
    }

    private static String describe(HttpResult result, String fallback) {
        String message = null;
        if (!result.body.isEmpty()) {
            try {
                JsonObject json = result.json();
                message = firstNonEmpty(optString(json, "error_description"),
                        optString(json, "msg"), optString(json, "message"),
                        optString(json, "error"));
            } catch (RuntimeException ignored) {
                message = null;
            }
        }
        return message != null ? message : fallback + " (HTTP " + result.code + ")";
    }

    @Nullable
    private static String optString(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element == null || element.isJsonNull() || !element.isJsonPrimitive()
                ? null : element.getAsString();
    }

    @Nullable
    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private static RequestBody toJsonBody(JsonObject json) {
        return RequestBody.create(json.toString(), JSON);
    }

    /**
     * Runs a call and reads the body exactly once. Reading a {@link ResponseBody} twice
     * throws, and error bodies are the only place GoTrue puts its message, so the body
     * has to survive into {@link #describe}.
     */
    private static HttpResult execute(CallSupplier supplier) {
        try {
            Response<ResponseBody> response = supplier.get();
            ResponseBody body = response.body();
            String raw = body == null ? "" : body.string();
            return new HttpResult(response.code(), raw);
        } catch (IOException ioException) {
            throw new NetworkFailure("Could not reach Supabase. Check your connection and "
                    + "try again — nothing was changed.", ioException);
        }
    }
}
