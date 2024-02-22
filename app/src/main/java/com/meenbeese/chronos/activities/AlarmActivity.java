package com.meenbeese.chronos.activities;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat;

import io.reactivex.disposables.Disposable;

import com.afollestad.aesthetic.Aesthetic;
import com.afollestad.aesthetic.AestheticActivity;
import com.meenbeese.chronos.Chronos;
import com.meenbeese.chronos.R;
import com.meenbeese.chronos.data.AlarmData;
import com.meenbeese.chronos.data.PreferenceData;
import com.meenbeese.chronos.data.SoundData;
import com.meenbeese.chronos.data.TimerData;
import com.meenbeese.chronos.dialogs.TimeChooserDialog;
import com.meenbeese.chronos.services.SleepReminderService;
import com.meenbeese.chronos.utils.FormatUtils;
import com.meenbeese.chronos.utils.ImageUtils;

import me.jfenn.slideactionview.SlideActionListener;
import me.jfenn.slideactionview.SlideActionView;

import java.util.Date;
import java.util.concurrent.TimeUnit;


public class AlarmActivity extends AestheticActivity implements SlideActionListener {

    public static final String EXTRA_ALARM = "meenbeese.chronos.AlarmActivity.EXTRA_ALARM";
    public static final String EXTRA_TIMER = "meenbeese.chronos.AlarmActivity.EXTRA_TIMER";

    private View overlay;
    private TextView time;

    private Chronos chronos;
    private Vibrator vibrator;
    private AudioManager audioManager;

    private long triggerMillis;
    private AlarmData alarm;
    private SoundData sound;
    private boolean isVibrate;

    private boolean isSlowWake;
    private long slowWakeMillis;

    private int currentVolume;
    private int minVolume;
    private int originalVolume;
    private int volumeRange;

    private Handler handler;
    private Runnable runnable;

    private Disposable textColorPrimaryInverseSubscription;
    private Disposable isDarkSubscription;

    private boolean isDark;

    public AlarmActivity() {
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alarm);
        chronos = (Chronos) getApplicationContext();

        overlay = findViewById(R.id.overlay);
        TextView date = findViewById(R.id.date);
        time = findViewById(R.id.time);
        SlideActionView actionView = findViewById(R.id.slideView);

        // Lock orientation
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);

        textColorPrimaryInverseSubscription = Aesthetic.Companion.get()
                .textColorPrimaryInverse()
                .subscribe(integer -> overlay.setBackgroundColor(integer));

        isDarkSubscription = Aesthetic.Companion.get()
                .isDark()
                .subscribe(aBoolean -> isDark = aBoolean);

        actionView.setLeftIcon(VectorDrawableCompat.create(getResources(), R.drawable.ic_snooze, getTheme()));
        actionView.setRightIcon(VectorDrawableCompat.create(getResources(), R.drawable.ic_close, getTheme()));
        actionView.setListener(this);

        isSlowWake = PreferenceData.SLOW_WAKE_UP.getValue(this);
        slowWakeMillis = PreferenceData.SLOW_WAKE_UP_TIME.getValue(this);

        boolean isAlarm = getIntent().hasExtra(EXTRA_ALARM);
        if (isAlarm) {
            alarm = getIntent().getParcelableExtra(EXTRA_ALARM);
            assert alarm != null;
            isVibrate = alarm.isVibrate;
            if (alarm.hasSound())
                sound = alarm.getSound();
        } else if (getIntent().hasExtra(EXTRA_TIMER)) {
            TimerData timer = getIntent().getParcelableExtra(EXTRA_TIMER);
            assert timer != null;
            isVibrate = timer.isVibrate;
            if (timer.hasSound())
                sound = timer.sound;
        } else finish();

        date.setText(FormatUtils.format(new Date(), FormatUtils.FORMAT_DATE + ", " + FormatUtils.getShortFormat(this)));

        if (sound != null && !sound.isSetVolumeSupported()) {
            // Use the backup method if it is not supported
            audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM);
            if (isSlowWake) {
                minVolume = 0;
                volumeRange = originalVolume - minVolume;
                currentVolume = minVolume;
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, minVolume, 0);
            }
        }

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        triggerMillis = System.currentTimeMillis();
        handler = new Handler();
        runnable = new Runnable() {
            @Override
            public void run() {
                long elapsedMillis = System.currentTimeMillis() - triggerMillis;
                String text = FormatUtils.formatMillis(elapsedMillis);
                time.setText(String.format("-%s", text.substring(0, text.length() - 3)));

                if (isVibrate) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
                    else vibrator.vibrate(500);
                }

                if (sound != null && !sound.isPlaying(chronos))
                    sound.play(chronos);

                if (alarm != null && isSlowWake) {
                    float slowWakeProgress = (float) elapsedMillis / slowWakeMillis;

                    WindowManager.LayoutParams params = getWindow().getAttributes();
                    params.screenBrightness = Math.max(0.01f, Math.min(1f, slowWakeProgress));
                    getWindow().setAttributes(params);
                    getWindow().addFlags(WindowManager.LayoutParams.FLAGS_CHANGED);

                    if (sound != null && sound.isSetVolumeSupported()) {
                        float newVolume = Math.min(1f, slowWakeProgress);

                        sound.setVolume(chronos, newVolume);
                    } else if (currentVolume < originalVolume) {
                        // Backup volume setting behavior
                        int newVolume = minVolume + (int) Math.min(originalVolume, slowWakeProgress * volumeRange);
                        if (newVolume != currentVolume) {
                            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, newVolume, 0);
                            currentVolume = newVolume;
                        }
                    }
                }

                handler.postDelayed(this, 1000);
            }
        };
        handler.post(runnable);

        if (sound != null)
            sound.play(chronos);

        SleepReminderService.refreshSleepTime(chronos);

        if (PreferenceData.RINGING_BACKGROUND_IMAGE.getValue(this))
            ImageUtils.getBackgroundImage((ImageView) findViewById(R.id.background));
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (textColorPrimaryInverseSubscription != null && isDarkSubscription != null) {
            textColorPrimaryInverseSubscription.dispose();
            isDarkSubscription.dispose();
        }

        stopAnnoyance();
    }

    private void stopAnnoyance() {
        if (handler != null)
            handler.removeCallbacks(runnable);

        if (sound != null && sound.isPlaying(chronos)) {
            sound.stop(chronos);

            if (isSlowWake && !sound.isSetVolumeSupported()) {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalVolume, 0);
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        finish();
        startActivity(new Intent(intent));
    }

    @Override
    public void onSlideLeft() {
        final int[] minutes = new int[]{2, 5, 10, 20, 30, 60};
        CharSequence[] names = new CharSequence[minutes.length + 1];
        for (int i = 0; i < minutes.length; i++) {
            names[i] = FormatUtils.formatUnit(AlarmActivity.this, minutes[i]);
        }

        names[minutes.length] = getString(R.string.title_snooze_custom);

        stopAnnoyance();
        new AlertDialog.Builder(AlarmActivity.this, isDark ? R.style.Theme_AppCompat_Dialog_Alert : R.style.Theme_AppCompat_Light_Dialog_Alert)
                .setItems(names, (dialog, which) -> {
                    if (which < minutes.length) {
                        TimerData timer = chronos.newTimer();
                        timer.setDuration(TimeUnit.MINUTES.toMillis(minutes[which]), chronos);
                        timer.setVibrate(AlarmActivity.this, isVibrate);
                        timer.setSound(AlarmActivity.this, sound);
                        timer.set(chronos, ((AlarmManager) AlarmActivity.this.getSystemService(Context.ALARM_SERVICE)));
                        chronos.onTimerStarted();

                        finish();
                    } else {
                        TimeChooserDialog timerDialog = new TimeChooserDialog(AlarmActivity.this);
                        timerDialog.setListener((hours, minutes1, seconds) -> {
                            TimerData timer = chronos.newTimer();
                            timer.setVibrate(AlarmActivity.this, isVibrate);
                            timer.setSound(AlarmActivity.this, sound);
                            timer.setDuration(TimeUnit.HOURS.toMillis(hours)
                                            + TimeUnit.MINUTES.toMillis(minutes1)
                                            + TimeUnit.SECONDS.toMillis(seconds),
                                    chronos);

                            timer.set(chronos, ((AlarmManager) getSystemService(Context.ALARM_SERVICE)));
                            chronos.onTimerStarted();
                            finish();
                        });
                        timerDialog.show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
                .show();

        overlay.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
    }

    @Override
    public void onSlideRight() {
        overlay.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        finish();
    }
}
