package com.sedentary.reminder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 前台常驻服务：进程保持存活 + 每秒检查到点。
 * 系统闹钟（AlarmReceiver）仍是主通道；本服务的进程内计时是兜底通道，
 * 两者共用 next_at 时间戳，先到者触发，后到者自动跳过，不会重复提醒。
 */
public class SedentaryService extends Service {

    public static volatile boolean running = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat fmt = new SimpleDateFormat("HH:mm", Locale.CHINA);

    private final Runnable check = new Runnable() {
        @Override
        public void run() {
            checkDue();
            handler.postDelayed(this, 1000L);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Notifier.createChannels(this);
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(
                "cycle", "提醒循环", NotificationManager.IMPORTANCE_LOW));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!running) {
            running = true;
            Notification n = buildNotification("久坐提醒循环运行中", "到点自动提醒");
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(1, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(1, n);
            }
            handler.removeCallbacks(check);
            handler.post(check);
        }
        return START_STICKY;
    }

    private Notification buildNotification(String title, String text) {
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, "cycle")
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void checkDue() {
        if (!Prefs.get(this).getBoolean(Prefs.ENABLED, false)) return;
        AlarmReceiver.handleFire(this); // 未到点时内部会直接跳过
        // 顺手把常驻通知的副标题更新为下次提醒时间
        long next = Prefs.get(this).getLong(Prefs.NEXT_AT, 0);
        if (next > 0) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.notify(2, buildNotification("久坐提醒循环运行中",
                    "下次提醒 " + fmt.format(new Date(next))));
        }
    }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacks(check);
        super.onDestroy();
    }
}
