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
    
    /**
     * 格式化时间戳
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

