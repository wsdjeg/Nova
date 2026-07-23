package net.wsdjeg.nova;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.style.ReplacementSpan;
import androidx.annotation.NonNull;

/**
 * 自定义行内代码 Span，控制背景高度避免连续行内代码背景相连。
 *
 * Markwon 默认 CodeSpan 使用 MetricAffectingSpan + bgColor，
 * 背景高度接近行高，导致连续行代码背景之间没有间隙。
 * 本 Span 使用 ReplacementSpan 完全控制绘制：
 * - 背景高度仅覆盖代码文字 + 少量内边距（不占满行高）
 * - 圆角矩形背景
 * - 等宽字体 + 0.87x 文字大小（与 Markwon 默认比例一致）
 */
public class InlineCodeSpan extends ReplacementSpan {

    private static final float TEXT_SIZE_RATIO = 0.87f;
    private static final float CORNER_RADIUS_DP = 3f;
    private static final float VERTICAL_PADDING_DP = 1.5f;

    private final int backgroundColor;
    private final float density;

    /**
     * @param backgroundColor 行内代码背景色，传 0 则在绘制时根据文字颜色自动计算（alpha=25）
     * @param density         屏幕密度，用于 dp -> px 转换
     */
    public InlineCodeSpan(int backgroundColor, float density) {
        this.backgroundColor = backgroundColor;
        this.density = density;
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text, int start, int end,
                       Paint.FontMetricsInt fm) {
        float originalSize = paint.getTextSize();
        Typeface originalTypeface = paint.getTypeface();

        paint.setTextSize(originalSize * TEXT_SIZE_RATIO);
        paint.setTypeface(Typeface.MONOSPACE);
        int width = Math.round(paint.measureText(text, start, end));

        paint.setTextSize(originalSize);
        paint.setTypeface(originalTypeface);
        return width;
    }

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end,
                     float x, int top, int y, int bottom, @NonNull Paint paint) {
        float originalSize = paint.getTextSize();
        int originalColor = paint.getColor();
        Typeface originalTypeface = paint.getTypeface();

        // Apply code text style
        paint.setTextSize(originalSize * TEXT_SIZE_RATIO);
        paint.setTypeface(Typeface.MONOSPACE);

        // Get code text metrics
        Paint.FontMetrics codeFm = paint.getFontMetrics();
        float codeTextHeight = codeFm.descent - codeFm.ascent;

        // Calculate vertically centered background (smaller than line height)
        float lineCenter = (top + bottom) / 2f;
        float vPad = VERTICAL_PADDING_DP * density;
        float bgTop = lineCenter - codeTextHeight / 2f - vPad;
        float bgBottom = lineCenter + codeTextHeight / 2f + vPad;

        float textWidth = paint.measureText(text, start, end);

        // Draw rounded rectangle background
        int bgColor = backgroundColor != 0
                ? backgroundColor
                : (originalColor & 0x00FFFFFF) | (25 << 24);
        paint.setColor(bgColor);
        paint.setStyle(Paint.Style.FILL);
        float cornerRadius = CORNER_RADIUS_DP * density;
        RectF rect = new RectF(x, bgTop, x + textWidth, bgBottom);
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint);

        // Draw text centered vertically
        paint.setColor(originalColor);
        float codeBaseline = lineCenter - (codeFm.ascent + codeFm.descent) / 2f;
        canvas.drawText(text, start, end, x, codeBaseline, paint);

        // Restore paint
        paint.setTextSize(originalSize);
        paint.setColor(originalColor);
        paint.setTypeface(originalTypeface);
        paint.setStyle(Paint.Style.FILL);
    }
}

