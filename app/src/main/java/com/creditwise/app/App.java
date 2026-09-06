package com.creditwise.app;

import android.app.Application;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.creditwise.app.data.local.ThemeStore;
import com.creditwise.app.work.ChallengeReminderNotifier;
import com.creditwise.app.work.ChallengeReminderWorker;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;

import java.util.concurrent.TimeUnit;

/** Application entry point. Initialises the on-device PDF library, the saved theme, and the
 *  weekly local reminder that nudges the user to feed the ongoing challenges a fresh statement. */
public class App extends Application {

    private static final String REMINDER_WORK_NAME = "challenge_reminder";

    @Override
    public void onCreate() {
        super.onCreate();
        ThemeStore.applyStartup(this);
        PDFBoxResourceLoader.init(getApplicationContext());
        ChallengeReminderNotifier.ensureChannel(this);
        scheduleChallengeReminders();
    }

    private void scheduleChallengeReminders() {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                ChallengeReminderWorker.class, 7, TimeUnit.DAYS).build();
        WorkManager.getInstance(this)
                .enqueueUniquePeriodicWork(REMINDER_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }
}
