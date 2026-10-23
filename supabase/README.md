# Amperly — Supabase setup

This folder is the **entire** database side of Amperly. There is no other Supabase
code, config or hidden SQL in this repository.

| File | What it is |
|---|---|
| `schema.sql` | All 23 tables, Row Level Security, policies, helper functions, indexes, grants, the settlement functions money is written through, the `auth.users` trigger that gives every account its profile, and the backfill that repairs accounts created before it. **This is the file you were looking for.** |
| `pilot_seed.sql` | Optional demo data (6 stations, 12 ports, 84 schedules, and the records the demo script needs). |
| `link_pilot_users.sql` | Connects the seeded users to real Supabase Auth accounts. |

---

## Run order

Do these in order. Each step is safe to re-run.

### 1. Create the tables

Supabase Dashboard → **SQL Editor** → **New query** → paste the whole of
`schema.sql` → **Run**.

You should end with 23 tables in the `public` schema, all with a red
"RLS enabled" marker in Table Editor.

The last statement in the file prints every Auth account next to the application
profile it resolves to. Every row must show an `app_user_id`: a blank one is the
single cause of an app that signs in and then shows nothing at all.

### 2. (Optional) Add the pilot data

New query → paste `pilot_seed.sql` → **Run**.

Verify:

```sql
select (select count(*) from public.charger_stations)       as stations,   -- 6
       (select count(*) from public.charger_ports)          as ports,      -- 12
       (select count(*) from public.availability_schedules) as schedules,  -- 84
       (select count(*) from public.users)                  as users;      -- 4
```

### 3. Where the URL and publishable key go

**Get them from:** Dashboard → **Project Settings** → **API Keys**
(also shown on the project home screen as "Connect").

Copy two values:

- **Project URL** — looks like `https://abcdefghijklmnop.supabase.co`
- **Publishable key** — looks like `sb_publishable_xxxxxxxxxxxx`
  (This is the renamed `anon` key. If your project still shows the legacy keys,
  the `anon` key works in exactly the same place.)

**Paste them into `local.properties`** in the project root — the same file that
already holds `sdk.dir` and `MAPS_API_KEY`:

```properties
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
MAPS_API_KEY=your_google_maps_key

SUPABASE_URL=https://abcdefghijklmnop.supabase.co
SUPABASE_PUBLISHABLE_KEY=sb_publishable_xxxxxxxxxxxx
```

`local.properties` is in `.gitignore`, so the key is never committed. `app/build.gradle.kts`
reads both values (falling back to the `SUPABASE_URL` / `SUPABASE_PUBLISHABLE_KEY`
environment variables) and exposes them as `BuildConfig.SUPABASE_URL` and
`BuildConfig.SUPABASE_PUBLISHABLE_KEY`.

**If these are absent, the app runs exactly as it does today** — on-device Room
plus the local seeder, no networking. Nothing breaks.

> ### ⚠️ The publishable key is only safe because of RLS
> A publishable key ships inside your APK and is designed to be public. That is
> only true because `schema.sql` turns on Row Level Security for every table and
> writes policies for it. It is not a secret, and you should not treat it as
> one — but if you ever disable RLS on a table, anyone who unzips your APK can
> read and write that table.
>
> **Never** put a `sb_secret_...` key (the old `service_role` key) in
> `local.properties` or anywhere in the app. It bypasses RLS completely.

### 4. Create the Auth accounts

`schema.sql` installs a trigger on `auth.users`, so an Auth account and its
`public.users` profile are created together and cannot drift apart. Creating the
Auth account is now the only step needed — no UUIDs are copied by hand and there
is no separate linking run.

Dashboard → **Authentication** → **Users** → **Add user** → **Create new user**.
Create one per pilot user, using the same email as the seed, with a password you
choose, and tick **Auto Confirm User**:

| Email | Application role |
|---|---|
| `host@abccollege.edu` | Host — ABC College |
| `aarav@abccollege.edu` | Consumer — the student |
| `president@greenpark.org` | Host — GreenPark |
| `admin@amperly.app` | Platform administrator |

When a seeded profile already exists for that email, the trigger links it in
place, so the seeded row keeps its role and owns the demo history. Anything the
app signs up on its own is created as a **Consumer**: `raw_user_meta_data` comes
from the client at sign-up, so it is never trusted to grant HOST or ADMIN. Promote
the platform administrator afterwards:

```sql
update public.users set role = 'ADMIN' where email = 'admin@amperly.app';
```

`link_pilot_users.sql` performs the same linking by hand. It is kept for projects
where the accounts already existed before the trigger was installed; you do not
need it if you created the accounts after running `schema.sql`.

#### Signing up from the app needs a working email sender

Sign-up returns a session immediately only when the project does not require
confirmation. With **Confirm email** on, GoTrue creates the account and then waits
for the link to be opened — and until it is, the app cannot sign in at all. The
account shows up in Authentication with no profile and no data anywhere, which
looks exactly like a broken backend.

Check **Authentication → Providers → Email**. Either turn **Confirm email** off
for the pilot, or configure **SMTP**. Supabase's built-in sender only delivers to
the project's own team members, so with the defaults no pilot user will ever
receive the link.

The profile row is created either way, at the moment the account is created, so
the app works as soon as the user is able to sign in.

---

## What each role can do

The policies in `schema.sql` implement this. Row Level Security is enforced by
Postgres, not by the app, so a modified APK cannot get around it.

| | Anonymous | Consumer | Host | Admin |
|---|---|---|---|---|
| Browse approved chargers, ports, schedules | ✅ | ✅ | ✅ | ✅ |
| See PENDING / paused stations | ❌ | ❌ | on their own stations | ✅ |
| Own vehicle, route plans, battery updates | ❌ | ✅ | ✅ | ✅ |
| Own reservations, sessions, invoices | ❌ | ✅ | — | ✅ |
| Reservations and sessions for their stations | ❌ | — | ✅ | ✅ |
| Write meter readings | ❌ | their own session's reading | ✅ | ✅ |
| Capture, refund, cancel, settle a bill | ❌ | via a function | via a function | ✅ |
| Issue invoices, refunds, disputes resolution | ❌ | ❌ | ❌ | ✅ |
| Chat | ❌ | participants only | participants only | ✅ |
| Change a user's role or block state | ❌ | ❌ | ❌ | ✅ |

Two deliberate hard rules worth knowing about, because they are the ones an
attacker would try first:

- **A consumer cannot decide what they are charged.** They may add a meter
  reading for their own session, but the reading the bill is calculated from —
  and the amount itself — are written by `finalize_charging_session()` on the
  server. `payments` and `invoices` have no client write policy at all, and the
  billing columns of `charging_sessions` are pinned by a trigger.
- **A user cannot promote themselves.** Roles are a column on `users`, and the
  obvious attack is to `PATCH` your own row and set `role = 'ADMIN'`. The
  `prevent_privilege_escalation` trigger rejects any role, block-state or
  verification-status change from a non-admin, which is something a policy alone
  cannot express.

---

## Money is settled by functions, not by the client

The app computes a bill locally so that it works offline, but the client is no
longer allowed to *store* one. The billing transitions go through
`SECURITY DEFINER` functions, called as `POST /rest/v1/rpc/<name>`:

| Function | Who may call it | What it does |
|---|---|---|
| `authorize_payment` | the consumer booking | Creates the authorization (a hold, not a charge) |
| `finalize_charging_session` | that reservation's consumer or host | Recomputed from the meter readings; writes the session, the receipt and the capture, closes the reservation, frees the port |
| `cancel_reservation` | that reservation's consumer or host | Applies the published late-cancellation policy server-side and releases the hold |
| `decide_station_approval` | admin | Approves or rejects a listing and notifies the host |
| `resolve_dispute_refund` | admin | Refunds the invoice's energy charge, refunds the payment, resolves the dispute |
| `freeze_dispute_payout` | admin | Holds the host's payout |
| `apply_host_enforcement` | admin | Records the enforcement action and pauses the station when the policy calls for it |

Each one checks who is calling before it writes, and the app calls it directly
from the save that needs it. There is no offline queue: a call that fails is
reported in the account screen's sync status instead of being replayed later.

Three things to keep in mind:

- **Run `schema.sql` as the table owner** — the Supabase SQL editor does this.
  The functions are `SECURITY DEFINER`, and the guard triggers treat a session
  whose `current_user` is neither `anon` nor `authenticated` as trusted. If the
  functions ended up owned by some other role, RLS would apply to their writes
  too and settlements would fail.
- **A refused write is reported, not queued.** The app holds no offline write
  queue, so a row the server refuses is logged and shown in the account screen's
  sync status; the local copy in Room stays but is not re-sent.
- **The server's figure is the one that counts.** After a settlement, pull so the
  locally computed amounts are replaced by the server's.

---

## How the schema relates to the app

Column names here are **quoted camelCase and identical to the Java field names**
in `app/src/main/java/com/amperly/app/data/local/entity/`. None of the Room
entities use `@ColumnInfo`, so the names already matched on the device side too.

That is deliberate: it means a PostgREST response deserialises straight into the
existing entity classes with no DTO or mapping layer at all:

```
PostgREST JSON key  ==  Java field name  ==  Room column
```

The cost is quoted identifiers in SQL. Two columns exist only on the server and
are ignored by the app's JSON parser:

- **`users."authUserId"`** — links an application user to an Auth account.
- **`"serverUpdatedAt"`** — stamped by a trigger on every UPDATE, and what makes
  incremental sync possible. Do not remove it, and do not edit it from the app.

`"authUserId"` is why sections 1b and 1c exist. The column has no counterpart on
the Room entity, so a row built from the entity alone would arrive without it and
`users_insert_self` would refuse the insert. The app still creates its own profile
row on first sign-in, as a fallback for a project where those sections have not
been applied yet, and it sends that row straight to PostgREST with the server-only
column included.

---

## Troubleshooting

**`relation "public.users" does not exist` while running the file** — the copy you
pasted creates a function that reads `public.users` above the statement that
creates it. PostgreSQL parses and analyses the body of a `LANGUAGE SQL` function
when it is created, so the file has to be ordered tables-first. Sections 1b and 1c
are placed after section 1 for exactly that reason. The error quotes a line inside
the function, and nothing before it in the file was applied.

**"new row violates row-level security policy"** — the signed-in user has no
`public.users` row, so `current_app_user_id()` returned null. Re-run `schema.sql`:
the backfill at the end creates the missing profile for every existing Auth
account, and the `on_auth_user_created` trigger stops it happening again.

**The account is in Authentication but not in `public.users`** — this is the state
that makes the whole app look empty, because every policy resolves the caller
through `users."authUserId"`. Re-run `schema.sql` and read the table it prints at
the end: every Auth account must show an `app_user_id`.

**Queries return an empty list instead of an error** — that is RLS working
correctly: you are seeing zero rows because no policy grants you any. Same fix
as above.

**401 / "No API key found in request"** — `SUPABASE_PUBLISHABLE_KEY` is empty or
still the placeholder. Check `local.properties` and rebuild; `BuildConfig` values
are baked in at compile time.

**A new column or table is not visible to the API** — PostgREST caches the
schema. Run `notify pgrst, 'reload schema';`, or wait a few seconds and retry.

**Consumer discovery shows nothing** — discovery deliberately exposes only
stations with `"approvalStatus" = 'APPROVED'` and `"isPaused" = false`. The
seeded `st_office_pending` is meant to be invisible until an admin approves it.

**Testing policies by hand** — RLS applies to the API roles, not to you in the
SQL editor, so a query that works in the editor may still fail from the app. To
check what a signed-in user actually sees, impersonate them:

```sql
set local role authenticated;
set local request.jwt.claims = '{"sub":"<auth-user-uuid>"}';
select * from public.reservations;
reset role;
```
