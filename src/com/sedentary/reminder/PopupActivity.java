package com.sedentary.reminder;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

/** 到点弹出的提醒卡片：悬浮于当前界面之上，点击任意处关闭，60 秒后自动关闭。 */
public class PopupActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autoClose = new Runnable() {
        @Override
        public void run() {
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        setContentView(R.layout.activity_popup);

        String phase = getIntent() != null ? getIntent().getStringExtra("phase") : null;
        if (phase == null) phase = "sed";
        int round = getIntent() != null ? getIntent().getIntExtra("round", 1) : 1;

        SharedPreferences p = Prefs.get(this);
        int sedMin = Prefs.sedMin(p);
        int actMin = Prefs.actMin(p);

        TextView emoji = (TextView) findViewById(R.id.popup_emoji);
        TextView title = (TextView) findViewById(R.id.popup_title);
        TextView sub = (TextView) findViewById(R.id.popup_sub);

        if ("sed".equals(phase)) {
            emoji.setText("🧍");
            title.setText("该起来活动啦！");
            sub.setText("已连续久坐 " + sedMin + " 分钟\n起来活动 " + actMin + " 分钟吧");
        } else {
            emoji.setText("💼");
            title.setText("活动结束，继续加油");
            sub.setText("已活动 " + actMin + " 分钟\n马上开始第 " + (round + 1) + " 轮");
        }

        findViewById(R.id.popup_ok).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        findViewById(R.id.popup_root).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        findViewById(R.id.popup_stop).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LoopScheduler.cancel(PopupActivity.this);
                Prefs.get(PopupActivity.this).edit().putBoolean(Prefs.ENABLED, false).apply();
                Notifier.cancel(PopupActivity.this);
                finish();
            }
        });

        handler.postDelayed(autoClose, 60 * 1000L);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(autoClose);
        super.onDestroy();
    }
}
