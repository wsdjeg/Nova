package net.wsdjeg.nova;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewParent;
import android.widget.ScrollView;

/**
 * 工具卡片内容用的自定义 ScrollView。
 * 
 * 解决嵌套在 RecyclerView + HorizontalScrollView 中的 ScrollView
 * 上下滚动经常失败的问题：RecyclerView 抢先拦截了垂直滚动事件。
 * 
 * 策略：
 * - 只有展开状态（scrollEnabled=true）且内容可滚动时，才接管垂直滚动
 * - 折叠状态（scrollEnabled=false）时，所有触摸事件传递给父视图（RecyclerView），
 *   让用户滚动整个消息列表
 * - 展开后：
 *   - ACTION_DOWN: 如果内容可滚动，立即禁止父视图拦截
 *   - ACTION_MOVE: 判断方向
 *     - 垂直滑动 -> 继续禁止父视图拦截，让本 ScrollView 处理
 *     - 水平滑动 -> 放开拦截，让外层 HorizontalScrollView 处理
 *   - ACTION_UP/CANCEL: 恢复父视图拦截权限
 */
public class ToolContentScrollView extends ScrollView {

    private float startX, startY;
    private boolean isVerticalScroll = false;
    private boolean isHandlingTouch = false;
    private boolean scrollEnabled = false;

    private static final int TOUCH_SLOP = 10;

    public ToolContentScrollView(Context context) {
        super(context);
    }

    public ToolContentScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ToolContentScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /**
     * 设置是否允许内部滚动。
     * 折叠状态设为 false，展开状态设为 true。
     */
    public void setScrollEnabled(boolean enabled) {
        this.scrollEnabled = enabled;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // 折叠状态下，不拦截任何触摸事件，全部交给父视图（RecyclerView）
        if (!scrollEnabled) {
            return false;
        }

        int action = ev.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                startX = ev.getX();
                startY = ev.getY();
                isVerticalScroll = false;
                // 只有内容可滚动时才接管触摸事件
                isHandlingTouch = canScrollVertically(1) || canScrollVertically(-1);
                if (isHandlingTouch) {
                    // 立即禁止 RecyclerView 拦截
                    disallowParentIntercept(true);
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (!isHandlingTouch) break;
                float dx = Math.abs(ev.getX() - startX);
                float dy = Math.abs(ev.getY() - startY);
                if (!isVerticalScroll && dy > dx && dy > TOUCH_SLOP) {
                    isVerticalScroll = true;
                }
                if (isVerticalScroll) {
                    // 垂直滚动 -> 禁止所有父视图拦截
                    disallowParentIntercept(true);
                } else if (dx > dy && dx > TOUCH_SLOP) {
                    // 水平滚动 -> 放开拦截，让 HorizontalScrollView 处理
                    disallowParentIntercept(false);
                    isHandlingTouch = false;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isVerticalScroll = false;
                isHandlingTouch = false;
                disallowParentIntercept(false);
                break;
        }
        return super.onInterceptTouchEvent(ev);
    }

    /**
     * 向上遍历父视图链，设置是否禁止拦截触摸事件。
     * requestDisallowInterceptTouchEvent 会自动向上传播，
     * 但我们手动遍历确保 HSV 和 RecyclerView 都收到。
     */
    private void disallowParentIntercept(boolean disallow) {
        ViewParent parent = getParent();
        while (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallow);
            parent = parent.getParent();
        }
    }
}

