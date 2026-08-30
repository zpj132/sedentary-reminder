package com.sedentary.reminder;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private SharedPreferences p;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean syncingUi = false;
    private boolean pendingStart = false;

    private TextView statusView, countdownView, captionView, sedValue, actValue;
    private Button mainBtn;
    private Switch popupSwitch, soundSwitch, vibrateSwitch;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refreshStatus();
            handler.postDelayed(this, 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Notifier.createChannels(this);
        p = Prefs.get(this);
        setContentView(R.layout.activity_main);

        statusView = (TextView) findViewById(R.id.status);
        countdownView = (TextView) findViewById(R.id.countdown);
        captionView = (TextView) findViewById(R.id.caption);
        sedValue = (TextView) findViewById(R.id.sed_value);
        actValue = (TextView) findViewById(R.id.act_value);
        mainBtn = (Button) findViewById(R.id.btn_main);
        popupSwitch = (Switch) findViewById(R.id.sw_popup);
        soundSwitch = (Switch) findViewById(R.id.sw_sound);
        vibrateSwitch = (Switch) findViewById(R.id.sw_vibrate);

        mainBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (p.getBoolean(Prefs.ENABLED, false)) stopLoop();
                else tryStart();
            }
        });

        findViewById(R.id.sed_minus).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { adjust(Prefs.SED_MIN, Prefs.sedMin(p) - 5); }
        });
        findViewById(R.id.sed_plus).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { adjust(Prefs.SED_MIN, Prefs.sedMin(p) + 5); }
        });
        findViewById(R.id.act_minus).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { adjust(Prefs.ACT_MIN, Prefs.actMin(p) - 1); }
        });
        findViewById(R.id.act_plus).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { adjust(Prefs.ACT_MIN, Prefs.actMin(p) + 1); }
        });

        popupSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (syncingUi) return;
                p.edit().putBoolean(Prefs.POPUP, isChecked).apply();
                if (isChecked && !Settings.canDrawOverlays(MainActivity.this)) {
                    Toast.makeText(MainActivity.this,
                            "需要「显示在其他应用上层」权限才能弹窗，请在下一个页面允许",
                            Toast.LENGTH_LONG).show();
                    Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                }
            }
        });
        soundSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (syncingUi) return;
                p.edit().putBoolean(Prefs.SOUND, isChecked).apply();
            }
        });
        vibrateSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (syncingUi) return;
                p.edit().putBoolean(Prefs.VIBRATE, isChecked).apply();
            }
        });
    }

    private void adjust(String key, int newValue) {
        int v;
        if (Prefs.SED_MIN.equals(key)) v = Prefs.clamp(newValue, 5, 240);
        else v = Prefs.clamp(newValue, 1, 120);
        p.edit().putInt(key, v).apply();
        syncingUi = true;
        sedValue.setText(String.valueOf(Prefs.sedMin(p)));
        actValue.setText(String.valueOf(Prefs.actMin(p)));
        syncingUi = false;
        if (p.getBoolean(Prefs.ENABLED, false)) {
            Toast.makeText(this, "将在下一阶段生效", Toast.LENGTH_SHORT).show();
        }
    }

    private void tryStart() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            pendingStart = true;
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            return;
        }
        doStart();
    }

    private void doStart() {
        long now = System.currentTimeMillis();
        p.edit()
                .putBoolean(Prefs.ENABLED, true)
                .putString(Prefs.PHASE, "sed")
                .putInt(Prefs.ROUND, 1)
                .putLong(Prefs.NEXT_AT, now + Prefs.sedMin(p) * 60000L)
                .apply();
        LoopScheduler.scheduleNext(this);
        startService(new Intent(this, SedentaryService.class));
        refreshStatus();
        Toast.makeText(this, "已开始循环，到点自动提醒", Toast.LENGTH_SHORT).show();

        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (am != null && !am.canScheduleExactAlarms()) {
                Toast.makeText(this, "建议允许「闹钟和提醒」权限，保证准时提醒",
                        Toast.LENGTH_LONG).show();
                try {
                    startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:" + getPackageName())));
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void stopLoop() {
        LoopScheduler.cancel(this);
        p.edit().putBoolean(Prefs.ENABLED, false).apply();
        stopService(new Intent(this, SedentaryService.class));
        Notifier.cancel(this);
        refreshStatus();
        Toast.makeText(this, "已停止", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (requestCode == 1 && pendingStart) {
            pendingStart = false;
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                doStart();
            } else {
                Toast.makeText(this, "未授予通知权限：仍可通过弹窗和震动提醒", Toast.LENGTH_LONG).show();
                doStart();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 循环开启但服务被系统杀掉时，打开 App 自动恢复
        if (p.getBoolean(Prefs.ENABLED, false) && !SedentaryService.running) {
            boolean notifOk = Build.VERSION.SDK_INT < 33 ||
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                            == PackageManager.PERMISSION_GRANTED;
            if (notifOk) startService(new Intent(this, SedentaryService.class));
        }
        syncSwitches();
        refreshStatus();
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    private void syncSwitches() {
        syncingUi = true;
        popupSwitch.setChecked(p.getBoolean(Prefs.POPUP, true));
        soundSwitch.setChecked(p.getBoolean(Prefs.SOUND, false));
        vibrateSwitch.setChecked(p.getBoolean(Prefs.VIBRATE, true));
        sedValue.setText(String.valueOf(Prefs.sedMin(p)));
        actValue.setText(String.valueOf(Prefs.actMin(p)));
        syncingUi = false;
    }

    private void refreshStatus() {
        boolean enabled = p.getBoolean(Prefs.ENABLED, false);
        if (!enabled) {
            statusView.setText("已停止");
            countdownView.setText("—");
            captionView.setText("设置好时长后点击开始");
            mainBtn.setText("开始循环");
            return;
        }
        mainBtn.setText("停止循环");
        String phase = p.getString(Prefs.PHASE, "sed");
        int round = p.getInt(Prefs.ROUND, 1);
        statusView.setText("第 " + round + " 轮 · " + Prefs.phaseLabel(phase));

        long remain = p.getLong(Prefs.NEXT_AT, 0) - System.currentTimeMillis();
        if (remain < 0) remain = 0;
        long min = remain / 60000L;
        long sec = (remain / 1000L) % 60;
        countdownView.setText(String.format("%02d:%02d", min, sec));
        captionView.setText("距下次提醒");
    }
}
