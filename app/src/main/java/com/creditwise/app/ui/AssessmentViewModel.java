package com.creditwise.app.ui;

import android.content.Context;
import android.net.Uri;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.creditwise.app.data.classify.TransactionClassifier;
import com.creditwise.app.data.local.CreditCaseStore;
import com.creditwise.app.data.local.LocalAuthStore;
import com.creditwise.app.data.model.EmploymentType;
import com.creditwise.app.data.model.ParseResult;
import com.creditwise.app.data.model.TelegramScanResult;
import com.creditwise.app.data.parser.SberStatementParser;
import com.creditwise.app.data.telegram.TelegramExportScanner;
import com.creditwise.app.domain.AggregationEngine;
import com.creditwise.app.domain.CreditScoreCalculator;
import com.creditwise.app.domain.HabitsScorer;
import com.creditwise.app.domain.LoanApprovalEstimator;
import com.creditwise.app.domain.LoanCalculator;
import com.creditwise.app.domain.OptimizationEngine;
import com.creditwise.app.domain.StatementMerger;
import com.creditwise.app.domain.TrustworthinessCalculator;
import com.creditwise.app.util.PdfTextExtractor;

import java.io.InputStream;
import java.time.YearMonth;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AssessmentViewModel extends ViewModel {

    public enum Status { IDLE, LOADING, OK, ERROR }

    private final MutableLiveData<Status> statementStatus = new MutableLiveData<>(Status.IDLE);
    private final MutableLiveData<String> statementMessage = new MutableLiveData<>("");
    private final MutableLiveData<Status> telegramStatus = new MutableLiveData<>(Status.IDLE);
    private final MutableLiveData<String> telegramMessage = new MutableLiveData<>("");
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    public Assessment data = new Assessment();

    public LiveData<Status> statementStatus() { return statementStatus; }
    public LiveData<String> statementMessage() { return statementMessage; }
    public LiveData<Status> telegramStatus() { return telegramStatus; }
    public LiveData<String> telegramMessage() { return telegramMessage; }

    public void setExternalRating(int rating) {
        data.externalRating = rating;
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
                ParseResult parsed = SberStatementParser.parse(text);
                if (!parsed.isUsable()) {
                    throw new IllegalStateException("Не похоже на выписку по платёжному счёту СберБанка");
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

    public void setLoan(double amount, int termMonths, double annualRatePercent, YearMonth start) {
        data.loan.amount = amount;
        data.loan.termMonths = termMonths;
        data.loan.annualRatePercent = annualRatePercent;
        data.loan.start = start;
    }

    public void computeResults(Context context) {
        String email = new LocalAuthStore(context).currentEmail();
        CreditCaseStore store = new CreditCaseStore(context);
        int questBonus = store.loadBonusPoints(email);
        int[] habitsSelections = store.loadHabitsSelections(email);
        int habitsBonus = HabitsScorer.score(habitsSelections);
        data.habitsReasons = HabitsScorer.reasons(habitsSelections);

        data.trust = new TrustworthinessCalculator().score(data.analysis, Math.max(0, data.externalRating),
                data.telegram, data.telegramConsent, questBonus, habitsBonus, data.employmentType);

        CreditScoreCalculator calc = new CreditScoreCalculator();
        data.score = calc.score(Math.max(0, data.externalRating), data.analysis);
        data.loanEval = new LoanCalculator().evaluate(data.loan, data.analysis);
        data.optimization = new OptimizationEngine(calc)
                .build(Math.max(0, data.externalRating), data.analysis, data.score.finalScore);
        data.approval = new LoanApprovalEstimator().estimate(data.trust, data.loanEval, data.analysis);
    }

    public void reset() {
        data = new Assessment();
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
