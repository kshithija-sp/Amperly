package com.amperly.app.feature.consumer;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.amperly.app.R;
import com.amperly.app.communication.ChatManager;
import com.amperly.app.core.common.AppConstants;
import com.amperly.app.core.nav.Navigator;
import com.amperly.app.core.security.AuthManager;
import com.amperly.app.core.state.AppStateStore;
import com.amperly.app.data.local.entity.ChatMessage;
import com.amperly.app.data.local.entity.CommunicationThread;
import com.amperly.app.data.local.entity.Reservation;
import com.amperly.app.data.local.entity.User;
import com.amperly.app.data.repository.AmperlyRepository;
import com.amperly.app.ui.Ux;
import com.amperly.app.ui.adapter.ChatMessageAdapter;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * Reservation-scoped communication. Free-text chat is deliberately phone-only: the car display never
 * offers it. Numbers are masked, threads expire, and off-platform payment requests are flagged.
 */
public class HostChatFragment extends Fragment {

    private AmperlyRepository repo;
    private ChatMessageAdapter messageAdapter;
    private EditText input;
    private TextView threadTitle;
    private TextView threadExpiry;
    private TextView emptyText;
    private ChipGroup threadsChipGroup;
    private CommunicationThread activeThread;
    private String userId;
    private boolean hostMode;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_host_chat, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repo = AmperlyRepository.getInstance(requireContext());
        userId = AuthManager.getInstance().getCurrentUserId();
        hostMode = AppConstants.ROLE_HOST.equals(AuthManager.getInstance().getCurrentRole());

        input = view.findViewById(R.id.chatInputEdit);
        threadTitle = view.findViewById(R.id.threadTitleText);
        threadExpiry = view.findViewById(R.id.threadExpiryText);
        emptyText = view.findViewById(R.id.chatEmptyText);
        threadsChipGroup = view.findViewById(R.id.threadsChipGroup);

        RecyclerView messages = view.findViewById(R.id.messagesRecyclerView);
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        layoutManager.setStackFromEnd(true);
        messages.setLayoutManager(layoutManager);
        messageAdapter = new ChatMessageAdapter(userId);
        messages.setAdapter(messageAdapter);

        ChipGroup quickQuestions = view.findViewById(R.id.quickQuestionsChipGroup);
        for (String question : ChatManager.QUICK_QUESTIONS) {
            Chip chip = new Chip(requireContext());
            chip.setText(question);
            chip.setCheckable(false);
            chip.setOnClickListener(v -> {
                input.setText(question);
                sendMessage();
            });
            quickQuestions.addView(chip);
        }

        view.findViewById(R.id.sendChatButton).setOnClickListener(v -> sendMessage());
        view.findViewById(R.id.reportThreadButton).setOnClickListener(v -> {
            if (activeThread == null) {
                Ux.toast(this, "Select a conversation first.");
                return;
            }
            repo.reportThread(activeThread.getId());
            Ux.toast(this, "Conversation reported. The admin team will review the messages and evidence.");
        });
        view.findViewById(R.id.blockThreadButton).setOnClickListener(v -> {
            if (activeThread == null) {
                Ux.toast(this, "Select a conversation first.");
                return;
            }
            repo.blockThread(activeThread.getId());
            Ux.toast(this, "This conversation is blocked. Contact support if the issue continues.");
        });

        if (hostMode) {
            repo.observeThreadsForHost(userId).observe(getViewLifecycleOwner(), this::bindThreads);
        } else {
            repo.observeThreadsForConsumer(userId).observe(getViewLifecycleOwner(), this::bindThreads);
        }
    }

    private void bindThreads(List<CommunicationThread> threads) {
        threadsChipGroup.removeAllViews();
        if (threads == null || threads.isEmpty()) {
            threadTitle.setText(hostMode ? "No host conversations yet" : "No conversations yet");
            threadExpiry.setText("Conversations appear here once a reservation exists. They close "
                    + "automatically after the reservation or dispute window.");
            emptyText.setVisibility(View.VISIBLE);
            return;
        }
        for (CommunicationThread thread : threads) {
            Chip chip = new Chip(requireContext());
            String label = hostMode ? thread.getConsumerName() : thread.getHostName();
            chip.setText(label == null ? "Conversation" : label);
            chip.setCheckable(true);
            chip.setChecked(!thread.isBlocked() && thread == firstThread(threads));
            chip.setOnClickListener(v -> selectThread(thread));
            threadsChipGroup.addView(chip);
        }
        selectThread(threads.get(0));
    }

    private CommunicationThread firstThread(List<CommunicationThread> threads) {
        return threads.isEmpty() ? null : threads.get(0);
    }

    private void selectThread(CommunicationThread thread) {
        activeThread = thread;
        User consumer = AuthManager.getInstance().getCurrentUser();
        threadTitle.setText((hostMode ? thread.getConsumerName() : thread.getHostName())
                + " · booking " + thread.getReservationId());
        boolean open = ChatManager.isThreadOpen(thread, System.currentTimeMillis());
        threadExpiry.setText("Masked numbers: consumer " + thread.getMaskedConsumerNumber()
                + " · host " + thread.getMaskedHostNumber()
                + "\nConversation " + (open ? "closes on " + ChatManager.expiryLabel(thread.getExpiresAtMs())
                : "has expired"));
        if (!open) {
            emptyText.setVisibility(View.VISIBLE);
            emptyText.setText(R.string.chat_expired);
        }

        repo.observeMessages(thread.getId()).observe(getViewLifecycleOwner(), messages -> {
            messageAdapter.submit(messages);
            emptyText.setVisibility(messages == null || messages.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    private void sendMessage() {
        String text = input.getText().toString().trim();
        if (TextUtils.isEmpty(text)) {
            return;
        }
        if (activeThread == null) {
            Ux.toast(this, "Select a conversation, or create a reservation first.");
            return;
        }
        if (!ChatManager.isThreadOpen(activeThread, System.currentTimeMillis())) {
            Ux.toast(this, getString(R.string.chat_expired));
            return;
        }

        User me = AuthManager.getInstance().getCurrentUser();
        String receiver = hostMode ? activeThread.getConsumerId() : activeThread.getHostId();
        String otherPhone = hostMode ? activeThread.getMaskedHostNumber() : activeThread.getMaskedConsumerNumber();

        ChatMessage message = ChatManager.createMessage(activeThread.getId(),
                activeThread.getReservationId(), me.getId(), receiver, text, me.getPhone(), otherPhone);
        repo.saveMessage(message);
        if (ChatManager.containsOffPlatformPaymentRequest(text)) {
            Ux.toast(this, getString(R.string.chat_off_platform_warning));
        }
        input.setText("");

        if (activeThread.getReservationId() != null) {
            repo.background(() -> {
                Reservation reservation = repo.getReservationSync(activeThread.getReservationId());
                return reservation == null ? null : reservation.getStationName();
            }, stationName -> {
                if (stationName != null) {
                    AppStateStore.get().setLastRecalculationSummary("");
                }
            });
        }
    }
}
