package com.sedentary.reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/** 手机重启后恢复计时循环。 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        SharedPreferences p = Prefs.get(context);
        if (!p.getBoolean(Prefs.ENABLED, false)) return;

        long next = p.getLong(Prefs.NEXT_AT, 0);
        if (next <= System.currentTimeMillis()) {
            // 关机期间已到点的阶段：立即补一次提醒并继续循环
            AlarmReceiver.handleFire(context);
        } else {
            LoopScheduler.scheduleNext(context);
        }
    }
}
