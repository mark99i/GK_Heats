package ru.mark99.gk_heats;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import ru.mark99.gk_heats.proto.HeatMode;
import ru.mark99.gk_heats.proto.HeatsClient;
import ru.mark99.gk_heats.proto.Seat;
import ru.mark99.gk_heats.proto.SeatStatus;

public class HeatBoardController implements ESPSearch.OnDeviceFoundListener {
    private String TAG = "GKH_HeatBoardCtrl";
    private final int row;

    public ESPSearch espSearch;
    public HeatsClient heatsClient;
    public LunarisAppMessenger lunarisAppMessenger;
    public Handler handler;

    public volatile String controllerState;

    public String login;
    public String password;
    public String host;

    public SeatStatus left;
    public SeatStatus right;

    public HeatBoardController(int row) {
        this.row = row;
        TAG += "_" + row;

        Log.d(TAG, "init()");

        HandlerThread handlerThread = new HandlerThread(TAG + "_b" + row);
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    void reloadConfig() {
        Log.d(TAG, "reloading config");
        var p = PreferenceManager.getDefaultSharedPreferences(MainService.context);

        if (!p.getBoolean("board" + row + "_enabled", false) && lunarisAppMessenger != null) {
            stop();
            Log.d(TAG, "board disabled");
            return;
        }

        if (p.getBoolean("board" + row + "_enabled", false) && lunarisAppMessenger == null) {
            Log.d(TAG, "board enabled");
            stop();
            connectToLunarisApp();
        }

        login = p.getString("board" + row + "_login", "Lunaris");
        password = p.getString("board" + row + "_password", "1234554321");
        host = null;
    }

    void stop() {
        if (espSearch != null) {
            espSearch.stopScan();
        }
        heatsClient = null;
        left = null;
        right = null;
        host = null;
        controllerState = "disabled";
        if (lunarisAppMessenger != null) {
            MainService.context.unbindService(lunarisAppConnection);
        }
        handler.removeCallbacksAndMessages(null);
    }

    void searchEsp() {
        controllerState = "searching_esp";
        espSearch = new ESPSearch(MainService.context, this);
        espSearch.startScan();
    }

    void loadFirstState() {
        heatsClient = new HeatsClient(host, login, password);

        try {
            left = heatsClient.status(Seat.LEFT);
            right = heatsClient.status(Seat.RIGHT);
        } catch (IOException e) {
            left = null;
            right = null;
            controllerState = "error_on_loading_first_state";
            lunarisAppMessenger.notifyOfConnectionChanged(LunarisAppMessenger.ConnectionStateError);
            Log.e(TAG, "exception on loading first state");
            e.getStackTrace();
            return;
        }
        controllerState = "working";
        lunarisAppMessenger.notifyOfConnectionChanged(LunarisAppMessenger.ConnectionStateConnected);

        lunarisAppMessenger.notifyOfSeatHeatChanged(
                row == 1 ? 1 : 16,
                left.invertedInt()
        );

        lunarisAppMessenger.notifyOfSeatHeatChanged(
                row == 1 ? 4 : 64,
                right.invertedInt()
        );

        handler.postDelayed(this::periodicallyUpdateState, 500);
    }

    void periodicallyUpdateState() {
        SeatStatus last_left;
        SeatStatus last_right;
        try {
            last_left = heatsClient.status(Seat.LEFT);
            last_right = heatsClient.status(Seat.RIGHT);
        } catch (IOException e) {
            controllerState = "error_on_refresh_state";
            lunarisAppMessenger.notifyOfConnectionChanged(LunarisAppMessenger.ConnectionStateError);
            Log.e(TAG, "exception on refresh state");
            e.getStackTrace();
            handler.postDelayed(this::periodicallyUpdateState, 2000);
            return;
        }

        if (!Objects.equals("working", controllerState)) {
            lunarisAppMessenger.notifyOfConnectionChanged(LunarisAppMessenger.ConnectionStateConnected);
            controllerState = "working";
        }

        if (!Objects.equals(left, last_left)) {
            Log.d(TAG, "Updated state of left: currentValue=" + right.invertedInt());
            lunarisAppMessenger.notifyOfSeatHeatChanged(
                    row == 1 ? 1 : 16,
                    left.invertedInt()
            );
        }

        if (!Objects.equals(right, last_right)) {
            Log.d(TAG, "Updated state of right: currentValue=" + right.invertedInt());
            lunarisAppMessenger.notifyOfSeatHeatChanged(
                    row == 1 ? 4 : 64,
                    right.invertedInt()
            );
        }

        handler.postDelayed(this::periodicallyUpdateState, 500);
    }

    private final LunarisAppMessenger.CommandListener commandListener = (seatPosition, seatMode) -> {
        if (heatsClient == null) {
            Log.e(TAG, "Cannot execute command because heatsClient == null");
            return;
        }

        handler.post(() -> {
            Log.d(TAG, "Changing " + seatPosition + " to " + seatMode);
            try {
                heatsClient.setMode(
                        seatPosition == 1 || seatPosition == 16 ? Seat.LEFT: Seat.RIGHT,
                        HeatMode.fromInvertedInt(seatMode)
                );
            } catch (IOException e) {
                Log.e(TAG, "exception on changing state");
                e.getStackTrace();
            }
        });
    };

    private final ServiceConnection lunarisAppConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.d(TAG, "lunarisapp is connected");
            lunarisAppMessenger = LunarisAppMessenger.build(iBinder, commandListener, row);
            lunarisAppMessenger.notifyOfConnectionChanged(LunarisAppMessenger.ConnectionStateSearching);
            handler.post(HeatBoardController.this::searchEsp);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            lunarisAppMessenger.onDisconnect();
            lunarisAppMessenger = null;
            Log.d(TAG, "lunarisapp is disconnected, reconnecting in 3s");
            handler.postDelayed(() -> connectToLunarisApp(), 3000);
        }
    };

    private void connectToLunarisApp() {
        Log.d(TAG, "connecting to lunarisapp");

        controllerState = "connecting_lapp";

        Intent intent = new Intent();
        intent.setComponent(new ComponentName(
                "ru.mark99.carapp",
                "ru.mark99.carapp.ExternalHeatBoardService"
        ));
        var bind = MainService.context.bindService(intent, lunarisAppConnection, Context.BIND_AUTO_CREATE);
        Log.d(TAG, "bind lunarisapp = " + bind);

        if (!bind) {
            Log.d(TAG, "next connect try in 3sec");
            handler.postDelayed(this::connectToLunarisApp, 3000);
        }
    }

    @Override
    public void onDeviceFound(String ipAddress) {
        host = ipAddress;
        controllerState = "pulling_first_state";
        handler.post(this::loadFirstState);
    }

    @Override
    public void onScanFinished(String errorMessage) {
        controllerState = "board_not_found";
    }

    @Override
    public void onScanError(String message) {
        controllerState = "board_not_found";
    }

    @NotNull
    @Override
    public String toString() {
        var state = switch (controllerState) {
            case "searching_esp" -> "Поиск платы в сети";
            case "board_not_found" -> "Плата не найдена";
            case "pulling_first_state" -> "Получение первого состояния";
            case "connecting_lapp" -> "Подключение к LunarisApp";
            case "working" -> "Работает";
            case "disabled" -> "Отключено";
            case null, default -> controllerState;
        };

        return String.join("\n", List.of(
                state,
                "IP: " + host,
                "LApp: " + (lunarisAppMessenger != null ? "Подключен" : "Не подключен"),
                "Последние данные: L:" +
                        (left == null ? "-" : left.invertedInt()) +
                        " R:" + (right == null ? "-" : right.invertedInt())
        ));
    }

}
