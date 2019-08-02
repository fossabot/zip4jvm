package com.cop.zip4j.engine;

import com.cop.zip4j.core.readers.LocalFileHeaderReader;
import com.cop.zip4j.crypto.Decoder;
import com.cop.zip4j.crypto.aes.AesDecoder;
import com.cop.zip4j.crypto.aesnew.AesNewDecoder;
import com.cop.zip4j.crypto.pkware.PkwareHeader;
import com.cop.zip4j.exception.Zip4jException;
import com.cop.zip4j.io.InflaterInputStream;
import com.cop.zip4j.io.LittleEndianRandomAccessFile;
import com.cop.zip4j.io.PartInputStream;
import com.cop.zip4j.io.ZipInputStream;
import com.cop.zip4j.model.CentralDirectory;
import com.cop.zip4j.model.CompressionMethod;
import com.cop.zip4j.model.Encryption;
import com.cop.zip4j.model.LocalFileHeader;
import com.cop.zip4j.model.ZipModel;
import com.cop.zip4j.utils.InternalZipConstants;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.IOUtils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.zip.CRC32;

/**
 * @author Oleg Cherednik
 * @since 14.03.2019
 */
@RequiredArgsConstructor
public class UnzipEngine {

    @Getter
    @NonNull
    private final ZipModel zipModel;
    private final char[] password;
    private final CRC32 crc = new CRC32();

    @Getter
    @Deprecated
    private CentralDirectory.FileHeader fileHeader;
    @Deprecated
    private LocalFileHeader localFileHeader;

    private int currSplitFileCounter;
    @Getter
    @NonNull
    private Decoder decoder = Decoder.NULL;

    public void extractEntries(@NonNull Path destDir, @NonNull Collection<String> entries) {
        getFileHeaders(entries).forEach(fileHeader -> extractEntry(destDir, fileHeader));
    }

    private List<CentralDirectory.FileHeader> getFileHeaders(@NonNull Collection<String> entries) {
        return entries.stream()
                      .map(entryName -> zipModel.getCentralDirectory().getFileHeadersByPrefix(entryName))
                      .flatMap(List::stream)
                      .filter(Objects::nonNull)
                      .collect(Collectors.toList());
    }

    private void extractEntry(Path destDir, CentralDirectory.FileHeader fileHeader) {
        crc.reset();

        if (fileHeader.isDirectory())
            extractDirectory(destDir, fileHeader);
        else
            extractFile(destDir, fileHeader);
    }

    private static void extractDirectory(Path destDir, CentralDirectory.FileHeader fileHeader) {
        try {
            Files.createDirectories(destDir.resolve(fileHeader.getFileName()));
        } catch(IOException e) {
            throw new Zip4jException(e);
        }
    }

    private void extractFile(Path destDir, CentralDirectory.FileHeader fileHeader) {
        try (InputStream in = extractEntryAsStream(fileHeader); OutputStream out = getOutputStream(destDir, fileHeader)) {
            IOUtils.copyLarge(in, out);
        } catch(IOException e) {
            throw new Zip4jException(e);
        }
    }

    @NonNull
    public InputStream extractEntry(@NonNull String entryName) {
        return extractEntryAsStream(zipModel.getCentralDirectory().getFileHeaderByEntryName(entryName));
    }

    @NonNull
    private InputStream extractEntryAsStream(@NonNull CentralDirectory.FileHeader fileHeader) {
        try {
            // TODO temporary
            this.fileHeader = fileHeader;

            LittleEndianRandomAccessFile in = openFile(fileHeader);
            LocalFileHeader localFileHeader = readLocalFileHeader(fileHeader);
            this.localFileHeader = localFileHeader;
            decoder = localFileHeader.getEncryption().decoder(in, localFileHeader, password);

            long comprSize = localFileHeader.getCompressedSize();
            long offs = localFileHeader.getOffs();

            if (localFileHeader.getEncryption() == Encryption.AES) {
                AesDecoder dec = (AesDecoder)decoder;
                comprSize -= dec.getSaltLength() + dec.getPasswordVerifierLength() + 10;
                offs += dec.getSaltLength() + dec.getPasswordVerifierLength();
            } else if (localFileHeader.getEncryption() == Encryption.AES_NEW) {
                AesNewDecoder dec = (AesNewDecoder)decoder;
                comprSize -= dec.getSaltLength() + AesNewDecoder.PASSWORD_VERIFIER_LENGTH + 10;
                offs += dec.getSaltLength() + AesNewDecoder.PASSWORD_VERIFIER_LENGTH;
            } else if (localFileHeader.getEncryption() == Encryption.PKWARE) {
                // TODO decrypter throws unsupported exception
                comprSize -= PkwareHeader.SIZE;
                offs += PkwareHeader.SIZE;
            }

            in.seek(offs);

            if (fileHeader.getActualCompressionMethod() == CompressionMethod.STORE)
                return new ZipInputStream(new PartInputStream(in, comprSize, this), this);
            if (fileHeader.getActualCompressionMethod() == CompressionMethod.DEFLATE)
                return new ZipInputStream(new InflaterInputStream(in, comprSize, this), this);

            throw new Zip4jException("compression type not supported");
        } catch(Zip4jException e) {
            throw e;
        } catch(Exception e) {
            throw new Zip4jException(e);
        }
    }

    public void checkCRC() {
        if (fileHeader == null)
            return;

        if (decoder instanceof AesDecoder || decoder instanceof AesNewDecoder) {
            decoder.checkCRC();
        } else {
            // TODO should be moved to decoder
            long calculatedCRC = crc.getValue() & 0xFFFFFFFFL;
            if (calculatedCRC != (fileHeader.getCrc32() & 0xFFFFFFFFL)) {
                String errMsg = "invalid CRC for file: " + fileHeader.getFileName();
                if (localFileHeader.getEncryption() == Encryption.PKWARE)
                    errMsg += " - Wrong Password?";
                throw new Zip4jException(errMsg);
            }
        }
    }

    @NonNull
    private LocalFileHeader readLocalFileHeader(@NonNull CentralDirectory.FileHeader fileHeader) throws IOException {
        try (LittleEndianRandomAccessFile in = openFile(fileHeader)) {
            return new LocalFileHeaderReader(fileHeader).read(in);
        }
    }

    private LittleEndianRandomAccessFile openFile(@NonNull CentralDirectory.FileHeader fileHeader) throws IOException {
        if (!zipModel.isSplitArchive())
            return new LittleEndianRandomAccessFile(zipModel.getZipFile());

        int diskNumber = fileHeader.getDiskNumber();
        currSplitFileCounter = diskNumber + 1;

        LittleEndianRandomAccessFile in = new LittleEndianRandomAccessFile(zipModel.getPartFile(diskNumber));

        if (currSplitFileCounter == 1) {
            int signature = in.readDword();

            if (signature != InternalZipConstants.SPLITSIG)
                throw new Zip4jException("Expected first part of split file signature (offs:" + in.getFilePointer() + ')');
        }

        return in;
    }

    private static FileOutputStream getOutputStream(@NonNull Path destDir, @NonNull CentralDirectory.FileHeader fileHeader) {
        try {
            Path file = destDir.resolve(fileHeader.getFileName());
            Path parent = file.getParent();

            if (!Files.exists(file))
                Files.createDirectories(parent);

            Files.deleteIfExists(file);

            return new FileOutputStream(file.toFile());
        } catch(IOException e) {
            throw new Zip4jException(e);
        }
    }

    public RandomAccessFile startNextSplitFile() throws IOException {
        Path currSplitFile = zipModel.getZipFile();

        if (currSplitFileCounter != zipModel.getEndCentralDirectory().getSplitParts())
            currSplitFile = ZipModel.getSplitFilePath(currSplitFile, currSplitFileCounter + 1);

        if (!Files.exists(currSplitFile))
            throw new Zip4jException("split file: " + currSplitFile.getFileName() + " does not exists");

        currSplitFileCounter++;
        return new RandomAccessFile(currSplitFile.toFile(), "r");
    }

    public void updateCRC(int b) {
        crc.update(b);
    }

}
