package net.wsdjeg.nova;

import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.style.MetricAffectingSpan;
import androidx.annotation.NonNull;

/**
 * 自定义行内代码 Span。
 *
 * ⚠️ 必须使用 MetricAffectingSpan（不能用 ReplacementSpan）。
 *
 * ReplacementSpan 在 StaticLayout 中被视为不可分割的原子单元，
 * 即使文本中插入了 ZWSP（零宽空格），StaticLayout 也无法在 span
 * 内部断行。这会导致表格单元格中的长代码标识符溢出列宽。
 *
 * MetricAffectingSpan 仅修改 TextPaint 属性（字体、字号、背景色），
 * 不替换文本绘制，因此 StaticLayout 可以正常在 span 内部断行。
 * ZWSP 插入的断行点也能正确生效。
 *
 * 背景色通过 TextPaint.bgColor 实现，由 StaticLayout 自动绘制。
 * 相比 ReplacementSpan 的圆角矩形背景，bgColor 为全行高矩形，
 * 但换来了关键的文本换行能力。
 */
public class InlineCodeSpan extends MetricAffectingSpan {

    private static final float TEXT_SIZE_RATIO = 0.87f;

    private final int backgroundColor;
    private final float density;

    /**
     * @param backgroundColor 行内代码背景色，传 0 则在绘制时根据文字颜色自动计算（alpha=25）
     * @param density         屏幕密度（保留兼容性，当前未使用）
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

        int bgColor = backgroundColor != 0
                ? backgroundColor
                : (paint.getColor() & 0x00FFFFFF) | (25 << 24);
        paint.bgColor = bgColor;
    }
}

