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

package net.lingala.zip4j.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.util.InternalZipConstants;
import net.lingala.zip4j.util.ZipUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.ArrayUtils;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Oleg Cherednik
 * @since 05.03.2019
 */
@Getter
@Setter
public class CentralDirectory {

    @NonNull
    private List<FileHeader> fileHeaders = Collections.emptyList();
    private DigitalSignature digitalSignature;

    public void addFileHeader(FileHeader fileHeader) {
        fileHeaders = fileHeaders.isEmpty() ? new ArrayList<>() : fileHeaders;
        fileHeaders.add(fileHeader);
    }

    @NonNull
    public List<FileHeader> getFileHeadersByPrefix(@NonNull String prefix) {
        String name = FilenameUtils.normalize(prefix.toLowerCase(), true);

        return fileHeaders.stream()
                          .filter(fileHeader -> fileHeader.getFileName().toLowerCase().startsWith(name))
                          .collect(Collectors.toList());
    }

    @NonNull
    public FileHeader getFileHeaderByEntryName(@NonNull String entryName) {
        FileHeader fileHeader = getNullableFileHeaderByEntryName(entryName);

        if (fileHeaders.isEmpty())
            throw new ZipException("File header with entry name '" + entryName + "' was not found");

        return fileHeader;
    }

    public FileHeader getNullableFileHeaderByEntryName(@NonNull String entryName) {
        String name = FilenameUtils.normalize(entryName.toLowerCase(), true);

        List<FileHeader> fileHeaders = this.fileHeaders.stream()
                                                       .filter(fileHeader -> fileHeader.getFileName().toLowerCase().equals(name))
                                                       .collect(Collectors.toList());

        if (fileHeaders.size() > 1)
            throw new ZipException("Multiple file headers found for entry name '" + entryName + '\'');

        return fileHeaders.isEmpty() ? null : fileHeaders.get(0);
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class FileHeader {

        // size:4 - signature (0x02014b50)
        private final int signature = InternalZipConstants.CENSIG;
        // size:2 - version made by
        private short versionMadeBy;
        // size:2 - version needed to extractEntries
        private short versionToExtract;
        // size:2 - general purpose bit flag
        private final GeneralPurposeFlag generalPurposeFlag = new GeneralPurposeFlag();
        // size:2 - compression method
        @NonNull
        private CompressionMethod compressionMethod = CompressionMethod.STORE;
        // size:2 - last mod file time
        // size:2 - last mod file date
        private int lastModifiedTime;
        // size:4 - crc-32
        private long crc32;
        @Deprecated
        private byte[] crcBuff;
        // size:4 - compressed size
        private long compressedSize;
        // size:4 - uncompressed size
        private long uncompressedSize;
        // size:2 - file name length (n)
//        private int fileNameLength;
        // size:2 - extra field length (m)
        private int extraFieldLength;
        // size:2 - file comment length
        private int fileCommentLength;
        // size:2 - disk number start
        private int diskNumber;
        // size:2 - internal file attributes
        private byte[] internalFileAttributes;
        // size:4 - external file attributes
        private byte[] externalFileAttributes;
        // size:4 - relative offset of local header
        private long offsLocalFileHeader;
        // size:n - file name
        private String fileName;
        // size:m - extra field
        @NonNull
        private Map<Short, ExtraDataRecord> extraDataRecords = Collections.emptyMap();
        // size:k - extra field
        private String fileComment;
        @NonNull
        private Encryption encryption = Encryption.OFF;
        private char[] password;
        private Zip64ExtendedInfo zip64ExtendedInfo;
        private AESExtraDataRecord aesExtraDataRecord;

        public FileHeader(String fileName, Encryption encryption) {
            this.fileName = fileName;
            this.encryption = encryption;
        }

        @NonNull
        public byte[] getFileName(@NonNull Charset charset) {
            return fileName != null ? fileName.getBytes(charset) : ArrayUtils.EMPTY_BYTE_ARRAY;
        }

        public boolean isDirectory() {
            return ZipUtils.isDirectory(fileName);
        }

        public void setZip64ExtendedInfo(Zip64ExtendedInfo info) {
            zip64ExtendedInfo = info;

            if (info != null) {
                uncompressedSize = info.getUnCompressedSize() != -1 ? info.getUnCompressedSize() : uncompressedSize;
                compressedSize = info.getCompressedSize() != -1 ? info.getCompressedSize() : uncompressedSize;
                offsLocalFileHeader = info.getOffsLocalHeaderRelative() != -1 ? info.getOffsLocalHeaderRelative() : offsLocalFileHeader;
                diskNumber = info.getDiskNumberStart() != -1 ? info.getDiskNumberStart() : diskNumber;
            }
        }

        public void setAesExtraDataRecord(AESExtraDataRecord record) {
            aesExtraDataRecord = record;
            updateEncryption();
        }

        public ExtraDataRecord getExtraDataRecordByHeader(short header) {
            return extraDataRecords.get(header);
        }

        public void setGeneralPurposeFlag(short data) {
            generalPurposeFlag.setData(data);
            updateEncryption();
        }

        private void updateEncryption() {
            if (aesExtraDataRecord != null)
                encryption = Encryption.AES;
            else if (generalPurposeFlag.isStrongEncryption())
                encryption = Encryption.STRONG;
            else if (generalPurposeFlag.isEncrypted())
                encryption = Encryption.STANDARD;
            else
                encryption = Encryption.OFF;

            generalPurposeFlag.setEncrypted(isEncrypted());
        }

        public void updateOffLocalHeaderRelative(long delta) {
            offsLocalFileHeader += delta;
        }

        public boolean isEncrypted() {
            return encryption != Encryption.OFF;
        }

        @Override
        public String toString() {
            return fileName;
        }
    }

    @Getter
    @Setter
    public static class DigitalSignature {

        // size:4 - header signature (0x06054b50)
        private final int signature = InternalZipConstants.DIGSIG;
        // size:2 - size of data (n)
        private int sizeOfData;
        // size:n - signature data
        private byte[] signatureData;

    }

}
