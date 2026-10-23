# Security review — Amperly (October 2026)

Scope: the Android client (`app/src`), the Gradle build and key injection, and the Supabase
schema (`supabase/schema.sql`). The review looked for ways an attacker holding the shipped APK
and the publishable key — or a malicious app on the same device — could read another account's
data, change money, or take over an account.

Each confirmed issue is listed with what it allowed, what was changed, and where.

---

## Fixed in this change

### 1. A consumer could refund their own payment (high)

`payments_update_consumer` is a row-level policy, so it allowed the owner of a payment row to
write **any column**, including `capturedAmount`, `refundedAmount`, `lateCancellationFee` and
`status`. A signed-in consumer could `PATCH` their own payment to `status = 'REFUNDED'` with a
full `refundedAmount`, or zero the captured amount before settlement.

**Fix:** `prevent_payment_tampering()` + trigger on `payments` (insert and update). Capture,
refund, release and status transitions are only accepted from a trusted SQL context — the
`SECURITY DEFINER` settlement functions — not from the API role.
`supabase/schema.sql`.

### 2. A settled session could be fabricated on INSERT (high)

`prevent_billing_tampering` was `BEFORE UPDATE` only. A caller could `POST` a
`charging_sessions` row that was already settled, with their own `totalPaid` and
`energyCharge`, corrupting host revenue and every analytics figure built on it.

**Fix:** `prevent_session_billing_on_insert()` + `BEFORE INSERT` trigger. Opening a session
(money at zero) is still allowed; a settled row can only be created by
`finalize_charging_session()`.

### 3. A booking's slot, port and price could be rewritten after booking (high)

`reservations_update_parties` has to allow both parties to move `status` (host check-in,
consumer cancellation), but RLS cannot restrict columns. Either party could move their booking
to a busier slot, change the port, or rewrite `pricePerKwh` / `serviceFee` / `gatewayFee`.

**Fix:** `prevent_reservation_tampering()` + `BEFORE UPDATE` trigger pins
`startTimeMs`, `endTimeMs`, `portId`, `stationId`, `consumerId`, `pricePerKwh`, `serviceFee`,
`gatewayFee` and `bookingCode`. Status, check-in and cancellation fields remain writable.

### 4. Settlement ignored the host's meter evidence (high)

`finalize_charging_session()` billed from the readings the caller passed in. The caller may be
the consumer, so a custom client could settle a session for far less than the meter showed —
the one number the whole product promises to bill from.

**Fix:** before computing the bill, the function now reads the latest `meter_readings` row for
that session (a table only the station host can write) and refuses the settlement if the
supplied readings differ by more than 0.01 kWh. See "Residual risk 2" for the remaining case.

### 5. Session tokens were stored in plaintext (high)

`SupabaseAuth` wrote the access and refresh tokens to ordinary `SharedPreferences`. Anyone
with a backup, a rooted device or a physical extraction got a full account.

**Fix:** tokens now live in `EncryptedSharedPreferences` (Android Keystore, AES-256-GCM for
values, AES-256-SIV for keys). Existing installs are migrated once and the plaintext file is
wiped. If the Keystore is unusable the app **fails closed** and asks for a fresh sign-in; it
never falls back to plaintext. Confirmed on device: only
`amperly_supabase_session_secure.xml` exists after launch, and the log reports the encrypted
store.

### 6. Backups included the tokens and the whole database (high)

`android:allowBackup="true"` with empty extraction rules meant the Room database (names, emails,
phone numbers, licence plates, meter readings) and the session file went into cloud backups and
device-to-device transfer.

**Fix:** `allowBackup="false"`, `fullBackupContent` exclusions for pre-Android-12, and
`data_extraction_rules.xml` excluding every domain for both cloud backup and device transfer.

### 7. Booking codes were predictable (medium)

`AMP-` plus four digits from `java.util.Random`: 9,000 possibilities and derivable from a
previous code. A booking code is a bearer credential — the host checks a car in by looking it
up.

**Fix:** `SecureRandom`, six characters from a 31-symbol alphabet with the ambiguous
`I L O 0 1` removed (~30 bits). The format stays human-readable (`AMP-7KQ2MX`) and nothing
parses the old shape.

### 8. Cleartext HTTP was allowed on Android 7 and below (medium)

`usesCleartextTraffic` defaulted to true below API 28, which allowed a downgrade attack on a
hostile network.

**Fix:** `android:usesCleartextTraffic="false"` plus a `network_security_config.xml` that denies
cleartext for every origin and trusts only system CAs. Nothing in the app uses HTTP.

### 9. Payment idempotency was check-then-act (medium)

`PaymentManager.authorize` checked a plain `HashSet` and added to it later, so two concurrent
retries could both authorize — two holds on one booking.

**Fix:** the method is `synchronized` and the set is a `ConcurrentHashMap` key set, making the
check-and-record atomic.

### 10. A deep link could fake "email confirmed" (low)

`amperly://auth-callback` is an exported custom scheme any app can fire, and the app answered
it with "Email Confirmed Successfully!". Nothing was actually validated, so the message was
both spoofable and untrue.

**Fix:** the handler now shows a neutral "back in Amperly, sign in to continue" message. No
security state is inferred from an intent the app did not originate.

---

## Residual risk (documented, not exploitable by the fixes above)

1. **Room is not encrypted at rest.** The database holds profile and billing data. Backups are
   now off, but a rooted device can still read it. Full fix: SQLCipher
   (`net.zetetic:sqlcipher-android`) with a Keystore-derived passphrase; a larger migration than
   this review.
2. **Settlement still trusts the consumer when no host evidence exists.** If the host recorded no
   `meter_readings` row for the session, the consumer can settle with under-reported readings.
   Closing this fully means making host evidence mandatory before billing (or restricting
   settlement to the host/admin), which changes the "complete and bill" consumer flow. Recorded
   as the next security change, not silently half-done.
3. **A reservation can be created without an authorization.** A custom client can insert a
   reservation and hold a port without paying. Availability is then denied to real drivers until
   it expires. Fix: a `create_reservation` RPC that checks the payment hold in one transaction.
4. **No certificate pinning.** System CAs are trusted, which is the platform default; pinning
   would harden against a malicious CA but adds an operational rotation burden.
5. **R8/minification is off in release.** That is obfuscation, not a security boundary — the
   publishable key and API shape are public by design — but enabling R8 with keep rules for the
   Gson-mapped entities is worth doing separately.
6. **No client-side rate limiting** on sign-in or booking-code lookup. GoTrue rate-limits sign-in
   server-side; booking-code lookups happen against the caller's own RLS-filtered rows.
7. **Google Maps key must be restricted in Google Cloud** to `com.amperly.app` plus the
   debug/release SHA-1 and to the four Maps Platform APIs. This is console configuration, not
   code; an unrestricted key is billable by anyone who extracts it.
8. **`supabase/pilot_seed.sql` creates demo accounts and stations.** It is a manual script and is
   never run by the app. Never run it against production.

---

## Deploying the database fixes

The SQL guards are in `supabase/schema.sql` but are **not** applied to the hosted project by the
app build. Run the file in the Supabase SQL editor once (the new triggers live at the end of
section 5, *Settlement*, and their `revoke` statements follow them), then verify:

```sql
select tgname from pg_trigger
 where tgname in ('prevent_payment_tampering', 'prevent_session_billing_on_insert',
                  'prevent_reservation_tampering');
```

Three rows should come back. Until then, the client fixes apply but the server-side guards do
not.
