package com.amperly.app.data.remote;

import java.util.Map;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.QueryMap;

/**
 * The two halves of the Supabase HTTP surface, both served from the project URL.
 *
 * <p>These are deliberately generic: the table name is a path parameter and filters are
 * a query map, so all 23 tables go through one small interface instead of 23 typed ones.
 * Responses come back as raw {@link ResponseBody} and are mapped to entities by
 * {@link SupabaseRest}, which is the only place that knows about the entity classes.
 */
public final class SupabaseApi {

    private SupabaseApi() {
    }

    /**
     * PostgREST (the auto-generated data API).
     *
     * <p>Filters are expressed like {@code id=eq.st_campus_1} or
     * {@code serverUpdatedAt=gt.2026-10-11T04:00:00Z} in the query map. The
     * {@code apikey} header is added by the OkHttp interceptor; {@code Authorization}
     * carries the signed-in user's access token so Row Level Security can identify them.
     */
    public interface Postgrest {

        @GET("rest/v1/{table}")
        Call<ResponseBody> select(@Path("table") String table,
                                  @QueryMap Map<String, String> query,
                                  @Header("Authorization") String authorization);

        /** Insert, or update in place when the primary key already exists. */
        @POST("rest/v1/{table}")
        @Headers("Prefer: resolution=merge-duplicates,return=representation")
        Call<ResponseBody> upsert(@Path("table") String table,
                                  @Body RequestBody body,
                                  @Header("Authorization") String authorization);

        /**
         * Calls a Postgres function. Used for the settlement transitions — payment
         * capture, session completion, dispute resolution — which the database does
         * not allow a client to perform with a plain table write.
         */
        @POST("rest/v1/rpc/{function}")
        Call<ResponseBody> rpc(@Path("function") String function,
                               @Body RequestBody body,
                               @Header("Authorization") String authorization);

        @PATCH("rest/v1/{table}")
        @Headers("Prefer: return=representation")
        Call<ResponseBody> update(@Path("table") String table,
                                  @QueryMap Map<String, String> filter,
                                  @Body RequestBody body,
                                  @Header("Authorization") String authorization);

        @DELETE("rest/v1/{table}")
        @Headers("Prefer: return=representation")
        Call<ResponseBody> delete(@Path("table") String table,
                                  @QueryMap Map<String, String> filter,
                                  @Header("Authorization") String authorization);

        /** Used by SyncEngine to confirm the project is reachable and the key is valid. */
        @GET("rest/v1/{table}")
        Call<ResponseBody> ping(@Path("table") String table,
                                @Query("select") String select,
                                @Query("limit") int limit,
                                @Header("Authorization") String authorization);
    }

    /** GoTrue (auth). */
    public interface GoTrue {

        @POST("auth/v1/token")
        Call<ResponseBody> grant(@Query("grant_type") String grantType,
                                 @Body RequestBody body);

        @POST("auth/v1/signup")
        Call<ResponseBody> signUp(@Body RequestBody body);

        @POST("auth/v1/logout")
        Call<ResponseBody> logout(@Header("Authorization") String authorization);
    }
}
