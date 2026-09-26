package net.wsdjeg.nova;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.LeadingMarginSpan;
import android.text.style.LineBackgroundSpan;
import android.text.style.MetricAffectingSpan;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.noties.markwon.core.spans.TextLayoutSpan;

/**
 * 自定义行内代码 Span。
 *
 * 一个类实现两个接口，协同完成行内代码的渲染：
 *
 * 1. MetricAffectingSpan —— 修改文本测量属性（等宽字体、0.87 倍字号）。
 *    ⚠️ 不能改用 ReplacementSpan：ReplacementSpan 在 StaticLayout 中被视为
 *    不可分割的原子单元，无法在其内部断行，表格单元格中的长代码标识符
 *    会溢出列宽。MetricAffectingSpan 仅修改 TextPaint 属性，不影响断行。
 *
 * 2. LineBackgroundSpan —— 自绘代码背景。
 *
 * 为什么不再使用 TextPaint.bgColor 绘制背景？
 *    AOSP 的 TextLine 绘制 bgColor 的矩形是
 *        drawRect(leftX, lineTop(n), rightX, lineTop(n + 1))
 *    即完整的行高（含 lineSpacingExtra），且相邻行边界连续
 *    （lineBottom(n) == lineTop(n + 1)）。当段落中上下两行都含行内代码时，
 *    两块背景零间隙垂直粘连，连成一大块。
 *    任何行高调整都无法产生间隙（背景永远填满行高），因此必须自绘。
 *
 *    自绘后背景在行内上下各内缩 BG_INSET_DP：
 *    - 相邻两行代码背景之间出现 2 x BG_INSET_DP 的清晰间隙，彼此独立；
 *    - 单行代码背景高度仅缩小 2 x BG_INSET_DP，文字位置、行高、行距
 *      完全不变，视觉效果与原实现基本一致。
 *
 * 水平定位策略：
 * - 表格单元格（Markwon TableRowSpan 使用嵌套 StaticLayout 绘制单元格）：
 *   通过 TextLayoutSpan.layoutOf() 拿到嵌套 Layout，用 getPrimaryHorizontal()
 *   精确查询文本坐标，自动处理单元格对齐（ALIGN_CENTER 等）。
 * - 普通段落：本应用所有渲染 Markdown 的 TextView 均未设置 gravity
 *   （即 ALIGN_NORMAL + LTR），行首 x = left + LeadingMarginSpan 累计缩进
 *   （列表、引用块等），再按文本测量推进到代码片段的起点。
 */
public class InlineCodeSpan extends MetricAffectingSpan implements LineBackgroundSpan {

    private static final float TEXT_SIZE_RATIO = 0.87f;

    /** 背景上下内缩量（dp）：相邻两行代码背景之间的间隙 = 2 x 该值 */
    private static final float BG_INSET_DP = 1.5f;

    private final int backgroundColor;
    private final float density;

    /** 背景画笔（成员复用，避免绘制期频繁分配） */
    private final Paint bgPaint = new Paint();

    /** 测量工作画笔：以行的 TextPaint 为基准，逐段应用 MetricAffectingSpan 后测量 */
    private final TextPaint workPaint = new TextPaint();

    /**
     * @param backgroundColor 行内代码背景色，传 0 则在绘制时根据文字颜色自动计算（alpha=25）
     * @param density         屏幕密度，用于把背景内缩量换算成像素
     */
    public InlineCodeSpan(int backgroundColor, float density) {
        this.backgroundColor = backgroundColor;
        this.density = density;
    }

    @Override
    public void updateMeasureState(@NonNull TextPaint paint) {
        apply(paint);
    }

    @Override
    public void updateDrawState(@NonNull TextPaint paint) {
        apply(paint);
    }

    private void apply(@NonNull TextPaint paint) {
        paint.setTypeface(Typeface.MONOSPACE);
        paint.setTextSize(paint.getTextSize() * TEXT_SIZE_RATIO);

        // 注意：这里刻意不设置 paint.bgColor。
        // bgColor 由系统按整行高矩形绘制，相邻行背景必然粘连（见类注释），
        // 背景改由 drawBackground() 自绘以控制垂直范围。
    }

    /**
     * 自绘行内代码背景。
     *
     * 每一个与代码相交的显示行都会调用一次本方法（代码跨行时，
     * 每行各自绘制一块背景），垂直范围在行内上下各内缩 BG_INSET_DP，
     * 因此上下相邻行的代码背景之间必然留出间隙。
     */
    @Override
    public void drawBackground(@NonNull Canvas canvas, @NonNull Paint paint,
                               int left, int right, int top, int baseline, int bottom,
                               @NonNull CharSequence text, int start, int end, int lnum) {
        if (!(text instanceof Spanned)) return;
        Spanned spanned = (Spanned) text;

        int spanStart = spanned.getSpanStart(this);
        int spanEnd = spanned.getSpanEnd(this);
        if (spanStart < 0 || spanEnd <= spanStart) return;

        // 本行与代码 span 的交集
        int codeStart = Math.max(spanStart, start);
        int codeEnd = Math.min(spanEnd, end);
        if (codeEnd <= codeStart) return;

        float x0;
        float x1;

        Layout layout = TextLayoutSpan.layoutOf(spanned);
        if (layout != null) {
            // 表格单元格：代码渲染在嵌套 Layout 中，
            // 直接查询精确坐标（自动处理对齐、缩进）
            float a = layout.getPrimaryHorizontal(codeStart);
            float b = layout.getPrimaryHorizontal(codeEnd);
            x0 = Math.min(a, b);
            x1 = Math.max(a, b);
        } else {
            // 普通段落：需要行基准画笔做文本测量
            if (!(paint instanceof TextPaint)) return;
            TextPaint base = (TextPaint) paint;

            float lineX = left + resolveLeadingMargin(spanned, start);
            x0 = lineX + measureSpannedWidth(base, spanned, start, codeStart);
            x1 = x0 + measureSpannedWidth(base, spanned, codeStart, codeEnd);
        }

        if (x1 <= x0) return;

        float inset = BG_INSET_DP * density;
        float bgTop = top + inset;
        float bgBottom = bottom - inset;
        // 行高过小时（极端字号/行距）退回整行高度，避免背景被压没
        if (bgBottom - bgTop < 2 * density) {
            bgTop = top;
            bgBottom = bottom;
        }

        bgPaint.setColor(resolveBackgroundColor(paint));
        bgPaint.setStyle(Paint.Style.FILL);
        canvas.drawRect(x0, bgTop, x1, bgBottom, bgPaint);
    }

    private int resolveBackgroundColor(Paint paint) {
        return backgroundColor != 0
                ? backgroundColor
                : (paint.getColor() & 0x00FFFFFF) | (25 << 24);
    }

    /**
     * 计算覆盖本行的 LeadingMarginSpan 的累计缩进（列表、引用块等）。
     * 段落首行判定与 Layout 一致：行首 offset 为 0，或前置字符是 '\n'。
     */
    private static float resolveLeadingMargin(Spanned spanned, int lineStart) {
        int queryEnd = Math.min(lineStart + 1, spanned.length());
        if (queryEnd <= lineStart) return 0f;

        LeadingMarginSpan[] spans =
                spanned.getSpans(lineStart, queryEnd, LeadingMarginSpan.class);
        if (spans == null || spans.length == 0) return 0f;

        boolean firstLine = lineStart == 0
                || spanned.charAt(lineStart - 1) == '\n';

        float margin = 0f;
        for (LeadingMarginSpan span : spans) {
            margin += span.getLeadingMargin(firstLine);
        }
        return margin;
    }

    /**
     * 测量 spanned 文本 [from, to) 的水平宽度。
     *
     * 按 MetricAffectingSpan 的边界把区间切成若干段，逐段应用该段上生效的
     * span 属性后测量，与系统 TextLine 的绘制行为保持一致。这样同一行内
     * "普通文字 + 代码" 混排（或一行内出现多个行内代码）时，背景的水平
     * 定位依然准确。
     */
    private float measureSpannedWidth(TextPaint base, Spanned spanned, int from, int to) {
        if (from >= to) return 0f;

        MetricAffectingSpan[] spans =
                spanned.getSpans(from, to, MetricAffectingSpan.class);
        if (spans == null || spans.length == 0) {
            return base.measureText(spanned, from, to);
        }

        // 收集段边界点：from、to 以及落在区间内的各 span 起止点
        int[] spanStarts = new int[spans.length];
        int[] spanEnds = new int[spans.length];
        List<Integer> points = new ArrayList<>(spans.length * 2 + 2);
        points.add(from);
        points.add(to);
        for (int i = 0; i < spans.length; i++) {
            int ss = spanned.getSpanStart(spans[i]);
            int se = spanned.getSpanEnd(spans[i]);
            spanStarts[i] = ss;
            spanEnds[i] = se;
            if (se > ss) {
                if (ss > from && ss < to) points.add(ss);
                if (se > from && se < to) points.add(se);
            }
        }
        Collections.sort(points);

        float width = 0f;
        for (int i = 0; i < points.size() - 1; i++) {
            int segStart = points.get(i);
            int segEnd = points.get(i + 1);
            if (segEnd <= segStart) continue;

            workPaint.set(base);
            for (int j = 0; j < spans.length; j++) {
                // 段被该 span 完全覆盖时才应用其属性
                if (spanStarts[j] <= segStart && spanEnds[j] >= segEnd) {
                    spans[j].updateMeasureState(workPaint);
                }
            }
            width += workPaint.measureText(spanned, segStart, segEnd);
        }
        return width;
    }
}

