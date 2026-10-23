-- ============================================================================
--  Amperly — pilot seed data
-- ============================================================================
--  Optional. Run AFTER schema.sql, in the Supabase SQL Editor.
--  It is idempotent: every insert is guarded by ON CONFLICT DO NOTHING, so
--  re-running it will not duplicate rows.
--
--  This mirrors PilotDataSeeder.java so the demo tells the same story whether
--  the app is running against Supabase or against the local Room fallback.
--  If you change one, change the other.
--
--  IMPORTANT: users rows are created with "authUserId" = NULL. The app can only
--  act as one of these users once you attach a real Supabase Auth account —
--  see supabase/README.md, step 4.
-- ============================================================================

do $$
declare
    three_days_ago  bigint := (extract(epoch from now()) * 1000)::bigint - (3::bigint * 24 * 60 * 60 * 1000);
    yesterday       bigint := (extract(epoch from now()) * 1000)::bigint - (24::bigint * 60 * 60 * 1000);
    slot_start      bigint := (extract(epoch from now()) * 1000)::bigint + (2::bigint * 60 * 60 * 1000);
    slot_end        bigint := (extract(epoch from now()) * 1000)::bigint + (2::bigint * 60 * 60 * 1000) + (90::bigint * 60 * 1000);
    thread_expiry   bigint := (extract(epoch from now()) * 1000)::bigint + (2::bigint * 60 * 60 * 1000)
                              + (90::bigint * 60 * 1000) + (7::bigint * 24 * 60 * 60 * 1000);
begin

    -- ------------------------------------------------------------------ users
    insert into public.users (id, name, email, phone, role, "organizationName", "verificationStatus", "isBlocked")
    values
        ('user_campus_admin',   'ABC College Administration', 'host@abccollege.edu',   '+919876543210', 'HOST',     'ABC College of Engineering', 'VERIFIED', false),
        ('user_student_1',      'Aarav Student',             'aarav@abccollege.edu',  '+919876543211', 'CONSUMER', null,                        'VERIFIED', false),
        ('user_apt_association','GreenPark Association',     'president@greenpark.org','+919876543220','HOST',     'GreenPark Apartments',       'VERIFIED', false),
        ('user_platform_admin', 'Amperly Ops',               'admin@amperly.app',     '+919876543299', 'ADMIN',    'Amperly',                    'VERIFIED', false)
    on conflict (id) do nothing;

    -- ---------------------------------------------------------------- vehicle
    insert into public.vehicles (id, "userId", make, model, variant, "purchaseYear",
        "batteryCapacityKwh", "connectorType", "maxAcKw", "maxDcKw", "typicalEfficiencyKmPerKwh",
        "batteryHealthPercent", "efficiencyCorrectionPercent", "minArrivalSocPercent",
        "maxChargeSocPercent", "licensePlate")
    values
        ('veh_101', 'user_student_1', 'Tata', 'Nexon EV', 'Max', 2023, 40.5, 'CCS_2_DC',
         7.2, 30.0, 6.5, 96.0, 0.0, 15, 80, 'KA09EV1234')
    on conflict (id) do nothing;

    -- --------------------------------------------------------------- stations
    -- isVerified mirrors ChargerStation.create(): true unless the meter level is
    -- LEVEL_3. isDcCapable is true when the connector list mentions CCS/CHADEMO.
    insert into public.charger_stations (id, "hostId", "hostName", name, address,
        latitude, longitude, "locationType", "operatingHours", "pricePerKwh", "serviceFee",
        "gatewayFee", "verificationLevel", "isVerified", "isPaused", "approvalStatus",
        "reviewNote", "connectorTypes", "maxPowerKw", "isDcCapable", "portCount",
        "accessRules", rating, "reliabilityScore", "incidentCount", "lastUpdatedMs")
    values
        ('st_campus_1', 'user_campus_admin', 'ABC College Administration',
         'ABC College Campus Charger', 'Gate 2 Parking, ABC Campus, Mysore',
         12.3010, 76.6450, 'CAMPUS', '08:00 - 20:00', 18.0, 5.0, 4.0, 'LEVEL_1',
         true, false, 'APPROVED', '', 'TYPE_2_AC,CCS_2_DC', 30.0, true, 4,
         'Students and staff with a campus vehicle pass. Show the booking code at Gate 2.',
         4.9, 92.0, 0, (extract(epoch from now()) * 1000)::bigint),

        ('st_apt_1', 'user_apt_association', 'GreenPark Association',
         'GreenPark Shared Charger', 'Visitor Parking, GreenPark Apartments, Mysore',
         12.3105, 76.6402, 'APARTMENT', '24/7', 16.0, 5.0, 4.0, 'LEVEL_1',
         true, false, 'APPROVED', '', 'TYPE_2_AC', 7.2, false, 2,
         'Residents and registered visitors. Register the vehicle number with the association.',
         4.7, 92.0, 0, (extract(epoch from now()) * 1000)::bigint),

        ('st_highway_1', 'user_campus_admin', 'Highway Charge Partners',
         'Highway Hub Charger', 'Mysore - Mangalore Road, Km 68',
         12.6000, 75.7900, 'COMMERCIAL', '24/7', 21.0, 6.0, 4.0, 'LEVEL_2',
         true, false, 'APPROVED', '', 'CCS_2_DC,CHADEMO', 60.0, true, 2,
         'Open to the public. Attendant on site between 06:00 and 22:00.',
         4.8, 92.0, 0, (extract(epoch from now()) * 1000)::bigint),

        ('st_roadside_1', 'user_apt_association', 'Kodagu Road Services',
         'Kodagu Roadside AC Charger', 'Kodagu Road, Km 72 near the tea estate',
         12.6050, 75.8200, 'PARKING', '06:00 - 22:00', 15.0, 5.0, 4.0, 'LEVEL_1',
         true, false, 'APPROVED', '', 'TYPE_2_AC', 7.2, false, 1,
         'Slower AC charger at the parking bay. Ring the bell for the attendant.',
         4.4, 92.0, 0, (extract(epoch from now()) * 1000)::bigint),

        ('st_cafe_1', 'user_apt_association', 'Cafe Owner',
         'Cafe ChargePoint (no verified meter)', '12 Sayyaji Rao Road, Mysore',
         12.3060, 76.6500, 'CAFE', '09:00 - 21:00', 14.0, 0.0, 0.0, 'LEVEL_3',
         false, false, 'APPROVED', '', 'TYPE_2_AC', 3.3, false, 1,
         'Discovery only: the cafe has no verified meter, so no energy-based payment is captured.',
         4.2, 92.0, 0, (extract(epoch from now()) * 1000)::bigint),

        -- Sits in the admin verification queue; hidden from consumer discovery
        -- until it is approved.
        ('st_office_pending', 'user_apt_association', 'Tech Park Facilities',
         'Tech Park Tower Charger', 'Tower B Basement, Tech Park, Mysore',
         12.3200, 76.6600, 'OFFICE', '09:00 - 18:00', 17.0, 5.0, 4.0, 'LEVEL_1',
         false, false, 'PENDING', '', 'TYPE_2_AC', 11.0, false, 2,
         'Employee access only, badge required at the basement gate.',
         0.0, 92.0, 0, (extract(epoch from now()) * 1000)::bigint)
    on conflict (id) do nothing;

    -- ------------------------------------------------------------------ ports
    insert into public.charger_ports (id, "stationId", "portNumber", "connectorType", "powerKw", "isDc", status)
    values
        ('port_campus_1',    'st_campus_1',      '1', 'TYPE_2_AC', 7.2,  false, 'AVAILABLE'),
        ('port_campus_2',    'st_campus_1',      '2', 'CCS_2_DC',  30.0, true,  'AVAILABLE'),
        ('port_campus_3',    'st_campus_1',      '3', 'CCS_2_DC',  30.0, true,  'AVAILABLE'),
        ('port_campus_4',    'st_campus_1',      '4', 'TYPE_2_AC', 7.2,  false, 'MAINTENANCE'),
        ('port_apt_1',       'st_apt_1',         '1', 'TYPE_2_AC', 7.2,  false, 'AVAILABLE'),
        ('port_apt_2',       'st_apt_1',         '2', 'TYPE_2_AC', 7.2,  false, 'AVAILABLE'),
        ('port_highway_1',   'st_highway_1',     '1', 'CCS_2_DC',  60.0, true,  'AVAILABLE'),
        ('port_highway_2',   'st_highway_1',     '2', 'CHADEMO',   50.0, true,  'AVAILABLE'),
        ('port_roadside_1',  'st_roadside_1',    '1', 'TYPE_2_AC', 7.2,  false, 'AVAILABLE'),
        ('port_cafe_1',      'st_cafe_1',        '1', 'TYPE_2_AC', 3.3,  false, 'AVAILABLE'),
        ('port_office_1',    'st_office_pending','1', 'TYPE_2_AC', 11.0, false, 'AVAILABLE'),
        ('port_office_2',    'st_office_pending','2', 'TYPE_2_AC', 11.0, false, 'AVAILABLE')
    on conflict (id) do nothing;

    -- ------------------------------------- availability (7 days per port)
    -- Opening hours come from the station; a port in MAINTENANCE is blocked on
    -- every day, which is what AvailabilityEngine reports as the reason.
    insert into public.availability_schedules (id, "stationId", "portId", "dayOfWeek",
        "openMinutes", "closeMinutes", "isBlocked", "blockReason", "updatedAtMs")
    select p.id || '_d' || d.dow,
           p."stationId",
           p.id,
           d.dow,
           case p."stationId"
               when 'st_apt_1'          then 0
               when 'st_highway_1'      then 0
               when 'st_roadside_1'     then 360
               when 'st_cafe_1'         then 540
               when 'st_office_pending' then 540
               else 480
           end,
           case p."stationId"
               when 'st_apt_1'          then 1440
               when 'st_highway_1'      then 1440
               when 'st_roadside_1'     then 1320
               when 'st_cafe_1'         then 1260
               when 'st_office_pending' then 1080
               else 1200
           end,
           (p.status = 'MAINTENANCE'),
           case when p.status = 'MAINTENANCE' then 'Port under maintenance' else null end,
           (extract(epoch from now()) * 1000)::bigint
    from public.charger_ports p
    cross join generate_series(1, 7) as d(dow)
    on conflict (id) do nothing;

    -- ------------------------------------------------------- completed demo 1
    insert into public.reservations (id, "consumerId", "stationId", "stationName", "portId",
        "portLabel", "bookingCode", "startTimeMs", "endTimeMs", status, "pricePerKwh",
        "serviceFee", "gatewayFee", "cancellationFee", "isPaid", "paymentId",
        "checkedInAtMs", "cancelledAtMs", "createdAtMs")
    values
        ('res_demo_done', 'user_student_1', 'st_campus_1', 'ABC College Campus Charger',
         'port_campus_2', 'Port 2 · CCS_2_DC · 30 kW DC', 'AMP-1001',
         three_days_ago, three_days_ago + (90::bigint * 60 * 1000), 'COMPLETED',
         18.0, 5.0, 4.0, 0.0, true, 'pay_demo_1',
         three_days_ago, 0, three_days_ago - (2::bigint * 60 * 60 * 1000)),
        ('res_demo_done_2', 'user_student_1', 'st_apt_1', 'GreenPark Shared Charger',
         'port_apt_1', 'Port 1 · TYPE_2_AC · 7 kW AC', 'AMP-1002',
         yesterday, yesterday + (120::bigint * 60 * 1000), 'COMPLETED',
         16.0, 5.0, 4.0, 0.0, true, 'pay_demo_2',
         yesterday, 0, yesterday - (60::bigint * 60 * 1000))
    on conflict (id) do nothing;

    insert into public.charging_sessions (id, "reservationId", "consumerId", "hostId",
        "stationId", "stationName", "portId", "startTimeMs", "endTimeMs", "startingMeterKwh",
        "endingMeterKwh", "verifiedKwhConsumed", "energyCharge", "serviceFee", "gatewayFee",
        "totalPaid", status, "meterReadingId", "invoiceId", "isMeterSimulated", "verificationLevel")
    values
        ('sess_demo_1', 'res_demo_done', 'user_student_1', 'user_campus_admin', 'st_campus_1',
         'ABC College Campus Charger', 'port_campus_2',
         three_days_ago, three_days_ago + (90::bigint * 60 * 1000),
         125.40, 137.90, 12.50, 180.0, 5.0, 4.0, 189.0, 'COMPLETED',
         'mr_demo_1', 'inv_demo_1', true, 'LEVEL_1'),
        ('sess_demo_2', 'res_demo_done_2', 'user_student_1', 'user_apt_association', 'st_apt_1',
         'GreenPark Shared Charger', 'port_apt_1',
         yesterday, yesterday + (120::bigint * 60 * 1000),
         240.10, 248.30, 8.20, 131.20, 5.0, 4.0, 140.20, 'COMPLETED',
         'mr_demo_2', 'inv_demo_2', true, 'LEVEL_1')
    on conflict (id) do nothing;

    insert into public.meter_readings (id, "sessionId", "stationId", "startMeterKwh",
        "endMeterKwh", "photoEvidenceUri", "verificationLevel", "timestampMs", "isSimulated")
    values
        ('mr_demo_1', 'sess_demo_1', 'st_campus_1', 125.40, 137.90,
         'content://amperly.demo/meter_photo_1.jpg', 'LEVEL_1',
         three_days_ago + (90::bigint * 60 * 1000), true),
        ('mr_demo_2', 'sess_demo_2', 'st_apt_1', 240.10, 248.30,
         'content://amperly.demo/meter_photo_2.jpg', 'LEVEL_1',
         yesterday + (120::bigint * 60 * 1000), true)
    on conflict (id) do nothing;

    insert into public.invoices (id, "sessionId", "consumerId", "hostId", "stationName",
        "verifiedKwh", "energyCharge", "serviceFee", "gatewayFee", adjustment, "adjustmentNote",
        "finalTotal", "paymentId", status, "issuedAtMs")
    values
        ('inv_demo_1', 'sess_demo_1', 'user_student_1', 'user_campus_admin',
         'ABC College Campus Charger', 12.50, 180.0, 5.0, 4.0, 0.0, '', 189.0,
         'pay_demo_1', 'PAID', three_days_ago + (90::bigint * 60 * 1000)),
        ('inv_demo_2', 'sess_demo_2', 'user_student_1', 'user_apt_association',
         'GreenPark Shared Charger', 8.20, 131.20, 5.0, 4.0, 0.0, '', 140.20,
         'pay_demo_2', 'PAID', yesterday + (120::bigint * 60 * 1000))
    on conflict (id) do nothing;

    insert into public.payments (id, "reservationId", "sessionId", "consumerId", "hostId",
        "idempotencyKey", gateway, "gatewayReference", "authorizedAmount", "capturedAmount",
        "refundedAmount", "lateCancellationFee", status, "failureReason", "createdAtMs", "updatedAtMs")
    values
        ('pay_demo_1', 'res_demo_done', 'sess_demo_1', 'user_student_1', 'user_campus_admin',
         'seed-demo-1', 'SANDBOX', 'auth_seed_demo_1', 226.80, 189.0, 0.0, 0.0, 'CAPTURED', null,
         three_days_ago - (2::bigint * 60 * 60 * 1000), three_days_ago + (90::bigint * 60 * 1000)),
        ('pay_demo_2', 'res_demo_done_2', 'sess_demo_2', 'user_student_1', 'user_apt_association',
         'seed-demo-2', 'SANDBOX', 'auth_seed_demo_2', 168.24, 140.20, 0.0, 0.0, 'CAPTURED', null,
         yesterday, yesterday)
    on conflict (id) do nothing;

    -- ------------------------------------------ upcoming reservation + QR code
    insert into public.reservations (id, "consumerId", "stationId", "stationName", "portId",
        "portLabel", "bookingCode", "startTimeMs", "endTimeMs", status, "pricePerKwh",
        "serviceFee", "gatewayFee", "cancellationFee", "isPaid", "paymentId",
        "checkedInAtMs", "cancelledAtMs", "createdAtMs")
    values
        ('res_demo_pending', 'user_student_1', 'st_campus_1', 'ABC College Campus Charger',
         'port_campus_3', 'Port 3 · CCS_2_DC · 30 kW DC', 'AMP-2002',
         slot_start, slot_end, 'RESERVED', 18.0, 5.0, 4.0, 0.0, true, 'pay_demo_3',
         0, 0, (extract(epoch from now()) * 1000)::bigint - (30::bigint * 60 * 1000))
    on conflict (id) do nothing;

    insert into public.payments (id, "reservationId", "sessionId", "consumerId", "hostId",
        "idempotencyKey", gateway, "gatewayReference", "authorizedAmount", "capturedAmount",
        "refundedAmount", "lateCancellationFee", status, "failureReason", "createdAtMs", "updatedAtMs")
    values
        ('pay_demo_3', 'res_demo_pending', null, 'user_student_1', 'user_campus_admin',
         'seed-demo-3', 'SANDBOX', 'auth_seed_demo_3', 226.80, 0.0, 0.0, 0.0, 'AUTHORIZED', null,
         (extract(epoch from now()) * 1000)::bigint, (extract(epoch from now()) * 1000)::bigint)
    on conflict (id) do nothing;

    -- --------------------------------------------------------------- dispute
    insert into public.disputes (id, "sessionId", "consumerId", "consumerName", "hostId",
        "stationId", "stationName", reason, "consumerNotes", "hostNotes", evidence, status,
        "enforcementAction", "refundAmount", "resolutionNote", "isPayoutFrozen",
        "createdAtMs", "resolvedAtMs")
    values
        ('dsp_demo_1', 'sess_demo_1', 'user_student_1', 'Aarav Student', 'user_campus_admin',
         'st_campus_1', 'ABC College Campus Charger', 'Incorrect meter reading',
         'I was charged for 12.5 kWh but the charger display showed 9.8 kWh.',
         'The meter is a campus submeter; the reading was recorded at the start and end of the slot.',
         'mr_demo_1', 'AWAITING_HOST', 'NONE', 0.0, '', true,
         (extract(epoch from now()) * 1000)::bigint - (2::bigint * 60 * 60 * 1000), 0)
    on conflict (id) do nothing;

    -- ---------------------------------------------------------------- report
    insert into public.reports (id, "reporterId", "reporterName", "targetStationId",
        "targetHostId", "sessionId", "reportType", description, status, "createdAtMs")
    values
        ('rpt_demo_1', 'user_student_1', 'Aarav Student', 'st_cafe_1', 'user_apt_association',
         null, 'Host requested off-platform payment',
         'The cafe asked me to pay cash instead of using the app.', 'OPEN',
         (extract(epoch from now()) * 1000)::bigint - (5::bigint * 60 * 60 * 1000))
    on conflict (id) do nothing;

    -- ------------------------------------------------------------------ chat
    -- Masked numbers and the off-platform flag are exactly what
    -- ChatManager.createMessage() produces, so the thread looks the same in
    -- either data source.
    insert into public.communication_threads (id, "reservationId", "consumerId", "consumerName",
        "hostId", "hostName", "maskedConsumerNumber", "maskedHostNumber", "isReported",
        "isBlocked", "isChatEnabled", "createdAtMs", "expiresAtMs")
    values
        ('thread_demo_1', 'res_demo_pending', 'user_student_1', 'Aarav Student',
         'user_campus_admin', 'ABC College Administration', '+91 **** **11', '+91 **** **10',
         false, false, true,
         (extract(epoch from now()) * 1000)::bigint - (30::bigint * 60 * 1000), thread_expiry)
    on conflict (id) do nothing;

    insert into public.chat_messages (id, "threadId", "reservationId", "senderId", "receiverId",
        "messageText", "createdAtMs", "isRead", "isReported", "expiresAtMs")
    values
        ('cm_demo_1', 'thread_demo_1', 'res_demo_pending', 'user_student_1', 'user_campus_admin',
         'Where is the charging entrance?',
         (extract(epoch from now()) * 1000)::bigint - (29::bigint * 60 * 1000), false, false, 0),
        ('cm_demo_2', 'thread_demo_1', 'res_demo_pending', 'user_campus_admin', 'user_student_1',
         'Gate 2, second level of the parking block. Call me on +91 **** **10',
         (extract(epoch from now()) * 1000)::bigint - (28::bigint * 60 * 1000), false, false, 0),
        ('cm_demo_3', 'thread_demo_1', 'res_demo_pending', 'user_campus_admin', 'user_student_1',
         E'You can pay cash at the counter if you prefer.\n[Amperly: off-platform payment requests are flagged. Paying outside the app removes your dispute protection.]',
         (extract(epoch from now()) * 1000)::bigint - (27::bigint * 60 * 1000), false, false, 0)
    on conflict (id) do nothing;

    -- ---------------------------------------------------------------- safety
    insert into public.trusted_contacts (id, "userId", name, phone)
    values ('tc_demo_1', 'user_student_1', 'Ananya (sister)', '+919812345678')
    on conflict (id) do nothing;

    insert into public.safety_events (id, "consumerId", "stationId", "emergencyType",
        "isLiveLocationSharingActive", "trustedContactPhone", "expiresAtMs", "createdAtMs")
    values ('se_demo_1', 'user_student_1', 'st_campus_1', 'SOS', false, '+919812345678',
        (extract(epoch from now()) * 1000)::bigint - (60::bigint * 60 * 1000),
        (extract(epoch from now()) * 1000)::bigint - (2::bigint * 60 * 60 * 1000))
    on conflict (id) do nothing;

    -- --------------------------------------------------------- notifications
    insert into public.notifications (id, "userId", title, message, "isRead", "createdAtMs")
    values
        ('ntf_demo_1', 'user_student_1', 'Reservation confirmed',
         'AMP-2002 at ABC College Campus Charger is confirmed. Show the booking code at Gate 2.',
         false, (extract(epoch from now()) * 1000)::bigint - (30::bigint * 60 * 1000)),
        ('ntf_demo_2', 'user_student_1', 'Receipt available',
         'Your session at ABC College Campus Charger was billed on verified meter readings.',
         true, three_days_ago + (95::bigint * 60 * 1000)),
        ('ntf_demo_3', 'user_campus_admin', 'New reservation',
         'Aarav Student reserved Port 3 for a 90-minute slot.',
         false, (extract(epoch from now()) * 1000)::bigint - (30::bigint * 60 * 1000))
    on conflict (id) do nothing;

    -- ------------------------------------------- completed route plan + stops
    insert into public.route_plans (id, "consumerId", "vehicleId", origin, destination,
        "originLat", "originLng", "destinationLat", "destinationLng", "corridorKm", preference,
        "totalDistanceKm", "driveMinutes", "detourMinutes", "chargingMinutes",
        "totalJourneyMinutes", "plannedStartSoc", "arrivalSocTarget", "isSocAuthoritative",
        assumptions, confidence, status, "createdAtMs", "updatedAtMs")
    values
        ('plan_demo_1', 'user_student_1', 'veh_101', 'Mysore', 'Mangalore',
         12.2958, 76.6394, 12.9141, 74.8560, 10.0, 'FASTEST',
         140.0, 186, 8, 18, 212, 100, 20, true,
         '140 km at an average 45 km/h; 6.2 km/kWh after the health adjustment; 80% of the lower of vehicle and charger peak power for charging time.',
         'MEDIUM', 'COMPLETED',
         three_days_ago, yesterday)
    on conflict (id) do nothing;

    insert into public.route_stops (id, "planId", "sequence", "stationId", "stationName",
        "isBackup", "isReachable", "distanceAlongRouteKm", "detourKm", "detourMinutes",
        "arrivalSoc", "departureSoc", "energyAddedKwh", "chargingMinutes", "estimatedCost",
        "recommendationReason", "reservationId")
    values
        ('rs_demo_1', 'plan_demo_1', 1, 'st_highway_1', 'Highway Hub Charger',
         false, true, 68.0, 6.4, 9, 29, 59, 12.1, 30, 259.6,
         E'Detour: +6.4 km / +9 min\nEstimated charge: 30 min\nEstimated energy added: 12.1 kWh\nExpected battery on arrival: 29%\nExpected battery after charging: 59%\nExpected battery at destination: 34%',
         null),
        ('rs_demo_2', 'plan_demo_1', 2, 'st_roadside_1', 'Kodagu Roadside AC Charger',
         true, true, 72.0, 2.0, 4, 27, 45, 7.2, 75, 118.0,
         'Backup: slower AC charger if the fast port is occupied.', null)
    on conflict (id) do nothing;

    insert into public.trip_estimates (id, "planId", "vehicleId", "segmentDistanceKm",
        "estimatedDrainKwh", "actualDrainKwh", "estimatedArrivalSoc", "actualArrivalSoc",
        confidence, notes, "createdAtMs")
    values
        ('te_demo_1', 'plan_demo_1', 'veh_101', 140.0, 26.5, 28.4, 22, 20, 'MEDIUM',
         'Reported by the consumer after the trip: actual battery fell slightly faster than planned.',
         yesterday)
    on conflict (id) do nothing;

    insert into public.android_auto_sessions (id, "userId", "vehicleId", "planId",
        "lastTemplateShown", "lastSocUpdatePercent", "connectedAtMs", "lastSocUpdateAtMs",
        "disconnectedAtMs")
    values
        ('aas_demo_1', 'user_student_1', 'veh_101', 'plan_demo_1', 'ROUTE_OVERVIEW', 25,
         three_days_ago, three_days_ago + (60::bigint * 60 * 1000),
         three_days_ago + (3::bigint * 60 * 60 * 1000))
    on conflict (id) do nothing;

    insert into public.battery_updates (id, "vehicleId", "tripPlanId", "estimatedSoc",
        "actualDashboardSoc", latitude, longitude, "timestampMs")
    values
        ('bu_demo_1', 'veh_101', 'plan_demo_1', 35, 25, 12.45, 76.05,
         three_days_ago + (60::bigint * 60 * 1000))
    on conflict (id) do nothing;

    raise notice 'Amperly pilot data seeded.';
end $$;

-- ============================================================================
--  Verify
-- ============================================================================
--   select (select count(*) from public.charger_stations) as stations,
--          (select count(*) from public.charger_ports)    as ports,
--          (select count(*) from public.availability_schedules) as schedules,
--          (select count(*) from public.users)            as users;
--   Expect: 6 stations, 12 ports, 84 schedules, 4 users.
-- ============================================================================
