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
package ru.olegcherednik.zip4jvm.crypto.tripledes;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;

/**
 * @author Oleg Cherednik
 * @since 16.02.2020
 */
@Getter
@SuppressWarnings("MethodCanBeVariableArityMethod")
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public enum TripleDesStrength {

    NULL(0, 0),
    S112(1, 112),
    S168(2, 168);

    private final int code;
    private final int size;

    public final int saltLength() {
        return size / 16;
    }

    private int macLength() {
        return size / 8;
    }

    private int keyLength() {
        return size / 8;
    }

    public SecretKeySpec createSecretKeyForCipher(byte[] key) {
        return new SecretKeySpec(key, "TripleDES");
    }

    public SecretKeySpec createSecretKeyForMac(byte[] key) {
        return new SecretKeySpec(key, keyLength(), macLength(), "HmacSHA1");
    }

    public byte[] createPasswordChecksum(byte[] key) {
        final int offs = keyLength() + macLength();
        return new byte[] { key[offs], key[offs + 1] };
    }

    public byte[] generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] buf = new byte[saltLength()];
        random.nextBytes(buf);
        return buf;
    }

    public static TripleDesStrength parseValue(int code) {
        for (TripleDesStrength aesKeyStrength : values())
            if (aesKeyStrength.getCode() == code)
                return aesKeyStrength;

        throw new EnumConstantNotPresentException(TripleDesStrength.class, "code=" + code);
    }

}
