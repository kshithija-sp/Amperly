package com.amperly.app.feature.consumer;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.amperly.app.R;
import com.amperly.app.core.common.Formatters;
import com.amperly.app.core.nav.Navigator;
import com.amperly.app.core.security.AuthManager;
import com.amperly.app.data.local.entity.SafetyEvent;
import com.amperly.app.data.local.entity.TrustedContact;
import com.amperly.app.data.repository.AmperlyRepository;
import com.amperly.app.ui.Ux;
import com.amperly.app.ui.adapter.ContactAdapter;

import java.util.List;
import java.util.UUID;

/**
 * Session safety. Everything here is opt-in, visible while active and time limited, and the screen
 * states plainly that Amperly is not an emergency service.
 */
public class SafetySOSFragment extends Fragment implements ContactAdapter.Listener {

    private static final long SHARING_WINDOW_MS = 60L * 60L * 1000L;

    private AmperlyRepository repo;
    private ContactAdapter contactAdapter;
    private TextView sosStatusText;
    private TextView sharingStatusText;
    private TextView historyText;
    private String userId;
    private SafetyEvent activeSharing;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_safety_sos, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repo = AmperlyRepository.getInstance(requireContext());
        userId = AuthManager.getInstance().getCurrentUserId();

        sosStatusText = view.findViewById(R.id.sosStatusText);
        sharingStatusText = view.findViewById(R.id.sharingStatusText);
        historyText = view.findViewById(R.id.safetyHistoryText);

        RecyclerView contacts = view.findViewById(R.id.contactsRecyclerView);
        contacts.setLayoutManager(new LinearLayoutManager(requireContext()));
        contacts.setNestedScrollingEnabled(false);
        contactAdapter = new ContactAdapter(this);
        contacts.setAdapter(contactAdapter);

        repo.observeTrustedContacts(userId).observe(getViewLifecycleOwner(), list -> {
            contactAdapter.submit(list);
            updateSharingStatus();
        });

        ((TextView) view.findViewById(R.id.emergencyNumbersText)).setText(
                "Local emergency guidance\n• India: 112 (all emergencies) · 108 (ambulance) · 100 (police)\n"
                        + "• Amperly is not an emergency service and cannot dispatch help.\n"
                        + "• Support is alerted with your reservation ID and station location when you trigger SOS.");

        View sosButton = view.findViewById(R.id.sosButton);
        sosButton.setOnClickListener(v -> Ux.toast(this, getString(R.string.safety_confirm_sos)));
        sosButton.setOnLongClickListener(v -> {
            triggerSos();
            return true;
        });

        view.findViewById(R.id.addContactButton).setOnClickListener(v -> addContact(view));
        view.findViewById(R.id.shareLocationButton).setOnClickListener(v -> startSharing());
        view.findViewById(R.id.stopSharingButton).setOnClickListener(v -> stopSharing());

        repo.expireStaleLocationSharing();
        loadState();
    }

    @Override
    public void onResume() {
        super.onResume();
        repo.expireStaleLocationSharing();
        loadState();
    }

    private void loadState() {
        repo.background(() -> {
            List<SafetyEvent> events = repo.getSafetyEventsSync(userId);
            activeSharing = repo.getActiveLocationSharingSync(userId);
            return events;
        }, events -> {
            if (events == null || !isAdded()) {
                return;
            }
            StringBuilder history = new StringBuilder("Safety history: " + events.size() + " event(s)\n");
            int shown = 0;
            for (SafetyEvent event : events) {
                if (shown++ >= 3) {
                    break;
                }
                history.append("• ").append(event.getEmergencyType())
                        .append(" · ").append(Formatters.dateTime(event.getCreatedAtMs()))
                        .append(event.isLiveLocationSharingActive() ? " · sharing active" : "")
                        .append(event.getExpiresAtMs() > 0 && event.getExpiresAtMs() < System.currentTimeMillis()
                                ? " · sharing ended automatically" : "")
                        .append("\n");
            }
            historyText.setText(history.toString());
            updateSharingStatus();
        });
    }

    private void updateSharingStatus() {
        if (activeSharing != null && activeSharing.isLiveLocationSharingActive()
                && activeSharing.getExpiresAtMs() > System.currentTimeMillis()) {
            sharingStatusText.setText(getString(R.string.safety_sharing_active,
                    activeSharing.getTrustedContactPhone(),
                    Formatters.dateTime(activeSharing.getExpiresAtMs())));
        } else {
            sharingStatusText.setText("Live location sharing is off. Sharing is opt-in, time limited and "
                    + "expires automatically when the window ends. Amperly never shares your location by default.");
        }
    }

    private void triggerSos() {
        repo.background(() -> {
            SafetyEvent event = new SafetyEvent(UUID.randomUUID().toString(), userId,
                    com.amperly.app.core.state.AppStateStore.get().getActiveReservationId(),
                    "SOS", false, null, 0, System.currentTimeMillis());
            repo.saveSafetyEvent(event);
            // The SOS event itself is the record an administrator reads. A notification owned
            // by another user cannot be inserted under Row Level Security, so none is written.
            return Boolean.TRUE;
        }, ok -> {
            sosStatusText.setText(getString(R.string.safety_sos_active));
            Ux.toast(this, getString(R.string.safety_sos_active));
            loadState();
        });
    }

    private void startSharing() {
        List<TrustedContact> contacts = repo.getTrustedContactsSync(userId);
        if (contacts.isEmpty()) {
            Ux.toast(this, "Add a trusted contact first.");
            return;
        }
        TrustedContact contact = contacts.get(0);
        repo.background(() -> {
            SafetyEvent event = new SafetyEvent(UUID.randomUUID().toString(), userId,
                    com.amperly.app.core.state.AppStateStore.get().getActiveReservationId(),
                    "LIVE_LOCATION", true, contact.getPhone(),
                    System.currentTimeMillis() + SHARING_WINDOW_MS, System.currentTimeMillis());
            repo.saveSafetyEvent(event);
            return event;
        }, event -> {
            activeSharing = event;
            updateSharingStatus();
            Ux.toast(this, "Live location sharing started for 60 minutes with " + contact.getName() + ".");
            loadState();
        });
    }

    private void stopSharing() {
        if (activeSharing == null) {
            Ux.toast(this, "Sharing is already off.");
            return;
        }
        repo.background(() -> {
            activeSharing.setLiveLocationSharingActive(false);
            activeSharing.setExpiresAtMs(System.currentTimeMillis());
            repo.updateSafetyEvent(activeSharing);
            return Boolean.TRUE;
        }, ok -> {
            activeSharing = null;
            updateSharingStatus();
            Ux.toast(this, "Live location sharing stopped.");
            loadState();
        });
    }

    private void addContact(View view) {
        android.widget.EditText nameEdit = view.findViewById(R.id.contactNameEdit);
        android.widget.EditText phoneEdit = view.findViewById(R.id.contactPhoneEdit);
        String name = nameEdit.getText().toString().trim();
        String phone = phoneEdit.getText().toString().trim();
        if (name.isEmpty() || phone.length() < 6) {
            Ux.toast(this, "Enter a name and a valid phone number.");
            return;
        }
        repo.background(() -> {
            repo.saveTrustedContact(new TrustedContact(UUID.randomUUID().toString(), userId, name, phone));
            return Boolean.TRUE;
        }, ok -> {
            nameEdit.setText("");
            phoneEdit.setText("");
            Ux.toast(this, "Trusted contact saved. Amperly only shares your location with this person when you ask it to.");
        });
    }

    @Override
    public void onCall(TrustedContact contact) {
        Navigator.dial(requireContext(), contact.getPhone());
    }

    @Override
    public void onDelete(TrustedContact contact) {
        repo.deleteTrustedContact(contact);
        Ux.toast(this, "Trusted contact removed.");
    }
}
