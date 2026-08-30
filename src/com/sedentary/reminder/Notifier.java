package com.sedentary.reminder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;

/** 提醒出口：弹窗（悬浮权限下直接弹出卡片）＋ 通知栏横幅（响铃/震动按用户开关选择通道）。 */
public class Notifier {
    public static final int NOTIF_ID = 1001;

    public static void createChannels(Context c) {
        NotificationManager nm =
                (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (alarmSound == null) {
            alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        }
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        long[] vib = new long[]{0, 500, 400, 500, 400, 800};
        createChannel(nm, "ch_sv", "提醒（响铃＋震动）", alarmSound, attrs, vib, NotificationManager.IMPORTANCE_HIGH);
        createChannel(nm, "ch_s",  "提醒（仅响铃）",     alarmSound, attrs, null, NotificationManager.IMPORTANCE_HIGH);
        createChannel(nm, "ch_v",  "提醒（仅震动）",     null, null, vib, NotificationManager.IMPORTANCE_HIGH);
        createChannel(nm, "ch_m",  "提醒（静默弹窗）",   null, null, null, NotificationManager.IMPORTANCE_HIGH);
    }

    private static void createChannel(NotificationManager nm, String id, String name,
                                      Uri sound, AudioAttributes attrs, long[] vib, int importance) {
        NotificationChannel ch = new NotificationChannel(id, name, importance);
        ch.setSound(sound, attrs);
        ch.enableVibration(vib != null);
        ch.setVibrationPattern(vib);
        ch.enableLights(false);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
    }

    public static String channelFor(SharedPreferences p) {
        boolean s = p.getBoolean(Prefs.SOUND, false);
        boolean v = p.getBoolean(Prefs.VIBRATE, true);
        if (s && v) return "ch_sv";
        if (s) return "ch_s";
        if (v) return "ch_v";
        return "ch_m";
    }

    /** @param endedPhase 刚结束的阶段："sed" = 久坐结束该活动了；"act" = 活动结束该回座位了 */
    public static void fire(Context c, String endedPhase, int round) {
        SharedPreferences p = Prefs.get(c);
        boolean wantPopup = p.getBoolean(Prefs.POPUP, true);
        int sedMin = Prefs.sedMin(p);
        int actMin = Prefs.actMin(p);

        String title;
        String text;
        if ("sed".equals(endedPhase)) {
            title = "该起来活动啦！";
            text = "已连续久坐 " + sedMin + " 分钟，起来活动 " + actMin + " 分钟吧";
        } else {
            title = "活动结束，继续加油";
            text = "已活动 " + actMin + " 分钟，马上开始第 " + (round + 1) + " 轮";
        }

        // 已授予悬浮窗权限时，直接在当前界面上弹出全屏卡片（真正的“弹窗”）
        if (wantPopup && Settings.canDrawOverlays(c)) {
            Intent pi = new Intent(c, PopupActivity.class);
            pi.putExtra("phase", endedPhase);
            pi.putExtra("round", round);
            pi.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            try {
                c.startActivity(pi);
            } catch (Exception ignored) {
            }
        }

        // 通道震动之外，再加一次直接震动兜底（个别系统会压制通知渠道震动）
        if (p.getBoolean(Prefs.VIBRATE, true)) {
            try {
                Vibrator v = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null) {
                    v.vibrate(VibrationEffect.createWaveform(
                            new long[]{0, 500, 400, 500, 400, 800}, -1));
                }
            } catch (Exception ignored) {
            }
        }

        NotificationManager nm =
                (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        Notification.Builder b = new Notification.Builder(c, channelFor(p))
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC);

        b.setContentIntent(PendingIntent.getActivity(c, 2001,
                new Intent(c, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        // 未解锁/熄屏时由系统直接拉起弹窗页；已解锁时会显示横幅通知作兜底
        if (wantPopup) {
            Intent fs = new Intent(c, PopupActivity.class);
            fs.putExtra("phase", endedPhase);
            fs.putExtra("round", round);
            fs.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            b.setFullScreenIntent(PendingIntent.getActivity(c, 2002, fs,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE), true);
        }

        nm.notify(NOTIF_ID, b.build());
    }

    public static void cancel(Context c) {
        NotificationManager nm =
                (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(NOTIF_ID);
    }
}
