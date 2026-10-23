package com.amperly.app.data.remote;

import androidx.annotation.Nullable;

import com.amperly.app.BuildConfig;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Holds the Retrofit instances and the access token for the current session.
 *
 * <p>The publishable key is attached to every request as the {@code apikey} header.
 * The user's access token, when there is one, is attached as {@code Authorization:
 * Bearer ...} by the call sites in {@link SupabaseRest} — that header is what lets Row
 * Level Security decide which rows the caller may see.
 */
public final class SupabaseClient {

    /** Updated by {@link SupabaseAuth} whenever the session changes. */
    private static volatile String accessToken;
    private static volatile String appUserId;

    private static Retrofit postgrestRetrofit;
    private static Retrofit goTrueRetrofit;

    private SupabaseClient() {
    }

    static void setAccessToken(@Nullable String token) {
        accessToken = token;
    }

    /** The application-level user id the server reported, for cross-checking locally. */
    static void setAppUserId(@Nullable String userId) {
        appUserId = userId;
    }

    @Nullable
    static String appUserId() {
        return appUserId;
    }

    public static boolean isReady() {
        return SupabaseConfig.isConfigured() && !SupabaseConfig.looksLikeSecretKey();
    }

    /** {@code "Bearer <access token>"}, or null when nobody is signed in. */
    @Nullable
    static String authHeader() {
        String token = accessToken;
        return token == null || token.isEmpty() ? null : "Bearer " + token;
    }

    private static OkHttpClient httpClient() {
        // BASIC, not BODY: request/response bodies carry JWTs, phone numbers, masked
        // numbers and meter readings, and must not end up in logcat.
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(BuildConfig.DEBUG
                ? HttpLoggingInterceptor.Level.BASIC
                : HttpLoggingInterceptor.Level.NONE);

        return new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    Request.Builder builder = original.newBuilder()
                            .header("apikey", SupabaseConfig.publishableKey())
                            .header("Accept", "application/json");
                    if (original.header("Authorization") == null) {
                        String bearer = authHeader();
                        if (bearer != null) {
                            builder.header("Authorization", bearer);
                        }
                    }
                    return chain.proceed(builder.build());
                })
                .addInterceptor(logging)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    private static Retrofit.Builder retrofitBuilder() {
        return new Retrofit.Builder()
                .baseUrl(SupabaseConfig.baseUrl())
                .client(httpClient())
                .addConverterFactory(GsonConverterFactory.create());
    }

    public static SupabaseApi.Postgrest postgrest() {
        if (!SupabaseConfig.isConfigured()) {
            throw new IllegalStateException(
                    "Supabase is not configured. Check SupabaseClient.isReady() before use.");
        }
        if (postgrestRetrofit == null) {
            synchronized (SupabaseClient.class) {
                if (postgrestRetrofit == null) {
                    postgrestRetrofit = retrofitBuilder().build();
                }
            }
        }
        return postgrestRetrofit.create(SupabaseApi.Postgrest.class);
    }

    public static SupabaseApi.GoTrue goTrue() {
        if (!SupabaseConfig.isConfigured()) {
            throw new IllegalStateException(
                    "Supabase is not configured. Check SupabaseClient.isReady() before use.");
        }
        if (goTrueRetrofit == null) {
            synchronized (SupabaseClient.class) {
                if (goTrueRetrofit == null) {
                    goTrueRetrofit = retrofitBuilder().build();
                }
            }
        }
        return goTrueRetrofit.create(SupabaseApi.GoTrue.class);
    }
}
