package ru.mark99.gk_heats.proto;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Клиент подогрева сидений «Lunaris Warm». Три операции: пинг, чтение статуса
 * сиденья, установка ступени подогрева.
 *
 * <p>Все методы блокирующие — вызывать только с фонового потока.
 *
 * <p>Заголовок {@code Authorization} ставится упреждающе, а не в ответ на 401:
 * это экономит по одному лишнему запросу к ESP на каждую операцию.
 *
 * <p>Обмен идёт по HTTP без шифрования — учётные данные Basic-Auth передаются
 * открытым текстом. Для устройства в доверенной локальной сети это штатный
 * режим GyverPortal, но по недоверенной сети так работать нельзя.
 *
 * <pre>
 * HeatsClient client = new HeatsClient("10.13.164.114", "Lunaris", pass);
 * if (client.ping()) {
 *     client.setMode(Seat.LEFT, HeatMode.LEVEL_2);
 *     SeatStatus s = client.status(Seat.LEFT);
 *     Log.i(TAG, s.mode() + " при " + s.temperature() + " °C");
 * }
 * </pre>
 */
public final class HeatsClient {

    /** Таймаут запроса, {@code _tout} в GP_SCRIPT.js. */
    private static final long TIMEOUT_MS = 2000L;

    /** Тело для POST: протокол ничего в теле не передаёт, всё в query string. */
    private static final RequestBody EMPTY_BODY = RequestBody.create(new byte[0], null);

    private final String baseUrl;
    private final OkHttpClient http;

    /**
     * @param host     адрес устройства, например {@code "10.13.164.114"}
     * @param username логин Basic-Auth
     * @param password пароль Basic-Auth
     */
    public HeatsClient(String host, String username, String password) {
        this.baseUrl = "http://" + host;
        final String credentials = Credentials.basic(username, password);
        this.http = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .writeTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .addInterceptor(chain -> chain.proceed(chain.request().newBuilder()
                        .header("Authorization", credentials)
                        .build()))
                .build();
    }

    /**
     * Пинг-запрос: {@code GET /GP_ping?}.
     *
     * <p>Признак доступности — сам факт HTTP-ответа, а не его код. Так же
     * трактует связь и веб-интерфейс: оффлайном он считает только транспортный
     * сбой. Ответ 401 тоже означает, что устройство на месте, но пароль неверен.
     *
     * @return {@code true}, если устройство ответило
     */
    public boolean ping() {
        final Request request = new Request.Builder()
                .url(baseUrl + "/GP_ping?")
                .get()
                .build();
        try (Response response = http.newCall(request).execute()) {
            return response.code() > 0;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Текущий статус подогрева сиденья: {@code GET /GP_update?<режим>,<температура>=}.
     *
     * <p>Оба поля читаются одним запросом, порядок id в запросе задаёт порядок
     * значений в кадре.
     *
     * @throws IOException при ошибке транспорта, коде ответа не 2xx или
     *                     несовпадении длины кадра
     */
    public SeatStatus status(Seat seat) throws IOException {
        final String path = "/GP_update?" + seat.modeWidget + ',' + seat.tempWidget + '=';
        final Request request = new Request.Builder().url(baseUrl + path).get().build();
        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + " на " + path);
            }
            final ResponseBody body = response.body();
            return SeatStatus.parse(body == null ? new byte[0] : body.bytes());
        }
    }

    /**
     * Установка ступени подогрева: {@code POST /GP_click?<режим>=<0..3>}.
     *
     * <p>Пишется та же радиогруппа, которую щёлкает веб-интерфейс.
     *
     * <p>Взаимодействие с авторежимом ({@code chLeftAutoWarm} / {@code chRightAutoWarm})
     * из веб-интерфейса не выводится: неизвестно, снимает ли прошивка авторежим
     * при ручной установке ступени, или авторежим позже перебьёт заданное
     * значение. Если такое поведение обнаружится, авторежим нужно будет гасить
     * отдельным {@code GP_click} перед установкой ступени.
     *
     * @throws IOException при ошибке транспорта или коде ответа не 2xx
     */
    public void setMode(Seat seat, HeatMode mode) throws IOException {
        final String path = "/GP_click?" + seat.modeWidget + '=' + mode.wire();
        final Request request = new Request.Builder()
                .url(baseUrl + path)
                .post(EMPTY_BODY)
                .build();
        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + " на " + path);
            }
        }
    }
}
