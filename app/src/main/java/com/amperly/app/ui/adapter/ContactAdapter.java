package com.amperly.app.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.amperly.app.R;
import com.amperly.app.data.local.entity.TrustedContact;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class ContactAdapter extends RecyclerView.Adapter<ContactAdapter.ContactViewHolder> {

    public interface Listener {
        void onCall(TrustedContact contact);
        void onDelete(TrustedContact contact);
    }

    private final List<TrustedContact> items = new ArrayList<>();
    private final Listener listener;

    public ContactAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<TrustedContact> contacts) {
        items.clear();
        if (contacts != null) {
            items.addAll(contacts);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_contact, parent, false);
        return new ContactViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ContactViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class ContactViewHolder extends RecyclerView.ViewHolder {
        private final TextView name;
        private final TextView phone;
        private final MaterialButton call;
        private final MaterialButton delete;

        ContactViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.contactNameText);
            phone = itemView.findViewById(R.id.contactPhoneText);
            call = itemView.findViewById(R.id.callContactButton);
            delete = itemView.findViewById(R.id.deleteContactButton);
        }

        void bind(TrustedContact contact) {
            name.setText(contact.getName());
            phone.setText(contact.getPhone());
            call.setOnClickListener(v -> {
                if (listener != null) listener.onCall(contact);
            });
            delete.setOnClickListener(v -> {
                if (listener != null) listener.onDelete(contact);
            });
        }
    }
}
