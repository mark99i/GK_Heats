package ru.mark99.gk_heats.proto;

import com.igormaznitsa.jbbp.JBBPCustomFieldTypeProcessor;
import com.igormaznitsa.jbbp.compiler.JBBPNamedFieldInfo;
import com.igormaznitsa.jbbp.compiler.tokenizer.JBBPFieldTypeParameterContainer;
import com.igormaznitsa.jbbp.io.JBBPArraySizeLimiter;
import com.igormaznitsa.jbbp.io.JBBPBitInputStream;
import com.igormaznitsa.jbbp.io.JBBPBitOrder;
import com.igormaznitsa.jbbp.model.JBBPAbstractField;
import com.igormaznitsa.jbbp.model.JBBPFieldString;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Кастомный тип JBBP {@code sohstr} — строка, ограниченная байтом-разделителем
 * {@code 0x01} (SOH) либо концом потока.
 *
 * <p>Нужен потому, что ответ на {@code /GP_update} — это поток значений,
 * склеенных через {@code 0x01}, а у JBBP нет встроенного примитива «читать до
 * байта-терминатора»: штатный {@code stringj} рассчитан на формат с префиксом
 * длины.
 *
 * <p>Поддерживается только именованное одиночное поле:
 * <pre>
 *   sohstr mode;
 *   sohstr temperature;
 * </pre>
 * Массивы намеренно не поддержаны — все кадры этого клиента фиксированной длины.
 */
public final class SohStringType implements JBBPCustomFieldTypeProcessor {

    /** Имя типа в JBBP-скрипте. Обязано быть в нижнем регистре. */
    public static final String TYPE_NAME = "sohstr";

    /** Разделитель значений в кадре ответа. */
    public static final int SEPARATOR = 0x01;

    public static final SohStringType INSTANCE = new SohStringType();

    private static final String[] TYPES = {TYPE_NAME};

    private SohStringType() {
    }

    @Override
    public String[] getCustomFieldTypes() {
        return TYPES.clone();
    }

    @Override
    public boolean isAllowed(final JBBPFieldTypeParameterContainer fieldType,
                             final String fieldName,
                             final int extraData,
                             final boolean isArray) {
        // Тип не параметризуется ('sohstr:8' смысла не имеет) и не бывает массивом.
        return extraData == 0 && !isArray;
    }

    @Override
    public JBBPAbstractField readCustomFieldType(final JBBPBitInputStream in,
                                                 final JBBPBitOrder bitOrder,
                                                 final int parserFlags,
                                                 final JBBPFieldTypeParameterContainer customTypeFieldInfo,
                                                 final JBBPNamedFieldInfo fieldName,
                                                 final int extraData,
                                                 final boolean readWholeStream,
                                                 final int arrayLength,
                                                 final JBBPArraySizeLimiter arraySizeLimiter)
            throws IOException {

        final ByteArrayOutputStream buffer = new ByteArrayOutputStream(16);
        while (true) {
            final int b = in.read();
            if (b < 0 || b == SEPARATOR) {
                break;
            }
            buffer.write(b);
        }
        return new JBBPFieldString(fieldName,
                new String(buffer.toByteArray(), StandardCharsets.UTF_8));
    }
}
