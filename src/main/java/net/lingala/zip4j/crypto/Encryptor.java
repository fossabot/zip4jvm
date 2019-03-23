/*
 * Copyright 2010 Srikanth Reddy Lingala
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.lingala.zip4j.crypto;

import net.lingala.zip4j.exception.ZipException;

import java.io.IOException;
import java.io.OutputStream;

@SuppressWarnings("MethodCanBeVariableArityMethod")
public interface Encryptor {

    Encryptor NULL = new Encryptor() {
        @Override
        public void encrypt(byte[] buf, int offs, int len) throws ZipException {
        }

        @Override
        public long write(OutputStream out) throws IOException {
            return 0;
        }
    };

    default void encrypt(byte[] buf) throws ZipException {
        encrypt(buf, 0, buf.length);
    }

    void encrypt(byte[] buf, int offs, int len) throws ZipException;

    long write(OutputStream out) throws IOException;

}
