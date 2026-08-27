package ru.mark99.gk_heats;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

@SuppressWarnings("CallToPrintStackTrace")
public class HeatBoardController implements ESPSearch.OnDeviceFoundListener {
    private String TAG = "GKH_HeatBoardCtrl";
    private final int row;

    public ESPSearch espSearch;
    public GKClient heatsClient;
    public LunarisAppMessenger lunarisAppMessenger;
    private final HandlerThread handlerThread;
    public Handler handler;
    volatile boolean isBound = false;

    public volatile String controllerState = "disabled";

    public String login;
    public String password;
    public String host;

    public int left;
    public int right;

    public HeatBoardController(int row) {
        this.row = row;
        TAG += "_" + row;

        Log.d(TAG, "init()");

        handlerThread = new HandlerThread(TAG + "_b" + row);
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    void reloadConfig() {
        Log.d(TAG, "reloading config");
        var p = PreferenceManager.getDefaultSharedPreferences(MainService.context);

        if (!p.getBoolean("board" + row + "_enabled", false)) {
            stop(false);
            Log.d(TAG, "board disabled");
            return;
        }

        if (p.getBoolean("board" + row + "_enabled", false)) {
            Log.d(TAG, "board enabled");
            stop(false);
            connectToLunarisApp();
        }

        login = p.getString("board" + row + "_login", "Lunaris");
        password = p.getString("board" + row + "_password", "1234554321");
        host = null;
    }

    public void setControllerState(String controllerState) {
        Log.d(TAG, "state changed to " + controllerState);
        this.controllerState = controllerState;
    }

    void stop(boolean destroyApp) {
        handler.removeCallbacksAndMessages(null);
        if (destroyApp)
            handlerThread.quitSafely();
        if (espSearch != null) {
            espSearch.stopScan();
        }
        heatsClient = null;
        left = 0;
        right = 0;
        host = null;
        setControllerState("disabled");
        if (lunarisAppMessenger != null) {
            lunarisAppMessenger.onDisconnect();
            lunarisAppMessenger = null;
        }
        if (isBound) {
            MainService.context.unbindService(lunarisAppConnection);
            isBound = false;
        }
    }

    void searchEsp() {
        setControllerState("searching_esp");
        espSearch = new ESPSearch(MainService.context, this);
        espSearch.startScan();
    }

    void loadFirstState() {
        heatsClient = new GKClient(host, login, password);

        try {
            var state = heatsClient.fetchStatus();
            left = state.left;
            right = state.right;
            if (lunarisAppMessenger == null) return;
        } catch (IOException e) {
            if (lunarisAppMessenger == null) return;
            left = 0;
            right = 0;
            setControllerState("error_on_loading_first_state");
            lunarisAppMessenger.notifyOfConnectionChanged(LunarisAppMessenger.ConnectionStateError);
            Log.e(TAG, "exception on loading first state");
            e.printStackTrace();
            handler.postDelayed(this::searchEsp, 5000);
            return;
        }
        setControllerState("working");
        lunarisAppMessenger.notifyOfConnectionChanged(LunarisAppMessenger.ConnectionStateConnected);

        lunarisAppMessenger.notifyOfSeatHeatChanged(
                row == 1 ? 1 : 16,
                left
        );

        lunarisAppMessenger.notifyOfSeatHeatChanged(
                row == 1 ? 4 : 64,
                right
        );

        handler.postDelayed(this::periodicallyUpdateState, 500);
    }

    void periodicallyUpdateState() {
        int last_left;
        int last_right;
        try {
            var state = heatsClient.fetchStatus();
            last_left = state.left;
            last_right = state.right;
            if (lunarisAppMessenger == null) return;
        } catch (IOException e) {
            if (lunarisAppMessenger == null) return;
            setControllerState("error_on_refresh_state");
            lunarisAppMessenger.notifyOfConnectionChanged(LunarisAppMessenger.ConnectionStateError);
            Log.e(TAG, "exception on refresh state");
            e.printStackTrace();
            handler.postDelayed(this::periodicallyUpdateState, 1000);
            return;
        }

        if (!Objects.equals("working", controllerState)) {
            lunarisAppMessenger.notifyOfConnectionChanged(LunarisAppMessenger.ConnectionStateConnected);
            setControllerState("working");
        }

        if (left != last_left) {
            Log.d(TAG, "Updated state of left: currentValue=" + left);
            lunarisAppMessenger.notifyOfSeatHeatChanged(
                    row == 1 ? 1 : 16,
                    last_left
            );
            left = last_left;
        }

        if (right != last_right) {
            Log.d(TAG, "Updated state of right: currentValue=" + right);
            lunarisAppMessenger.notifyOfSeatHeatChanged(
                    row == 1 ? 4 : 64,
                    last_right
            );
            right = last_right;
        }

        handler.postDelayed(this::periodicallyUpdateState, 300);
    }

    private final LunarisAppMessenger.CommandListener commandListener = (seatPosition, seatMode) -> {
        handler.post(() -> {
            if (heatsClient == null) {
                Log.e(TAG, "Cannot execute command because heatsClient == null");
                return;
            }

            Log.d(TAG, "Changing " + seatPosition + " to " + seatMode);
            try {
                heatsClient.setMode(
                        seatPosition == 1 || seatPosition == 16 ? 1: 2,
                        seatMode
                );
            } catch (IOException e) {
                Log.e(TAG, "exception on changing state");
                e.printStackTrace();
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
            handler.post(() -> {
                handler.removeCallbacksAndMessages(null);
                if (espSearch != null) espSearch.stopScan();
                if (lunarisAppMessenger != null) {
                    lunarisAppMessenger.onDisconnect();
                    lunarisAppMessenger = null;
                }
                setControllerState("connecting_lapp");
            });
            Log.d(TAG, "lunarisapp onServiceDisconnected, clearing state and wait reconnect");
        }

        @Override
        public void onNullBinding(ComponentName name) {
            Log.w(TAG, "connectToLunarisApp call onNullBinding ??? reconnecting in 5s");
            handler.postDelayed(() -> connectToLunarisApp(), 5000);
        }

        @Override
        public void onBindingDied(ComponentName name) {
            new Handler(handlerThread.getLooper()).post(() -> {
                stop(false);
                handler.postDelayed(() -> connectToLunarisApp(), 5000);
            });
            Log.d(TAG, "lunarisapp onBindingDied, reconnecting in 5s");
        }
    };

    private void connectToLunarisApp() {
        Log.d(TAG, "connecting to lunarisapp");

        setControllerState("connecting_lapp");

        Intent intent = new Intent();
        intent.setComponent(new ComponentName(
                "ru.mark99.carapp",
                "ru.mark99.carapp.ExternalHeatBoardService"
        ));

        if (isBound)
            MainService.context.unbindService(lunarisAppConnection);
        var bound = MainService.context.bindService(intent, lunarisAppConnection, Context.BIND_AUTO_CREATE);
        isBound = true;
        Log.d(TAG, "bind lunarisapp = " + bound);

        if (!bound) {
            Log.d(TAG, "next connect try in 3sec");
            handler.postDelayed(this::connectToLunarisApp, 3000);
        }
    }

    @Override
    public void onDeviceFound(String ipAddress) {
        if (lunarisAppMessenger == null) return;
        host = ipAddress;
        setControllerState("pulling_first_state");
        handler.post(this::loadFirstState);
    }

    @Override
    public void onScanFinished(String errorMessage) {
        if (lunarisAppMessenger == null) return;
        setControllerState("board_not_found");
        handler.postDelayed(this::searchEsp, 15000);
    }

    @Override
    public void onScanError(String message) {
        if (lunarisAppMessenger == null) return;
        setControllerState("board_not_found");
        handler.postDelayed(this::searchEsp, 15000);
    }

    @NotNull
    @Override
    public String toString() {
        var state = switch (controllerState) {
            case "searching_esp" -> "Поиск платы в сети";
            case "board_not_found" -> "Плата не найдена, повторное сканирование через ~15 секунд";
            case "pulling_first_state" -> "Получение первого состояния";
            case "connecting_lapp" -> "Подключение к LunarisApp";
            case "error_on_refresh_state" -> "Ошибка обновления статуса";
            case "error_on_loading_first_state" -> "Ошибка получения первого состояния";
            case "working" -> "Работает";
            case "disabled" -> "Отключено";
            case null -> "Неизвестно";
            default -> controllerState;
        };

        return String.join("\n", List.of(
                state,
                "IP: " + host,
                "LApp: " + (lunarisAppMessenger != null ? "Подключен" : "Не подключен"),
                "Последние данные: L:" + left + " R:" + right)
        );
    }
}
