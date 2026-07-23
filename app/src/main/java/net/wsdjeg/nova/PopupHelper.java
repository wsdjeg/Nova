package net.wsdjeg.nova;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import androidx.core.content.ContextCompat;

import java.util.List;

/**
 * 通用长按弹窗助手
 *
 * 提供统一的圆角 PopupWindow 风格，用于消息长按、会话长按等场景。
 * 弹窗在触点位置显示，自动处理边界检测。
 */
public final class PopupHelper {

    /** 弹窗菜单项 */
    public static class PopupItem {
        public final String text;
        public final boolean isDanger;   // 红色文字（删除等）
        public final Runnable onClick;

        public PopupItem(String text, Runnable onClick) {
            this(text, false, onClick);
        }

        public PopupItem(String text, boolean isDanger, Runnable onClick) {
            this.text = text;
            this.isDanger = isDanger;
            this.onClick = onClick;
        }
    }

    private PopupHelper() {}

    /**
     * 显示长按操作弹窗
     *
     * @param context    上下文
     * @param anchorView 锚定 View（用于 showAtLocation）
     * @param touchX     触点屏幕绝对坐标 X
     * @param touchY     触点屏幕绝对坐标 Y
     * @param items      菜单项列表
     */
    public static void show(Context context, View anchorView,
                            float touchX, float touchY,
                            List<PopupItem> items) {
        if (items == null || items.isEmpty()) return;

        int popupBg = ContextCompat.getColor(context, R.color.popup_bg);
        int popupText = ContextCompat.getColor(context, R.color.popup_text);
        int errorColor = ContextCompat.getColor(context, R.color.error);
        float density = context.getResources().getDisplayMetrics().density;
        int padH = (int) (6 * density + 0.5f);
        int padV = (int) (10 * density + 0.5f);
        int cornerRadius = (int) (8 * density + 0.5f);

        // 圆角背景
        GradientDrawable bgDrawable = new GradientDrawable();
        bgDrawable.setColor(popupBg);
        bgDrawable.setCornerRadius(cornerRadius);

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackground(bgDrawable);
        layout.setElevation(6 * density);
        layout.setClipToOutline(true);
        layout.setMinimumWidth((int) (128 * density + 0.5f));

        final PopupWindow[] popupHolder = new PopupWindow[1];

        for (int i = 0; i < items.size(); i++) {
            PopupItem item = items.get(i);

            // 分隔线（非第一项之前）
            if (i > 0) {
                View divider = new View(context);
                divider.setBackgroundColor(popupText);
                divider.setAlpha(0.12f);
                layout.addView(divider, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 1));
            }

            TextView tv = new TextView(context);
            tv.setText(item.text);
            tv.setTextColor(item.isDanger ? errorColor : popupText);
            tv.setTextSize(14);
            tv.setPadding(padH, padV, padH, padV);
            tv.setOnClickListener(v -> {
                if (item.onClick != null) item.onClick.run();
                if (popupHolder[0] != null) popupHolder[0].dismiss();
            });
            layout.addView(tv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        PopupWindow popup = new PopupWindow(layout,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setOutsideTouchable(true);
        popup.setElevation(6 * density);
        popupHolder[0] = popup;

        // 触点定位
        int offsetX = (int) touchX;
        int offsetY = (int) touchY - (int) (10 * density + 0.5f);

        // 测量布局
        layout.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int popupWidth = layout.getMeasuredWidth();
        int popupHeight = layout.getMeasuredHeight();

        popup.setWidth(popupWidth);
        popup.setHeight(popupHeight);

        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = context.getResources().getDisplayMetrics().heightPixels;

        // 右边界
        if (offsetX + popupWidth > screenWidth - (int) (8 * density)) {
            offsetX = screenWidth - popupWidth - (int) (8 * density);
        }
        // 左边界
        if (offsetX < (int) (8 * density)) {
            offsetX = (int) (8 * density);
        }
        // 底部边界
        if (offsetY + popupHeight > screenHeight - (int) (8 * density)) {
            offsetY = (int) touchY - popupHeight - (int) (10 * density + 0.5f);
        }

        popup.showAtLocation(anchorView, Gravity.NO_GRAVITY, offsetX, offsetY);
    }
}

