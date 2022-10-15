/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package ru.olegcherednik.zip4jvm.io.ppmd;

import com.github.luben.zstd.ZstdInputStream;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.IOUtils;
import ru.olegcherednik.zip4jvm.io.in.data.DataInput;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author Oleg Cherednik
 * @since 14.10.2022
 */
public class PpmdInputStream extends InputStream {

    private final PpmDecompress ppmd;
    private final byte[] buf = new byte[1];
    private final DataInput dataInput;
    private final long finalAbsoluteOffs;
    private long bytesToRead;

    public PpmdInputStream(DataInput in, long uncompressedSize, long compressedSize) throws IOException {
        ppmd = new PpmDecompress(new Decorator(in));
        dataInput = in;
        finalAbsoluteOffs = dataInput.getAbsoluteOffs() + compressedSize;
        bytesToRead = uncompressedSize;
    }

    @Override
    public int read() throws IOException {
        return read(buf, 0, 1) == -1 ? -1 : (buf[0] & 0xFF);
    }

    @Override
    public int read(byte[] buf, int offs, int len) throws IOException {
        if (bytesToRead <= 0) {
            dataInput.seek(finalAbsoluteOffs);
            return IOUtils.EOF;
        }

        int total = ppmd.read(buf, offs, (int)Math.min(bytesToRead, len));
        bytesToRead -= total;
        return total;
    }

    @RequiredArgsConstructor
    private static final class Decorator extends InputStream {

        private final DataInput in;

        @Override
        public int read() throws IOException {
            return in.readByte();
        }

        @Override
        public int read(byte[] buf, int offs, int len) throws IOException {
            return in.read(buf, offs, len);
        }

        @Override
        public void close() throws IOException {
            in.close();
        }

    }

}


