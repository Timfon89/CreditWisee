package com.creditwise.app.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;

/** A 240° arc gauge for a 0–999 score with an animated sweep. */
public class ScoreGaugeView extends View {

    private static final float START_ANGLE = 150f;
    private static final float SWEEP = 240f;

    private int max = 999;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint numberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint capPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc = new RectF();

    private int targetScore = 0;
    private float animatedScore = 0f;
    @Nullable private ValueAnimator animator;

    public ScoreGaugeView(Context c) { this(c, null); }

    public ScoreGaugeView(Context c, @Nullable AttributeSet a) {
        super(c, a);
        float density = getResources().getDisplayMetrics().density;

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(14 * density);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setColor(0x33FFFFFF);

        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(14 * density);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setColor(Color.WHITE);

        capPaint.setColor(Color.WHITE);

        numberPaint.setColor(Color.WHITE);
        numberPaint.setTextAlign(Paint.Align.CENTER);
        numberPaint.setFakeBoldText(true);
        numberPaint.setTextSize(44 * density);
    }

    /** Sets the upper bound of the scale (e.g. 100 or 999). */
    public void setRange(int max) {
        this.max = Math.max(1, max);
        invalidate();
    }

    public void setScore(int score) {
        this.targetScore = Math.max(0, Math.min(max, score));
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(animatedScore, targetScore);
        animator.setDuration(900);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(v -> {
            animatedScore = (float) v.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float density = getResources().getDisplayMetrics().density;
        float pad = 12 * density;
        // Arc spans 240°: vertical extent is R (top) + R*sin(30°) (ends) = 1.5R.
        float radius = Math.min((w - 2 * pad) / 2f, (h - 2 * pad) / 1.5f);
        float cx = w / 2f;
        float cy = pad + radius;
        arc.set(cx - radius, cy - radius, cx + radius, cy + radius);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawArc(arc, START_ANGLE, SWEEP, false, trackPaint);
        float fraction = animatedScore / max;
        canvas.drawArc(arc, START_ANGLE, SWEEP * fraction, false, progressPaint);
        canvas.drawText(String.valueOf(Math.round(animatedScore)),
                arc.centerX(), arc.centerY() + numberPaint.getTextSize() / 3f, numberPaint);
    }
}
