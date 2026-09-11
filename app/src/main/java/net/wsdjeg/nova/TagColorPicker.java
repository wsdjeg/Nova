package net.wsdjeg.nova;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

/**
 * 账户标签颜色取色器（公共组件）
 *
 * 供 SettingsActivity（全局默认颜色）与 AccountEditActivity（单账号覆盖颜色）
 * 复用，替代两处近乎重复的取色器实现，并统一颜色索引语义：
 *
 *   -1 = 自动（全局设置页）/ 跟随全局（账号编辑页），对应 SettingsManager.AUTO_COLOR_INDEX
 *   0-4 = 固定颜色，对应 SettingsManager.ACCOUNT_TAG_COLORS[0..4]
 */
public class TagColorPicker {

    /** 颜色选中回调：colorIndex 为 -1（自动/跟随全局）或 0-4 */
    public interface OnColorSelectedListener {
        void onColorSelected(int colorIndex);
    }

    /** 「自动」选项的渐变色（彩虹效果） */
    private static final int[] AUTO_GRADIENT_COLORS = {
        Color.parseColor("#FF6B6B"),
        Color.parseColor("#4ECDC4"),
        Color.parseColor("#45B7D1"),
        Color.parseColor("#F7DC6F")
    };

    private final View[] colorViews; // [0]=自动项, [1..5]=颜色项
    private final OnColorSelectedListener listener;
    private int selectedColorIndex = SettingsManager.AUTO_COLOR_INDEX;

    /**
     * 构建取色器并填充到指定容器
     *
     * @param context        上下文
     * @param container      横向 LinearLayout 容器
     * @param autoLabel      自动选项的中央文字（如 "A"），传 null 表示无文字
     * @param circleSizeDp   圆点尺寸（dp）
     * @param circleMarginDp 圆点左右间距（dp）
     * @param listener       选中回调（存储索引语义：-1 或 0-4），可为 null
     */
    public TagColorPicker(Context context, LinearLayout container, String autoLabel,
            int circleSizeDp, int circleMarginDp, OnColorSelectedListener listener) {
        this.listener = listener;
        this.colorViews = new View[SettingsManager.ACCOUNT_TAG_COLORS.length + 1];

        int size = dpToPx(context, circleSizeDp);
        int margin = dpToPx(context, circleMarginDp);

        // 自动项（渐变圆）
        colorViews[0] = buildAutoOption(context, container, size, margin, autoLabel);

        // 颜色项
        for (int i = 0; i < SettingsManager.ACCOUNT_TAG_COLORS.length; i++) {
            final int colorIndex = i;
            View colorView = new View(context);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMargins(margin, 0, margin, 0);
            colorView.setLayoutParams(params);
            colorView.setOnClickListener(v -> select(colorIndex));
            container.addView(colorView);
            colorViews[i + 1] = colorView;
        }

        updateSelectionVisuals();
    }

    /** 创建「自动」选项视图 */
    private View buildAutoOption(Context context, LinearLayout container,
            int size, int margin, String label) {
        FrameLayout autoView = new FrameLayout(context);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
        params.setMargins(margin, 0, margin, 0);
        autoView.setLayoutParams(params);
        autoView.setOnClickListener(v -> select(SettingsManager.AUTO_COLOR_INDEX));

        if (label != null && !label.isEmpty()) {
            TextView labelView = new TextView(context);
            labelView.setText(label);
            labelView.setTextColor(Color.WHITE);
            labelView.setTextSize(14);
            labelView.setGravity(Gravity.CENTER);
            labelView.setTypeface(null, Typeface.BOLD);
            FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
            textParams.gravity = Gravity.CENTER;
            labelView.setLayoutParams(textParams);
            autoView.addView(labelView);
        }

        container.addView(autoView);
        return autoView;
    }

    /** 用户点击选中颜色（存储索引：-1 或 0-4），触发回调 */
    private void select(int colorIndex) {
        selectedColorIndex = colorIndex;
        updateSelectionVisuals();
        if (listener != null) {
            listener.onColorSelected(colorIndex);
        }
    }

    /** 恢复选中状态（存储索引：-1 或 0-4，非法值归一化为 -1），不触发回调 */
    public void setSelected(int colorIndex) {
        selectedColorIndex = normalize(colorIndex);
        updateSelectionVisuals();
    }

    /** 当前选中的存储索引（-1 或 0-4） */
    public int getSelected() {
        return selectedColorIndex;
    }

    /** 归一化颜色索引：有效范围为 0..ACCOUNT_TAG_COLORS.length-1，其余归为 -1 */
    private int normalize(int colorIndex) {
        if (colorIndex >= 0 && colorIndex < SettingsManager.ACCOUNT_TAG_COLORS.length) {
            return colorIndex;
        }
        return SettingsManager.AUTO_COLOR_INDEX;
    }

    /** 重绘所有圆点与选中描边 */
    private void updateSelectionVisuals() {
        int selectedViewIndex = selectedColorIndex + 1; // 0=自动项
        for (int i = 0; i < colorViews.length; i++) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.OVAL);

            if (i == 0) {
                // 自动项使用彩虹渐变
                drawable.setColors(AUTO_GRADIENT_COLORS);
                drawable.setGradientType(GradientDrawable.SWEEP_GRADIENT);
            } else {
                drawable.setColor(Color.parseColor(SettingsManager.ACCOUNT_TAG_COLORS[i - 1]));
            }

            // 选中的选项加描边
            if (i == selectedViewIndex) {
                drawable.setStroke(4, ContextCompat.getColor(
                        colorViews[i].getContext(), R.color.primary));
            }

            colorViews[i].setBackground(drawable);
        }
    }

    private static int dpToPx(Context context, int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }
}

