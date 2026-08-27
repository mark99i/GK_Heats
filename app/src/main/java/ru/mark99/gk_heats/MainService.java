package ru.mark99.gk_heats;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Messenger;
import android.util.Log;

import androidx.preference.PreferenceManager;

public class MainService extends Service {
    private static final String TAG = "GKH_MainService";

    public static MainService context;

    public HeatBoardController board1;
    public HeatBoardController board2;

    public MainService() {}

    @SuppressLint("ForegroundServiceType")
    @Override
    public void onCreate() {
        super.onCreate();
        context = this;
        Log.d(TAG, "service started");

        startForeground(1002, Utils.getForegroundNotification(this));

        board1 = new HeatBoardController(1);
        board2 = new HeatBoardController(2);

        board1.reloadConfig();
        board2.reloadConfig();
    }

    public void onConfigurationChanged() {
        Log.d(TAG, "onConfigurationChanged, reloading board config");
        board1.handler.post(() -> board1.reloadConfig());
        board2.handler.post(() -> board2.reloadConfig());
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "service destroy");
        board1.stop(true);
        board2.stop(true);
        stopForeground(Service.STOP_FOREGROUND_REMOVE);
        context = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}