package com.sedentary.reminder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

/** 用系统精确闹钟调度下一次提醒：不依赖进程存活，Doze 模式也能触发。 */
public class LoopScheduler {

    static PendingIntent firePendingIntent(Context c) {
        Intent i = new Intent(c, AlarmReceiver.class);
        i.setAction("com.sedentary.reminder.ACTION_FIRE");
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(c, 1001, i, flags);
    }

    public static void scheduleNext(Context c) {
        SharedPreferences p = Prefs.get(c);
        long at = p.getLong(Prefs.NEXT_AT, System.currentTimeMillis() + 60000L);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = firePendingIntent(c);

        boolean exact = true;
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                exact = am.canScheduleExactAlarms();
            } catch (SecurityException e) {
                exact = false;
            }
        }
        if (exact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        } else {
            // 未授予精确闹钟权限时的兜底：仍会触发，但系统可能推迟（最长约 15 分钟）
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }

    public static void cancel(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(firePendingIntent(c));
    }
}
