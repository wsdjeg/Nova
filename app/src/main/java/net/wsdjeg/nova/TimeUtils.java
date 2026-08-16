package net.wsdjeg.nova;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * 时间格式化工具类
 * 统一时间显示格式
 */
public class TimeUtils {

    /** 中文星期名（下标对应 Calendar.DAY_OF_WEEK - 1） */
    private static final String[] WEEKDAY_NAMES = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};

    /**
     * 格式化时间戳（会话列表等旧场景使用）
     * 今天：HH:mm（24小时制）
     * 非今天但今年：MM-dd HH:mm
     * 非今年：yyyy-MM-dd HH:mm
     *
     * @param timestamp 毫秒时间戳
     * @return 格式化后的时间字符串
     */
    public static String formatTime(long timestamp) {
        if (timestamp <= 0) {
            return "";
        }

        Date messageDate = new Date(timestamp);
        Calendar messageCal = Calendar.getInstance();
        messageCal.setTime(messageDate);

        Calendar nowCal = Calendar.getInstance();

        // 判断是否是今天
        if (isSameDay(messageCal, nowCal)) {
            // 今天：HH:mm
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            return sdf.format(messageDate);
        }

        // 判断是否是今年
        if (messageCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR)) {
            // 今年但非今天：MM-dd HH:mm
            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
            return sdf.format(messageDate);
        }

        // 非今年：yyyy-MM-dd HH:mm
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        return sdf.format(messageDate);
    }

    /**
     * 格式化聊天时间轴节点文字
     *
     * 时间是消息流中的独立分隔节点，而非每条消息的属性。
     * 规则（按优先级从高到低）：
     * - 今天："08:10"
     * - 昨天："昨天 18:22"
     * - 本周（早于昨天）："周一 22:35"
     * - 今年更早："8月15日 18:22"
     * - 跨年份："2025年8月15日 18:22"
     *
     * @param timestamp 毫秒时间戳
     * @return 格式化后的时间节点文字
     */
    public static String formatTimelineTime(long timestamp) {
        if (timestamp <= 0) {
            return "";
        }

        Calendar msgCal = Calendar.getInstance();
        msgCal.setTimeInMillis(timestamp);
        Calendar nowCal = Calendar.getInstance();

        String hm = new SimpleDateFormat("HH:mm", Locale.getDefault())
            .format(new Date(timestamp));

        // 今天：08:10
        if (isSameDay(msgCal, nowCal)) {
            return hm;
        }

        // 昨天：昨天 18:22
        Calendar yesterdayCal = (Calendar) nowCal.clone();
        yesterdayCal.add(Calendar.DAY_OF_YEAR, -1);
        if (isSameDay(msgCal, yesterdayCal)) {
            return "昨天 " + hm;
        }

        // 本周（早于昨天）：周一 22:35
        if (isWithinCurrentWeek(msgCal, nowCal)) {
            return WEEKDAY_NAMES[msgCal.get(Calendar.DAY_OF_WEEK) - 1] + " " + hm;
        }

        int year = msgCal.get(Calendar.YEAR);
        int month = msgCal.get(Calendar.MONTH) + 1;
        int day = msgCal.get(Calendar.DAY_OF_MONTH);

        // 今年更早：8月15日 18:22
        if (year == nowCal.get(Calendar.YEAR)) {
            return month + "月" + day + "日 " + hm;
        }

        // 跨年份：2025年8月15日 18:22
        return year + "年" + month + "月" + day + "日 " + hm;
    }

    /**
     * 判断目标时间是否不早于"本周起始日 00:00"
     * 按 Locale 的每周第一天计算（中文环境为周一）
     */
    private static boolean isWithinCurrentWeek(Calendar target, Calendar now) {
        Calendar weekStart = (Calendar) now.clone();
        int firstDay = weekStart.getFirstDayOfWeek();
        int delta = (weekStart.get(Calendar.DAY_OF_WEEK) - firstDay + 7) % 7;
        weekStart.add(Calendar.DAY_OF_MONTH, -delta);
        weekStart.set(Calendar.HOUR_OF_DAY, 0);
        weekStart.set(Calendar.MINUTE, 0);
        weekStart.set(Calendar.SECOND, 0);
        weekStart.set(Calendar.MILLISECOND, 0);
        return !target.before(weekStart);
    }

    /**
     * 判断两个日历是否是同一天
     */
    private static boolean isSameDay(Calendar cal1, Calendar cal2) {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
            && cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH)
            && cal1.get(Calendar.DAY_OF_MONTH) == cal2.get(Calendar.DAY_OF_MONTH);
    }

    /**
     * 从会话 ID 解析创建时间戳
     * 会话 ID 格式：YYYY-MM-DD-HH-MM-SS
     * 例如：2024-01-15-10-30-00 -> 对应的毫秒时间戳
     *
     * @param sessionId 会话 ID
     * @return 毫秒时间戳，解析失败返回 -1
     */
    public static long parseSessionIdToTimestamp(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return -1;
        }

        try {
            // 格式：YYYY-MM-DD-HH-MM-SS
            String[] parts = sessionId.split("-");
            if (parts.length < 6) {
                return -1;
            }

            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int day = Integer.parseInt(parts[2]);
            int hour = Integer.parseInt(parts[3]);
            int minute = Integer.parseInt(parts[4]);
            int second = Integer.parseInt(parts[5]);

            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.YEAR, year);
            cal.set(Calendar.MONTH, month - 1); // Calendar.MONTH 从 0 开始
            cal.set(Calendar.DAY_OF_MONTH, day);
            cal.set(Calendar.HOUR_OF_DAY, hour);
            cal.set(Calendar.MINUTE, minute);
            cal.set(Calendar.SECOND, second);
            cal.set(Calendar.MILLISECOND, 0);

            return cal.getTimeInMillis();
        } catch (Exception e) {
            return -1;
        }
    }
}

