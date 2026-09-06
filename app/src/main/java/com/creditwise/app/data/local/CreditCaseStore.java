package com.creditwise.app.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import com.creditwise.app.data.model.ChallengeState;
import com.creditwise.app.data.model.CreditCase;
import com.creditwise.app.data.model.EmploymentType;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Per-user local storage for saved assessments and ongoing-challenge progress. */
public final class CreditCaseStore {

    private static final String PREFS = "creditwise_cases";
    private static final int MAX_CASES = 50;

    private final SharedPreferences prefs;

    public CreditCaseStore(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------------ cases

    public List<CreditCase> loadCases(String email) {
        List<CreditCase> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(key(email, "cases"), "[]"));
            for (int i = 0; i < arr.length(); i++) out.add(fromJson(arr.getJSONObject(i)));
        } catch (JSONException ignored) {
            // corrupted local data - start fresh rather than crash
        }
        Collections.sort(out, (a, b) -> b.createdAt.compareTo(a.createdAt));
        return out;
    }

    public void saveCase(String email, CreditCase c) {
        List<CreditCase> cases = loadCases(email);
        cases.add(0, c);
        while (cases.size() > MAX_CASES) cases.remove(cases.size() - 1);
        JSONArray arr = new JSONArray();
        for (CreditCase cc : cases) arr.put(toJson(cc));
        prefs.edit().putString(key(email, "cases"), arr.toString()).apply();
    }

    public void deleteCase(String email, String id) {
        List<CreditCase> cases = loadCases(email);
        cases.removeIf(c -> c.id.equals(id));
        JSONArray arr = new JSONArray();
        for (CreditCase cc : cases) arr.put(toJson(cc));
        prefs.edit().putString(key(email, "cases"), arr.toString()).apply();
    }

    private static JSONObject toJson(CreditCase c) {
        try {
            JSONObject o = new JSONObject();
            o.put("id", c.id);
            o.put("createdAt", c.createdAt.toString());
            o.put("loanAmount", c.loanAmount);
            o.put("termMonths", c.termMonths);
            o.put("annualRatePercent", c.annualRatePercent);
            o.put("trustTotal", c.trustTotal);
            o.put("trustBand", c.trustBand);
            o.put("baseScore", c.baseScore);
            o.put("externalBuff", c.externalBuff);
            o.put("challengesBuff", c.challengesBuff);
            o.put("habitsBuff", c.habitsBuff);
            o.put("riskPenalty", c.riskPenalty);
            o.put("telegramAdjustment", c.telegramAdjustment);
            o.put("approvalPercent", c.approvalPercent);
            o.put("approvalBand", c.approvalBand);
            o.put("topDiscretionaryCategory", c.topDiscretionaryCategory);
            o.put("topDiscretionaryMonthlyAmount", c.topDiscretionaryMonthlyAmount);
            o.put("avgIncome", c.avgIncome);
            o.put("avgExpense", c.avgExpense);

            JSONArray factors = new JSONArray();
            for (CreditCase.FactorSnapshot f : c.baseFactors) {
                JSONObject fo = new JSONObject();
                fo.put("name", f.name);
                fo.put("detail", f.detail);
                fo.put("value", f.value);
                fo.put("points", f.points);
                factors.put(fo);
            }
            o.put("baseFactors", factors);

            JSONArray months = new JSONArray();
            for (CreditCase.MonthPoint mp : c.monthlySeries) {
                JSONObject mo = new JSONObject();
                mo.put("label", mp.label);
                mo.put("income", mp.income);
                mo.put("expense", mp.expense);
                months.put(mo);
            }
            o.put("monthlySeries", months);
            o.put("telegramReasons", new JSONArray(c.telegramReasons));
            o.put("approvalReasons", new JSONArray(c.approvalReasons));
            o.put("habitsReasons", new JSONArray(c.habitsReasons));
            o.put("riskReasons", new JSONArray(c.riskReasons));
            o.put("challengesReasons", new JSONArray(c.challengesReasons));
            return o;
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    private static CreditCase fromJson(JSONObject o) throws JSONException {
        CreditCase c = new CreditCase();
        c.id = o.getString("id");
        c.createdAt = LocalDateTime.parse(o.getString("createdAt"));
        c.loanAmount = o.getDouble("loanAmount");
        c.termMonths = o.getInt("termMonths");
        c.annualRatePercent = o.getDouble("annualRatePercent");
        c.trustTotal = o.getInt("trustTotal");
        c.trustBand = o.optString("trustBand", "");
        c.baseScore = o.optInt("baseScore", 0);
        c.externalBuff = o.optInt("externalBuff", 0);
        c.challengesBuff = o.optInt("challengesBuff", o.optInt("questBuff", 0));
        c.habitsBuff = o.optInt("habitsBuff", 0);
        c.riskPenalty = o.optInt("riskPenalty", 0);
        c.telegramAdjustment = o.optInt("telegramAdjustment", 0);
        c.approvalPercent = o.optInt("approvalPercent", 0);
        c.approvalBand = o.optString("approvalBand", "");
        c.topDiscretionaryCategory = o.optString("topDiscretionaryCategory", "");
        c.topDiscretionaryMonthlyAmount = o.optDouble("topDiscretionaryMonthlyAmount", 0);
        c.avgIncome = o.optDouble("avgIncome", 0);
        c.avgExpense = o.optDouble("avgExpense", 0);

        JSONArray factors = o.optJSONArray("baseFactors");
        if (factors != null) {
            for (int i = 0; i < factors.length(); i++) {
                JSONObject fo = factors.getJSONObject(i);
                CreditCase.FactorSnapshot f = new CreditCase.FactorSnapshot();
                f.name = fo.optString("name", "");
                f.detail = fo.optString("detail", "");
                f.value = fo.optDouble("value", 0);
                f.points = fo.optInt("points", 0);
                c.baseFactors.add(f);
            }
        }
        JSONArray tg = o.optJSONArray("telegramReasons");
        if (tg != null) for (int i = 0; i < tg.length(); i++) c.telegramReasons.add(tg.getString(i));
        JSONArray ar = o.optJSONArray("approvalReasons");
        if (ar != null) for (int i = 0; i < ar.length(); i++) c.approvalReasons.add(ar.getString(i));
        JSONArray hr = o.optJSONArray("habitsReasons");
        if (hr != null) for (int i = 0; i < hr.length(); i++) c.habitsReasons.add(hr.getString(i));
        JSONArray rr = o.optJSONArray("riskReasons");
        if (rr != null) for (int i = 0; i < rr.length(); i++) c.riskReasons.add(rr.getString(i));
        JSONArray cr = o.optJSONArray("challengesReasons");
        if (cr != null) for (int i = 0; i < cr.length(); i++) c.challengesReasons.add(cr.getString(i));
        JSONArray months = o.optJSONArray("monthlySeries");
        if (months != null) {
            for (int i = 0; i < months.length(); i++) {
                JSONObject mo = months.getJSONObject(i);
                CreditCase.MonthPoint mp = new CreditCase.MonthPoint();
                mp.label = mo.optString("label", "");
                mp.income = mo.optDouble("income", 0);
                mp.expense = mo.optDouble("expense", 0);
                c.monthlySeries.add(mp);
            }
        }
        return c;
    }

    // ------------------------------------------------------------ challenges

    public ChallengeState loadChallengeState(String email) {
        ChallengeState s = new ChallengeState();
        String raw = prefs.getString(key(email, "challenges"), null);
        if (raw == null) return s;
        try {
            JSONObject o = new JSONObject(raw);
            s.baselineExpense = o.optDouble("baselineExpense", 0);
            s.baselineMonth = o.optString("baselineMonth", "");
            s.monthsSinceBaselineReset = o.optInt("monthsSinceBaselineReset", 0);
            s.savingsPoints = o.optDouble("savingsPoints", 0);
            s.savingsConsecutiveMisses = o.optInt("savingsConsecutiveMisses", 0);
            s.regularityPoints = o.optDouble("regularityPoints", 0);
            s.regularityConsecutiveMisses = o.optInt("regularityConsecutiveMisses", 0);
            s.lastUpdatedAt = o.optString("lastUpdatedAt", "");

            addAll(o.optJSONArray("savingsHistory"), s.savingsHistory);
            addAll(o.optJSONArray("regularityHistory"), s.regularityHistory);
            addAll(o.optJSONArray("processedMonths"), s.processedMonths);
            addAll(o.optJSONArray("processedWeeks"), s.processedWeeks);
        } catch (JSONException ignored) {
            // corrupted local data - start fresh rather than crash
        }
        return s;
    }

    public void saveChallengeState(String email, ChallengeState s) {
        try {
            JSONObject o = new JSONObject();
            o.put("baselineExpense", s.baselineExpense);
            o.put("baselineMonth", s.baselineMonth);
            o.put("monthsSinceBaselineReset", s.monthsSinceBaselineReset);
            o.put("savingsPoints", s.savingsPoints);
            o.put("savingsConsecutiveMisses", s.savingsConsecutiveMisses);
            o.put("regularityPoints", s.regularityPoints);
            o.put("regularityConsecutiveMisses", s.regularityConsecutiveMisses);
            o.put("lastUpdatedAt", s.lastUpdatedAt);
            o.put("savingsHistory", new JSONArray(s.savingsHistory));
            o.put("regularityHistory", new JSONArray(s.regularityHistory));
            o.put("processedMonths", new JSONArray(s.processedMonths));
            o.put("processedWeeks", new JSONArray(s.processedWeeks));
            prefs.edit().putString(key(email, "challenges"), o.toString()).apply();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void addAll(@androidx.annotation.Nullable JSONArray arr, java.util.Collection<String> into) throws JSONException {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) into.add(arr.getString(i));
    }

    // ---------------------------------------------------------- habits quiz

    /** {@code null} if the user has never completed the questionnaire. */
    @androidx.annotation.Nullable
    public int[] loadHabitsSelections(String email) {
        String raw = prefs.getString(key(email, "habits"), null);
        if (raw == null) return null;
        try {
            JSONArray arr = new JSONArray(raw);
            int[] out = new int[arr.length()];
            for (int i = 0; i < arr.length(); i++) out[i] = arr.getInt(i);
            return out;
        } catch (JSONException e) {
            return null;
        }
    }

    public void saveHabitsSelections(String email, int[] selections) {
        JSONArray arr = new JSONArray();
        for (int s : selections) arr.put(s);
        prefs.edit().putString(key(email, "habits"), arr.toString()).apply();
    }

    // -------------------------------------------------------- employment type

    public EmploymentType loadEmploymentType(String email) {
        String raw = prefs.getString(key(email, "employment"), null);
        if (raw == null) return EmploymentType.EMPLOYEE;
        try {
            return EmploymentType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return EmploymentType.EMPLOYEE;
        }
    }

    public void saveEmploymentType(String email, EmploymentType type) {
        prefs.edit().putString(key(email, "employment"), type.name()).apply();
    }

    // -------------------------------------------------------- external bureau rating

    /** {@code -1} if the user has never entered one. */
    public int loadExternalRating(String email) {
        return prefs.getInt(key(email, "externalRating"), -1);
    }

    public void saveExternalRating(String email, int rating) {
        prefs.edit().putInt(key(email, "externalRating"), rating).apply();
    }

    private static String key(String email, String suffix) {
        return (email == null ? "anon" : email) + ":" + suffix;
    }
}
