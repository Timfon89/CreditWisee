package com.creditwise.app.ui;

import android.content.Context;
import android.net.Uri;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.creditwise.app.data.classify.TransactionClassifier;
import com.creditwise.app.data.local.CreditCaseStore;
import com.creditwise.app.data.local.LocalAuthStore;
import com.creditwise.app.data.model.ChallengeState;
import com.creditwise.app.data.model.CreditCase;
import com.creditwise.app.data.model.EmploymentType;
import com.creditwise.app.data.model.LenderOffer;
import com.creditwise.app.data.model.ParseResult;
import com.creditwise.app.data.model.QuickUpdateResult;
import com.creditwise.app.data.model.StatementAnalysis;
import com.creditwise.app.data.model.TelegramScanResult;
import com.creditwise.app.data.model.TrustworthinessScore;
import com.creditwise.app.data.parser.StatementParsers;
import com.creditwise.app.data.telegram.TelegramExportScanner;
import com.creditwise.app.domain.AggregationEngine;
import com.creditwise.app.domain.ChallengeEngine;
import com.creditwise.app.domain.CreditScoreCalculator;
import com.creditwise.app.domain.HabitsScorer;
import com.creditwise.app.domain.OptimizationEngine;
import com.creditwise.app.domain.StatementMerger;
import com.creditwise.app.domain.TelegramScoring;
import com.creditwise.app.domain.TrustworthinessCalculator;
import com.creditwise.app.util.PdfTextExtractor;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AssessmentViewModel extends ViewModel {

    public enum Status { IDLE, LOADING, OK, ERROR }

    private final MutableLiveData<Status> statementStatus = new MutableLiveData<>(Status.IDLE);
    private final MutableLiveData<String> statementMessage = new MutableLiveData<>("");
    private final MutableLiveData<Status> telegramStatus = new MutableLiveData<>(Status.IDLE);
    private final MutableLiveData<String> telegramMessage = new MutableLiveData<>("");
    private final MutableLiveData<Status> quickUpdateStatus = new MutableLiveData<>(Status.IDLE);
    private final MutableLiveData<QuickUpdateResult> quickUpdateResult = new MutableLiveData<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    public Assessment data = new Assessment();

    public LiveData<Status> statementStatus() { return statementStatus; }
    public LiveData<String> statementMessage() { return statementMessage; }
    public LiveData<Status> telegramStatus() { return telegramStatus; }
    public LiveData<String> telegramMessage() { return telegramMessage; }
    public LiveData<Status> quickUpdateStatus() { return quickUpdateStatus; }
    public LiveData<QuickUpdateResult> quickUpdateResult() { return quickUpdateResult; }

    public void setExternalRating(Context context, int rating) {
        data.externalRating = rating;
        String email = new LocalAuthStore(context).currentEmail();
        new CreditCaseStore(context).saveExternalRating(email, rating);
    }

    public void setEmploymentType(Context context, EmploymentType type) {
        data.employmentType = type;
        String email = new LocalAuthStore(context).currentEmail();
        new CreditCaseStore(context).saveEmploymentType(email, type);
    }

    /** Parses and appends one more statement (a second/third account, say) to the current set. */
    public void addStatement(Context context, Uri uri) {
        final Context app = context.getApplicationContext();
        statementStatus.setValue(Status.LOADING);
        io.execute(() -> {
            try (InputStream in = app.getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IllegalStateException("Не удалось открыть файл");
                String text = PdfTextExtractor.extract(in);
                ParseResult parsed = StatementParsers.parse(text);
                if (!parsed.isUsable()) {
                    throw new IllegalStateException("Не удалось распознать формат этой выписки");
                }
                data.statements.add(parsed);
                recomputeMerged();
                statementMessage.postValue(summaryMessage());
                statementStatus.postValue(Status.OK);
            } catch (Exception e) {
                statementMessage.postValue(e.getMessage() == null ? "Ошибка чтения файла" : e.getMessage());
                statementStatus.postValue(Status.ERROR);
            }
        });
    }

    /** Drops one uploaded statement and recomputes the combined analysis from the rest. */
    public void removeStatement(int index) {
        if (index < 0 || index >= data.statements.size()) return;
        data.statements.remove(index);
        recomputeMerged();
        statementMessage.setValue(summaryMessage());
    }

    public int statementCount() {
        return data.statements.size();
    }

    /** Re-classifies every held statement from scratch, reconciles self-transfers between them,
     *  then re-aggregates — safe to call repeatedly as statements are added or removed. */
    private void recomputeMerged() {
        for (ParseResult p : data.statements) {
            new TransactionClassifier(p.header).classifyAll(p.transactions);
        }
        data.transferNotes = StatementMerger.reconcileSelfTransfers(data.statements);
        data.analysis = data.statements.isEmpty() ? null : new AggregationEngine().aggregate(data.statements);
    }

    private String summaryMessage() {
        if (data.statements.isEmpty()) return "";
        int totalTx = 0;
        for (ParseResult p : data.statements) totalTx += p.transactions.size();
        StringBuilder sb = new StringBuilder();
        sb.append("Выписок: ").append(data.statements.size())
                .append(" · операций: ").append(totalTx);
        if (data.analysis != null) sb.append(" · полных месяцев: ").append(data.analysis.monthCount);
        for (String note : data.transferNotes) sb.append("\n").append(note);
        return sb.toString();
    }

    public void scanTelegram(Context context, Uri uri) {
        final Context app = context.getApplicationContext();
        telegramStatus.setValue(Status.LOADING);
        io.execute(() -> {
            try (InputStream in = app.getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IllegalStateException("Не удалось открыть файл");
                TelegramScanResult scan = new TelegramExportScanner().scan(in);
                data.telegram = scan;
                telegramMessage.postValue("");
                telegramStatus.postValue(Status.OK);
            } catch (Exception e) {
                data.telegram = null;
                telegramMessage.postValue(e.getMessage() == null ? "Ошибка чтения файла" : e.getMessage());
                telegramStatus.postValue(Status.ERROR);
            }
        });
    }

    public void clearTelegram() {
        data.telegram = null;
        telegramStatus.setValue(Status.IDLE);
        telegramMessage.setValue("");
    }

    public void setTelegramConsent(boolean consent) {
        data.telegramConsent = consent;
    }

    /**
     * Called when the user finishes reviewing their Telegram scan on the "Задания" tab (either
     * "Далее" with consent given, or "Пропустить"/consent withdrawn). Evaluates the adjustment
     * once and persists it — every future score computation replays this persisted value rather
     * than needing the raw export again.
     */
    public void finishTelegramReview(Context context) {
        String email = new LocalAuthStore(context).currentEmail();
        CreditCaseStore store = new CreditCaseStore(context);
        if (data.telegramConsent && data.telegram != null) {
            TelegramScoring.Result r = TelegramScoring.evaluate(data.telegram, true);
            store.saveTelegramAdjustment(email, r);
        } else {
            store.clearTelegramAdjustment(email);
        }
        data.telegram = null;
        telegramStatus.setValue(Status.IDLE);
    }

    public void computeResults(Context context) {
        String email = new LocalAuthStore(context).currentEmail();
        CreditCaseStore store = new CreditCaseStore(context);
        int[] habitsSelections = store.loadHabitsSelections(email);
        int habitsBonus = HabitsScorer.score(habitsSelections);
        data.habitsReasons = HabitsScorer.reasons(habitsSelections);

        boolean newUser = store.loadCases(email).isEmpty();
        ChallengeState challenges = store.loadChallengeState(email);
        ChallengeEngine.updateSavings(challenges, data.analysis);
        ChallengeEngine.updateRegularity(challenges, data.statements,
                data.analysis == null ? 0 : data.analysis.incomeCv);
        challenges.lastUpdatedAt = LocalDate.now().toString();
        store.saveChallengeState(email, challenges);
        data.challenges = challenges;

        TelegramScoring.Result telegram = store.loadTelegramAdjustment(email);
        data.trust = new TrustworthinessCalculator().score(data.analysis, Math.max(0, data.externalRating),
                telegram, challenges.combinedPoints(), habitsBonus, data.employmentType, newUser);

        CreditScoreCalculator calc = new CreditScoreCalculator();
        data.score = calc.score(Math.max(0, data.externalRating), data.analysis);
        data.optimization = new OptimizationEngine(calc)
                .build(Math.max(0, data.externalRating), data.analysis, data.score.finalScore);
    }

    /**
     * Standalone "keep the challenges going" check-in: parses one fresh statement, feeds it into
     * the ongoing {@link ChallengeState} (same idempotent engine as a full assessment), and
     * recomputes the 0–100 index against the persisted employment type / external rating /
     * habits bonus / Telegram adjustment — without creating a new saved {@link CreditCase}. Used
     * by the standalone "Обновить выписку" screen reachable from Quests or the reminder
     * notification.
     */
    public void quickUpdate(Context context, Uri uri) {
        final Context app = context.getApplicationContext();
        quickUpdateStatus.setValue(Status.LOADING);
        io.execute(() -> {
            try (InputStream in = app.getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IllegalStateException("Не удалось открыть файл");
                String text = PdfTextExtractor.extract(in);
                ParseResult parsed = StatementParsers.parse(text);
                if (!parsed.isUsable()) {
                    throw new IllegalStateException("Не удалось распознать формат этой выписки");
                }
                new TransactionClassifier(parsed.header).classifyAll(parsed.transactions);
                StatementAnalysis analysis = new AggregationEngine().aggregate(parsed);

                String email = new LocalAuthStore(app).currentEmail();
                CreditCaseStore store = new CreditCaseStore(app);
                List<CreditCase> cases = store.loadCases(email);
                boolean newUser = cases.isEmpty();
                int trustBefore = cases.isEmpty() ? 0 : cases.get(0).trustTotal;

                ChallengeState challenges = store.loadChallengeState(email);
                ChallengeEngine.updateSavings(challenges, analysis);
                ChallengeEngine.updateRegularity(challenges, Collections.singletonList(parsed), analysis.incomeCv);
                challenges.lastUpdatedAt = LocalDate.now().toString();
                store.saveChallengeState(email, challenges);

                int habitsBonus = HabitsScorer.score(store.loadHabitsSelections(email));
                EmploymentType employmentType = store.loadEmploymentType(email);
                int externalRating = store.loadExternalRating(email);
                TelegramScoring.Result telegram = store.loadTelegramAdjustment(email);

                TrustworthinessScore trust = new TrustworthinessCalculator().score(analysis,
                        Math.max(0, externalRating), telegram, challenges.combinedPoints(),
                        habitsBonus, employmentType, newUser);

                // Home shows offers gated by the *current* index, so a check-in has to actually
                // move that needle — save a fresh snapshot the same way a full assessment would.
                store.saveCase(email, buildCase(trust, analysis, challenges,
                        HabitsScorer.reasons(store.loadHabitsSelections(email))));

                QuickUpdateResult result = new QuickUpdateResult();
                result.success = true;
                result.trustBefore = trustBefore;
                result.trustAfter = trust.total;
                for (LenderOffer offer : LenderOffer.CATALOG) {
                    if (offer.isEligible(trust.total) && !offer.isEligible(trustBefore)) {
                        result.newlyUnlocked.add(offer);
                    }
                }
                quickUpdateResult.postValue(result);
                quickUpdateStatus.postValue(Status.OK);
            } catch (Exception e) {
                QuickUpdateResult result = new QuickUpdateResult();
                result.message = e.getMessage() == null ? "Ошибка чтения файла" : e.getMessage();
                quickUpdateResult.postValue(result);
                quickUpdateStatus.postValue(Status.ERROR);
            }
        });
    }

    /** Same field population as a full assessment's save, minus the extended 0–999 model (that
     *  one's never persisted onto {@link CreditCase} even from the full wizard). */
    private static CreditCase buildCase(TrustworthinessScore trust, StatementAnalysis analysis,
                                        ChallengeState challenges, List<String> habitsReasons) {
        CreditCase c = new CreditCase();
        c.trustTotal = trust.total;
        c.trustBand = trust.band;
        c.baseScore = trust.base;
        c.externalBuff = trust.externalBuff;
        c.challengesBuff = trust.challengesBuff;
        c.challengesReasons.addAll(ChallengeEngine.savingsReasons(challenges));
        c.challengesReasons.addAll(ChallengeEngine.regularityReasons(challenges));
        c.habitsBuff = trust.habitsBuff;
        if (habitsReasons != null) c.habitsReasons.addAll(habitsReasons);
        c.riskPenalty = trust.riskPenalty;
        c.riskReasons.addAll(trust.riskReasons);
        c.telegramAdjustment = trust.adjustment;
        for (TrustworthinessScore.Factor f : trust.factors) {
            CreditCase.FactorSnapshot fs = new CreditCase.FactorSnapshot();
            fs.name = f.name;
            fs.detail = f.detail;
            fs.value = f.value;
            fs.points = f.points;
            c.baseFactors.add(fs);
        }
        for (TrustworthinessScore.Adjustment adj : trust.adjustments) {
            c.telegramReasons.add((adj.points >= 0 ? "+" : "") + adj.points + "  " + adj.label);
        }
        if (analysis != null) {
            c.avgIncome = analysis.avgIncome;
            c.avgExpense = analysis.avgExpense;
            for (com.creditwise.app.data.model.MonthlyAggregate m : analysis.months) {
                CreditCase.MonthPoint mp = new CreditCase.MonthPoint();
                mp.label = TrendChartView.shortRuMonth(m.month);
                mp.income = m.incomeEffective;
                mp.expense = m.expenseTotal;
                c.monthlySeries.add(mp);
            }
        }
        return c;
    }

    public void reset(Context context) {
        data = new Assessment();
        String email = new LocalAuthStore(context).currentEmail();
        data.employmentType = new CreditCaseStore(context).loadEmploymentType(email);
        statementStatus.setValue(Status.IDLE);
        statementMessage.setValue("");
        telegramStatus.setValue(Status.IDLE);
        telegramMessage.setValue("");
    }

    @Override
    protected void onCleared() {
        io.shutdownNow();
    }
}
