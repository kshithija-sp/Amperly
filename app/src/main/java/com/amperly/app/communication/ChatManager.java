package com.amperly.app.communication;

import com.amperly.app.data.local.entity.ChatMessage;
import com.amperly.app.data.local.entity.CommunicationThread;
import com.amperly.app.data.local.entity.Reservation;
import com.amperly.app.data.local.entity.User;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Reservation-scoped communication.
 *
 * Personal phone numbers are masked, chats are tied to a reservation, the conversation expires
 * automatically after the reservation/dispute window, and off-platform payment requests are flagged.
 * Free-text chat is a phone-only feature — it is never offered on the car display.
 */
public class ChatManager {

    /** One week after the reservation ends, the thread closes on its own. */
    public static final long THREAD_LIFETIME_AFTER_SLOT_MS = 7L * 24L * 60L * 60L * 1000L;

    public static final List<String> QUICK_QUESTIONS = Arrays.asList(
            "Where is the charging entrance?",
            "The port looks occupied, can you help?",
            "Can I extend my slot by 15 minutes?",
            "Can you confirm the meter readings?"
    );

    private ChatManager() {
    }

    public static String maskPhoneNumber(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return "••••";
        }
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() < 4) {
            return "••••";
        }
        String last2 = digits.substring(digits.length() - 2);
        return "+91 •••• ••" + last2;
    }

    /** Replaces any real phone number the user typed with the masked form before storing. */
    public static String sanitizeText(String text, String ownPhone, String otherPhone) {
        if (text == null) {
            return "";
        }
        String sanitized = text;
        if (ownPhone != null && !ownPhone.trim().isEmpty()) {
            sanitized = sanitized.replace(ownPhone.trim(), maskPhoneNumber(ownPhone));
        }
        if (otherPhone != null && !otherPhone.trim().isEmpty()) {
            sanitized = sanitized.replace(otherPhone.trim(), maskPhoneNumber(otherPhone));
        }
        // Generic 10+ digit runs are masked too.
        return sanitized.replaceAll("(?<!\\d)(\\d[\\d\\s-]{8,}\\d)(?!\\d)",
                "•••• masked ••••");
    }

    public static boolean containsOffPlatformPaymentRequest(String text) {
        if (text == null) {
            return false;
        }
        String lower = text.toLowerCase();
        return lower.contains("pay cash") || lower.contains("direct gpay") || lower.contains("off platform")
                || lower.contains("transfer account") || lower.contains("paytm me directly")
                || lower.contains("upi to my") || lower.contains("outside the app");
    }

    public static CommunicationThread createThread(Reservation reservation, User consumer, User host) {
        long now = System.currentTimeMillis();
        long expiry = (reservation != null ? reservation.getEndTimeMs() : now) + THREAD_LIFETIME_AFTER_SLOT_MS;
        return new CommunicationThread(UUID.randomUUID().toString(),
                reservation == null ? null : reservation.getId(),
                consumer == null ? null : consumer.getId(),
                consumer == null ? "Consumer" : consumer.getName(),
                host == null ? null : host.getId(),
                host == null ? "Host" : host.getName(),
                maskPhoneNumber(consumer == null ? null : consumer.getPhone()),
                maskPhoneNumber(host == null ? null : host.getPhone()),
                false, false, true, now, expiry);
    }

    public static ChatMessage createMessage(String threadId, String reservationId, String senderId,
                                            String receiverId, String rawText, String ownPhone,
                                            String otherPhone) {
        boolean offPlatform = containsOffPlatformPaymentRequest(rawText);
        String body = sanitizeText(rawText, ownPhone, otherPhone);
        if (offPlatform) {
            body = body + "\n[Amperly: off-platform payment requests are flagged. Paying outside the app "
                    + "removes your dispute protection.]";
        }
        long now = System.currentTimeMillis();
        return new ChatMessage(UUID.randomUUID().toString(), threadId, reservationId, senderId,
                receiverId, body, now, false, false, 0);
    }

    public static boolean isThreadOpen(CommunicationThread thread, long nowMs) {
        return thread != null && !thread.isBlocked() && !thread.isExpired(nowMs);
    }

    public static String expiryLabel(long expiresAtMs) {
        return com.amperly.app.core.common.Formatters.dateTime(expiresAtMs);
    }
}
