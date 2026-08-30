package com.sedentary.reminder;

import android.content.Context;
import android.content.SharedPreferences;

/** 所有状态（含计时状态）都持久化在这里，进程被杀后仍能恢复与续算。 */
public class Prefs {
    public static final String NAME = "sedentary_prefs";

    public static final String ENABLED  = "enabled";
    public static final String PHASE    = "phase";      // "sed" = 久坐阶段, "act" = 活动阶段
    public static final String ROUND    = "round";      // 第几轮
    public static final String NEXT_AT  = "next_at";    // 下次提醒的绝对时间戳（毫秒）

    public static final String SED_MIN  = "sed_min";    // 久坐时长（分钟）
    public static final String ACT_MIN  = "act_min";    // 活动时长（分钟）

    public static final String POPUP    = "popup";      // 弹窗提醒
    public static final String SOUND    = "sound";      // 响铃
    public static final String VIBRATE  = "vibrate";    // 震动

    public static SharedPreferences get(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static int sedMin(SharedPreferences p) { return clamp(p.getInt(SED_MIN, 45), 5, 240); }
    public static int actMin(SharedPreferences p) { return clamp(p.getInt(ACT_MIN, 5), 1, 120); }

    public static int clamp(int v, int min, int max) { return v < min ? min : (v > max ? max : v); }

    public static String phaseLabel(String phase) {
        return "sed".equals(phase) ? "久坐中" : "活动中";
    }
}
