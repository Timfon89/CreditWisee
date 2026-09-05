package com.creditwise.app.ui;

import com.creditwise.app.R;

/** Maps a {@code LoanApprovalEstimate.Band} name ("HIGH"/"MEDIUM"/"LOW") to its display color. */
final class ApprovalColors {

    private ApprovalColors() {}

    static int colorRes(String band) {
        if ("HIGH".equals(band)) return R.color.score_high;
        if ("MEDIUM".equals(band)) return R.color.score_mid;
        return R.color.score_low;
    }
}
