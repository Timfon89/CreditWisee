package com.creditwise.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentContainerView;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    /** Set on the launch intent by {@link com.creditwise.app.work.ChallengeReminderNotifier} to
     *  open the standalone statement check-in screen directly, bypassing the onboarding wizard. */
    public static final String EXTRA_OPEN_QUICK_UPDATE = "open_quick_update";

    private NavController navController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Destinations that show the bottom tab bar — everything else is a full-screen flow.
        Set<Integer> tabDestinations = new HashSet<>(Arrays.asList(
                R.id.homeFragment, R.id.explainFragment, R.id.questsFragment, R.id.profileFragment));

        FragmentContainerView navHostView = findViewById(R.id.nav_host);
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(navHostView.getId());
        if (navHostFragment == null) return;
        navController = navHostFragment.getNavController();

        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        NavigationUI.setupWithNavController(bottomNav, navController);

        navController.addOnDestinationChangedListener((controller, destination, arguments) ->
                bottomNav.setVisibility(tabDestinations.contains(destination.getId())
                        ? View.VISIBLE : View.GONE));

        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(@Nullable Intent intent) {
        if (navController == null || intent == null) return;
        if (intent.getBooleanExtra(EXTRA_OPEN_QUICK_UPDATE, false)) {
            intent.removeExtra(EXTRA_OPEN_QUICK_UPDATE);
            navController.navigate(R.id.quickUpdateFragment);
        }
    }
}
