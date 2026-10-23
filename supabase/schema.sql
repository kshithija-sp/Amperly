-- ============================================================================
--  Amperly — Supabase / Postgres schema
-- ============================================================================
--  Paste this whole file into:  Supabase Dashboard -> SQL Editor -> New query
--  It is idempotent: safe to run more than once.
--
--  DESIGN NOTE — why the column names look unusual
--  ---------------------------------------------------------------------------
--  Every column name below is quoted camelCase and matches the Java field name
--  in app/src/main/java/com/amperly/app/data/local/entity/ exactly. None of the
--  Room entities use @ColumnInfo, so the Room/SQLite column already equals the
--  field name. By keeping the same names here:
--
--      PostgREST JSON key  ==  Java field name  ==  Room column
--
--  ...which means Gson deserialises PostgREST responses straight into the
--  existing entity classes and the app needs no DTO/mapping layer at all.
--  The cost is quoted identifiers in SQL. That is a deliberate trade.
--
--  Columns NOT present in the Room entities (authUserId, serverUpdatedAt) are
--  server-only: Gson silently ignores JSON keys it has no field for, so adding
--  them is safe.
--
--  Table names below are already snake_case, matching Room's @Entity(tableName).
-- ============================================================================


-- ---------------------------------------------------------------------------
-- 0. Helper functions that depend on nothing
--    (functions that read a table live in section 1b, after those tables exist)
-- ---------------------------------------------------------------------------

-- True when the caller is a direct SQL session (the dashboard SQL editor, the
-- service role, a migration) rather than an API request. PostgREST always
-- connects as one of the two API roles below, so this reliably distinguishes
-- "trusted operator at a keyboard" from "someone holding the publishable key".
-- Deliberately SECURITY INVOKER (the default) so current_user is the caller.
create or replace function public.is_trusted_sql_context()
returns boolean
language sql
stable
as $$
    select current_user not in ('anon', 'authenticated')
$$;


-- Stamps serverUpdatedAt on every UPDATE. The sync engine pulls rows where
-- serverUpdatedAt > last successful pull, so this is what makes incremental
-- sync possible.
create or replace function public.touch_server_updated_at()
returns trigger
language plpgsql
as $$
begin
    new."serverUpdatedAt" = now();
    return new;
end;
$$;





-- ---------------------------------------------------------------------------
-- 1. Tables
--    23 tables, one per Room entity.
-- ---------------------------------------------------------------------------

-- users ---------------------------------------------------------------
create table if not exists public.users (
    id                  text primary key,
    "authUserId"        uuid unique references auth.users (id) on delete set null,
    name                text,
    email               text,
    phone               text,
    role                text,     -- CONSUMER, HOST, ADMIN
    "organizationName"  text,
    "verificationStatus" text,    -- UNVERIFIED, PENDING, VERIFIED
    "isBlocked"         boolean not null default false,
    "serverUpdatedAt"   timestamptz not null default now()
);

-- vehicles ------------------------------------------------------------
create table if not exists public.vehicles (
    id                              text primary key,
    "userId"                        text,
    make                            text,
    model                           text,
    variant                         text,
    "purchaseYear"                  integer,
    "batteryCapacityKwh"            double precision,
    "connectorType"                 text,
    "maxAcKw"                       double precision,
    "maxDcKw"                       double precision,
    "typicalEfficiencyKmPerKwh"     double precision,
    "batteryHealthPercent"          double precision,
    "efficiencyCorrectionPercent"   double precision,
    "minArrivalSocPercent"          integer,
    "maxChargeSocPercent"           integer,
    "licensePlate"                  text,
    "serverUpdatedAt"               timestamptz not null default now()
);

-- charger_stations ----------------------------------------------------
create table if not exists public.charger_stations (
    id                  text primary key,
    "hostId"            text,
    "hostName"          text,
    name                text,
    address             text,
    latitude            double precision,
    longitude           double precision,
    "locationType"      text,
    "operatingHours"    text,
    "pricePerKwh"       double precision,
    "serviceFee"        double precision,
    "gatewayFee"        double precision,
    "verificationLevel" text,     -- LEVEL_1_VERIFIED_MANUAL, LEVEL_2_SIMULATED_SMART_METER, LEVEL_3_NO_VERIFIED_METER
    "isVerified"        boolean not null default false,
    "isPaused"          boolean not null default false,
    "approvalStatus"    text,     -- PENDING, APPROVED, REJECTED
    "reviewNote"        text,
    "connectorTypes"    text,     -- comma separated, e.g. 'TYPE_2_AC,CCS_2_DC'
    "maxPowerKw"        double precision,
    "isDcCapable"       boolean not null default false,
    "portCount"         integer,
    "accessRules"       text,
    rating              double precision,
    "reliabilityScore"  double precision,
    "incidentCount"     integer,
    "lastUpdatedMs"     bigint,
    "serverUpdatedAt"   timestamptz not null default now()
);

-- charger_ports -------------------------------------------------------
create table if not exists public.charger_ports (
    id              text primary key,
    "stationId"     text,
    "portNumber"    text,
    "connectorType" text,
    "powerKw"       double precision,
    "isDc"          boolean not null default false,
    status          text,     -- AVAILABLE, OCCUPIED, MAINTENANCE, RESERVED
    "serverUpdatedAt" timestamptz not null default now()
);

-- availability_schedules ----------------------------------------------
create table if not exists public.availability_schedules (
    id              text primary key,
    "stationId"     text,
    "portId"        text,
    "dayOfWeek"     integer,   -- Calendar.MONDAY..SUNDAY (1..7)
    "openMinutes"   integer,   -- minutes from local midnight
    "closeMinutes"  integer,
    "isBlocked"     boolean not null default false,
    "blockReason"   text,
    "updatedAtMs"   bigint,
    "serverUpdatedAt" timestamptz not null default now()
);

-- reservations --------------------------------------------------------
create table if not exists public.reservations (
    id                text primary key,
    "consumerId"      text,
    "stationId"       text,
    "stationName"     text,
    "portId"          text,
    "portLabel"       text,
    "bookingCode"     text,
    "startTimeMs"     bigint,
    "endTimeMs"       bigint,
    status            text,   -- RESERVED, CHECKED_IN, CHARGING, COMPLETED, CANCELLED, EXPIRED, DISPUTED, REFUNDED
    "pricePerKwh"     double precision,
    "serviceFee"      double precision,
    "gatewayFee"      double precision,
    "cancellationFee" double precision,
    "isPaid"          boolean not null default false,
    "paymentId"       text,
    "checkedInAtMs"   bigint,
    "cancelledAtMs"   bigint,
    "createdAtMs"     bigint,
    "serverUpdatedAt" timestamptz not null default now()
);

-- charging_sessions ---------------------------------------------------
create table if not exists public.charging_sessions (
    id                    text primary key,
    "reservationId"       text,
    "consumerId"          text,
    "hostId"              text,
    "stationId"           text,
    "stationName"         text,
    "portId"              text,
    "startTimeMs"         bigint,
    "endTimeMs"           bigint,
    "startingMeterKwh"    double precision,
    "endingMeterKwh"      double precision,
    "verifiedKwhConsumed" double precision,
    "energyCharge"        double precision,
    "serviceFee"          double precision,
    "gatewayFee"          double precision,
    "totalPaid"           double precision,
    status                text,
    "meterReadingId"      text,
    "invoiceId"           text,
    "isMeterSimulated"    boolean not null default false,
    "verificationLevel"   text,
    "serverUpdatedAt"     timestamptz not null default now()
);

-- meter_readings ------------------------------------------------------
create table if not exists public.meter_readings (
    id                  text primary key,
    "sessionId"         text,
    "stationId"         text,
    "startMeterKwh"     double precision,
    "endMeterKwh"       double precision,
    "photoEvidenceUri"  text,
    "verificationLevel" text,
    "timestampMs"       bigint,
    "isSimulated"       boolean not null default false,
    "serverUpdatedAt"   timestamptz not null default now()
);

-- invoices ------------------------------------------------------------
create table if not exists public.invoices (
    id              text primary key,
    "sessionId"     text,
    "consumerId"    text,
    "hostId"        text,
    "stationName"   text,
    "verifiedKwh"   double precision,
    "energyCharge"  double precision,
    "serviceFee"    double precision,
    "gatewayFee"    double precision,
    adjustment      double precision,
    "adjustmentNote" text,
    "finalTotal"    double precision,
    "paymentId"     text,
    status          text,   -- ISSUED, PARTIALLY_REFUNDED, REFUNDED, PAID
    "issuedAtMs"    bigint,
    "serverUpdatedAt" timestamptz not null default now()
);

-- payments ------------------------------------------------------------
create table if not exists public.payments (
    id                    text primary key,
    "reservationId"       text,
    "sessionId"           text,
    "consumerId"          text,
    "hostId"              text,
    "idempotencyKey"      text unique,
    gateway               text,
    "gatewayReference"    text,
    "authorizedAmount"    double precision,
    "capturedAmount"      double precision,
    "refundedAmount"      double precision,
    "lateCancellationFee" double precision,
    status                text,  -- AUTHORIZED, CAPTURED, FAILED, REFUNDED, RELEASED
    "failureReason"       text,
    "createdAtMs"         bigint,
    "updatedAtMs"         bigint,
    "serverUpdatedAt"     timestamptz not null default now()
);

-- route_plans ---------------------------------------------------------
create table if not exists public.route_plans (
    id                  text primary key,
    "consumerId"        text,
    "vehicleId"         text,
    origin              text,
    destination         text,
    "originLat"         double precision,
    "originLng"         double precision,
    "destinationLat"    double precision,
    "destinationLng"    double precision,
    "corridorKm"        double precision,
    preference          text,  -- FASTEST, CHEAPEST, RELIABLE, SHORTEST_DETOUR
    "totalDistanceKm"   double precision,
    "driveMinutes"      integer,
    "detourMinutes"     integer,
    "chargingMinutes"   integer,
    "totalJourneyMinutes" integer,
    "plannedStartSoc"   integer,
    "arrivalSocTarget"  integer,
    "isSocAuthoritative" boolean not null default false,
    assumptions         text,
    confidence          text,  -- HIGH, MEDIUM, LOW
    status              text,  -- DRAFT, ACTIVE, COMPLETED, CANCELLED
    "createdAtMs"       bigint,
    "updatedAtMs"       bigint,
    "serverUpdatedAt"   timestamptz not null default now()
);

-- route_stops ---------------------------------------------------------
create table if not exists public.route_stops (
    id                    text primary key,
    "planId"              text,
    "sequence"            integer,
    "stationId"           text,
    "stationName"         text,
    "isBackup"            boolean not null default false,
    "isReachable"         boolean not null default false,
    "distanceAlongRouteKm" double precision,
    "detourKm"            double precision,
    "detourMinutes"       integer,
    "arrivalSoc"          integer,
    "departureSoc"        integer,
    "energyAddedKwh"      double precision,
    "chargingMinutes"     integer,
    "estimatedCost"       double precision,
    "recommendationReason" text,
    "reservationId"       text,
    "serverUpdatedAt"     timestamptz not null default now()
);

-- trip_estimates ------------------------------------------------------
create table if not exists public.trip_estimates (
    id                    text primary key,
    "planId"              text,
    "vehicleId"           text,
    "segmentDistanceKm"   double precision,
    "estimatedDrainKwh"   double precision,
    "actualDrainKwh"      double precision,
    "estimatedArrivalSoc" integer,
    "actualArrivalSoc"    integer,
    confidence            text,
    notes                 text,
    "createdAtMs"         bigint,
    "serverUpdatedAt"     timestamptz not null default now()
);

-- battery_updates -----------------------------------------------------
create table if not exists public.battery_updates (
    id                    text primary key,
    "vehicleId"           text,
    "tripPlanId"          text,
    "estimatedSoc"        integer,
    "actualDashboardSoc"  integer,
    latitude              double precision,
    longitude             double precision,
    "timestampMs"         bigint,
    "serverUpdatedAt"     timestamptz not null default now()
);

-- communication_threads -----------------------------------------------
create table if not exists public.communication_threads (
    id                    text primary key,
    "reservationId"       text,
    "consumerId"          text,
    "consumerName"        text,
    "hostId"              text,
    "hostName"            text,
    "maskedConsumerNumber" text,
    "maskedHostNumber"    text,
    "isReported"          boolean not null default false,
    "isBlocked"           boolean not null default false,
    "isChatEnabled"       boolean not null default true,
    "createdAtMs"         bigint,
    "expiresAtMs"         bigint,
    "serverUpdatedAt"     timestamptz not null default now()
);

-- chat_messages -------------------------------------------------------
create table if not exists public.chat_messages (
    id              text primary key,
    "threadId"      text,
    "reservationId" text,
    "senderId"      text,
    "receiverId"    text,
    "messageText"   text,
    "createdAtMs"   bigint,
    "isRead"        boolean not null default false,
    "isReported"    boolean not null default false,
    "expiresAtMs"   bigint,
    "serverUpdatedAt" timestamptz not null default now()
);

-- safety_events -------------------------------------------------------
create table if not exists public.safety_events (
    id                            text primary key,
    "consumerId"                  text,
    "stationId"                   text,
    "emergencyType"               text,
    "isLiveLocationSharingActive" boolean not null default false,
    "trustedContactPhone"         text,
    "expiresAtMs"                 bigint,
    "createdAtMs"                 bigint,
    "serverUpdatedAt"             timestamptz not null default now()
);

-- trusted_contacts ----------------------------------------------------
create table if not exists public.trusted_contacts (
    id            text primary key,
    "userId"      text,
    name          text,
    phone         text,
    "serverUpdatedAt" timestamptz not null default now()
);

-- reports -------------------------------------------------------------
create table if not exists public.reports (
    id                text primary key,
    "reporterId"      text,
    "reporterName"    text,
    "targetStationId" text,
    "targetHostId"    text,
    "sessionId"       text,
    "reportType"      text,
    description       text,
    status            text,   -- OPEN, REVIEWED, RESOLVED
    "createdAtMs"     bigint,
    "serverUpdatedAt" timestamptz not null default now()
);

-- disputes ------------------------------------------------------------
create table if not exists public.disputes (
    id                  text primary key,
    "sessionId"         text,
    "consumerId"        text,
    "consumerName"      text,
    "hostId"            text,
    "stationId"         text,
    "stationName"       text,
    reason              text,
    "consumerNotes"     text,
    "hostNotes"         text,
    evidence            text,
    status              text,
    "enforcementAction" text,   -- NONE, WARNING, FINE, SUSPENSION, BAN
    "refundAmount"      double precision,
    "resolutionNote"    text,
    "isPayoutFrozen"    boolean not null default false,
    "createdAtMs"       bigint,
    "resolvedAtMs"      bigint,
    "serverUpdatedAt"   timestamptz not null default now()
);

-- host_enforcements ---------------------------------------------------
create table if not exists public.host_enforcements (
    id              text primary key,
    "hostId"        text,
    "stationId"     text,
    "actionType"    text,   -- WARNING, FINE, SUSPENSION, BAN
    reason          text,
    "fineAmount"    double precision,
    "createdAtMs"   bigint,
    "serverUpdatedAt" timestamptz not null default now()
);

-- notifications -------------------------------------------------------
create table if not exists public.notifications (
    id            text primary key,
    "userId"      text,
    title         text,
    message       text,
    "isRead"      boolean not null default false,
    "createdAtMs" bigint,
    "serverUpdatedAt" timestamptz not null default now()
);

-- android_auto_sessions -----------------------------------------------
create table if not exists public.android_auto_sessions (
    id                    text primary key,
    "userId"              text,
    "vehicleId"           text,
    "planId"              text,
    "lastTemplateShown"   text,
    "lastSocUpdatePercent" integer,
    "connectedAtMs"       bigint,
    "lastSocUpdateAtMs"   bigint,
    "disconnectedAtMs"    bigint,
    "serverUpdatedAt"     timestamptz not null default now()
);


-- ---------------------------------------------------------------------------
-- 1b. Identity: the caller behind the JWT, and one profile per Auth account
--
--     Everything here reads public.users, so it is created AFTER the tables
--     above. The order is not cosmetic: PostgreSQL parses and analyses the body
--     of a LANGUAGE SQL function when it is created, so a function that selects
--     from a table which does not exist yet fails the whole script with
--     "relation public.users does not exist". Keeping these below section 1 is
--     what makes this file runnable from an empty database in one pass.
-- ---------------------------------------------------------------------------

-- The application user id (e.g. 'user_student_1') behind the current JWT.
-- SECURITY DEFINER so it can read public.users while RLS is active, which
-- avoids infinite policy recursion. search_path is pinned for safety.
create or replace function public.current_app_user_id()
returns text
language sql
stable
security definer
set search_path = public
as $$
    select u.id
    from public.users u
    where u."authUserId" = auth.uid()
    limit 1
$$;

create or replace function public.current_app_user_role()
returns text
language sql
stable
security definer
set search_path = public
as $$
    select u.role
    from public.users u
    where u."authUserId" = auth.uid()
    limit 1
$$;

create or replace function public.is_platform_admin()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select coalesce(
        (select u.role = 'ADMIN'
         from public.users u
         where u."authUserId" = auth.uid()
         limit 1),
        false)
$$;


-- Blocks a signed-in user from promoting themselves to ADMIN or from clearing
-- their own block flag. Without this, a user could PATCH their own users row
-- and take over the platform — the users UPDATE policy alone cannot express
-- "but you may not change this column".
--
-- SECURITY INVOKER on purpose: is_trusted_sql_context() inspects current_user,
-- and inside a SECURITY DEFINER function current_user becomes the function
-- owner, which would make every request look trusted and defeat the check.
create or replace function public.prevent_privilege_escalation()
returns trigger
language plpgsql
set search_path = public
as $$
begin
    if public.is_trusted_sql_context() or public.is_platform_admin() then
        return new;
    end if;
    if new.role is distinct from old.role then
        raise exception 'Changing the role of a user is an administrator action.';
    end if;
    if new."isBlocked" is distinct from old."isBlocked" then
        raise exception 'Changing the blocked state of a user is an administrator action.';
    end if;
    if new."verificationStatus" is distinct from old."verificationStatus" then
        raise exception 'Changing a verification status is an administrator action.';
    end if;
    return new;
end;
$$;


-- ---------------------------------------------------------------------------
-- 1c. One profile per Auth account, created together with the account
--
--  The app identifies the caller with current_app_user_id(), which resolves
--  users."authUserId" against auth.uid(). An Auth account with no public.users
--  row therefore resolves to NULL, and NULL matches no policy on any table: the
--  signed-in user sees an empty app and every single write is refused. That is
--  the failure this section removes.
--
--  Relying on the app to insert the row right after sign-in is not enough. A
--  project with "Confirm email" enabled returns no session from sign-up until the
--  address is confirmed, and a single rejected request left the account anonymous
--  forever, because nothing retried it. Making the profile a database guarantee
--  means an Auth account is visible to every policy from the moment it exists.
-- ---------------------------------------------------------------------------

-- Links, or creates, the application profile behind one Auth account.
-- SECURITY DEFINER so it can touch public.users and so the guard triggers treat it
-- as the trusted table owner rather than as an API caller.
create or replace function public.ensure_app_user(
    p_auth_user_id uuid,
    p_email        text,
    p_metadata     jsonb
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    v_name text;
begin
    -- 1. An account that pilot_seed.sql described before it had an Auth account is
    --    linked in place. This is checked first so a seeded row keeps its role and
    --    its demo history instead of being duplicated.
    if p_email is not null then
        if (select count(*) from public.users u
             where u."authUserId" is null
               and lower(u.email) = lower(p_email)) = 1 then
            update public.users u
               set "authUserId" = p_auth_user_id
             where u."authUserId" is null
               and lower(u.email) = lower(p_email);
            return;
        end if;
    end if;

    -- 2. Nothing seeded matches, so create the profile.
    --    The role is always CONSUMER: raw_user_meta_data is supplied by the client
    --    at sign-up, so trusting it would let anyone register as HOST or ADMIN.
    --    Promotion is an administrator action on public.users.
    v_name := nullif(btrim(coalesce(p_metadata ->> 'name', '')), '');
    if v_name is null then
        v_name := nullif(split_part(coalesce(p_email, ''), '@', 1), '');
    end if;
    if v_name is null then
        v_name := 'Amperly user';
    end if;

    insert into public.users (id, "authUserId", name, email, phone, role,
                              "organizationName", "verificationStatus", "isBlocked")
    values (p_auth_user_id::text, p_auth_user_id, v_name, p_email, '', 'CONSUMER',
            '', 'VERIFIED', false)
    on conflict (id) do nothing;
end;
$$;

-- The trigger itself. Kept separate from the work above so the backfill at the end
-- of this file can run exactly the same logic for accounts that already exist.
create or replace function public.handle_new_auth_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    perform public.ensure_app_user(new.id, new.email, new.raw_user_meta_data);
    return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function public.handle_new_auth_user();

-- Neither function is API surface — the trigger calls one and the backfill calls
-- the other. PostgreSQL grants EXECUTE on a new function to PUBLIC by default,
-- which would let any signed-in caller mint a profile row for an arbitrary Auth
-- account. A trigger still fires after this revoke.
revoke all on function public.ensure_app_user(uuid, text, jsonb) from public;
revoke all on function public.handle_new_auth_user() from public;


-- ---------------------------------------------------------------------------
-- 2. Indexes
--    serverUpdatedAt on every table powers the incremental sync pull.
--    The rest cover the ownership lookups used by both the policies and the app.
-- ---------------------------------------------------------------------------

do $$
declare t text;
begin
    foreach t in array array[
        'users','vehicles','charger_stations','charger_ports','availability_schedules',
        'reservations','charging_sessions','meter_readings','invoices','payments',
        'route_plans','route_stops','trip_estimates','battery_updates',
        'communication_threads','chat_messages','safety_events','trusted_contacts',
        'reports','disputes','host_enforcements','notifications','android_auto_sessions'
    ] loop
        execute format('create index if not exists %I on public.%I ("serverUpdatedAt")',
                       'idx_' || t || '_server_updated_at', t);
    end loop;
end $$;

create index if not exists idx_users_auth_user_id       on public.users ("authUserId");
create index if not exists idx_vehicles_user            on public.vehicles ("userId");
create index if not exists idx_stations_host            on public.charger_stations ("hostId");
create index if not exists idx_stations_discovery       on public.charger_stations ("approvalStatus", "isPaused");
create index if not exists idx_ports_station            on public.charger_ports ("stationId");
create index if not exists idx_schedules_station        on public.availability_schedules ("stationId", "dayOfWeek");
create index if not exists idx_reservations_consumer    on public.reservations ("consumerId");
create index if not exists idx_reservations_station     on public.reservations ("stationId", "startTimeMs");
create index if not exists idx_reservations_port        on public.reservations ("portId", "startTimeMs");
create index if not exists idx_sessions_consumer        on public.charging_sessions ("consumerId");
create index if not exists idx_sessions_host            on public.charging_sessions ("hostId");
create index if not exists idx_meter_readings_session   on public.meter_readings ("sessionId");
create index if not exists idx_invoices_consumer        on public.invoices ("consumerId");
create index if not exists idx_invoices_host            on public.invoices ("hostId");
create index if not exists idx_payments_consumer        on public.payments ("consumerId");
create index if not exists idx_route_plans_consumer     on public.route_plans ("consumerId");
create index if not exists idx_route_stops_plan         on public.route_stops ("planId", "sequence");
create index if not exists idx_trip_estimates_plan      on public.trip_estimates ("planId");
create index if not exists idx_battery_updates_vehicle  on public.battery_updates ("vehicleId");
create index if not exists idx_threads_consumer         on public.communication_threads ("consumerId");
create index if not exists idx_threads_host             on public.communication_threads ("hostId");
create index if not exists idx_chat_messages_thread     on public.chat_messages ("threadId", "createdAtMs");
create index if not exists idx_safety_events_consumer   on public.safety_events ("consumerId");
create index if not exists idx_trusted_contacts_user    on public.trusted_contacts ("userId");
create index if not exists idx_reports_reporter         on public.reports ("reporterId");
create index if not exists idx_reports_station          on public.reports ("targetStationId");
create index if not exists idx_disputes_consumer        on public.disputes ("consumerId");
create index if not exists idx_disputes_host            on public.disputes ("hostId");
create index if not exists idx_enforcements_host        on public.host_enforcements ("hostId");
create index if not exists idx_notifications_user       on public.notifications ("userId");
create index if not exists idx_auto_sessions_user       on public.android_auto_sessions ("userId");


-- ---------------------------------------------------------------------------
-- 3. serverUpdatedAt triggers
-- ---------------------------------------------------------------------------

do $$
declare t text;
begin
    foreach t in array array[
        'users','vehicles','charger_stations','charger_ports','availability_schedules',
        'reservations','charging_sessions','meter_readings','invoices','payments',
        'route_plans','route_stops','trip_estimates','battery_updates',
        'communication_threads','chat_messages','safety_events','trusted_contacts',
        'reports','disputes','host_enforcements','notifications','android_auto_sessions'
    ] loop
        execute format('drop trigger if exists touch_server_updated_at on public.%I', t);
        execute format('create trigger touch_server_updated_at before update on public.%I '
                       'for each row execute function public.touch_server_updated_at()', t);
    end loop;
end $$;

drop trigger if exists prevent_privilege_escalation on public.users;
create trigger prevent_privilege_escalation
    before update on public.users
    for each row execute function public.prevent_privilege_escalation();


-- ---------------------------------------------------------------------------
-- 4. Row Level Security
--    RLS is enabled on EVERY table before any policy is written, so there is no
--    window in which a table is reachable without policies.
-- ---------------------------------------------------------------------------

do $$
declare t text;
begin
    foreach t in array array[
        'users','vehicles','charger_stations','charger_ports','availability_schedules',
        'reservations','charging_sessions','meter_readings','invoices','payments',
        'route_plans','route_stops','trip_estimates','battery_updates',
        'communication_threads','chat_messages','safety_events','trusted_contacts',
        'reports','disputes','host_enforcements','notifications','android_auto_sessions'
    ] loop
        execute format('alter table public.%I enable row level security', t);
    end loop;
end $$;


-- ---- users ---------------------------------------------------------------
drop policy if exists users_select_self_or_admin on public.users;
create policy users_select_self_or_admin on public.users
    for select to authenticated
    using (id = public.current_app_user_id() or public.is_platform_admin());

drop policy if exists users_update_self_or_admin on public.users;
create policy users_update_self_or_admin on public.users
    for update to authenticated
    using (id = public.current_app_user_id() or public.is_platform_admin())
    with check (id = public.current_app_user_id() or public.is_platform_admin());

-- Self-registration. A user may create their own row once they hold an auth
-- account, but only as a Consumer or Host: ADMIN is deliberately excluded, and
-- the prevent_privilege_escalation() trigger stops it being set afterwards.
drop policy if exists users_insert_self on public.users;
create policy users_insert_self on public.users
    for insert to authenticated
    with check ("authUserId" = auth.uid() and role in ('CONSUMER', 'HOST'));

drop policy if exists users_admin_insert on public.users;
create policy users_admin_insert on public.users
    for insert to authenticated
    with check (public.is_platform_admin());

drop policy if exists users_admin_delete on public.users;
create policy users_admin_delete on public.users
    for delete to authenticated
    using (public.is_platform_admin());


-- ---- vehicles ------------------------------------------------------------
drop policy if exists vehicles_select_own on public.vehicles;
create policy vehicles_select_own on public.vehicles
    for select to authenticated
    using ("userId" = public.current_app_user_id() or public.is_platform_admin());

drop policy if exists vehicles_write_own on public.vehicles;
create policy vehicles_write_own on public.vehicles
    for all to authenticated
    using ("userId" = public.current_app_user_id() or public.is_platform_admin())
    with check ("userId" = public.current_app_user_id() or public.is_platform_admin());


-- ---- charger_stations ----------------------------------------------------
-- Discovery is intentionally readable by anon: a charger listing is public
-- information, like a map pin. Only approved, unpaused stations are exposed.
drop policy if exists stations_read_public on public.charger_stations;
create policy stations_read_public on public.charger_stations
    for select to anon, authenticated
    using ("approvalStatus" = 'APPROVED' and "isPaused" = false);

drop policy if exists stations_read_own_or_admin on public.charger_stations;
create policy stations_read_own_or_admin on public.charger_stations
    for select to authenticated
    using ("hostId" = public.current_app_user_id() or public.is_platform_admin());

drop policy if exists stations_insert_host on public.charger_stations;
create policy stations_insert_host on public.charger_stations
    for insert to authenticated
    with check ("hostId" = public.current_app_user_id() or public.is_platform_admin());

drop policy if exists stations_update_own_or_admin on public.charger_stations;
create policy stations_update_own_or_admin on public.charger_stations
    for update to authenticated
    using ("hostId" = public.current_app_user_id() or public.is_platform_admin())
    with check ("hostId" = public.current_app_user_id() or public.is_platform_admin());

drop policy if exists stations_delete_own_or_admin on public.charger_stations;
create policy stations_delete_own_or_admin on public.charger_stations
    for delete to authenticated
    using ("hostId" = public.current_app_user_id() or public.is_platform_admin());


-- ---- charger_ports -------------------------------------------------------
drop policy if exists ports_read_public on public.charger_ports;
create policy ports_read_public on public.charger_ports
    for select to anon, authenticated
    using (exists (select 1 from public.charger_stations s
                   where s.id = "stationId"
                     and s."approvalStatus" = 'APPROVED'
                     and s."isPaused" = false));

drop policy if exists ports_write_host_or_admin on public.charger_ports;
create policy ports_write_host_or_admin on public.charger_ports
    for all to authenticated
    using (exists (select 1 from public.charger_stations s
                   where s.id = "stationId" and s."hostId" = public.current_app_user_id())
           or public.is_platform_admin())
    with check (exists (select 1 from public.charger_stations s
                        where s.id = "stationId" and s."hostId" = public.current_app_user_id())
                or public.is_platform_admin());


-- ---- availability_schedules ---------------------------------------------
drop policy if exists schedules_read_public on public.availability_schedules;
create policy schedules_read_public on public.availability_schedules
    for select to anon, authenticated
    using (exists (select 1 from public.charger_stations s
                   where s.id = "stationId"
                     and s."approvalStatus" = 'APPROVED'
                     and s."isPaused" = false));

drop policy if exists schedules_write_host_or_admin on public.availability_schedules;
create policy schedules_write_host_or_admin on public.availability_schedules
    for all to authenticated
    using (exists (select 1 from public.charger_stations s
                   where s.id = "stationId" and s."hostId" = public.current_app_user_id())
           or public.is_platform_admin())
    with check (exists (select 1 from public.charger_stations s
                        where s.id = "stationId" and s."hostId" = public.current_app_user_id())
                or public.is_platform_admin());


-- ---- reservations --------------------------------------------------------
drop policy if exists reservations_read_parties on public.reservations;
create policy reservations_read_parties on public.reservations
    for select to authenticated
    using ("consumerId" = public.current_app_user_id()
           or exists (select 1 from public.charger_stations s
                      where s.id = "stationId" and s."hostId" = public.current_app_user_id())
           or public.is_platform_admin());

drop policy if exists reservations_insert_consumer on public.reservations;
create policy reservations_insert_consumer on public.reservations
    for insert to authenticated
    with check ("consumerId" = public.current_app_user_id() or public.is_platform_admin());

-- Both the consumer and the station host legitimately move a reservation's
-- status (check-in by the host, cancellation by the consumer).
drop policy if exists reservations_update_parties on public.reservations;
create policy reservations_update_parties on public.reservations
    for update to authenticated
    using ("consumerId" = public.current_app_user_id()
           or exists (select 1 from public.charger_stations s
                      where s.id = "stationId" and s."hostId" = public.current_app_user_id())
           or public.is_platform_admin())
    with check ("consumerId" = public.current_app_user_id()
           or exists (select 1 from public.charger_stations s
                      where s.id = "stationId" and s."hostId" = public.current_app_user_id())
           or public.is_platform_admin());

drop policy if exists reservations_admin_delete on public.reservations;
create policy reservations_admin_delete on public.reservations
    for delete to authenticated
    using (public.is_platform_admin());


-- ---- charging_sessions ---------------------------------------------------
drop policy if exists sessions_read_parties on public.charging_sessions;
create policy sessions_read_parties on public.charging_sessions
    for select to authenticated
    using ("consumerId" = public.current_app_user_id()
           or "hostId" = public.current_app_user_id()
           or public.is_platform_admin());

-- Either party can create/advance a session: the host checks the car in, the
-- consumer completes it.
drop policy if exists sessions_write_parties on public.charging_sessions;
create policy sessions_write_parties on public.charging_sessions
    for all to authenticated
    using ("consumerId" = public.current_app_user_id()
           or "hostId" = public.current_app_user_id()
           or public.is_platform_admin())
    with check ("consumerId" = public.current_app_user_id()
           or "hostId" = public.current_app_user_id()
           or public.is_platform_admin());


-- ---- meter_readings ------------------------------------------------------
drop policy if exists meter_readings_read_parties on public.meter_readings;
create policy meter_readings_read_parties on public.meter_readings
    for select to authenticated
    using (exists (select 1 from public.charging_sessions cs
                   where cs.id = "sessionId"
                     and (cs."consumerId" = public.current_app_user_id()
                          or cs."hostId" = public.current_app_user_id()))
           or public.is_platform_admin());

-- Only the host (or an admin) records meter evidence; a consumer must never be
-- able to write the reading their own bill is calculated from.
drop policy if exists meter_readings_write_host on public.meter_readings;
create policy meter_readings_write_host on public.meter_readings
    for all to authenticated
    using (exists (select 1 from public.charger_stations s
                   where s.id = "stationId" and s."hostId" = public.current_app_user_id())
           or public.is_platform_admin())
    with check (exists (select 1 from public.charger_stations s
                        where s.id = "stationId" and s."hostId" = public.current_app_user_id())
                or public.is_platform_admin());


-- ---- invoices ------------------------------------------------------------
drop policy if exists invoices_read_parties on public.invoices;
create policy invoices_read_parties on public.invoices
    for select to authenticated
    using ("consumerId" = public.current_app_user_id()
           or "hostId" = public.current_app_user_id()
           or public.is_platform_admin());

-- Invoices and refunds are issued by the platform, not by the parties.
drop policy if exists invoices_write_admin on public.invoices;
create policy invoices_write_admin on public.invoices
    for all to authenticated
    using (public.is_platform_admin())
    with check (public.is_platform_admin());


-- ---- payments ------------------------------------------------------------
drop policy if exists payments_read_parties on public.payments;
create policy payments_read_parties on public.payments
    for select to authenticated
    using ("consumerId" = public.current_app_user_id()
           or "hostId" = public.current_app_user_id()
           or public.is_platform_admin());

drop policy if exists payments_insert_consumer on public.payments;
create policy payments_insert_consumer on public.payments
    for insert to authenticated
    with check ("consumerId" = public.current_app_user_id() or public.is_platform_admin());

-- A consumer may only authorise/release their own payment. Capture, refund and
-- settlement amounts are administrator actions.
drop policy if exists payments_update_consumer on public.payments;
create policy payments_update_consumer on public.payments
    for update to authenticated
    using ("consumerId" = public.current_app_user_id() or public.is_platform_admin())
    with check ("consumerId" = public.current_app_user_id() or public.is_platform_admin());


-- ---- route_plans ---------------------------------------------------------
drop policy if exists route_plans_own on public.route_plans;
create policy route_plans_own on public.route_plans
    for all to authenticated
    using ("consumerId" = public.current_app_user_id() or public.is_platform_admin())
    with check ("consumerId" = public.current_app_user_id() or public.is_platform_admin());


-- ---- route_stops ---------------------------------------------------------
drop policy if exists route_stops_own on public.route_stops;
create policy route_stops_own on public.route_stops
    for all to authenticated
    using (exists (select 1 from public.route_plans p
                   where p.id = "planId" and p."consumerId" = public.current_app_user_id())
           or public.is_platform_admin())
    with check (exists (select 1 from public.route_plans p
                        where p.id = "planId" and p."consumerId" = public.current_app_user_id())
                or public.is_platform_admin());


-- ---- trip_estimates ------------------------------------------------------
drop policy if exists trip_estimates_own on public.trip_estimates;
create policy trip_estimates_own on public.trip_estimates
    for all to authenticated
    using (exists (select 1 from public.route_plans p
                   where p.id = "planId" and p."consumerId" = public.current_app_user_id())
           or public.is_platform_admin())
    with check (exists (select 1 from public.route_plans p
                        where p.id = "planId" and p."consumerId" = public.current_app_user_id())
                or public.is_platform_admin());


-- ---- battery_updates -----------------------------------------------------
drop policy if exists battery_updates_own on public.battery_updates;
create policy battery_updates_own on public.battery_updates
    for all to authenticated
    using (exists (select 1 from public.vehicles v
                   where v.id = "vehicleId" and v."userId" = public.current_app_user_id())
           or public.is_platform_admin())
    with check (exists (select 1 from public.vehicles v
                        where v.id = "vehicleId" and v."userId" = public.current_app_user_id())
                or public.is_platform_admin());


-- ---- communication_threads ----------------------------------------------
-- Reservation-scoped: only the two participants.
drop policy if exists threads_read_participants on public.communication_threads;
create policy threads_read_participants on public.communication_threads
    for select to authenticated
    using ("consumerId" = public.current_app_user_id()
           or "hostId" = public.current_app_user_id()
           or public.is_platform_admin());

drop policy if exists threads_write_participants on public.communication_threads;
create policy threads_write_participants on public.communication_threads
    for all to authenticated
    using ("consumerId" = public.current_app_user_id()
           or "hostId" = public.current_app_user_id()
           or public.is_platform_admin())
    with check ("consumerId" = public.current_app_user_id()
           or "hostId" = public.current_app_user_id()
           or public.is_platform_admin());


-- ---- chat_messages -------------------------------------------------------
drop policy if exists chat_read_participants on public.chat_messages;
create policy chat_read_participants on public.chat_messages
    for select to authenticated
    using (exists (select 1 from public.communication_threads t
                   where t.id = "threadId"
                     and (t."consumerId" = public.current_app_user_id()
                          or t."hostId" = public.current_app_user_id()))
           or public.is_platform_admin());

drop policy if exists chat_write_participants on public.chat_messages;
create policy chat_write_participants on public.chat_messages
    for insert to authenticated
    with check (("senderId" = public.current_app_user_id()
                 and exists (select 1 from public.communication_threads t
                             where t.id = "threadId"
                               and (t."consumerId" = public.current_app_user_id()
                                    or t."hostId" = public.current_app_user_id())))
           or public.is_platform_admin());


-- ---- safety_events -------------------------------------------------------
drop policy if exists safety_events_own on public.safety_events;
create policy safety_events_own on public.safety_events
    for all to authenticated
    using ("consumerId" = public.current_app_user_id() or public.is_platform_admin())
    with check ("consumerId" = public.current_app_user_id() or public.is_platform_admin());


-- ---- trusted_contacts ----------------------------------------------------
drop policy if exists trusted_contacts_own on public.trusted_contacts;
create policy trusted_contacts_own on public.trusted_contacts
    for all to authenticated
    using ("userId" = public.current_app_user_id() or public.is_platform_admin())
    with check ("userId" = public.current_app_user_id() or public.is_platform_admin());


-- ---- reports -------------------------------------------------------------
drop policy if exists reports_read_reporter_or_admin on public.reports;
create policy reports_read_reporter_or_admin on public.reports
    for select to authenticated
    using ("reporterId" = public.current_app_user_id() or public.is_platform_admin());

-- Anyone may file a report; only an administrator may resolve one.
drop policy if exists reports_insert_self on public.reports;
create policy reports_insert_self on public.reports
    for insert to authenticated
    with check ("reporterId" = public.current_app_user_id() or public.is_platform_admin());

drop policy if exists reports_update_admin on public.reports;
create policy reports_update_admin on public.reports
    for update to authenticated
    using (public.is_platform_admin())
    with check (public.is_platform_admin());


-- ---- disputes ------------------------------------------------------------
drop policy if exists disputes_read_parties on public.disputes;
create policy disputes_read_parties on public.disputes
    for select to authenticated
    using ("consumerId" = public.current_app_user_id()
           or "hostId" = public.current_app_user_id()
           or public.is_platform_admin());

drop policy if exists disputes_insert_consumer on public.disputes;
create policy disputes_insert_consumer on public.disputes
    for insert to authenticated
    with check ("consumerId" = public.current_app_user_id() or public.is_platform_admin());

-- The host may add their response; resolution, refund and enforcement are
-- administrator actions enforced by the app.
drop policy if exists disputes_update_parties on public.disputes;
create policy disputes_update_parties on public.disputes
    for update to authenticated
    using ("consumerId" = public.current_app_user_id()
           or "hostId" = public.current_app_user_id()
           or public.is_platform_admin())
    with check ("consumerId" = public.current_app_user_id()
           or "hostId" = public.current_app_user_id()
           or public.is_platform_admin());


-- ---- host_enforcements ---------------------------------------------------
drop policy if exists enforcements_read_host_or_admin on public.host_enforcements;
create policy enforcements_read_host_or_admin on public.host_enforcements
    for select to authenticated
    using ("hostId" = public.current_app_user_id() or public.is_platform_admin());

drop policy if exists enforcements_write_admin on public.host_enforcements;
create policy enforcements_write_admin on public.host_enforcements
    for all to authenticated
    using (public.is_platform_admin())
    with check (public.is_platform_admin());


-- ---- notifications -------------------------------------------------------
drop policy if exists notifications_own_or_admin on public.notifications;
create policy notifications_own_or_admin on public.notifications
    for all to authenticated
    using ("userId" = public.current_app_user_id() or public.is_platform_admin())
    with check ("userId" = public.current_app_user_id() or public.is_platform_admin());


-- ---- android_auto_sessions ----------------------------------------------
drop policy if exists auto_sessions_own on public.android_auto_sessions;
create policy auto_sessions_own on public.android_auto_sessions
    for all to authenticated
    using ("userId" = public.current_app_user_id() or public.is_platform_admin())
    with check ("userId" = public.current_app_user_id() or public.is_platform_admin());


-- ---------------------------------------------------------------------------
-- 5. Settlement: the money transitions are performed by the server
--
--    `payments` and `invoices` deliberately have no client write policy, and the
--    billing columns of `charging_sessions` are pinned by the trigger below. So
--    these functions are the only route by which money can be written at all —
--    without them the app's authorization, capture, refund, cancellation and
--    enforcement calls have nothing to reach, and those rows never exist.
--
--    Each one re-checks who is calling before it writes, and each is SECURITY
--    DEFINER: inside it current_user is the table owner, which is what makes
--    is_trusted_sql_context() true and lets the guard triggers stand aside.
--
--    The app computes the same figures locally so the UI works offline, but the
--    server's numbers are the ones that count and replace the local ones on the
--    next pull. The argument names match what
--    AmperlyRepository passes, because PostgREST resolves a function by them.
-- ---------------------------------------------------------------------------

-- Rupees to two decimals. round(x, 2) has no double precision overload, so the
-- cast is required. Mirrors BillingEngine.round2().
create or replace function public.round2(p_value double precision)
returns double precision
language sql
immutable
as $$ select round(coalesce(p_value, 0)::numeric, 2)::double precision $$;


-- Creates the authorization — a hold, not a charge. Idempotent on the key the
-- app generated, so a retried request cannot put two holds on one booking.
create or replace function public.authorize_payment(
    p_id               text,
    p_reservation_id   text,
    p_station_id       text,
    p_amount           double precision,
    p_idempotency_key  text
)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
    v_consumer text;
    v_host     text;
    v_existing text;
    v_now      bigint;
begin
    select r."consumerId" into v_consumer
      from public.reservations r
     where r.id = p_reservation_id;

    if v_consumer is null then
        raise exception 'No reservation % to authorize a payment for.', p_reservation_id;
    end if;

    if not (v_consumer = public.current_app_user_id() or public.is_platform_admin()) then
        raise exception 'Only the consumer who made the booking may authorize its payment.';
    end if;

    select s."hostId" into v_host
      from public.charger_stations s
     where s.id = p_station_id;

    select p.id into v_existing
      from public.payments p
     where p."idempotencyKey" = p_idempotency_key;

    if v_existing is not null then
        return v_existing;
    end if;

    v_now := (extract(epoch from now()) * 1000)::bigint;

    insert into public.payments (id, "reservationId", "sessionId", "consumerId", "hostId",
        "idempotencyKey", gateway, "gatewayReference", "authorizedAmount", "capturedAmount",
        "refundedAmount", "lateCancellationFee", status, "failureReason",
        "createdAtMs", "updatedAtMs")
    values (p_id, p_reservation_id, null, v_consumer, v_host,
        p_idempotency_key, 'SANDBOX', 'auth_' || p_id, public.round2(p_amount), 0.0,
        0.0, 0.0, 'AUTHORIZED', null, v_now, v_now)
    on conflict (id) do update
        set "authorizedAmount" = excluded."authorizedAmount",
            "updatedAtMs"      = excluded."updatedAtMs";

    return p_id;
end;
$$;


-- Completes a session: recomputes the bill from the meter readings and the
-- station's tariff, then writes the session, the reading, the receipt and the
-- capture in one transaction, closes the reservation and frees the port.
create or replace function public.finalize_charging_session(
    p_session_id        text,
    p_reservation_id    text,
    p_start_meter_kwh   double precision,
    p_end_meter_kwh     double precision,
    p_is_simulated      boolean,
    p_meter_reading_id  text,
    p_invoice_id        text
)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
    v_consumer     text;
    v_host         text;
    v_station      text;
    v_station_name text;
    v_port         text;
    v_level        text;
    v_tariff       double precision;
    v_service      double precision;
    v_gateway      double precision;
    v_start        double precision;
    v_end          double precision;
    v_kwh          double precision;
    v_energy       double precision;
    v_total        double precision;
    v_payment      text;
    v_now          bigint;
    v_evidence_start double precision;
    v_evidence_end   double precision;
begin
    select r."consumerId", r."stationId", r."stationName", r."portId"
      into v_consumer, v_station, v_station_name, v_port
      from public.reservations r
     where r.id = p_reservation_id;

    if v_consumer is null then
        raise exception 'No reservation % to finalize.', p_reservation_id;
    end if;

    -- The defaults are AppConstants.DEFAULT_TARIFF_INR_PER_KWH, DEFAULT_SERVICE_FEE_INR
    -- and DEFAULT_GATEWAY_FEE_INR, so a row with a missing tariff settles for the same
    -- figure the app showed the consumer rather than a different one.
    select s."hostId", s."verificationLevel", coalesce(s."pricePerKwh", 18.0),
           coalesce(s."serviceFee", 5.0), coalesce(s."gatewayFee", 4.0)
      into v_host, v_level, v_tariff, v_service, v_gateway
      from public.charger_stations s
     where s.id = v_station;

    if not (v_consumer = public.current_app_user_id()
            or v_host = public.current_app_user_id()
            or public.is_platform_admin()) then
        raise exception 'Only the consumer or the station host may complete this session.';
    end if;

    -- Meter-first billing: a station with no verified meter cannot take an
    -- energy charge, so it cannot be settled through this function at all.
    if v_level is null or v_level not in ('LEVEL_1', 'LEVEL_2') then
        raise exception 'Station % has no verified meter, so it cannot be billed.', v_station;
    end if;

    if p_start_meter_kwh is null or p_end_meter_kwh is null then
        raise exception 'Both meter readings are required to bill a session.';
    end if;
    if p_end_meter_kwh < p_start_meter_kwh then
        raise exception 'The ending meter reading (%) is below the starting reading (%).',
            p_end_meter_kwh, p_start_meter_kwh;
    end if;

    -- If the host recorded meter evidence for this session, the settled figures must be
    -- the recorded ones. The caller supplies the readings, and a caller who owes the money
    -- must never be able to settle for less than the evidence shows. meter_readings is
    -- host-write-only, so a row here is evidence rather than an app-supplied number.
    select mr."startMeterKwh", mr."endMeterKwh"
      into v_evidence_start, v_evidence_end
      from public.meter_readings mr
     where mr."sessionId" = p_session_id
     order by mr."timestampMs" desc
     limit 1;

    if v_evidence_start is not null then
        if abs(v_evidence_start - p_start_meter_kwh) > 0.01
           or abs(v_evidence_end - p_end_meter_kwh) > 0.01 then
            raise exception 'The readings do not match the meter evidence recorded by the host.';
        end if;
    end if;

    v_start  := public.round2(p_start_meter_kwh);
    v_end    := public.round2(p_end_meter_kwh);
    v_kwh    := public.round2(v_end - v_start);
    v_energy := public.round2(v_kwh * v_tariff);
    if v_kwh <= 0 then
        -- Published rule: no energy charge when no energy was consumed.
        v_service := 0.0;
        v_gateway := 0.0;
    end if;
    v_total := public.round2(v_energy + v_service + v_gateway);
    v_now   := (extract(epoch from now()) * 1000)::bigint;

    -- The authorization taken when the slot was booked becomes the capture.
    select p.id into v_payment
      from public.payments p
     where p."reservationId" = p_reservation_id
     order by p."createdAtMs" desc
     limit 1;

    insert into public.charging_sessions (id, "reservationId", "consumerId", "hostId",
        "stationId", "stationName", "portId", "startTimeMs", "endTimeMs",
        "startingMeterKwh", "endingMeterKwh", "verifiedKwhConsumed", "energyCharge",
        "serviceFee", "gatewayFee", "totalPaid", status, "meterReadingId", "invoiceId",
        "isMeterSimulated", "verificationLevel")
    values (p_session_id, p_reservation_id, v_consumer, v_host,
        v_station, v_station_name, v_port, v_now, v_now,
        v_start, v_end, v_kwh, v_energy,
        v_service, v_gateway, v_total, 'COMPLETED', p_meter_reading_id, p_invoice_id,
        coalesce(p_is_simulated, false), v_level)
    on conflict (id) do update
        set "startingMeterKwh"    = excluded."startingMeterKwh",
            "endingMeterKwh"      = excluded."endingMeterKwh",
            "verifiedKwhConsumed" = excluded."verifiedKwhConsumed",
            "energyCharge"        = excluded."energyCharge",
            "serviceFee"          = excluded."serviceFee",
            "gatewayFee"          = excluded."gatewayFee",
            "totalPaid"           = excluded."totalPaid",
            status                = excluded.status,
            "meterReadingId"      = excluded."meterReadingId",
            "invoiceId"           = excluded."invoiceId",
            "verificationLevel"   = excluded."verificationLevel";

    if p_meter_reading_id is not null then
        insert into public.meter_readings (id, "sessionId", "stationId", "startMeterKwh",
            "endMeterKwh", "verificationLevel", "timestampMs", "isSimulated")
        values (p_meter_reading_id, p_session_id, v_station, v_start,
            v_end, v_level, v_now, coalesce(p_is_simulated, false))
        on conflict (id) do update
            set "startMeterKwh"     = excluded."startMeterKwh",
                "endMeterKwh"       = excluded."endMeterKwh",
                "verificationLevel" = excluded."verificationLevel",
                "timestampMs"       = excluded."timestampMs",
                "isSimulated"       = excluded."isSimulated";
    end if;

    if p_invoice_id is not null then
        insert into public.invoices (id, "sessionId", "consumerId", "hostId", "stationName",
            "verifiedKwh", "energyCharge", "serviceFee", "gatewayFee", adjustment,
            "adjustmentNote", "finalTotal", "paymentId", status, "issuedAtMs")
        values (p_invoice_id, p_session_id, v_consumer, v_host, v_station_name,
            v_kwh, v_energy, v_service, v_gateway, 0.0,
            '', v_total, v_payment, 'PAID', v_now)
        on conflict (id) do update
            set "verifiedKwh"  = excluded."verifiedKwh",
                "energyCharge" = excluded."energyCharge",
                "serviceFee"   = excluded."serviceFee",
                "gatewayFee"   = excluded."gatewayFee",
                "finalTotal"   = excluded."finalTotal",
                "paymentId"    = excluded."paymentId",
                status         = excluded.status;
    end if;

    if v_payment is not null then
        update public.payments
           set "sessionId"     = p_session_id,
               status          = 'CAPTURED',
               "capturedAmount" = v_total,
               "updatedAtMs"    = v_now
         where id = v_payment;
    end if;

    update public.reservations
       set status = 'COMPLETED', "isPaid" = true, "paymentId" = v_payment
     where id = p_reservation_id;

    if v_port is not null then
        update public.charger_ports set status = 'AVAILABLE' where id = v_port;
    end if;

    return p_session_id;
end;
$$;


-- Applies the published cancellation policy on the server and releases the hold.
-- Free until 60 minutes before the slot, then the disclosed reservation-protection
-- fee. Mirrors BillingEngine.calculateCancellation() and PaymentManager.release().
create or replace function public.cancel_reservation(p_reservation_id text)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
    v_consumer text;
    v_host     text;
    v_station  text;
    v_port     text;
    v_start    bigint;
    v_now      bigint;
    v_fee      double precision := 0.0;
begin
    select r."consumerId", r."stationId", r."portId", r."startTimeMs"
      into v_consumer, v_station, v_port, v_start
      from public.reservations r
     where r.id = p_reservation_id;

    if v_consumer is null then
        raise exception 'No reservation % to cancel.', p_reservation_id;
    end if;

    select s."hostId" into v_host
      from public.charger_stations s
     where s.id = v_station;

    if not (v_consumer = public.current_app_user_id()
            or v_host = public.current_app_user_id()
            or public.is_platform_admin()) then
        raise exception 'Only the consumer or the station host may cancel this reservation.';
    end if;

    v_now := (extract(epoch from now()) * 1000)::bigint;

    -- AppConstants.LATE_CANCELLATION_FEE_INR is the same 20.00 the app shows.
    if v_start is not null and v_now > (v_start - (60::bigint * 60 * 1000)) then
        v_fee := 20.0;
    end if;

    update public.reservations
       set status            = 'CANCELLED',
           "cancelledAtMs"   = v_now,
           "cancellationFee" = v_fee
     where id = p_reservation_id;

    update public.payments
       set status                = case when v_fee > 0 then 'CAPTURED' else 'RELEASED' end,
           "capturedAmount"      = case when v_fee > 0 then v_fee else 0.0 end,
           "lateCancellationFee" = v_fee,
           "updatedAtMs"         = v_now
     where "reservationId" = p_reservation_id
       and status = 'AUTHORIZED';

    if v_port is not null then
        update public.charger_ports set status = 'AVAILABLE' where id = v_port;
    end if;

    return p_reservation_id;
end;
$$;


-- Approves or rejects a listing, and tells the host.
create or replace function public.decide_station_approval(
    p_station_id text,
    p_approve    boolean
)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
    v_host  text;
    v_name  text;
    v_level text;
    v_note  text;
    v_now   bigint;
begin
    if not public.is_platform_admin() then
        raise exception 'Only a platform administrator may approve a listing.';
    end if;

    select s."hostId", s.name, s."verificationLevel"
      into v_host, v_name, v_level
      from public.charger_stations s
     where s.id = p_station_id;

    if v_host is null then
        raise exception 'Unknown station %.', p_station_id;
    end if;

    v_note := case when coalesce(p_approve, false)
                   then 'Approved. The station is live in discovery.'
                   else 'Rejected. Correct the listing and resubmit it for review.'
              end;
    v_now  := (extract(epoch from now()) * 1000)::bigint;

    update public.charger_stations
       set "approvalStatus" = case when coalesce(p_approve, false)
                                   then 'APPROVED' else 'REJECTED' end,
           "reviewNote"     = v_note,
           -- An approved station with no verified meter stays discovery-only.
           "isVerified"     = coalesce(p_approve, false)
                              and v_level in ('LEVEL_1', 'LEVEL_2'),
           "lastUpdatedMs"  = v_now
     where id = p_station_id;

    insert into public.notifications (id, "userId", title, message, "isRead", "createdAtMs")
    values (gen_random_uuid()::text, v_host,
            case when coalesce(p_approve, false) then 'Listing approved'
                 else 'Listing rejected' end,
            v_name || ': ' || v_note,
            false, v_now);

    return p_station_id;
end;
$$;


-- Refunds the invoice's energy charge and the payment's capture, and resolves the
-- dispute. The service and gateway fees are not refunded: that work was done.
create or replace function public.resolve_dispute_refund(p_dispute_id text)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
    v_session  text;
    v_invoice  text;
    v_payment  text;
    v_energy   double precision;
    v_now      bigint;
begin
    if not public.is_platform_admin() then
        raise exception 'Only a platform administrator may refund a dispute.';
    end if;

    select d."sessionId" into v_session
      from public.disputes d
     where d.id = p_dispute_id;

    select i.id, i."paymentId", coalesce(i."energyCharge", 0)
      into v_invoice, v_payment, v_energy
      from public.invoices i
     where i."sessionId" = v_session
     limit 1;

    if v_invoice is null then
        raise exception 'Dispute % has no invoice to refund.', p_dispute_id;
    end if;

    v_now := (extract(epoch from now()) * 1000)::bigint;

    update public.invoices i
       set adjustment       = public.round2(coalesce(i.adjustment, 0) + v_energy),
           "adjustmentNote" = 'Dispute resolved: the energy charge was refunded.',
           "finalTotal"     = public.round2(greatest(0,
                                coalesce(i."energyCharge", 0) + coalesce(i."serviceFee", 0)
                                + coalesce(i."gatewayFee", 0)
                                - (coalesce(i.adjustment, 0) + v_energy))),
           status           = case
                                when coalesce(i.adjustment, 0) + v_energy
                                     >= coalesce(i."energyCharge", 0) + coalesce(i."serviceFee", 0)
                                        + coalesce(i."gatewayFee", 0)
                                then 'REFUNDED'
                                when coalesce(i.adjustment, 0) + v_energy > 0
                                then 'PARTIALLY_REFUNDED'
                                else i.status
                              end
     where i.id = v_invoice;

    if v_payment is not null then
        update public.payments
           set status           = 'REFUNDED',
               "refundedAmount" = v_energy,
               "updatedAtMs"    = v_now
         where id = v_payment;
    end if;

    update public.disputes
       set status           = 'RESOLVED',
           "refundAmount"   = v_energy,
           "resolutionNote" = 'The energy charge was refunded in full.',
           "resolvedAtMs"   = v_now
     where id = p_dispute_id;

    return p_dispute_id;
end;
$$;


-- Holds a host's payout while a dispute is under review.
create or replace function public.freeze_dispute_payout(p_dispute_id text)
returns text
language plpgsql
security definer
set search_path = public
as $$
begin
    if not public.is_platform_admin() then
        raise exception 'Only a platform administrator may hold a payout.';
    end if;

    update public.disputes set "isPayoutFrozen" = true where id = p_dispute_id;

    return p_dispute_id;
end;
$$;


-- Records the enforcement action, resolves the dispute with it, and takes a
-- suspended or banned host's station off the platform with them.
create or replace function public.apply_host_enforcement(
    p_dispute_id  text,
    p_action      text,
    p_fine_amount double precision,
    p_note        text
)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
    v_host    text;
    v_station text;
    v_now     bigint;
begin
    if not public.is_platform_admin() then
        raise exception 'Only a platform administrator may take enforcement action.';
    end if;

    select d."hostId", d."stationId"
      into v_host, v_station
      from public.disputes d
     where d.id = p_dispute_id;

    if v_host is null then
        raise exception 'Unknown dispute %.', p_dispute_id;
    end if;

    v_now := (extract(epoch from now()) * 1000)::bigint;

    insert into public.host_enforcements (id, "hostId", "stationId", "actionType", reason,
        "fineAmount", "createdAtMs")
    values (gen_random_uuid()::text, v_host, v_station, p_action, p_note,
        coalesce(p_fine_amount, 0.0), v_now);

    update public.disputes
       set "enforcementAction" = p_action,
           status              = 'RESOLVED',
           "resolutionNote"    = p_note,
           "resolvedAtMs"      = v_now
     where id = p_dispute_id;

    if p_action in ('SUSPENSION', 'BAN') and v_station is not null then
        update public.charger_stations
           set "isPaused" = true, "lastUpdatedMs" = v_now
         where id = v_station;
    end if;

    return p_dispute_id;
end;
$$;


-- A consumer legitimately updates their own session row — check-in and the
-- charging transition need it — but the amount they are charged is written only
-- by finalize_charging_session() above. A policy cannot express "but not this
-- column", so this trigger does. Recompute-then-write is what makes it safe:
-- tampering before settlement is overwritten, so only a settled row is pinned.
create or replace function public.prevent_billing_tampering()
returns trigger
language plpgsql
set search_path = public
as $$
begin
    if public.is_trusted_sql_context() then
        return new;
    end if;

    if new."verifiedKwhConsumed" is distinct from old."verifiedKwhConsumed"
       or new."energyCharge"     is distinct from old."energyCharge"
       or new."serviceFee"       is distinct from old."serviceFee"
       or new."gatewayFee"       is distinct from old."gatewayFee"
       or new."totalPaid"        is distinct from old."totalPaid"
       or new."invoiceId"        is distinct from old."invoiceId" then
        raise exception 'The billed amount is written by finalize_charging_session(), not by the app.';
    end if;

    return new;
end;
$$;

drop trigger if exists prevent_billing_tampering on public.charging_sessions;
create trigger prevent_billing_tampering
    before update on public.charging_sessions
    for each row execute function public.prevent_billing_tampering();


-- The UPDATE trigger above cannot see a row that is created already settled. An API
-- caller could therefore POST a charging_sessions row with its own energyCharge and
-- totalPaid, which would corrupt host revenue and the analytics built on it. Opening a
-- session (status IN_PROGRESS, money at zero) stays allowed; only finalize_charging_session()
-- may create a settled one.
create or replace function public.prevent_session_billing_on_insert()
returns trigger
language plpgsql
set search_path = public
as $$
begin
    if public.is_trusted_sql_context() then
        return new;
    end if;

    if coalesce(new."verifiedKwhConsumed", 0) <> 0
       or coalesce(new."energyCharge", 0)     <> 0
       or coalesce(new."serviceFee", 0)       <> 0
       or coalesce(new."gatewayFee", 0)       <> 0
       or coalesce(new."totalPaid", 0)        <> 0
       or new."invoiceId" is not null then
        raise exception 'A settled session is written by finalize_charging_session(), not by the app.';
    end if;

    return new;
end;
$$;

drop trigger if exists prevent_session_billing_on_insert on public.charging_sessions;
create trigger prevent_session_billing_on_insert
    before insert on public.charging_sessions
    for each row execute function public.prevent_session_billing_on_insert();


-- A consumer may update their own payment row — but the RLS policy is row-level, so it
-- cannot say "and not the amount". Without this trigger the consumer who owes the money
-- is also the one who can write capturedAmount, refundedAmount and status, which is a
-- self-refund. Capture, refund and release belong to the settlement functions, which run
-- as the table owner and are therefore trusted here.
create or replace function public.prevent_payment_tampering()
returns trigger
language plpgsql
set search_path = public
as $$
begin
    if public.is_trusted_sql_context() then
        return new;
    end if;

    if tg_op = 'INSERT' then
        if coalesce(new."capturedAmount", 0)       <> 0
           or coalesce(new."refundedAmount", 0)    <> 0
           or coalesce(new."lateCancellationFee", 0) <> 0 then
            raise exception 'A payment authorization is created by authorize_payment(), not by the app.';
        end if;
        return new;
    end if;

    if new."capturedAmount"        is distinct from old."capturedAmount"
       or new."refundedAmount"     is distinct from old."refundedAmount"
       or new."lateCancellationFee" is distinct from old."lateCancellationFee"
       or new.status               is distinct from old.status then
        raise exception 'Capture, refund and release are settlement actions, not app writes.';
    end if;

    return new;
end;
$$;

drop trigger if exists prevent_payment_tampering on public.payments;
create trigger prevent_payment_tampering
    before insert or update on public.payments
    for each row execute function public.prevent_payment_tampering();


-- Both parties move a reservation's status (check-in by the host, cancellation by the
-- consumer), so the UPDATE policy has to stay. The slot, the port and the price were chosen
-- when the booking was made, though: letting either party rewrite them afterwards would
-- allow jumping the queue or rewriting what was authorised. They are pinned here instead.
create or replace function public.prevent_reservation_tampering()
returns trigger
language plpgsql
set search_path = public
as $$
begin
    if public.is_trusted_sql_context() or public.is_platform_admin() then
        return new;
    end if;

    if new."startTimeMs"    is distinct from old."startTimeMs"
       or new."endTimeMs"    is distinct from old."endTimeMs"
       or new."portId"       is distinct from old."portId"
       or new."stationId"    is distinct from old."stationId"
       or new."consumerId"   is distinct from old."consumerId"
       or new."pricePerKwh"  is distinct from old."pricePerKwh"
       or new."serviceFee"   is distinct from old."serviceFee"
       or new."gatewayFee"   is distinct from old."gatewayFee"
       or new."bookingCode"  is distinct from old."bookingCode" then
        raise exception 'A reservation slot, port and price are fixed when the booking is made.';
    end if;

    return new;
end;
$$;

drop trigger if exists prevent_reservation_tampering on public.reservations;
create trigger prevent_reservation_tampering
    before update on public.reservations
    for each row execute function public.prevent_reservation_tampering();


-- None of these is API surface: they are called by triggers, or are internal helpers.
-- Strip the default PUBLIC execute grant.
revoke all on function public.prevent_billing_tampering() from public;
revoke all on function public.prevent_session_billing_on_insert() from public;
revoke all on function public.prevent_payment_tampering() from public;
revoke all on function public.prevent_reservation_tampering() from public;
revoke all on function public.round2(double precision) from public;

-- ---------------------------------------------------------------------------
-- 6. Grants
--    RLS filters rows; grants decide which verbs are reachable at all. Both are
--    required. The service_role bypasses RLS entirely — never ship that key.
-- ---------------------------------------------------------------------------

grant usage on schema public to anon, authenticated;

grant select, insert, update, delete on all tables in schema public to authenticated;

-- Anonymous visitors may browse charger listings (discovery) and nothing else.
grant select on public.charger_stations        to anon;
grant select on public.charger_ports           to anon;
grant select on public.availability_schedules  to anon;

grant execute on function public.current_app_user_id()      to anon, authenticated;
grant execute on function public.current_app_user_role()    to authenticated;
grant execute on function public.is_platform_admin()        to authenticated;
grant execute on function public.is_trusted_sql_context()   to anon, authenticated;

-- The settlement transitions. PostgreSQL grants EXECUTE on a new function to
-- PUBLIC, so each is revoked and then granted to signed-in callers only: an
-- anonymous visitor must not be able to reach money at all. The function itself
-- is what actually decides who may call it.
revoke all on function public.authorize_payment(text, text, text, double precision, text) from public;
revoke all on function public.finalize_charging_session(text, text, double precision, double precision, boolean, text, text) from public;
revoke all on function public.cancel_reservation(text) from public;
revoke all on function public.decide_station_approval(text, boolean) from public;
revoke all on function public.resolve_dispute_refund(text) from public;
revoke all on function public.freeze_dispute_payout(text) from public;
revoke all on function public.apply_host_enforcement(text, text, double precision, text) from public;

grant execute on function public.authorize_payment(text, text, text, double precision, text) to authenticated;
grant execute on function public.finalize_charging_session(text, text, double precision, double precision, boolean, text, text) to authenticated;
grant execute on function public.cancel_reservation(text) to authenticated;
grant execute on function public.decide_station_approval(text, boolean) to authenticated;
grant execute on function public.resolve_dispute_refund(text) to authenticated;
grant execute on function public.freeze_dispute_payout(text) to authenticated;
grant execute on function public.apply_host_enforcement(text, text, double precision, text) to authenticated;

-- ---------------------------------------------------------------------------
-- 7. Backfill: reconcile Auth accounts with application profiles
--    This is what repairs the accounts that are already broken. Re-running this
--    file links, or creates, the public.users row for every existing Auth account,
--    including accounts created in the Dashboard and accounts whose sign-up never
--    reached the app. No manual UUID copying is required.
-- ---------------------------------------------------------------------------

do $$
declare
    account record;
begin
    for account in select id, email, raw_user_meta_data from auth.users loop
        perform public.ensure_app_user(account.id, account.email, account.raw_user_meta_data);
    end loop;
end $$;

-- The state of both sides, side by side. Every Auth account should come back with
-- an app_user_id. A NULL there is the one thing that makes the whole app appear
-- empty, and re-running this file is the fix.
select a.id                             as auth_user_id,
       a.email,
       a.email_confirmed_at is not null as email_confirmed,
       u.id                             as app_user_id,
       u.role                           as app_role
from auth.users a
left join public.users u on u."authUserId" = a.id
order by a.created_at;


-- ============================================================================
--  Done. Next: supabase/pilot_seed.sql (optional demo data), then
--  supabase/README.md for the API key and auth-user linking steps.
-- ============================================================================
