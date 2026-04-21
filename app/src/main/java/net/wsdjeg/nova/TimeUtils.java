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
}
