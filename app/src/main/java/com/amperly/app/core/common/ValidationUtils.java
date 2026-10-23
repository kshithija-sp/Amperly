package com.amperly.app.core.common;

import android.text.TextUtils;

public class ValidationUtils {

    public static boolean isValidSoc(int soc) {
        return soc >= 0 && soc <= 100;
    }

    public static boolean isValidMeterReading(double startReading, double endReading) {
        return startReading >= 0 && endReading >= startReading;
    }

    public static boolean isValidEmail(String email) {
        return !TextUtils.isEmpty(email) && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    public static boolean isValidPhone(String phone) {
        return !TextUtils.isEmpty(phone) && phone.length() >= 10;
    }
}
