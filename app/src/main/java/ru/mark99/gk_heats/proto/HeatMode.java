package ru.mark99.gk_heats.proto;

/**
 * Ступень подогрева. На проводе передаётся индексом радиокнопки, 0..3.
 *
 * <p>Нумерация ступеней в интерфейсе обратна мощности: уставка режима 1 лежит в
 * диапазоне 75..100 %, режима 2 — 50..75 %, режима 3 — 25..50 %. То есть
 * {@link #LEVEL_1} — самый сильный подогрев.
 */
public enum HeatMode {

    /** Выкл. */
    OFF,
    /** Ступень 1, самая мощная. */
    LEVEL_1,
    /** Ступень 2. */
    LEVEL_2,
    /** Ступень 3, самая слабая. */
    LEVEL_3;

    private static final HeatMode[] BY_WIRE = values();

    /** Значение для {@code /GP_click}. */
    public int wire() {
        return ordinal();
    }

    /** @return ступень по значению с провода или {@code null}, если значение вне 0..3 */
    public static HeatMode fromWire(int wire) {
        return wire >= 0 && wire < BY_WIRE.length ? BY_WIRE[wire] : null;
    }

    public static HeatMode fromInvertedInt(int mode) {
        return switch (mode) {
            case 1 -> HeatMode.LEVEL_3;
            case 2 -> HeatMode.LEVEL_2;
            case 3 -> HeatMode.LEVEL_1;
            default -> HeatMode.OFF;
        };
    }
}
