package ru.mark99.gk_heats.proto;

/**
 * Сиденье. Прошивка «Lunaris Warm» управляет двумя независимыми каналами,
 * в веб-интерфейсе они подписаны «Левое» и «Правое».
 */
public enum Seat {

    LEFT("modeWarmLeft", "tempLeft"),
    RIGHT("modeWarmRight", "tempRight");

    /** Имя радиогруппы режима: пишется через {@code /GP_click}, читается через {@code /GP_update}. */
    final String modeWidget;

    /** Имя метки температуры: только чтение. */
    final String tempWidget;

    Seat(String modeWidget, String tempWidget) {
        this.modeWidget = modeWidget;
        this.tempWidget = tempWidget;
    }
}
