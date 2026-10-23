package com.amperly.app;

import com.amperly.app.communication.ChatManager;
import com.amperly.app.core.common.AppConstants;
import com.amperly.app.data.local.entity.ChatMessage;
import com.amperly.app.data.local.entity.CommunicationThread;
import com.amperly.app.data.local.entity.Reservation;
import com.amperly.app.data.local.entity.User;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChatManagerTest {

    private Reservation reservation() {
        long now = System.currentTimeMillis();
        return new Reservation("res_1", "cons_1", "st_1", "Campus Charger", "port_1",
                "Port 1 · CCS_2_DC", "AMP-1234", now, now + 5_400_000L,
                AppConstants.STATUS_RESERVED, 18.0, 5.0, 4.0, 0.0, true, "pay_1", 0, 0, now);
    }

    private User consumer() {
        return new User("cons_1", "Aarav", "aarav@example.com", "+919876543211",
                AppConstants.ROLE_CONSUMER, null, "VERIFIED", false);
    }

    private User host() {
        return new User("host_1", "Campus Admin", "host@example.com", "+919876543210",
                AppConstants.ROLE_HOST, "ABC College", "VERIFIED", false);
    }

    @Test
    public void phoneNumbersAreMasked() {
        String masked = ChatManager.maskPhoneNumber("+919876543211");
        assertTrue(masked.contains("••"));
        assertFalse(masked.contains("9876543211"));
    }

    @Test
    public void typedNumbersAreReplacedBeforeStorage() {
        String sanitized = ChatManager.sanitizeText("Call me on +919876543211 any time",
                "+919876543211", "+919876543210");
        assertFalse(sanitized.contains("9876543211"));
    }

    @Test
    public void bareDigitRunsAreMaskedToo() {
        String sanitized = ChatManager.sanitizeText("My number is 9876543211", null, null);
        assertTrue(sanitized.contains("masked"));
    }

    @Test
    public void offPlatformPaymentRequestsAreFlagged() {
        assertTrue(ChatManager.containsOffPlatformPaymentRequest("Please pay cash at the counter"));
        assertTrue(ChatManager.containsOffPlatformPaymentRequest("Transfer account details?"));
        assertFalse(ChatManager.containsOffPlatformPaymentRequest("Where is the entrance?"));

        ChatMessage message = ChatManager.createMessage("thread_1", "res_1", "host_1", "cons_1",
                "You can pay cash", "+919876543210", "+919876543211");
        assertTrue(message.getMessageText().contains("off-platform payment requests are flagged"));
    }

    @Test
    public void threadExpiresAfterTheReservationWindow() {
        CommunicationThread thread = ChatManager.createThread(reservation(), consumer(), host());
        assertTrue(thread.getMaskedConsumerNumber().contains("••"));
        assertTrue(thread.isChatEnabled());
        assertTrue(ChatManager.isThreadOpen(thread, thread.getCreatedAtMs()));
        assertTrue(ChatManager.isThreadOpen(thread, System.currentTimeMillis() + 60_000L));
        assertFalse(ChatManager.isThreadOpen(thread,
                reservation().getEndTimeMs() + ChatManager.THREAD_LIFETIME_AFTER_SLOT_MS + 1000L));
    }

    @Test
    public void blockedThreadsAreClosedEvenBeforeExpiry() {
        CommunicationThread thread = ChatManager.createThread(reservation(), consumer(), host());
        thread.setBlocked(true);
        assertFalse(ChatManager.isThreadOpen(thread, System.currentTimeMillis() + 60_000L));
    }

    @Test
    public void quickQuestionsCoverTheCommonVisitQuestions() {
        assertTrue(ChatManager.QUICK_QUESTIONS.size() >= 4);
        assertTrue(ChatManager.QUICK_QUESTIONS.get(0).toLowerCase().contains("entrance"));
    }

    @Test
    public void messagesCarryTheReservationForAudit() {
        ChatMessage message = ChatManager.createMessage("thread_1", "res_1", "cons_1", "host_1",
                "Where is the charging entrance?", "+919876543211", "+919876543210");
        assertEquals("res_1", message.getReservationId());
        assertEquals("thread_1", message.getThreadId());
        assertTrue(message.getCreatedAtMs() > 0);
    }
}
