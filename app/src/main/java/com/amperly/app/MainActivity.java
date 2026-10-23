package com.amperly.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import com.amperly.app.core.common.AppConstants;
import com.amperly.app.core.security.AuthManager;
import com.amperly.app.core.state.AppStateStore;
import com.amperly.app.core.state.SocStore;
import com.amperly.app.data.local.entity.User;
import com.amperly.app.data.local.entity.Vehicle;
import com.amperly.app.data.remote.SupabaseAuth;
import com.amperly.app.data.remote.SupabaseConfig;
import com.amperly.app.data.remote.SyncEngine;
import com.amperly.app.data.repository.AmperlyRepository;
import com.amperly.app.feature.admin.AdminDashboardFragment;
import com.amperly.app.feature.auth.AccountFragment;
import com.amperly.app.feature.auth.SignInFragment;
import com.amperly.app.feature.consumer.ActiveSessionFragment;
import com.amperly.app.feature.consumer.ChargerDiscoveryFragment;
import com.amperly.app.feature.consumer.HostChatFragment;
import com.amperly.app.feature.consumer.ReservationFragment;
import com.amperly.app.feature.consumer.RoutePlannerFragment;
import com.amperly.app.feature.consumer.SafetySOSFragment;
import com.amperly.app.feature.consumer.VehicleGalleryFragment;
import com.amperly.app.feature.consumer.VehicleProfileFragment;
import com.amperly.app.feature.host.HostDashboardFragment;
import com.amperly.app.feature.host.RegisterStationFragment;
import com.amperly.app.vehicle.SpecEstimator;
import com.amperly.app.vehicle.SpecResolver;
import com.amperly.app.model.Role;
import com.amperly.app.ui.BottomNavView;
import com.amperly.app.ui.Car3DView;
import com.amperly.app.ui.Ux;
import com.amperly.app.ui.content.NavItem;
import com.amperly.app.ui.content.NavigationConfig;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The application shell: the pixel app bar, the vehicle popup, the role switcher, one real
 * fragment host and the pixel bottom navigation.
 *
 * <p>Every screen inside the host is a production screen from the platform: it reads and
 * writes through {@link AmperlyRepository} (Supabase is the source of truth, Room the cache),
 * and the role switcher only changes the capacity of the one signed-in account.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Amperly";

    /** Remembers which account the on-device cache belongs to, so it is only wiped for a new one. */
    private static final String PREFS = "amperly_session";
    private static final String KEY_LAST_USER = "last_user_id";

    private AmperlyRepository repo;

    private View appBar;
    private View roleStrip;
    private View navDivider;
    private BottomNavView bottomNav;
    private ChipGroup roleChips;
    private Chip chipConsumer;
    private Chip chipHost;
    private Chip chipAdmin;
    private TextView summaryName;
    private TextView summaryRole;
    private TextView summaryMeta;
    private TextView avatarInitials;
    private View profileBadge;

    private LinearLayout vehicleCard;
    private LinearLayout vehicleHeader;
    private LinearLayout vehicleExpanded;
    private TextView vehicleName;
    private TextView vehicleSpec;
    private TextView vehicleBattery;
    private ImageView vehicleChevron;
    private Car3DView car3d;
    private SpecResolver specResolver;
    /** Guards against a late spec lookup overwriting a newer vehicle selection. */
    private String vehicleResolveKey;

    private List<NavItem> navItems = new ArrayList<>();
    private String currentPageId;
    private boolean popupExpanded = true;
    private boolean applyingRole;
    private boolean chromeVisible;

    /** The vehicle observer is re-bound whenever the signed-in account changes. */
    private LiveData<Vehicle> vehicleLiveData;
    private final Observer<Vehicle> vehicleObserver = this::renderVehicle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_main);
            repo = AmperlyRepository.getInstance(this);
            specResolver = SpecResolver.getInstance(this);

            bindShellViews();
            setupRoleChips();
            setupProfileMenu();
            setupVehiclePopup();

            // Registered before any screen can be shown, so a gesture straight after launch is
            // never answered by the system finishing the activity.
            getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    handleBack();
                }
            });

            handleDeepLink(getIntent());
            startBackend();
        } catch (Exception e) {
            Log.e(TAG, "Critical error in MainActivity onCreate: " + e.getMessage(), e);
            try {
                showSignIn();
            } catch (Exception ignored) {
            }
        }
    }

    private void bindShellViews() {
        appBar = findViewById(R.id.appBar);
        roleStrip = findViewById(R.id.roleStrip);
        navDivider = findViewById(R.id.navDivider);
        bottomNav = findViewById(R.id.bottomNav);
        roleChips = findViewById(R.id.roleChips);
        chipConsumer = findViewById(R.id.chipConsumer);
        chipHost = findViewById(R.id.chipHost);
        chipAdmin = findViewById(R.id.chipAdmin);
        summaryName = findViewById(R.id.summaryName);
        summaryRole = findViewById(R.id.summaryRole);
        summaryMeta = findViewById(R.id.summaryMeta);
        avatarInitials = findViewById(R.id.avatarInitials);
        profileBadge = findViewById(R.id.profileBadge);

        vehicleCard = findViewById(R.id.vehicleCard);
        vehicleHeader = findViewById(R.id.vehicleHeader);
        vehicleExpanded = findViewById(R.id.vehicleExpanded);
        vehicleName = findViewById(R.id.vehicleName);
        vehicleSpec = findViewById(R.id.vehicleSpec);
        vehicleBattery = findViewById(R.id.vehicleBattery);
        vehicleChevron = findViewById(R.id.vehicleChevron);
        car3d = findViewById(R.id.car3d);
    }

    // ------------------------------------------------------------- backend gate

    private void startBackend() {
        try {
            if (!SupabaseConfig.isConfigured() || SupabaseConfig.looksLikeSecretKey()) {
                if (SupabaseConfig.looksLikeSecretKey()) {
                    Log.e(TAG, "SUPABASE_PUBLISHABLE_KEY is a secret key. Sync is disabled; replace "
                            + "it with the publishable key in local.properties.");
                    Ux.toast(this, getString(R.string.auth_secret_key));
                }
                // The server is the only source of truth, so there is nothing to show without
                // it. The sign-in screen states the problem instead of seeding a local pilot.
                showSignIn();
                return;
            }
            if (SupabaseAuth.hasStoredSession(this)) {
                setChromeVisible(false);
                restoreSession();
            } else {
                showSignIn();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting backend: " + e.getMessage());
            showSignIn();
        }
    }

    private void restoreSession() {
        repo.background(() -> SupabaseAuth.restoreSession(this), session -> {
            if (session == null) {
                showSignIn();
                return;
            }
            repo.background(
                    () -> repo.resolveOrCreateAppUser(session.authUserId, session.email, null),
                    user -> {
                        if (user == null) {
                            Ux.toast(this, getString(R.string.auth_profile_offline));
                            showSignIn();
                            return;
                        }
                        onSignedIn(user);
                    });
        });
    }

    public void showSignIn() {
        currentPageId = null;
        setChromeVisible(false);
        getSupportFragmentManager()
                .popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new SignInFragment(), "sign_in")
                .commit();
    }

    public void onSignedIn(User user) {
        AuthManager.getInstance().setCurrentUser(user);
        // The battery reading the driver last confirmed survives a restart.
        int storedSoc = SocStore.load(this, user.getId());
        if (storedSoc >= 0 && AppStateStore.get().getCurrentSoc() < 0) {
            AppStateStore.get().setEstimatedSoc(storedSoc);
        }
        setChromeVisible(true);
        applyRole(AppConstants.ROLE_CONSUMER);
        Ux.toast(this, getString(R.string.auth_signed_in_as) + " " + user.getName());
        if (repo.didProfileWriteFail()) {
            // The account exists on the device but not in public.users, which makes it
            // anonymous to every RLS policy. Say so instead of showing an app that looks broken.
            Ux.toast(this, getString(R.string.auth_profile_local_only));
        }
        syncAfterSignIn();
    }

    public void onSignedOut() {
        AuthManager.getInstance().clearCurrentUser();
        AppStateStore.get().reset();
        showSignIn();
    }

    /**
     * Pulls the account's data from Supabase after a sign-in.
     *
     * <p>Room is only a cache, so it is wiped when a DIFFERENT account signs in. The same
     * account signing in again keeps its cache, which is what makes the app usable offline.
     */
    private void syncAfterSignIn() {
        repo.background(() -> {
            User currentUser = AuthManager.getInstance().getCurrentUser();
            if (currentUser != null) {
                String previousUserId = getSharedPreferences(PREFS, MODE_PRIVATE)
                        .getString(KEY_LAST_USER, "");
                if (!currentUser.getId().equals(previousUserId)) {
                    repo.clearLocalData();
                    try {
                        repo.database().userDao().insert(currentUser);
                    } catch (Exception ignored) {
                    }
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                            .putString(KEY_LAST_USER, currentUser.getId()).apply();
                }
            }
            return SyncEngine.syncNow(this, repo.database());
        }, result -> {
            if (result != null) {
                Log.i(TAG, "Signed-in sync: " + result.summary());
                if (chromeVisible) {
                    applyRole(AuthManager.getInstance().getCurrentRole());
                }
            }
        });
    }

    // ------------------------------------------------------------------ chrome

    private void setChromeVisible(boolean visible) {
        chromeVisible = visible;
        int visibility = visible ? View.VISIBLE : View.GONE;
        if (appBar != null) appBar.setVisibility(visibility);
        if (vehicleCard != null) vehicleCard.setVisibility(visibility);
        if (roleStrip != null) roleStrip.setVisibility(visibility);
        if (navDivider != null) navDivider.setVisibility(visibility);
        if (bottomNav != null) bottomNav.setVisibility(visibility);

        if (visible) {
            bindVehicle();
            playPopupEntrance();
        }
    }

    // ------------------------------------------------------------- role switch

    private void setupRoleChips() {
        roleChips.setOnCheckedChangeListener((group, checkedId) -> {
            if (applyingRole || checkedId == View.NO_ID) {
                return;
            }
            String role;
            if (checkedId == R.id.chipHost) {
                role = AppConstants.ROLE_HOST;
            } else if (checkedId == R.id.chipAdmin) {
                role = AppConstants.ROLE_ADMIN;
            } else {
                role = AppConstants.ROLE_CONSUMER;
            }
            applyRole(role);
        });
    }

    /**
     * Switches capacity. The account never changes, only what it is currently acting as, so a
     * Consumer booking a slot and a Host listing a charger are the same signed-in person.
     */
    private void applyRole(String role) {
        AuthManager auth = AuthManager.getInstance();
        String target = role;
        if (!auth.setRole(target)) {
            // No authority for that capacity; fall back rather than pretend otherwise.
            target = AppConstants.ROLE_CONSUMER;
            auth.setRole(target);
        }
        AppStateStore.get().setUserId(auth.getCurrentUserId());
        buildRoleChips(target);
        renderIdentity(target);
        buildNavigation(target);
    }

    /**
     * Shows the capacities this account actually holds. Consumer and Host come as a pair;
     * Admin appears only when the server says the account is platform ops.
     */
    private void buildRoleChips(String activeRole) {
        List<String> roles = AuthManager.getInstance().availableRoles();
        applyingRole = true;
        chipConsumer.setChecked(AppConstants.ROLE_CONSUMER.equals(activeRole));
        chipHost.setChecked(AppConstants.ROLE_HOST.equals(activeRole));
        chipAdmin.setChecked(AppConstants.ROLE_ADMIN.equals(activeRole));
        chipConsumer.setVisibility(roles.contains(AppConstants.ROLE_CONSUMER) ? View.VISIBLE : View.GONE);
        chipHost.setVisibility(roles.contains(AppConstants.ROLE_HOST) ? View.VISIBLE : View.GONE);
        chipAdmin.setVisibility(roles.contains(AppConstants.ROLE_ADMIN) ? View.VISIBLE : View.GONE);
        applyingRole = false;
    }

    private void renderIdentity(String role) {
        User user = AuthManager.getInstance().getCurrentUser();
        if (user == null) {
            summaryName.setText(R.string.account_signed_in);
            summaryRole.setText(NavigationConfig.modeText(Role.fromKey(role)));
            summaryMeta.setText("");
            avatarInitials.setText("\u2022");
            return;
        }
        summaryName.setText(user.getName());
        summaryRole.setText(NavigationConfig.modeText(Role.fromKey(role)));
        summaryMeta.setText(metaFor(role, user));
        avatarInitials.setText(initialsOf(user.getName()));
    }

    /** The small right-hand caption: what this capacity is currently about. */
    private String metaFor(String role, User user) {
        if (AppConstants.ROLE_ADMIN.equals(role)) {
            return getString(R.string.role_admin);
        }
        if (AppConstants.ROLE_HOST.equals(role)) {
            String org = user.getOrganizationName();
            return org == null || org.trim().isEmpty() ? "Host" : org;
        }
        return AuthManager.authorityLabel(user);
    }

    /** Re-renders the top-bar identity after the account page renames the signed-in person. */
    public void refreshProfileBadge() {
        renderIdentity(AuthManager.getInstance().getCurrentRole());
    }

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

    // ----------------------------------------------------------- navigation

    private void buildNavigation(String role) {
        if (bottomNav == null) {
            return;
        }
        navItems = NavigationConfig.forRole(Role.fromKey(role));
        bottomNav.setItems(navItems, (index, item) -> showPage(item.pageId, true));
        bottomNav.clearSelection();
        if (!navItems.isEmpty()) {
            bottomNav.select(0, false);
            showPage(navItems.get(0).pageId, false);
        }
    }

    /** Opens the default page for the role the account is currently acting as. */
    public void goHome() {
        if (!chromeVisible) {
            return;
        }
        applyRole(AuthManager.getInstance().getCurrentRole());
    }

    private void showPage(String pageId, boolean animate) {
        currentPageId = pageId;
        Fragment fragment = createPage(pageId);
        if (fragment == null) {
            return;
        }
        getSupportFragmentManager()
                .popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        if (animate) {
            transaction.setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out);
        }
        transaction.replace(R.id.fragment_container, fragment, pageId).commitAllowingStateLoss();

        for (int i = 0; i < navItems.size(); i++) {
            if (navItems.get(i).pageId.equals(pageId)) {
                bottomNav.select(i, false);
                break;
            }
        }
    }

    @Nullable
    private Fragment createPage(String pageId) {
        switch (pageId) {
            case NavigationConfig.PAGE_ROUTE:
                return new RoutePlannerFragment();
            case NavigationConfig.PAGE_RESERVE:
                return new ReservationFragment();
            case NavigationConfig.PAGE_SESSION:
                return new ActiveSessionFragment();
            case NavigationConfig.PAGE_SAFETY:
                return new SafetySOSFragment();
            case NavigationConfig.PAGE_HOST_DASHBOARD:
                return new HostDashboardFragment();
            case NavigationConfig.PAGE_HOST_REGISTER:
                return new RegisterStationFragment();
            case NavigationConfig.PAGE_HOST_CHAT:
                return new HostChatFragment();
            case NavigationConfig.PAGE_ADMIN:
                return new AdminDashboardFragment();
            case NavigationConfig.PAGE_DISCOVER:
            default:
                return new ChargerDiscoveryFragment();
        }
    }

    private void openRoot(Fragment fragment, String tag) {
        if (bottomNav != null) {
            bottomNav.clearSelection();
        }
        getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.fragment_container, fragment, tag)
                .commit();
    }

    /**
     * Back, from either the gesture or the navigation bar: the fragment stack first, then the
     * role's home screen, then the system.
     */
    private void handleBack() {
        FragmentManager manager = getSupportFragmentManager();
        if (manager.getBackStackEntryCount() > 0) {
            manager.popBackStack();
            return;
        }
        if (!chromeVisible || isAtHomeScreen()) {
            finish();
            return;
        }
        goHome();
    }

    /** True when the container already shows the current role's home screen (or sign-in). */
    private boolean isAtHomeScreen() {
        Fragment current = getSupportFragmentManager()
                .findFragmentById(R.id.fragment_container);
        if (current == null || current instanceof SignInFragment) {
            return true;
        }
        String role = AuthManager.getInstance().getCurrentRole();
        if (AppConstants.ROLE_HOST.equals(role)) {
            return current instanceof HostDashboardFragment;
        }
        if (AppConstants.ROLE_ADMIN.equals(role)) {
            return current instanceof AdminDashboardFragment;
        }
        return current instanceof ChargerDiscoveryFragment;
    }

    // ------------------------------------------------------------- profile menu

    private void setupProfileMenu() {
        profileBadge.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(this, profileBadge);
            Menu menu = popup.getMenu();
            menu.add(Menu.NONE, 1, 1, R.string.menu_account);
            menu.add(Menu.NONE, 2, 2, R.string.menu_vehicle);
            menu.add(Menu.NONE, 3, 3, R.string.menu_safety);
            MenuItem signOut = menu.add(Menu.NONE, 4, 4, R.string.menu_signout);
            signOut.setVisible(SupabaseAuth.isSignedIn());
            if (com.amperly.app.BuildConfig.DEBUG) {
                menu.add(Menu.NONE, 5, 5, R.string.menu_vehicle_gallery);
            }
            popup.setOnMenuItemClickListener(item -> {
                switch (item.getItemId()) {
                    case 1:
                        openRoot(new AccountFragment(), "account");
                        return true;
                    case 2:
                        applyRole(AppConstants.ROLE_CONSUMER);
                        openRoot(new VehicleProfileFragment(), "vehicle");
                        return true;
                    case 3:
                        openRoot(new SafetySOSFragment(), "safety");
                        return true;
                    case 4:
                        confirmSignOut();
                        return true;
                    case 5:
                        openRoot(new VehicleGalleryFragment(), "vehicle_gallery");
                        return true;
                    default:
                        return false;
                }
            });
            popup.show();
        });
    }

    private void confirmSignOut() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.account_sign_out)
                .setMessage(R.string.account_sign_out_confirm)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.account_sign_out, (dialog, which) -> performSignOut())
                .show();
    }

    /** Ends the session and wipes the local copy, exactly as the account page does. */
    private void performSignOut() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove(KEY_LAST_USER).apply();
        repo.background(() -> {
            SupabaseAuth.signOut(getApplicationContext());
            repo.clearLocalData();
            return Boolean.TRUE;
        }, done -> {
            AuthManager.getInstance().clearCurrentUser();
            AppStateStore.get().reset();
            onSignedOut();
        });
    }

    // ------------------------------------------------------------ vehicle popup

    private void setupVehiclePopup() {
        vehicleHeader.setOnClickListener(v -> setPopupExpanded(!popupExpanded, true));
        findViewById(R.id.vehicleResetButton).setOnClickListener(v -> {
            if (car3d != null) {
                car3d.resetView();
            }
        });
        findViewById(R.id.vehicleProfileLink).setOnClickListener(v -> {
            if (!chromeVisible) {
                return;
            }
            applyRole(AppConstants.ROLE_CONSUMER);
            openRoot(new VehicleProfileFragment(), "vehicle");
        });
        renderVehicle(null);
    }

    /**
     * Binds the popup to the signed-in account's real vehicle row.
     *
     * <p>The 3D model is decorative — a body style chosen from the vehicle's entered make and
     * model — while the name, spec and battery all come from the profile and the driver's own
     * reading.
     */
    private void bindVehicle() {
        User user = AuthManager.getInstance().getCurrentUser();
        if (user == null) {
            renderVehicle(null);
            return;
        }
        if (vehicleLiveData != null) {
            vehicleLiveData.removeObserver(vehicleObserver);
        }
        vehicleLiveData = repo.observeVehicle(user.getId());
        vehicleLiveData.observe(this, vehicleObserver);
    }

    private void renderVehicle(@Nullable Vehicle vehicle) {
        if (car3d == null) {
            return;
        }
        if (vehicle == null) {
            vehicleResolveKey = null;
            car3d.setLoading(false);
            car3d.setChargePortHighlighted(false);
            car3d.setVehicle(SpecEstimator.estimate("", "", ""));
            vehicleName.setText(R.string.shell_no_vehicle);
            vehicleSpec.setText(R.string.shell_no_vehicle_spec);
            vehicleBattery.setText(R.string.shell_soc_unset);
            return;
        }

        // Resolve the real make/model/variant off the main thread, then build the 3D model from
        // the resulting dimensions. The row itself already carries the name, spec and battery.
        final String make = vehicle.getMake();
        final String model = vehicle.getModel();
        final String variant = vehicle.getVariant();
        final String resolveKey = (make == null ? "" : make) + "|" + (model == null ? "" : model)
                + "|" + (variant == null ? "" : variant);
        vehicleResolveKey = resolveKey;
        car3d.setLoading(true);
        repo.background(() -> specResolver.resolve(make, model, variant,
                vehicle.getPurchaseYear()), specs -> {
            if (car3d == null || specs == null || !resolveKey.equals(vehicleResolveKey)) {
                return;
            }
            car3d.setLoading(false);
            car3d.setVehicle(specs);
            car3d.setChargePortHighlighted(
                    !AppStateStore.get().getActiveSessionId().isEmpty());
        });

        String name = join(vehicle.getMake(), vehicle.getModel());
        vehicleName.setText(name.isEmpty() ? getString(R.string.nav_vehicle) : name);

        StringBuilder spec = new StringBuilder();
        if (vehicle.getVariant() != null && !vehicle.getVariant().trim().isEmpty()) {
            spec.append(vehicle.getVariant().trim());
        }
        if (vehicle.getBatteryCapacityKwh() > 0) {
            if (spec.length() > 0) {
                spec.append(" · ");
            }
            spec.append(String.format(Locale.US, "%.1f kWh", vehicle.getBatteryCapacityKwh()));
        }
        if (vehicle.getConnectorType() != null && !vehicle.getConnectorType().trim().isEmpty()) {
            if (spec.length() > 0) {
                spec.append(" · ");
            }
            spec.append(vehicle.getConnectorType().trim());
        }
        vehicleSpec.setText(spec.length() == 0
                ? getString(R.string.shell_no_vehicle_spec) : spec.toString());

        int soc = SocStore.load(this, AuthManager.getInstance().getCurrentUserId());
        if (soc < 0) {
            soc = AppStateStore.get().getCurrentSoc();
        }
        vehicleBattery.setText(soc >= 0 && soc <= 100
                ? soc + "%" : getString(R.string.shell_soc_unset));
    }

    private static String join(String first, String second) {
        String a = first == null ? "" : first.trim();
        String b = second == null ? "" : second.trim();
        if (a.isEmpty()) {
            return b;
        }
        if (b.isEmpty()) {
            return a;
        }
        return a + " " + b;
    }

    private void setPopupExpanded(boolean expanded, boolean animate) {
        if (popupExpanded == expanded) {
            return;
        }
        popupExpanded = expanded;

        int expandedWidth = vehicleExpanded.getWidth() > 0
                ? vehicleExpanded.getWidth()
                : Math.max(1, vehicleCard.getWidth());
        vehicleExpanded.measure(
                View.MeasureSpec.makeMeasureSpec(expandedWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int target = expanded ? vehicleExpanded.getMeasuredHeight() : 0;
        int start = vehicleExpanded.getHeight();

        vehicleExpanded.setVisibility(View.VISIBLE);
        ViewGroup.LayoutParams params = vehicleExpanded.getLayoutParams();
        params.height = start;
        vehicleExpanded.setLayoutParams(params);

        vehicleChevron.animate()
                .rotation(expanded ? 0f : 180f)
                .setDuration(animate ? 220 : 0)
                .start();

        if (!animate) {
            params.height = expanded ? ViewGroup.LayoutParams.WRAP_CONTENT : 0;
            vehicleExpanded.setLayoutParams(params);
            vehicleExpanded.setVisibility(expanded ? View.VISIBLE : View.GONE);
            return;
        }

        ValueAnimator animator = ValueAnimator.ofInt(start, target);
        animator.setDuration(220);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            ViewGroup.LayoutParams layoutParams = vehicleExpanded.getLayoutParams();
            layoutParams.height = (int) animation.getAnimatedValue();
            vehicleExpanded.setLayoutParams(layoutParams);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                ViewGroup.LayoutParams layoutParams = vehicleExpanded.getLayoutParams();
                layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                vehicleExpanded.setLayoutParams(layoutParams);
                vehicleExpanded.setVisibility(expanded ? View.VISIBLE : View.GONE);
            }
        });
        animator.start();
    }

    private void playPopupEntrance() {
        if (vehicleCard == null) {
            return;
        }
        vehicleCard.post(() -> {
            vehicleCard.setAlpha(0f);
            vehicleCard.setTranslationY(-18f);
            vehicleCard.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(360)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        });
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleDeepLink(intent);
    }

    private void handleDeepLink(Intent intent) {
        if (intent == null || intent.getData() == null) {
            return;
        }
        android.net.Uri data = intent.getData();
        if ("amperly".equals(data.getScheme()) && "auth-callback".equals(data.getHost())) {
            // Any installed app can fire this custom scheme, so the intent is treated as
            // "the user came back", never as proof that an address was confirmed.
            Ux.toast(this, getString(R.string.auth_callback_return));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (repo == null || avatarInitials == null) {
            return;
        }
        if (chromeVisible) {
            renderIdentity(AuthManager.getInstance().getCurrentRole());
            bindVehicle();
        }
    }

    @Override
    protected void onDestroy() {
        if (vehicleLiveData != null) {
            vehicleLiveData.removeObserver(vehicleObserver);
        }
        super.onDestroy();
    }
}
