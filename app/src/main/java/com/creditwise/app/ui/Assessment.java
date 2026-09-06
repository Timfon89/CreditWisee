package com.creditwise.app.ui;

import com.creditwise.app.data.model.ChallengeState;
import com.creditwise.app.data.model.EmploymentType;
import com.creditwise.app.data.model.ParseResult;
import com.creditwise.app.data.model.ScoreBreakdown;
import com.creditwise.app.data.model.StatementAnalysis;
import com.creditwise.app.data.model.TelegramScanResult;
import com.creditwise.app.data.model.TrustworthinessScore;
import com.creditwise.app.domain.OptimizationEngine;

import java.util.ArrayList;
import java.util.List;

/** Everything gathered during one assessment session. */
public class Assessment {
    public int externalRating = -1;
    public EmploymentType employmentType = EmploymentType.EMPLOYEE;
    /** One entry per successfully-uploaded statement; usually one, optionally several accounts. */
    public final List<ParseResult> statements = new ArrayList<>();
    public StatementAnalysis analysis;
    public List<String> transferNotes = new ArrayList<>();

    /** Only populated while reviewing a fresh scan on the "Задания" tab — the resulting
     *  adjustment is persisted (see {@code CreditCaseStore#saveTelegramAdjustment}) and this
     *  live scan isn't needed once that's done. */
    public TelegramScanResult telegram;
    public boolean telegramConsent;

    public TrustworthinessScore trust;   // primary 0–100 result
    public ScoreBreakdown score;         // extended 0–999 result
    public OptimizationEngine.Result optimization;
    public List<String> habitsReasons = new ArrayList<>();
    public ChallengeState challenges;
}
