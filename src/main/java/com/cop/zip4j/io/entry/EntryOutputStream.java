package com.cop.zip4j.io.entry;

import com.cop.zip4j.core.builders.LocalFileHeaderBuilder;
import com.cop.zip4j.core.writers.DataDescriptorWriter;
import com.cop.zip4j.core.writers.LocalFileHeaderWriter;
import com.cop.zip4j.crypto.Encoder;
import com.cop.zip4j.exception.Zip4jException;
import com.cop.zip4j.core.builders.CentralDirectoryBuilder;
import com.cop.zip4j.io.out.MarkDataOutput;
import com.cop.zip4j.model.CentralDirectory;
import com.cop.zip4j.model.Compression;
import com.cop.zip4j.model.DataDescriptor;
import com.cop.zip4j.model.Encryption;
import com.cop.zip4j.model.LocalFileHeader;
import com.cop.zip4j.model.ZipModel;
import com.cop.zip4j.model.entry.PathZipEntry;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

/**
 * @author Oleg Cherednik
 * @since 26.07.2019
 */
@RequiredArgsConstructor
public abstract class EntryOutputStream extends OutputStream {

    public static final int SPLIT_SIGNATURE = 0x08074b50;

    private static final String MARK = EntryOutputStream.class.getSimpleName();

    private final ZipModel zipModel;
    private final CentralDirectory.FileHeader fileHeader;
    protected final Encoder encoder;
    protected final MarkDataOutput out;
    protected final Checksum checksum = new CRC32();

    public static EntryOutputStream create(@NonNull PathZipEntry entry, @NonNull ZipModel zipModel, @NonNull MarkDataOutput out) throws IOException {
        EntryOutputStream res = createOutputStream(entry, zipModel, out);
        res.writeHeader();
        return res;
    }

    private static EntryOutputStream createOutputStream(@NonNull PathZipEntry entry, @NonNull ZipModel zipModel, @NonNull MarkDataOutput out)
            throws IOException {
        Compression compression = entry.getCompression();
        Encoder encoder = entry.getEncryption().encoder(entry);
        CentralDirectory.FileHeader fileHeader = new CentralDirectoryBuilder(entry, zipModel, out.getCounter()).create();

        if (compression == Compression.DEFLATE)
            return new DeflateEntryOutputStream(zipModel, fileHeader, encoder, out, entry.getCompressionLevel());
        if (compression == Compression.STORE)
            return new StoreEntryOutputStream(zipModel, fileHeader, encoder, out);

        throw new Zip4jException("Compression is not supported: " + compression);
    }

    private void writeHeader() throws IOException {
        // only at the beginning of the split file
        if (zipModel.isSplitArchive() && zipModel.isEmpty())
            out.writeDword(SPLIT_SIGNATURE);

        zipModel.addFileHeader(fileHeader);

        writeLocalFileHeader();
        encoder.writeHeader(out);
    }

    private void writeLocalFileHeader() throws IOException {
        fileHeader.setOffsLocalFileHeader(out.getOffs());

        LocalFileHeader localFileHeader = new LocalFileHeaderBuilder(fileHeader).create();
        new LocalFileHeaderWriter(zipModel, localFileHeader).write(out);

        out.mark(MARK);
    }

    @Override
    public final void write(int b) throws IOException {
        write(new byte[] { (byte)b }, 0, 1);
    }

    @Override
    public void write(byte[] buf, int offs, int len) throws IOException {
        checksum.update(buf, offs, len);
    }

    @Override
    public void close() throws IOException {
        encoder.close(out);
        updateFileHeader();
        writeDataDescriptor();
    }

    private void updateFileHeader() {
        fileHeader.setCrc32(fileHeader.getEncryption() == Encryption.AES ? 0 : checksum.getValue());
        fileHeader.setCompressedSize(out.getWrittenBytesAmount(MARK));
    }

    private void writeDataDescriptor() throws IOException {
        // TODO should be isDataDescriptorExists == true only when parameters.getCompressionMethod() == CompressionMethod.DEFLATE
        if (!fileHeader.getGeneralPurposeFlag().isDataDescriptorExists())
            return;

        DataDescriptor dataDescriptor = new DataDescriptor();
        dataDescriptor.setCrc32(fileHeader.getCrc32());
        dataDescriptor.setCompressedSize(fileHeader.getCompressedSize());
        dataDescriptor.setUncompressedSize(fileHeader.getUncompressedSize());

        new DataDescriptorWriter(dataDescriptor).write(out);
    }

}
