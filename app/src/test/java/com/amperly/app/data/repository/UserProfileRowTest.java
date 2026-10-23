package com.amperly.app.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.amperly.app.core.common.AppConstants;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * A new account's insert must name real columns.
 *
 * <p>This is the bug that made a freshly signed-in account look like an empty app: the insert sent
 * {@code auth_user_id}, {@code verification_status} and {@code is_blocked}, which do not exist in
 * {@code public.users}. PostgREST rejects the whole statement when one column is unknown, so no
 * profile row was created, and Row Level Security then resolved the caller to nothing.
 *
 * <p>The first test pins the exact column set; the second checks it against the schema file in the
 * repository, so a rename in {@code schema.sql} fails here instead of in production.
 *
 * <p>The remaining tests cover the other half of the same guarantee. The app is no longer the only
 * thing that can create a profile: {@code schema.sql} puts a trigger on {@code auth.users} so an
 * Auth account and its application row are created together, and a backfill repairs accounts that
 * existed before the trigger. Both are asserted here, because an account with no
 * {@code public.users} row is anonymous to every Row Level Security policy — the app signs in and
 * then shows nothing, and no write of any kind is ever stored.
 */
public class UserProfileRowTest {

    private static final Set<String> EXPECTED_COLUMNS = new LinkedHashSet<>(Arrays.asList(
            "id", "authUserId", "email", "name", "phone", "role", "organizationName",
            "verificationStatus", "isBlocked"));

    @Test
    public void rowUsesExactlyTheColumnsTheUsersTableHas() {
        Map<String, Object> row = AmperlyRepository.newUserProfileRow(
                "11111111-2222-3333-4444-555555555555", "new@example.com", "new user");

        assertEquals("column set changed; update supabase/schema.sql and the RLS policies too",
                EXPECTED_COLUMNS, new LinkedHashSet<>(row.keySet()));
    }

    @Test
    public void rowNeverSendsSnakeCaseColumnNames() {
        Map<String, Object> row = AmperlyRepository.newUserProfileRow(
                "11111111-2222-3333-4444-555555555555", "new@example.com", "new user");
        for (String column : row.keySet()) {
            assertFalse("public.users has no snake_case column: " + column, column.contains("_"));
        }
    }

    @Test
    public void rowSatisfiesTheSelfRegistrationPolicy() {
        Map<String, Object> row = AmperlyRepository.newUserProfileRow(
                "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee", "new@example.com", "new user");

        // users_insert_self requires authUserId = auth.uid() and role in ('CONSUMER', 'HOST').
        assertEquals(row.get("id"), row.get("authUserId"));
        assertTrue("an account may not self-register as ADMIN",
                AppConstants.ROLE_CONSUMER.equals(row.get("role"))
                        || AppConstants.ROLE_HOST.equals(row.get("role")));
        assertEquals(Boolean.FALSE, row.get("isBlocked"));
    }

    @Test
    public void anAuthAccountAlwaysGetsAProfileRow() throws Exception {
        File schema = findSchemaFile();
        assumeTrue("supabase/schema.sql is not present in this checkout", schema != null);
        String sql = readFile(schema);

        assertTrue("schema.sql must install a trigger on auth.users: without it an Auth account "
                        + "can exist with no public.users row and be invisible to every policy",
                sql.contains("create trigger on_auth_user_created"));
        assertTrue("that trigger must fire for every new Auth account",
                sql.contains("after insert on auth.users"));
        assertTrue("the trigger must call the profile function",
                sql.contains("execute function public.handle_new_auth_user()"));
        assertTrue("schema.sql must also backfill existing Auth accounts, which is what repairs an "
                        + "app that signs in and then shows nothing",
                sql.contains("select id, email, raw_user_meta_data from auth.users"));
    }

    @Test
    public void theTriggerWritesTheSameColumnsTheAppDoes() throws Exception {
        File schema = findSchemaFile();
        assumeTrue("supabase/schema.sql is not present in this checkout", schema != null);

        Set<String> triggerColumns = profileInsertColumns(readFile(schema));
        assertFalse("could not find the profile insert in schema.sql", triggerColumns.isEmpty());

        // users_insert_self checks authUserId, and the two writers must agree on the rest or one
        // of them is refused by the policy the other depends on.
        assertEquals("the profile written by the auth.users trigger must use the same columns as "
                        + "AmperlyRepository.newUserProfileRow()",
                new HashSet<>(EXPECTED_COLUMNS), new HashSet<>(triggerColumns));
    }

    @Test
    public void everyColumnExistsInTheCommittedSchema() throws Exception {
        File schema = findSchemaFile();
        assumeTrue("supabase/schema.sql is not present in this checkout", schema != null);

        Set<String> columns = usersTableColumns(schema);
        assertFalse("could not parse the users table out of schema.sql", columns.isEmpty());

        for (String column : AmperlyRepository
                .newUserProfileRow("id-1", "new@example.com", "new user").keySet()) {
            assertTrue("public.users has no column named " + column
                            + " (schema has: " + columns + ")",
                    columns.contains(column));
        }
    }

    /** The column list of the first {@code insert into public.users (...)} in the schema. */
    private static Set<String> profileInsertColumns(String sql) {
        Set<String> columns = new LinkedHashSet<>();
        String marker = "insert into public.users (";
        int start = sql.indexOf(marker);
        if (start < 0) {
            return columns;
        }
        int open = start + marker.length() - 1;
        int close = sql.indexOf(')', open);
        if (close < 0) {
            return columns;
        }
        for (String raw : sql.substring(open + 1, close).split(",")) {
            String name = raw.trim().replace("\"", "");
            if (!name.isEmpty()) {
                columns.add(name);
            }
        }
        return columns;
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

    /** Reads the {@code create table ... public.users (...)} block and lists its columns. */
    private static Set<String> usersTableColumns(File schema) throws Exception {
        Set<String> columns = new HashSet<>();
        boolean inTable = false;
        try (BufferedReader reader = new BufferedReader(new FileReader(schema))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!inTable) {
                    if (trimmed.startsWith("create table if not exists public.users")) {
                        inTable = true;
                    }
                    continue;
                }
                if (trimmed.startsWith(");")) {
                    break;
                }
                if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                    continue;
                }
                String name = trimmed.split("\\s+")[0];
                if (name.contains("(") || name.matches("(?i)primary|unique|check|constraint|foreign")
                        || name.equals("references")) {
                    continue;
                }
                columns.add(name.replace("\"", ""));
            }
        }
        return columns;
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
