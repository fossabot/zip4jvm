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

import net.lingala.zip4j.crypto.engine.ZipCryptoEngine;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.util.InternalZipConstants;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Random;

public class StandardEncryptor implements Encryptor {

    private ZipCryptoEngine zipCryptoEngine;
    private byte[] headerBytes;

    public StandardEncryptor(char[] password, int crc) throws ZipException {
        if (password == null || password.length <= 0) {
            throw new ZipException("input password is null or empty in standard encrpyter constructor");
        }

        this.zipCryptoEngine = new ZipCryptoEngine();

        this.headerBytes = new byte[InternalZipConstants.STD_DEC_HDR_SIZE];
        init(password, crc);
    }

    private void init(char[] password, int crc) throws ZipException {
        if (password == null || password.length <= 0) {
            throw new ZipException("input password is null or empty, cannot initialize standard encrypter");
        }
        zipCryptoEngine.initKeys(password);
        headerBytes = generateRandomBytes(InternalZipConstants.STD_DEC_HDR_SIZE);
        // Initialize again since the generated bytes were encrypted.
        zipCryptoEngine.initKeys(password);

        headerBytes[InternalZipConstants.STD_DEC_HDR_SIZE - 1] = (byte)((crc >>> 24));
        headerBytes[InternalZipConstants.STD_DEC_HDR_SIZE - 2] = (byte)((crc >>> 16));

        if (headerBytes.length < InternalZipConstants.STD_DEC_HDR_SIZE) {
            throw new ZipException("invalid header bytes generated, cannot perform standard encryption");
        }

        encrypt(headerBytes);
    }

    @Override
    public void encrypt(byte[] buf, int offs, int len) throws ZipException {

        if (len < 0) {
            throw new ZipException("invalid length specified to decrpyt data");
        }

        try {
            for (int i = offs; i < offs + len; i++)
                buf[i] = encryptByte(buf[i]);
        } catch(Exception e) {
            throw new ZipException(e);
        }
    }

    @Override
    public long write(OutputStream out) throws IOException {
        out.write(headerBytes);
        return headerBytes.length;
    }

    protected byte encryptByte(byte val) {
        byte temp_val = (byte)(val ^ zipCryptoEngine.decryptByte() & 0xff);
        zipCryptoEngine.updateKeys(val);
        return temp_val;
    }

    protected byte[] generateRandomBytes(int size) throws ZipException {

        if (size <= 0) {
            throw new ZipException("size is either 0 or less than 0, cannot generate header for standard encryptor");
        }

        byte[] buff = new byte[size];

        Random rand = new Random();

        for (int i = 0; i < buff.length; i++) {
            // Encrypted to get less predictability for poorly implemented
            // rand functions.
            buff[i] = encryptByte((byte)rand.nextInt(256));
        }

//		buff[0] = (byte)87;
//		buff[1] = (byte)176;
//		buff[2] = (byte)-49;
//		buff[3] = (byte)-43;
//		buff[4] = (byte)93;
//		buff[5] = (byte)-204;
//		buff[6] = (byte)-105;
//		buff[7] = (byte)213;
//		buff[8] = (byte)-80;
//		buff[9] = (byte)-8;
//		buff[10] = (byte)21;
//		buff[11] = (byte)242;

//		for( int j=0; j<2; j++ ) {
//			Random rand = new Random();
//			int i = rand.nextInt();
//			buff[0+j*4] = (byte)(i>>24);
//			buff[1+j*4] = (byte)(i>>16);
//			buff[2+j*4] = (byte)(i>>8);
//			buff[3+j*4] = (byte)i;
//		}
        return buff;
    }

    public byte[] getHeaderBytes() {
        return headerBytes;
    }

}
