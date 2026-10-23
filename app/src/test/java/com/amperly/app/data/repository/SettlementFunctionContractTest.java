package com.amperly.app.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The settlement functions are the contract between the app and the database.
 *
 * <p>{@code payments} and {@code invoices} have no client write policy, and the billing columns of
 * {@code charging_sessions} are pinned by a trigger, so these functions are the only route by which
 * money is written at all. They were documented in {@code supabase/README.md} but had never been
 * written, which is why authorization, capture, refund, cancellation and enforcement all had
 * nothing to reach: the app queued the call, it came back 404, and the row never existed.
 *
 * <p>PostgREST resolves a function by the names of its arguments, so a signature that drifts from
 * the keys {@code AmperlyRepository} sends is a runtime failure with no compile-time warning. These
 * tests pin both sides of that interface against {@code supabase/schema.sql}.
 */
public class SettlementFunctionContractTest {

    /** Function name to the parameters the app supplies, which must be these exact names. */
    private static final Map<String, Set<String>> EXPECTED = new LinkedHashMap<>();

    static {
        EXPECTED.put("authorize_payment", set("p_id", "p_reservation_id", "p_station_id",
                "p_amount", "p_idempotency_key"));
        EXPECTED.put("finalize_charging_session", set("p_session_id", "p_reservation_id",
                "p_start_meter_kwh", "p_end_meter_kwh", "p_is_simulated", "p_meter_reading_id",
                "p_invoice_id"));
        EXPECTED.put("cancel_reservation", set("p_reservation_id"));
        EXPECTED.put("decide_station_approval", set("p_station_id", "p_approve"));
        EXPECTED.put("resolve_dispute_refund", set("p_dispute_id"));
        EXPECTED.put("freeze_dispute_payout", set("p_dispute_id"));
        EXPECTED.put("apply_host_enforcement", set("p_dispute_id", "p_action", "p_fine_amount",
                "p_note"));
    }

    @Test
    public void everySettlementFunctionIsDefined() throws Exception {
        String sql = requireSchema();

        for (String function : EXPECTED.keySet()) {
            assertTrue("supabase/schema.sql must define public." + function + "(), or the app's "
                            + "call to /rest/v1/rpc/" + function + " has nothing to reach",
                    sql.contains("create or replace function public." + function + "("));
        }
    }

    @Test
    public void everySettlementFunctionTakesTheArgumentsTheAppSends() throws Exception {
        String sql = requireSchema();

        for (Map.Entry<String, Set<String>> entry : EXPECTED.entrySet()) {
            Set<String> declared = parameters(sql, entry.getKey());
            assertFalse("could not read the parameters of public." + entry.getKey() + "()",
                    declared.isEmpty());
            assertEquals("public." + entry.getKey() + "() must take exactly the argument names "
                            + "AmperlyRepository passes; PostgREST resolves the function by them",
                    entry.getValue(), declared);
        }
    }

    @Test
    public void settlementFunctionsAreNotExecutableByThePublic() throws Exception {
        String sql = requireSchema();

        // PostgreSQL grants EXECUTE on a new function to PUBLIC. Each settlement function
        // re-checks the caller itself, but an anonymous visitor should not reach them at all.
        for (String function : EXPECTED.keySet()) {
            assertTrue("public." + function + "() must be revoked from PUBLIC in schema.sql",
                    sql.contains("revoke all on function public." + function + "("));
            assertTrue("public." + function + "() must be granted to authenticated",
                    sql.contains("grant execute on function public." + function + "("));
        }
    }

    @Test
    public void aClientCannotWriteTheBilledAmount() throws Exception {
        String sql = requireSchema();

        // The README promises the billing columns are pinned by a trigger. Without it a consumer
        // could PATCH their own session's totalPaid and pay nothing.
        assertTrue("schema.sql must pin charging_sessions' billing columns with a trigger",
                sql.contains("create trigger prevent_billing_tampering"));
        assertTrue("that trigger must fire on update",
                sql.contains("before update on public.charging_sessions"));
        assertTrue("the pin must reject a client-changed totalPaid",
                sql.contains("new.\"totalPaid\""));
    }

    private static String requireSchema() throws Exception {
        File schema = findSchemaFile();
        assumeTrue("supabase/schema.sql is not present in this checkout", schema != null);
        String sql = readFile(schema);
        assertFalse("supabase/schema.sql is empty", sql.isEmpty());
        return sql;
    }

    /** The declared parameter names of one function, read from its signature. */
    private static Set<String> parameters(String sql, String function) {
        String marker = "create or replace function public." + function + "(";
        int start = sql.indexOf(marker);
        if (start < 0) {
            return new LinkedHashSet<>();
        }
        int close = sql.indexOf(')', start);
        if (close < 0) {
            return new LinkedHashSet<>();
        }
        Set<String> parameters = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile("p_[a-z0-9_]+").matcher(sql.substring(start, close));
        while (matcher.find()) {
            parameters.add(matcher.group());
        }
        return parameters;
    }

    private static Set<String> set(String... values) {
        return new LinkedHashSet<>(Arrays.asList(values));
    }

    private static String readFile(File file) throws Exception {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString();
    }

    private static File findSchemaFile() {
        String[] candidates = {
                "../supabase/schema.sql",
                "supabase/schema.sql",
                "../../supabase/schema.sql",
        };
        for (String candidate : candidates) {
            File file = new File(candidate);
            if (file.isFile()) {
                return file;
            }
        }
        return null;
    }
}
