package com.creditwise.app;

import android.os.Bundle;
import android.view.View;

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
        NavController navController = navHostFragment.getNavController();

        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        NavigationUI.setupWithNavController(bottomNav, navController);

        navController.addOnDestinationChangedListener((controller, destination, arguments) ->
                bottomNav.setVisibility(tabDestinations.contains(destination.getId())
                        ? View.VISIBLE : View.GONE));
    }
}
