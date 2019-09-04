package ru.olegcherednik.zip4jvm.crypto;

import ru.olegcherednik.zip4jvm.io.out.DataOutput;
import lombok.NonNull;

import java.io.IOException;

/**
 * @author Oleg Cherednik
 * @since 09.08.2019
 */
final class NullEncoder implements Encoder {

    @Override
    public void writeEncryptionHeader(@NonNull DataOutput out) throws IOException {
    }

    @Override
    public void encrypt(@NonNull byte[] buf, int offs, int len) {
    }

    @Override
    public String toString() {
        return "<null>";
    }

}
