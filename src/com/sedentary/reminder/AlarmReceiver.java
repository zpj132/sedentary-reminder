package com.sedentary.reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/** 闹钟触发：发出提醒（弹窗/通知/震动/响铃），并自动安排下一阶段，形成无限循环。 */
public class AlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        handleFire(context);
    }

    /** 闹钟到点 / 服务计时到点 / 开机后补触发 时统一走这里。 */
    public static void handleFire(Context c) {
        SharedPreferences p = Prefs.get(c);
        if (!p.getBoolean(Prefs.ENABLED, false)) return;
        // 未到点直接跳过：服务计时和系统闹钟是双通道，谁先到点谁触发，另一方自动作废
        long next = p.getLong(Prefs.NEXT_AT, 0);
        if (System.currentTimeMillis() < next - 1500) return;

        String phase = p.getString(Prefs.PHASE, "sed");
        int round = p.getInt(Prefs.ROUND, 1);
        long now = System.currentTimeMillis();

        Notifier.fire(c, phase, round);

        // 进入下一阶段；回到久坐阶段视为新一轮开始
        String next2 = "sed".equals(phase) ? "act" : "sed";
        int durMin = "sed".equals(next2) ? Prefs.sedMin(p) : Prefs.actMin(p);
        if ("sed".equals(next2)) round = round + 1;

        p.edit()
                .putString(Prefs.PHASE, next2)
                .putInt(Prefs.ROUND, round)
                .putLong(Prefs.NEXT_AT, now + durMin * 60000L)
                .apply();

        LoopScheduler.scheduleNext(c);
    }
}
