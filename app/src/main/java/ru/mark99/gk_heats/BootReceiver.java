package ru.mark99.gk_heats;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("GKH_BootReceiver", "starting MainService");
        context.startForegroundService(new Intent(context, MainService.class));
    }
}