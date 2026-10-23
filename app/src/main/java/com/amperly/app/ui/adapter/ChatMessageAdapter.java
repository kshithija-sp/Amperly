package com.amperly.app.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.amperly.app.R;
import com.amperly.app.communication.ChatManager;
import com.amperly.app.core.common.Formatters;
import com.amperly.app.data.local.entity.ChatMessage;

import java.util.ArrayList;
import java.util.List;

public class ChatMessageAdapter extends RecyclerView.Adapter<ChatMessageAdapter.MessageViewHolder> {

    private final List<ChatMessage> items = new ArrayList<>();
    private final String currentUserId;

    public ChatMessageAdapter(String currentUserId) {
        this.currentUserId = currentUserId;
    }

    public void submit(List<ChatMessage> messages) {
        items.clear();
        if (messages != null) {
            items.addAll(messages);
        }
        notifyDataSetChanged();
    }

    public int getItemCountSafe() {
        return items.size();
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_message, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class MessageViewHolder extends RecyclerView.ViewHolder {
        private final LinearLayout root;
        private final View card;
        private final TextView meta;
        private final TextView text;
        private final TextView warning;

        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            root = itemView.findViewById(R.id.messageRoot);
            card = itemView.findViewById(R.id.messageCard);
            meta = itemView.findViewById(R.id.messageMetaText);
            text = itemView.findViewById(R.id.messageText);
            warning = itemView.findViewById(R.id.messageWarningText);
        }

        void bind(ChatMessage message) {
            boolean mine = currentUserId != null && currentUserId.equals(message.getSenderId());
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) card.getLayoutParams();
            params.gravity = mine ? android.view.Gravity.END : android.view.Gravity.START;
            card.setLayoutParams(params);
            card.setBackgroundTintList(ContextCompat.getColorStateList(itemView.getContext(),
                    mine ? R.color.amp_primary_container : R.color.amp_surface_variant));

            meta.setText(mine ? "You" : "Host · " + Formatters.dateTime(message.getCreatedAtMs()));
            text.setText(message.getMessageText());

            boolean flagged = ChatManager.containsOffPlatformPaymentRequest(message.getMessageText());
            warning.setVisibility(flagged ? View.VISIBLE : View.GONE);
            if (flagged) {
                warning.setText(R.string.chat_off_platform_warning);
            }
        }
    }
}
