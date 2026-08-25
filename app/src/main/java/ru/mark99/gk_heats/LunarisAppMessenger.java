package ru.mark99.gk_heats;

import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.List;

@SuppressWarnings("SpellCheckingInspection")
public class LunarisAppMessenger extends Handler {
    private static final String TAG = "LAppMessenger";

    public final static int CommandTXHello = 10000;
    public final static int CommandTXNotifyOfHeatStateChanged = 10001;
    public final static int CommandRXChangeSeatHeat = 10002;
    public final static int CommandTXNotifyOfConnectionChanged = 10003;
    public final static int CommandRXRefreshLastStateNow = 10004;

    public final static int SeatPositionLeft = 1;
    public final static int SeatPositionRight = 4;
    public final static int SeatPositionSecondLeft = 16;
    public final static int SeatPositionSecondRight = 64;

    public final static int SeatModeOff = 0;
    public final static int SeatModeLvl1 = 1;
    public final static int SeatModeLvl2 = 2;
    public final static int SeatModeLvl3 = 3;
    public final static int SeatModeAuto = 4;

    public final static int ConnectionStateDisabled = 0;
    public final static int ConnectionStateConnected = 1;
    public final static int ConnectionStateSearching = 2;
    public final static int ConnectionStateError = 3;

    private HandlerThread handlerThread;
    private Messenger messenger;
    private CommandListener commandListener;
    private Message lastStateMessage;

    public static LunarisAppMessenger build(IBinder service, CommandListener commandListener, int row) {
        var ht = new HandlerThread("LunarisAppMessenger");
        ht.start();

        var m = new LunarisAppMessenger(ht.getLooper());
        m.messenger = new Messenger(service);
        m.commandListener = commandListener;
        m.handlerThread = ht;
        m.sendHello(row);
        return m;
    }

    private LunarisAppMessenger(Looper looper) {
        super(looper);
    }

    public boolean notifyOfSeatHeatChanged(int seatPos, int seatHeatMode) {
        verifyIsSeatPos(seatPos);
        verifyIsSeatHeatMode(seatHeatMode);
        Log.d(TAG, "Notifying LApp of seatPos=" + seatPos + " heat mode changed to " + seatHeatMode);

        var payload = new Bundle();
        payload.putInt("seatPos", seatPos);
        payload.putInt("seatHeatMode", seatHeatMode);

        var message = new Message();
        message.what = CommandTXNotifyOfHeatStateChanged;
        message.setData(payload);
        lastStateMessage = message;
        try {
            messenger.send(message);
            return true;
        } catch (RemoteException e) {
            e.getStackTrace();
            return false;
        }
    }

    public boolean notifyOfConnectionChanged(int state) {
        verifyIsConnState(state);
        Log.d(TAG, "Notifying LApp of board connection state changed: " + state);

        var payload = new Bundle();
        payload.putInt("state", state);

        var message = new Message();
        message.what = CommandTXNotifyOfConnectionChanged;
        message.setData(payload);
        lastStateMessage = message;
        try {
            messenger.send(message);
            return true;
        } catch (RemoteException e) {
            e.getStackTrace();
            return false;
        }
    }

    public interface CommandListener {
        void changeSeatHeatState(int seatPosition, int seatMode);
    }

    private void sendHello(int row) {
        Bundle bundle = new Bundle();
        bundle.putString("row", row == 1 ? "first" : "second");

        var message = new Message();
        message.what = CommandTXHello;
        message.setData(bundle);
        message.replyTo = new Messenger(this);
        try { messenger.send(message); } catch (RemoteException ignored) {}
    }

    private void verifyIsSeatPos(int value) {
        if (!List.of(
                SeatPositionLeft,
                SeatPositionRight,
                SeatPositionSecondLeft,
                SeatPositionSecondRight
        ).contains(value)) {
            throw new IllegalArgumentException("value " + value + " not is SeatPosition");
        }
    }

    private void verifyIsSeatHeatMode(int value) {
        if (!List.of(
                SeatModeOff,
                SeatModeLvl1,
                SeatModeLvl2,
                SeatModeLvl3,
                SeatModeAuto
        ).contains(value)) {
            throw new IllegalArgumentException("value " + value + " not is SeatHeatMode");
        }
    }

    private void verifyIsConnState(int value) {
        if (!List.of(
                ConnectionStateDisabled,
                ConnectionStateConnected,
                ConnectionStateSearching,
                ConnectionStateError
        ).contains(value)) {
            throw new IllegalArgumentException("value " + value + " not is ConnectionState");
        }
    }

    public void onDisconnect() {
        removeCallbacksAndMessages(null);
        handlerThread.quitSafely();
    }

    @Override
    public void handleMessage(@NonNull Message msg) {
        switch (msg.what) {
            case CommandRXChangeSeatHeat -> {
                if (commandListener != null) {
                    Log.d(TAG, "Received request of state heat change");

                    var payload = msg.getData();
                    commandListener.changeSeatHeatState(
                            payload.getInt("seatPos"),
                            payload.getInt("seatHeatMode")
                    );
                } else {
                    Log.e(TAG, "commandListener == null, event dropped");
                }
            }
            case CommandRXRefreshLastStateNow -> {
                if (lastStateMessage != null) {
                    try {messenger.send(lastStateMessage);} catch (RemoteException ignored) {}
                }
            }
            default -> Log.e(TAG, "Unknown event received: " + msg.what);
        }
    }
}
