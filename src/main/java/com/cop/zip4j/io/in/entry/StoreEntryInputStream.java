package com.cop.zip4j.io.in.entry;

import com.cop.zip4j.crypto.Decoder;
import com.cop.zip4j.io.in.DataInput;
import com.cop.zip4j.model.entry.ZipEntry;
import org.apache.commons.io.IOUtils;

import java.io.IOException;

/**
 * @author Oleg Cherednik
 * @since 04.08.2019
 */
final class StoreEntryInputStream extends EntryInputStream {

    public StoreEntryInputStream(ZipEntry entry, DataInput in, Decoder decoder) {
        super(entry, in, decoder);
    }

    @Override
    public int read(byte[] buf, int offs, int len) throws IOException {
        len = (int)Math.min(len, getAvailableCompressedBytes());
        len = in.read(buf, offs, len);

        if (len == 0 || len == IOUtils.EOF)
            return IOUtils.EOF;

        decoder.decrypt(buf, offs, len);
        readCompressedBytes += len;
        writtenUncompressedBytes += len;
        updateChecksum(buf, offs, len);
        return len;
    }

}
