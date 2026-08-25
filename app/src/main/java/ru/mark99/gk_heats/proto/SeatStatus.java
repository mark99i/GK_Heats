package ru.mark99.gk_heats.proto;

import com.igormaznitsa.jbbp.JBBPParser;
import com.igormaznitsa.jbbp.mapper.Bin;
import com.igormaznitsa.jbbp.mapper.BinType;

import java.io.IOException;
import java.util.Objects;

/**
 * Кадр ответа {@code /GP_update} на запрос статуса одного сиденья: ступень
 * подогрева и температура.
 *
 * <p>JBBP-скрипт: {@code sohstr mode;sohstr temperature;}
 *
 * <p>Имена полей в скрипте локальные и намеренно не совпадают с именами виджетов
 * протокола: соответствие значений в кадре позиционное, а не по имени, поэтому
 * один скрипт и один парсер обслуживают оба сиденья.
 *
 * <p>Значения хранятся строками ровно как пришли. Пустая строка означает
 * «прошивка значение не прислала» — типизированные геттеры вернут для такого
 * поля {@code null}.
 */
public final class SeatStatus {

    /** JBBP-скрипт кадра. Порядок полей обязан совпадать с порядком id в запросе. */
    static final String SCRIPT =
            SohStringType.TYPE_NAME + " mode;" + SohStringType.TYPE_NAME + " temperature;";

    /** Сколько значений должно быть в кадре. */
    private static final int VALUE_COUNT = 2;

    private static final JBBPParser PARSER = JBBPParser.prepare(SCRIPT, SohStringType.INSTANCE);

    @Bin(name = "mode", type = BinType.STRING, comment = "Индекс ступени подогрева, 0..3")
    public String rawMode;

    @Bin(name = "temperature", type = BinType.STRING, comment = "Температура сиденья, °C")
    public String rawTemperature;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        SeatStatus that = (SeatStatus) o;
        return Objects.equals(rawMode, that.rawMode) && Objects.equals(rawTemperature, that.rawTemperature);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rawMode, rawTemperature);
    }

    /**
     * Разбирает тело ответа.
     *
     * @throws IOException если число значений в кадре не равно двум. По правилам
     *                     протокола рассинхронённый кадр отбрасывается целиком:
     *                     применить часть значений нельзя, позиции сдвинутся и
     *                     температура уедет в поле режима.
     */
    static SeatStatus parse(byte[] payload) throws IOException {
        int separators = 0;
        for (byte b : payload) {
            if (b == SohStringType.SEPARATOR) {
                separators++;
            }
        }
        // Завершающего разделителя протокол не присылает: значений всегда на одно
        // больше, чем разделителей.
        final int values = separators + 1;
        if (values != VALUE_COUNT) {
            throw new IOException("Кадр GP_update отброшен: ожидалось значений "
                    + VALUE_COUNT + ", получено " + values);
        }
        return PARSER.parse(payload).mapTo(new SeatStatus());
    }

    /** @return текущая ступень подогрева или {@code null}, если прошивка её не прислала */
    public HeatMode mode() {
        final Integer wire = asInt(rawMode);
        return wire == null ? null : HeatMode.fromWire(wire);
    }

    /** @return температура сиденья в °C или {@code null}, если значения нет */
    public Double temperature() {
        return asDouble(rawTemperature);
    }

    /** @return {@code true}, если подогрев включён на любой ступени */
    public boolean isHeating() {
        final HeatMode mode = mode();
        return mode != null && mode != HeatMode.OFF;
    }

    @Override
    public String toString() {
        return "SeatStatus{mode=" + mode() + ", temperature=" + temperature() + '}';
    }

    private static Double asDouble(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return Double.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Числа приходят и как {@code "2"}, и как {@code "2.00"} — обе формы годятся. */
    private static Integer asInt(String raw) {
        final Double parsed = asDouble(raw);
        return parsed == null ? null : (int) Math.round(parsed);
    }

    public int invertedInt() {
        return switch (asInt(rawMode)) {
            case 1 -> 3;
            case 2 -> 2;
            case 3 -> 1;
            case null, default -> 0;
        };
    }
}
