package com.creditwise.app.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/** A small animated income-vs-expense line chart across months, drawn with Canvas (no chart
 *  library) to match the rest of the app's hand-rolled DonutChartView/ScoreGaugeView style. */
public class TrendChartView extends View {

    public static final class Point {
        public final String label;
        public final float income;
        public final float expense;

        public Point(String label, float income, float expense) {
            this.label = label;
            this.income = income;
            this.expense = expense;
        }
    }

    private static final String LEGEND_INCOME = "Доход";
    private static final String LEGEND_EXPENSE = "Расход";
    private static final String[] RU_MONTH_SHORT = {
            "янв", "фев", "мар", "апр", "май", "июн", "июл", "авг", "сен", "окт", "ноя", "дек"
    };

    /** e.g. YearMonth.of(2026, 3) -> "мар '26" — used as the x-axis label for a data point. */
    public static String shortRuMonth(java.time.YearMonth ym) {
        return RU_MONTH_SHORT[ym.getMonthValue() - 1] + " '" + String.format(java.util.Locale.ROOT, "%02d", ym.getYear() % 100);
    }

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint incomeLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint expenseLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint incomeDot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint expenseDot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint legendPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private List<Point> points = Collections.emptyList();
    private float animatedFraction = 0f;
    @Nullable private ValueAnimator animator;

    private final float padLeft, padRight, padTop, padBottom, dotRadius, density;

    public TrendChartView(Context c) {
        this(c, null);
    }

    public TrendChartView(Context c, @Nullable AttributeSet a) {
        super(c, a);
        density = getResources().getDisplayMetrics().density;

        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(density);
        gridPaint.setColor(resolveThemeColor(android.R.attr.textColorSecondary, Color.GRAY));
        gridPaint.setAlpha(35);

        incomeLine.setStyle(Paint.Style.STROKE);
        incomeLine.setStrokeWidth(2.5f * density);
        incomeLine.setStrokeJoin(Paint.Join.ROUND);
        incomeLine.setStrokeCap(Paint.Cap.ROUND);

        expenseLine.setStyle(Paint.Style.STROKE);
        expenseLine.setStrokeWidth(2.5f * density);
        expenseLine.setStrokeJoin(Paint.Join.ROUND);
        expenseLine.setStrokeCap(Paint.Cap.ROUND);

        incomeDot.setStyle(Paint.Style.FILL);
        expenseDot.setStyle(Paint.Style.FILL);

        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(10.5f * density);
        labelPaint.setColor(resolveThemeColor(android.R.attr.textColorSecondary, Color.DKGRAY));

        legendPaint.setTextAlign(Paint.Align.LEFT);
        legendPaint.setTextSize(11.5f * density);
        legendPaint.setFakeBoldText(true);

        padLeft = 6 * density;
        padRight = 6 * density;
        padTop = 26 * density;
        padBottom = 20 * density;
        dotRadius = 3.5f * density;
    }

    private int resolveThemeColor(int attr, int fallback) {
        TypedValue tv = new TypedValue();
        if (getContext().getTheme().resolveAttribute(attr, tv, true)
                && tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return tv.data;
        }
        return fallback;
    }

    /** Replaces the series and animates the lines sweeping in left-to-right. Needs 2+ points
     *  to draw anything meaningful; fewer just clears the canvas. */
    public void setSeries(List<Point> newPoints, int incomeColor, int expenseColor) {
        points = newPoints;
        incomeLine.setColor(incomeColor);
        expenseLine.setColor(expenseColor);
        incomeDot.setColor(incomeColor);
        expenseDot.setColor(expenseColor);

        if (animator != null) animator.cancel();
        animatedFraction = 0f;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(900);
        animator.setStartDelay(100);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(v -> {
            animatedFraction = (float) v.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (points.size() < 2) return;

        float innerLeft = padLeft;
        float innerRight = w - padRight;
        float innerTop = padTop;
        float innerBottom = h - padBottom;
        float innerHeight = innerBottom - innerTop;

        float maxValue = 1f;
        for (Point p : points) maxValue = Math.max(maxValue, Math.max(p.income, p.expense));

        int n = points.size();
        float stepX = (innerRight - innerLeft) / (n - 1);

        // grid: baseline + midline
        canvas.drawLine(innerLeft, innerBottom, innerRight, innerBottom, gridPaint);
        canvas.drawLine(innerLeft, innerTop + innerHeight / 2f, innerRight, innerTop + innerHeight / 2f, gridPaint);

        Path incomePath = new Path();
        Path expensePath = new Path();
        float[] xs = new float[n];
        float[] incomeY = new float[n];
        float[] expenseY = new float[n];

        for (int i = 0; i < n; i++) {
            Point p = points.get(i);
            float x = innerLeft + i * stepX;
            xs[i] = x;
            incomeY[i] = innerBottom - innerHeight * (p.income / maxValue);
            expenseY[i] = innerBottom - innerHeight * (p.expense / maxValue);

            if (i == 0) {
                incomePath.moveTo(x, incomeY[i]);
                expensePath.moveTo(x, expenseY[i]);
            } else {
                incomePath.lineTo(x, incomeY[i]);
                expensePath.lineTo(x, expenseY[i]);
            }
        }

        // x-axis labels: however many months there are, pick an evenly-spaced subset that
        // actually fits without overlapping — always including the first and last month.
        float widestLabel = 0f;
        for (Point p : points) widestLabel = Math.max(widestLabel, labelPaint.measureText(p.label));
        float labelSlot = widestLabel + 10 * density;
        int maxLabels = Math.max(2, (int) ((innerRight - innerLeft) / labelSlot) + 1);
        int labelCount = Math.min(n, maxLabels);
        boolean[] showLabel = new boolean[n];
        if (labelCount <= 1) {
            showLabel[n - 1] = true;
        } else {
            for (int k = 0; k < labelCount; k++) {
                showLabel[Math.round(k * (n - 1) / (float) (labelCount - 1))] = true;
            }
        }
        for (int i = 0; i < n; i++) {
            if (showLabel[i]) canvas.drawText(points.get(i).label, xs[i], h - 4 * density, labelPaint);
        }

        // reveal the lines left-to-right as they animate in, rather than growing them vertically —
        // reads more clearly as "this is a timeline" than a height-only grow does.
        int save = canvas.save();
        canvas.clipRect(innerLeft, 0, innerLeft + (innerRight - innerLeft) * animatedFraction, h);
        canvas.drawPath(expensePath, expenseLine);
        canvas.drawPath(incomePath, incomeLine);
        for (int i = 0; i < n; i++) {
            canvas.drawCircle(xs[i], expenseY[i], dotRadius, expenseDot);
            canvas.drawCircle(xs[i], incomeY[i], dotRadius, incomeDot);
        }
        canvas.restoreToCount(save);

        // legend, top-left — always fully visible, not part of the reveal
        float legendY = 12 * density;
        canvas.drawCircle(innerLeft + 4 * density, legendY - 3.5f * density, dotRadius, incomeDot);
        legendPaint.setColor(incomeLine.getColor());
        canvas.drawText(LEGEND_INCOME, innerLeft + 12 * density, legendY, legendPaint);
        float expenseLegendX = innerLeft + 12 * density + legendPaint.measureText(LEGEND_INCOME) + 16 * density;
        canvas.drawCircle(expenseLegendX, legendY - 3.5f * density, dotRadius, expenseDot);
        legendPaint.setColor(expenseLine.getColor());
        canvas.drawText(LEGEND_EXPENSE, expenseLegendX + 8 * density, legendY, legendPaint);
    }
}
