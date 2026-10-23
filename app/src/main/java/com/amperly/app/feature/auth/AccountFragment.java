package com.amperly.app.feature.auth;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.amperly.app.MainActivity;
import com.amperly.app.R;
import com.amperly.app.core.security.AuthManager;
import com.amperly.app.core.state.AppStateStore;
import com.amperly.app.data.local.entity.User;
import com.amperly.app.data.remote.SupabaseAuth;
import com.amperly.app.data.remote.SupabaseConfig;
import com.amperly.app.data.repository.AmperlyRepository;
import com.amperly.app.ui.Ux;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * The account page: who is signed in, what the sync engine is doing, and how to sign out.
 *
 * <p>It doubles as the honest status report for the backend: when the last pull happened and
 * why the last save did not reach the database — because "why is my data missing" is the
 * question this screen exists to answer.
 *
 * <p>The display name is the one profile field the account owns. It is edited here and saved
 * through {@link AmperlyRepository#updateDisplayName}, which writes Room and sends the change
 * to the server in the same call.
 */
public class AccountFragment extends Fragment {

    private AmperlyRepository repo;

    private LinearLayout detailContainer;
    private View avatarBadge;
    private TextView avatarText;
    private TextView nameText;
    private TextView emailText;
    private TextView authorityText;
    private TextView syncStatusText;
    private TextView demoNoteText;
    private TextView roleNoteText;
    private MaterialButton editNameButton;
    private MaterialButton accountSignInButton;
    private MaterialButton signOutButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_account, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repo = AmperlyRepository.getInstance(requireContext());

        detailContainer = view.findViewById(R.id.accountDetailContainer);
        avatarBadge = view.findViewById(R.id.accountAvatarBadge);
        avatarText = view.findViewById(R.id.accountAvatarText);
        nameText = view.findViewById(R.id.accountNameText);
        emailText = view.findViewById(R.id.accountEmailText);
        authorityText = view.findViewById(R.id.accountAuthorityText);
        syncStatusText = view.findViewById(R.id.accountSyncStatusText);
        demoNoteText = view.findViewById(R.id.accountDemoNoteText);
        roleNoteText = view.findViewById(R.id.accountRoleNoteText);
        editNameButton = view.findViewById(R.id.editNameButton);
        accountSignInButton = view.findViewById(R.id.accountSignInButton);
        signOutButton = view.findViewById(R.id.signOutButton);

        boolean signedIn = SupabaseAuth.isSignedIn();
        boolean configured = SupabaseConfig.isConfigured() && !SupabaseConfig.looksLikeSecretKey();

        signOutButton.setVisibility(signedIn ? View.VISIBLE : View.GONE);
        // There is no demo mode to explain any more; the note only appears when a session has
        // lapsed and the account page can no longer show real data.
        demoNoteText.setVisibility(signedIn ? View.GONE : View.VISIBLE);
        roleNoteText.setVisibility(signedIn ? View.VISIBLE : View.GONE);
        accountSignInButton.setVisibility(!signedIn && configured ? View.VISIBLE : View.GONE);

        render(AuthManager.getInstance().getCurrentUser(), signedIn);

        editNameButton.setOnClickListener(v -> showEditNameDialog());
        signOutButton.setOnClickListener(v -> confirmSignOut());
        accountSignInButton.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).showSignIn();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        render(AuthManager.getInstance().getCurrentUser(), SupabaseAuth.isSignedIn());
        loadSyncStatus();
    }

    /**
     * Reads the sync state off the main thread.
     *
     * <p>{@code statusSummary} reads the stored pull cursor and the last write error, which is
     * cheap but still not work for the main thread. The label keeps its previous text until the
     * answer arrives rather than flashing empty.
     */
    private void loadSyncStatus() {
        repo.background(() -> repo.syncStatus(), status -> {
            if (isAdded() && status != null) {
                syncStatusText.setText(status);
            }
        });
    }

    // ------------------------------------------------------------------ render

    private void render(@Nullable User user, boolean signedIn) {
        detailContainer.removeAllViews();

        if (user == null) {
            // No account, so no monogram to draw — an empty badge would just be a blank disc.
            avatarBadge.setVisibility(View.GONE);
            editNameButton.setVisibility(View.GONE);
            authorityText.setVisibility(View.GONE);
            nameText.setText(R.string.account_demo_mode);
            emailText.setText("");
            return;
        }

        avatarBadge.setVisibility(View.VISIBLE);
        editNameButton.setVisibility(signedIn ? View.VISIBLE : View.GONE);
        authorityText.setVisibility(View.VISIBLE);

        avatarText.setText(initialsOf(user.getName()));
        nameText.setText(displayNameOf(user));
        emailText.setText(TextUtils.isEmpty(user.getEmail())
                ? getString(R.string.account_email) + " —"
                : user.getEmail());
        authorityText.setText(AuthManager.authorityLabel(user));

        addRow(getString(R.string.account_email), user.getEmail());
        addRow(getString(R.string.account_role), AuthManager.authorityLabel(user));
        addRow(getString(R.string.account_organization), user.getOrganizationName());
        addRow(getString(R.string.account_verification), user.getVerificationStatus());
        addRow(getString(R.string.account_id), user.getId());
    }

    /**
     * A label above its value. Built in code rather than declared, so the rows stay identical
     * instead of drifting apart in XML.
     */
    private void addRow(String label, @Nullable String value) {
        Context context = requireContext();
        float density = getResources().getDisplayMetrics().density;

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = (int) (density * 12);
        row.setLayoutParams(rowParams);

        TextView labelView = new TextView(context);
        labelView.setText(label);
        labelView.setTextAppearance(R.style.TextAppearance_Amperly_Label);
        row.addView(labelView);

        TextView valueView = new TextView(context);
        valueView.setText(TextUtils.isEmpty(value) || value.trim().isEmpty() ? "—" : value);
        valueView.setTextAppearance(R.style.TextAppearance_Amperly_BodyStrong);
        valueView.setGravity(Gravity.START);
        row.addView(valueView);

        detailContainer.addView(row);
    }

    @NonNull
    private String displayNameOf(@Nullable User user) {
        if (user == null || TextUtils.isEmpty(user.getName())) {
            return getString(R.string.account_name);
        }
        return user.getName();
    }

    /**
     * Up to two initials from a display name, for the monogram badge.
     *
     * <p>Falls back to a single character for one-word names, and to a dot for a name that
     * has no letters at all, so the badge is never blank.
     */
    private static String initialsOf(@Nullable String name) {
        if (name == null || name.trim().isEmpty()) {
            return "\u2022";
        }
        String[] words = name.trim().split("\\s+");
        StringBuilder initials = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            initials.append(Character.toUpperCase(word.charAt(0)));
            if (initials.length() == 2) {
                break;
            }
        }
        return initials.length() == 0 ? "\u2022" : initials.toString();
    }

    // ------------------------------------------------------------------ actions

    /**
     * Opens the rename dialog.
     *
     * <p>The positive button is wired by hand rather than through {@code setPositiveButton}, so
     * an empty name keeps the dialog open and points at the field instead of dismissing and
     * silently doing nothing.
     */
    private void showEditNameDialog() {
        User user = AuthManager.getInstance().getCurrentUser();
        if (user == null) {
            return;
        }

        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_edit_name, null, false);
        TextInputLayout field = content.findViewById(R.id.editNameField);
        TextInputEditText input = content.findViewById(R.id.editNameInput);
        input.setText(user.getName());
        input.setSelection(input.length());

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.account_edit_name_title)
                .setView(content)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_save, null)
                .create();

        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String name = input.getText() == null
                            ? "" : input.getText().toString().trim();
                    if (name.isEmpty()) {
                        field.setError(getString(R.string.account_edit_name_empty));
                        return;
                    }
                    field.setError(null);
                    dialog.dismiss();
                    saveName(name);
                }));
        dialog.show();
        input.requestFocus();
    }

    /** Writes the name locally, sends it to the server, then repaints the page and the bar. */
    private void saveName(@NonNull String name) {
        User user = AuthManager.getInstance().getCurrentUser();
        if (user == null) {
            return;
        }
        editNameButton.setEnabled(false);

        repo.background(() -> repo.updateDisplayName(user, name), updated -> {
            if (!isAdded()) {
                return;
            }
            editNameButton.setEnabled(true);
            if (updated == null) {
                Ux.toast(requireContext(), getString(R.string.account_name_update_failed));
                return;
            }
            render(updated, SupabaseAuth.isSignedIn());
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).refreshProfileBadge();
            }
            Ux.toast(requireContext(), getString(R.string.account_edit_name_saved));
            loadSyncStatus();
        });
    }

    private void confirmSignOut() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.account_sign_out)
                .setMessage(R.string.account_sign_out_confirm)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.account_sign_out, (dialog, which) -> signOut())
                .show();
    }

    /**
     * Ends the session and wipes the local copy.
     *
     * <p>Wiping matters: Room holds the reservations, receipts and messages that Row Level
     * Security only let this account see. Leaving them behind would show them to whoever
     * signs in next on a shared device.
     */
    private void signOut() {
        Context appContext = requireContext().getApplicationContext();
        repo.background(() -> {
            SupabaseAuth.signOut(appContext);
            repo.clearLocalData();
            return Boolean.TRUE;
        }, done -> {
            if (!isAdded()) {
                return;
            }
            AuthManager.getInstance().clearCurrentUser();
            AppStateStore.get().reset();
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).onSignedOut();
            }
        });
    }
}
