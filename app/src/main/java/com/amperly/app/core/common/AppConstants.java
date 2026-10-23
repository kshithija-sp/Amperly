package com.amperly.app.core.common;

public class AppConstants {
    public static final String ROLE_CONSUMER = "CONSUMER";
    public static final String ROLE_HOST = "HOST";
    public static final String ROLE_ADMIN = "ADMIN";

    public static final double DEFAULT_SERVICE_FEE_INR = 5.0;
    public static final double DEFAULT_GATEWAY_FEE_INR = 4.0;
    public static final double DEFAULT_TARIFF_INR_PER_KWH = 18.0;
    public static final double LATE_CANCELLATION_FEE_INR = 20.0;

    public static final int DEFAULT_MIN_RESERVE_SOC_PERCENT = 15;
    public static final int DEFAULT_MAX_TARGET_SOC_PERCENT = 80;

    public static final String LEVEL_1_VERIFIED_MANUAL = "LEVEL_1";
    public static final String LEVEL_2_SIMULATED_SMART_METER = "LEVEL_2";
    public static final String LEVEL_3_NO_VERIFIED_METER = "LEVEL_3";

    public static final String LOCATION_TYPE_CAMPUS = "CAMPUS";
    public static final String LOCATION_TYPE_APARTMENT = "APARTMENT";
    public static final String LOCATION_TYPE_OFFICE = "OFFICE";
    public static final String LOCATION_TYPE_CAFE = "CAFE";
    public static final String LOCATION_TYPE_HOTEL = "HOTEL";
    public static final String LOCATION_TYPE_PARKING = "PARKING";
    public static final String LOCATION_TYPE_PETROL_PUMP = "PETROL_PUMP";
    public static final String LOCATION_TYPE_COMMERCIAL = "COMMERCIAL";

    // Port status
    public static final String PORT_AVAILABLE = "AVAILABLE";
    public static final String PORT_OCCUPIED = "OCCUPIED";
    public static final String PORT_MAINTENANCE = "MAINTENANCE";

    // Reservation / session status
    public static final String STATUS_RESERVED = "RESERVED";
    public static final String STATUS_CHECKED_IN = "CHECKED_IN";
    public static final String STATUS_CHARGING = "CHARGING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String STATUS_DISPUTED = "DISPUTED";
    public static final String STATUS_REFUNDED = "REFUNDED";

    // Approval
    public static final String APPROVAL_PENDING = "PENDING";
    public static final String APPROVAL_APPROVED = "APPROVED";
    public static final String APPROVAL_REJECTED = "REJECTED";

    // Enforcement
    public static final String ENFORCEMENT_NONE = "NONE";
    public static final String ENFORCEMENT_WARNING = "WARNING";
    public static final String ENFORCEMENT_FINE = "FINE";
    public static final String ENFORCEMENT_SUSPENSION = "SUSPENSION";
    public static final String ENFORCEMENT_BAN = "BAN";

    // Payment
    public static final String PAYMENT_AUTHORIZED = "AUTHORIZED";
    public static final String PAYMENT_CAPTURED = "CAPTURED";
    public static final String PAYMENT_FAILED = "FAILED";
    public static final String PAYMENT_REFUNDED = "REFUNDED";
    public static final String PAYMENT_RELEASED = "RELEASED";

    /**
     * The first deployment's pilot area (one controlled campus plus one apartment community).
     *
     * <p>Only the name survives: there is deliberately no pilot centre coordinate to fall back to,
     * because silently using a fixed point as the user's location is forbidden. The driver's real
     * position comes from {@code LocationProvider} and, when there is none, the screen says so.
     */
    public static final String PILOT_NAME = "Mysore pilot";

    public static final double DEFAULT_CORRIDOR_KM = 10.0;
}
