package com.creditwise.app.work;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.creditwise.app.data.local.CreditCaseStore;
import com.creditwise.app.data.local.LocalAuthStore;
import com.creditwise.app.data.model.ChallengeState;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/** Runs roughly once a week (see {@code App.scheduleChallengeReminders}); posts a reminder only
 *  if the current user has actually started the challenges and hasn't fed them a fresh statement
 *  in a while — never nags someone who never opted into the challenges in the first place. */
public class ChallengeReminderWorker extends Worker {

    private static final int REMINDER_AFTER_DAYS = 7;

    public ChallengeReminderWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        String email = new LocalAuthStore(context).currentEmail();
        if (email == null) return Result.success();

        ChallengeState state = new CreditCaseStore(context).loadChallengeState(email);
        boolean hasStarted = !state.savingsHistory.isEmpty() || !state.regularityHistory.isEmpty();
        if (!hasStarted) return Result.success();

        long daysSince = daysSinceLastUpdate(state.lastUpdatedAt);
        if (daysSince < REMINDER_AFTER_DAYS) return Result.success();

        ChallengeReminderNotifier.show(context);
        return Result.success();
    }

    private static long daysSinceLastUpdate(String lastUpdatedAt) {
        if (lastUpdatedAt == null || lastUpdatedAt.isEmpty()) return Long.MAX_VALUE;
        try {
            return ChronoUnit.DAYS.between(LocalDate.parse(lastUpdatedAt), LocalDate.now());
        } catch (DateTimeParseException e) {
            return Long.MAX_VALUE;
        }
    }
}
