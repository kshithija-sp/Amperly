package com.amperly.app.feature.auth;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.amperly.app.MainActivity;
import com.amperly.app.R;
import com.amperly.app.data.local.entity.User;
import com.amperly.app.data.remote.SupabaseAuth;
import com.amperly.app.data.remote.SupabaseConfig;
import com.amperly.app.data.remote.SupabaseRest;
import com.amperly.app.data.repository.AmperlyRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

/**
 * Sign in or create an account.
 *
 * <p>Why this screen exists at all: every read and write in the app is authorised against
 * the signed-in Supabase account. With no session, Row Level Security returns no rows, so
 * the app would look empty — the screen is the difference between "nothing here" and a
 * working pilot. There is no offline demo path: the server is the only source of truth.
 *
 * <p>The work happens on a background thread through {@code AmperlyRepository.background},
 * and every failure is caught inside {@link #attempt} so that the reason survives. Letting
 * the executor swallow it would reduce a wrong password and an unreachable server to the
 * same blank look.
 */
public class SignInFragment extends Fragment {

    private AmperlyRepository repo;
    private EditText emailEdit;
    private EditText passwordEdit;
    private TextView errorText;
    private TextView infoText;
    private TextView offlineNoteText;
    private MaterialButton signInButton;
    private MaterialButton createAccountButton;
    private LinearProgressIndicator progress;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_sign_in, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repo = AmperlyRepository.getInstance(requireContext());

        emailEdit = view.findViewById(R.id.signInEmailEdit);
        passwordEdit = view.findViewById(R.id.signInPasswordEdit);
        errorText = view.findViewById(R.id.signInErrorText);
        infoText = view.findViewById(R.id.signInInfoText);
        offlineNoteText = view.findViewById(R.id.signInOfflineNoteText);
        signInButton = view.findViewById(R.id.signInButton);
        createAccountButton = view.findViewById(R.id.createAccountButton);
        progress = view.findViewById(R.id.signInProgress);

        boolean configured = serverConfigured();
        // Without a configured project there is no account to authorise against, so the screen
        // says exactly that instead of offering a local-only way in.
        offlineNoteText.setVisibility(configured ? View.GONE : View.VISIBLE);

        signInButton.setOnClickListener(v -> submit(false));
        createAccountButton.setOnClickListener(v -> submit(true));
        passwordEdit.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit(false);
                return true;
            }
            return false;
        });

        setBusy(false, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        setBusy(false, false);
    }

    private boolean serverConfigured() {
        return SupabaseConfig.isConfigured() && !SupabaseConfig.looksLikeSecretKey();
    }

    // ------------------------------------------------------------------ actions

    private void submit(boolean createAccount) {
        String email = emailEdit.getText().toString().trim();
        String password = passwordEdit.getText().toString();
        hideMessages();

        if (TextUtils.isEmpty(email) || !email.contains("@")) {
            showError(getString(R.string.auth_email_required));
            emailEdit.requestFocus();
            return;
        }
        if (password.length() < 6) {
            showError(getString(R.string.auth_password_too_short));
            passwordEdit.requestFocus();
            return;
        }

        Context appContext = requireContext().getApplicationContext();
        setBusy(true, createAccount);
        repo.background(() -> attempt(appContext, email, password, createAccount), this::handle);
    }

    // ------------------------------------------------------------- background

    /** Runs on a background thread. Never throws: the reason for a failure is the result. */
    private Outcome attempt(Context appContext, String email, String password,
                            boolean createAccount) {
        try {
            SupabaseAuth.Session session = createAccount
                    ? SupabaseAuth.signUp(appContext, email, password)
                    : SupabaseAuth.signIn(appContext, email, password);

            if (session == null) {
                // Sign-up succeeded but the project requires email confirmation.
                return Outcome.notice(
                        appContext.getString(R.string.auth_confirm_email, email));
            }

            User user = repo.resolveOrCreateAppUser(session.authUserId, session.email, null);
            if (user == null) {
                return Outcome.failed(appContext.getString(R.string.auth_no_profile));
            }
            return Outcome.signedIn(user);
        } catch (SupabaseAuth.Failure failure) {
            return Outcome.failed(messageOf(failure.getMessage(),
                    appContext.getString(R.string.auth_unexpected)));
        } catch (SupabaseAuth.NetworkFailure network) {
            return Outcome.failed(messageOf(network.getMessage(),
                    appContext.getString(R.string.auth_unexpected)));
        } catch (SupabaseRest.RestException rest) {
            return Outcome.failed(messageOf(rest.getMessage(),
                    appContext.getString(R.string.auth_no_profile)));
        } catch (RuntimeException unexpected) {
            return Outcome.failed(appContext.getString(R.string.auth_unexpected));
        }
    }

    private void handle(@Nullable Outcome outcome) {
        if (!isAdded()) {
            return;
        }
        setBusy(false, false);
        if (outcome == null) {
            showError(getString(R.string.auth_unexpected));
            return;
        }
        if (outcome.isSuccess()) {
            passwordEdit.setText("");
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).onSignedIn(outcome.user);
            }
            return;
        }
        if (outcome.info) {
            showInfo(outcome.message);
        } else {
            showError(outcome.message);
        }
    }

    private static String messageOf(String message, String fallback) {
        if (message == null || message.trim().isEmpty()) {
            return fallback;
        }
        String lower = message.toLowerCase();
        if (lower.contains("invalid login credentials") || lower.contains("invalid_credentials") || lower.contains("invalid grant")) {
            return "Invalid email or password. Please check your credentials and try again.";
        }
        if (lower.contains("email not confirmed") || lower.contains("email_not_confirmed")) {
            return "Please confirm your email address before signing in. Check your inbox for the confirmation link.";
        }
        if (lower.contains("permission denied") || lower.contains("row level security") || lower.contains("403") || lower.contains("401")) {
            return "Sign-in error. Please verify your email confirmation or account credentials.";
        }
        if (lower.contains("could not reach supabase") || lower.contains("network") || lower.contains("unable to resolve host") || lower.contains("timeout")) {
            return "Unable to connect to server. Please check your internet connection and try again.";
        }
        if (lower.contains("rate limit") || lower.contains("too many requests")) {
            return "Too many sign-in attempts. Please wait a moment and try again.";
        }
        if (lower.contains("user_already_exists") || lower.contains("already registered")) {
            return "An account with this email already exists. Please sign in instead.";
        }
        return message;
    }

    // ------------------------------------------------------------------- view

    private void setBusy(boolean busy, boolean createAccount) {
        boolean configured = serverConfigured();
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        emailEdit.setEnabled(!busy);
        passwordEdit.setEnabled(!busy);
        signInButton.setEnabled(!busy && configured);
        createAccountButton.setEnabled(!busy && configured);
        signInButton.setText(busy
                ? (createAccount ? R.string.auth_creating : R.string.auth_signing_in)
                : R.string.auth_sign_in);
    }

    private void hideMessages() {
        errorText.setVisibility(View.GONE);
        infoText.setVisibility(View.GONE);
    }

    private void showError(String message) {
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
        infoText.setVisibility(View.GONE);
    }

    private void showInfo(String message) {
        infoText.setText(message);
        infoText.setVisibility(View.VISIBLE);
        errorText.setVisibility(View.GONE);
    }

    /** What a background attempt produced, so the message is decided off the main thread. */
    private static final class Outcome {
        @Nullable
        final User user;
        @Nullable
        final String message;
        final boolean info;

        private Outcome(@Nullable User user, @Nullable String message, boolean info) {
            this.user = user;
            this.message = message;
            this.info = info;
        }

        static Outcome signedIn(User user) {
            return new Outcome(user, null, false);
        }

        static Outcome failed(String message) {
            return new Outcome(null, message, false);
        }

        static Outcome notice(String message) {
            return new Outcome(null, message, true);
        }

        boolean isSuccess() {
            return user != null;
        }
    }
}
