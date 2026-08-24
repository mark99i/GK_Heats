package ru.mark99.gk_heats;

import android.content.Context;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ESPSearch {
    private static final String TAG = "ESPSearch";
    private static final int SCAN_TIMEOUT_MS = 500;        // таймаут соединения
    private static final int THREAD_COUNT = 50;            // количество параллельных запросов
    private static final String TARGET_URL_PATH = "/GP_ping";     // изменяйте под свой URL, например "/api/version"

    private final Context context;
    private final OnDeviceFoundListener listener;
    private ExecutorService executor;
    private volatile boolean isScanning = false;

    public ESPSearch(Context context, OnDeviceFoundListener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public void startScan() {
        if (isScanning) return;
        isScanning = true;

        final String subnetPrefix = getCurrentSubnetPrefix();
        if (subnetPrefix == null) {
            listener.onScanError("Не удалось определить подсеть");
            isScanning = false;
            return;
        }

        final String ownIp = getOwnIpAddress();
        Log.d(TAG, "Сканирование подсети: " + subnetPrefix + ".*, наш IP: " + ownIp);

        executor = Executors.newFixedThreadPool(THREAD_COUNT);
        List<String> ipList = new ArrayList<>();

        // Генерируем все адреса в подсети (1-254), исключая свой
        for (int i = 1; i <= 254; i++) {
            String candidate = subnetPrefix + "." + i;
            if (!candidate.equals(ownIp)) {
                ipList.add(candidate);
            }
        }

        final Object lock = new Object();
        final int total = ipList.size();
        final int[] completed = {0};
        final boolean[] found = {false};

        for (String ip : ipList) {
            executor.submit(() -> {
                if (found[0]) return;

                if (checkDevice(ip)) {
                    synchronized (lock) {
                        if (!found[0]) {
                            found[0] = true;
                            new Handler(Looper.getMainLooper()).post(() -> listener.onDeviceFound(ip));
                            stopScan();
                        }
                    }
                }

                synchronized (lock) {
                    completed[0]++;
                    if (completed[0] == total && !found[0]) {
                        new Handler(Looper.getMainLooper()).post(() -> listener.onScanFinished(null));
                        stopScan();
                    }
                }
            });
        }

        executor.shutdown();
        // автоматическая остановка через некоторое время, если ничего не найдено
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (isScanning && !found[0]) {
                stopScan();
                listener.onScanFinished("Ни одно устройство не ответило на порт 80 с ожидаемым URL");
            }
        }, 15000);
    }

    public void stopScan() {
        isScanning = false;
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
            try {
                executor.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {}
        }
    }

    /**
     * Проверяет, отвечает ли устройство по порту 80 на заданный URL.
     * @return true, если ответ 200 OK и (опционально) тело содержит ожидаемые данные
     */
    private boolean checkDevice(String ip) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL("http://" + ip + TARGET_URL_PATH);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(SCAN_TIMEOUT_MS);
            conn.setReadTimeout(SCAN_TIMEOUT_MS);
            conn.setRequestMethod("GET");
            conn.connect();

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Опционально: прочитать ответ и проверить его содержимое
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                // Например, проверить наличие строки "ESP32" или "подогрев"
                if (response.toString().contains("ESP32")) { // измените под своё
                    Log.i(TAG, "Найдено устройство: " + ip);
                    return true;
                }
            }
        } catch (Exception e) {
            // таймаут или ошибка соединения — просто игнорируем
        } finally {
            if (conn != null) conn.disconnect();
        }
        return false;
    }

    /**
     * Возвращает префикс подсети (первые три октета) для текущего активного интерфейса.
     * Поддерживает как клиентский Wi‑Fi, так и режим точки доступа.
     */
    private String getCurrentSubnetPrefix() {
        // 1. Пробуем получить через WifiManager (если подключены к Wi‑Fi)
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            DhcpInfo dhcp = wifiManager.getDhcpInfo();
            if (dhcp != null && dhcp.netmask != 0 && dhcp.ipAddress != 0) {
                int ip = dhcp.ipAddress;
                int mask = dhcp.netmask;
                String ipStr = intToIp(ip);
                String maskStr = intToIp(mask);
                // Предполагаем /24 (маска 255.255.255.0) или вычисляем количество бит
                if (maskStr.startsWith("255.255.255.")) {
                    return ipStr.substring(0, ipStr.lastIndexOf('.'));
                }
                // Если маска не /24 – можно сделать общий расчёт, но для hotspot обычно /24
            }
        }

        // 2. Если не получилось (например, телефон раздаёт Wi-Fi), ищем интерфейс с частным IP
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String ip = addr.getHostAddress();
                        // Частные диапазоны: 192.168., 10., 172.16-31.
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") ||
                                (ip.startsWith("172.") && is172Private(ip))) {
                            // Возвращаем первые три октета (предполагая /24)
                            return ip.substring(0, ip.lastIndexOf('.'));
                        }
                    }
                }
            }
        } catch (SocketException e) {
            Log.e(TAG, "Ошибка получения NetworkInterface", e);
        }
        return null;
    }

    private boolean is172Private(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length < 2) return false;
        int second = Integer.parseInt(parts[1]);
        return second >= 16 && second <= 31;
    }

    private String getOwnIpAddress() {
        // Просто возвращаем IP устройства в текущей подсети через тот же метод
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            Log.e(TAG, "Ошибка получения своего IP", e);
        }
        return "0.0.0.0";
    }

    private String intToIp(int ip) {
        return (ip & 0xFF) + "." +
                ((ip >> 8) & 0xFF) + "." +
                ((ip >> 16) & 0xFF) + "." +
                ((ip >> 24) & 0xFF);
    }

    public interface OnDeviceFoundListener {
        void onDeviceFound(String ipAddress);
        void onScanFinished(String errorMessage);
        void onScanError(String message);
    }


}
