package com.creditwise.app.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A part-to-whole ring chart: rounded, gapped arcs with an animated sweep and a center label.
 *  Optionally tappable — see {@link #setOnSegmentTapListener}. */
public class DonutChartView extends View {

    public static final class Segment {
        public final String label;
        public final float value;
        public final int color;

        public Segment(String label, float value, int color) {
            this.label = label;
            this.value = value;
            this.color = color;
        }
    }

    /** Called when the user taps a visible slice of the ring. */
    public interface OnSegmentTapListener {
        void onSegmentTap(Segment segment);
    }

    /** Ring thickness as a share of the view's diameter — keeps the "hole" a sensible size
     *  whether the view is a small 120dp ring or a large 200dp chart, instead of a fixed dp
     *  stroke that looks thin on bigger instances. */
    private static final float STROKE_SHARE = 0.13f;
    private static final float GAP_DEGREES = 4f;
    private static final float START_ANGLE = -90f;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint bigPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint smallPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final float density;

    private List<Segment> segments = Collections.emptyList();
    // one {startAngle, finalSweep} pair per segment with value > 0, in the same order they appear
    private final List<float[]> arcs = new ArrayList<>();
    // hit-test data per visible segment, parallel to the loop order in setSegments/onDraw
    private final List<HitSegment> hitSegments = new ArrayList<>();

    private static final class HitSegment {
        final Segment segment;
        final float relStart; // degrees clockwise from START_ANGLE, 0..360
        final float sweep;    // full slice sweep, not reduced by the gap — easier to tap

        HitSegment(Segment segment, float relStart, float sweep) {
            this.segment = segment;
            this.relStart = relStart;
            this.sweep = sweep;
        }
    }

    @Nullable private OnSegmentTapListener tapListener;

    private String centerBig = "";
    private String centerSmall = "";
    private boolean centerIsCount = false;
    private int centerCountTarget = 0;

    private float animatedFraction = 0f;
    @Nullable private ValueAnimator animator;

    public DonutChartView(Context c) {
        this(c, null);
    }

    public DonutChartView(Context c, @Nullable AttributeSet a) {
        super(c, a);
        density = getResources().getDisplayMetrics().density;

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setColor(resolveThemeColor(android.R.attr.textColorSecondary, Color.GRAY));
        trackPaint.setAlpha(38);

        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);

        bigPaint.setTextAlign(Paint.Align.CENTER);
        bigPaint.setFakeBoldText(true);
        bigPaint.setTextSize(26 * density);
        bigPaint.setColor(resolveThemeColor(android.R.attr.textColorPrimary, Color.BLACK));

        smallPaint.setTextAlign(Paint.Align.CENTER);
        smallPaint.setTextSize(12 * density);
        smallPaint.setColor(resolveThemeColor(android.R.attr.textColorSecondary, Color.DKGRAY));
    }

    /** Set to be notified when the user taps a slice — e.g. to show its name somewhere. */
    public void setOnSegmentTapListener(@Nullable OnSegmentTapListener listener) {
        tapListener = listener;
        setClickable(listener != null);
    }

    private int resolveThemeColor(int attr, int fallback) {
        TypedValue tv = new TypedValue();
        if (getContext().getTheme().resolveAttribute(attr, tv, true)
                && tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return tv.data;
        }
        return fallback;
    }

    /** Big/small two-line label drawn in the hole of the ring. */
    public void setCenterText(String big, String small) {
        centerBig = big;
        centerSmall = small;
        centerIsCount = false;
        invalidate();
    }

    /**
     * Like {@link #setCenterText}, but the big number counts up from zero in sync with the ring's
     * own sweep-in animation (call together with {@link #setSegments}) instead of popping in.
     */
    public void setCenterCount(int target, String small) {
        centerCountTarget = target;
        centerSmall = small;
        centerIsCount = true;
        invalidate();
    }

    /** Replaces the segments and animates the sweep in from zero. Non-positive values are dropped. */
    public void setSegments(List<Segment> newSegments) {
        setSegments(newSegments, -1f);
    }

    /**
     * Same as {@link #setSegments(List)}, but the ring is filled against {@code explicitTotal}
     * rather than the sum of the segment values — pass a target/max to leave the rest of the
     * ring as bare track, e.g. a single "elapsed" segment against a day count (a progress ring).
     * A non-positive {@code explicitTotal} falls back to the sum of the values.
     */
    public void setSegments(List<Segment> newSegments, float explicitTotal) {
        segments = newSegments;
        arcs.clear();
        hitSegments.clear();

        float sum = 0f;
        int visible = 0;
        for (Segment s : segments) {
            if (s.value > 0f) {
                sum += s.value;
                visible++;
            }
        }
        float total = explicitTotal > 0f ? explicitTotal : sum;
        float gap = visible > 1 ? GAP_DEGREES : 0f;

        if (total > 0f) {
            float angle = START_ANGLE;
            for (Segment s : segments) {
                if (s.value <= 0f) continue;
                float slice = s.value / total * 360f;
                arcs.add(new float[]{angle, Math.max(0f, slice - gap)});
                hitSegments.add(new HitSegment(s, angle - START_ANGLE, slice));
                angle += slice;
            }
        }

        if (animator != null) animator.cancel();
        animatedFraction = 0f;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(750);
        animator.setStartDelay(100);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(v -> {
            animatedFraction = (float) v.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float stroke = Math.min(w, h) * STROKE_SHARE;
        trackPaint.setStrokeWidth(stroke);
        arcPaint.setStrokeWidth(stroke);

        float pad = stroke / 2f + 2f * density;
        float radius = Math.min(w, h) / 2f - pad;
        float cx = w / 2f;
        float cy = h / 2f;
        oval.set(cx - radius, cy - radius, cx + radius, cy + radius);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (tapListener != null && event.getAction() == MotionEvent.ACTION_UP) {
            handleTap(event.getX(), event.getY());
            performClick();
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private void handleTap(float x, float y) {
        if (hitSegments.isEmpty()) return;
        float dx = x - oval.centerX();
        float dy = y - oval.centerY();
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float radius = oval.width() / 2f;
        float strokeHalf = arcPaint.getStrokeWidth() / 2f;
        float slop = 6f * density;
        if (dist < radius - strokeHalf - slop || dist > radius + strokeHalf + slop) return;

        float touchAngle = (float) Math.toDegrees(Math.atan2(dy, dx));
        float rel = touchAngle - START_ANGLE;
        rel = ((rel % 360f) + 360f) % 360f;

        for (HitSegment hs : hitSegments) {
            if (rel >= hs.relStart && rel < hs.relStart + hs.sweep) {
                tapListener.onSegmentTap(hs.segment);
                return;
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawOval(oval, trackPaint);

        int i = 0;
        for (Segment s : segments) {
            if (s.value <= 0f || i >= arcs.size()) continue;
            float[] arc = arcs.get(i++);
            arcPaint.setColor(s.color);
            canvas.drawArc(oval, arc[0], arc[1] * animatedFraction, false, arcPaint);
        }

        float cx = oval.centerX();
        float cy = oval.centerY();
        String bigText = centerIsCount ? String.valueOf(Math.round(centerCountTarget * animatedFraction)) : centerBig;
        boolean hasSmall = !TextUtils.isEmpty(centerSmall);
        boolean hasBig = !TextUtils.isEmpty(bigText);

        if (hasBig && hasSmall) {
            // Two centered lines: the big value above the midline, the label below it.
            canvas.drawText(bigText, cx, cy - bigPaint.getTextSize() * 0.15f, bigPaint);
            float maxWidth = oval.width() - arcPaint.getStrokeWidth() * 2.4f;
            CharSequence label = TextUtils.ellipsize(centerSmall, smallPaint, maxWidth, TextUtils.TruncateAt.END);
            canvas.drawText(label, 0, label.length(), cx, cy + smallPaint.getTextSize() * 1.15f, smallPaint);
        } else if (hasBig) {
            canvas.drawText(bigText, cx, cy + bigPaint.getTextSize() / 3f, bigPaint);
        }
    }
}
