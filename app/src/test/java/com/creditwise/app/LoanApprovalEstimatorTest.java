package com.creditwise.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.creditwise.app.data.model.LoanApprovalEstimate;
import com.creditwise.app.data.model.LoanEvaluation;
import com.creditwise.app.data.model.StatementAnalysis;
import com.creditwise.app.data.model.TrustworthinessScore;
import com.creditwise.app.domain.LoanApprovalEstimator;

import org.junit.Test;

public class LoanApprovalEstimatorTest {

    private TrustworthinessScore trust(int total) {
        TrustworthinessScore s = new TrustworthinessScore();
        s.total = total;
        s.factors.add(new TrustworthinessScore.Factor("Test factor", "detail", total / 100.0, total));
        return s;
    }

    @Test
    public void highTrustAndLowDtiGivesHighBand() {
        LoanEvaluation loan = new LoanEvaluation();
        loan.dti = 0.2;
        LoanApprovalEstimate e = new LoanApprovalEstimator().estimate(trust(90), loan, new StatementAnalysis());
        assertEquals(LoanApprovalEstimate.Band.HIGH, e.band);
        assertTrue(e.probabilityPercent >= 70);
    }

    @Test
    public void highDtiPullsBandDown() {
        LoanEvaluation loan = new LoanEvaluation();
        loan.dti = 0.6;
        LoanApprovalEstimate e = new LoanApprovalEstimator().estimate(trust(90), loan, new StatementAnalysis());
        assertTrue(e.probabilityPercent < 90);
        assertFalse(e.reasons.isEmpty());
    }

    @Test
    public void lowTrustGivesLowBand() {
        LoanEvaluation loan = new LoanEvaluation();
        loan.dti = 0.2;
        LoanApprovalEstimate e = new LoanApprovalEstimator().estimate(trust(20), loan, new StatementAnalysis());
        assertEquals(LoanApprovalEstimate.Band.LOW, e.band);
        assertFalse(e.reasons.isEmpty());
    }

    @Test
    public void probabilityNeverHitsHardZero() {
        LoanEvaluation loan = new LoanEvaluation();
        loan.dti = 0.9;
        LoanApprovalEstimate e = new LoanApprovalEstimator().estimate(trust(0), loan, new StatementAnalysis());
        assertTrue(e.probabilityPercent >= 3);
    }
}
