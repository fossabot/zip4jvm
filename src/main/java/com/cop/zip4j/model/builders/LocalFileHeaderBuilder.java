package com.cop.zip4j.model.builders;

import com.cop.zip4j.model.CentralDirectory;
import com.cop.zip4j.model.ExtraField;
import com.cop.zip4j.model.LocalFileHeader;
import com.cop.zip4j.model.Zip64;
import com.cop.zip4j.model.ZipModel;
import com.cop.zip4j.model.aes.AesExtraDataRecord;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.function.LongSupplier;

/**
 * @author Oleg Cherednik
 * @since 04.08.2019
 */
@RequiredArgsConstructor
public final class LocalFileHeaderBuilder {

    public static final long LOOK_IN_DATA_DESCRIPTOR = 0;
    public static final long LOOK_IN_EXTRA_FIELD = ZipModel.ZIP_64_LIMIT;

    @NonNull
    private final ZipModel zipModel;
    @NonNull
    private final CentralDirectory.FileHeader fileHeader;

    public LocalFileHeader create() {
        LocalFileHeader localFileHeader = new LocalFileHeader();

        localFileHeader.setVersionToExtract(fileHeader.getVersionToExtract());
        localFileHeader.setGeneralPurposeFlag(fileHeader.getGeneralPurposeFlag().getAsInt());
        localFileHeader.setCompressionMethod(fileHeader.getEncryption().getCompressionMethod(fileHeader));
        localFileHeader.setLastModifiedTime(fileHeader.getLastModifiedTime());
        localFileHeader.setCrc32(getValue(() -> fileHeader.getEncryption().getChecksum(fileHeader)));
        localFileHeader.setCompressedSize(getValue(fileHeader::getCompressedSize));
        localFileHeader.setUncompressedSize(getValue(fileHeader::getCompressedSize));
        localFileHeader.setFileName(fileHeader.getFileName());
        localFileHeader.setExtraField(getExtraField());

        return localFileHeader;
    }

    private long getValue(LongSupplier supplier) {
        if (fileHeader.getGeneralPurposeFlag().isDataDescriptorExists())
            return LOOK_IN_DATA_DESCRIPTOR;
        if (zipModel.isZip64())
            return LOOK_IN_EXTRA_FIELD;
        return supplier.getAsLong();
    }

    private ExtraField getExtraField() {
        Zip64.ExtendedInfo extendedInfo = getExtendedInfo();
        // TODO should be created
        AesExtraDataRecord aesExtraDataRecord = AesExtraDataRecord.NULL;

        if (extendedInfo == Zip64.ExtendedInfo.NULL && aesExtraDataRecord == AesExtraDataRecord.NULL)
            return ExtraField.NULL;

        ExtraField extraField = new ExtraField();
        extraField.setExtendedInfo(extendedInfo);
        extraField.setAesExtraDataRecord(aesExtraDataRecord);
        return extraField;
    }

    private Zip64.ExtendedInfo getExtendedInfo() {
        if (fileHeader.getGeneralPurposeFlag().isDataDescriptorExists() || !zipModel.isZip64())
            return Zip64.ExtendedInfo.NULL;

        return Zip64.ExtendedInfo.builder()
                                 .size(8)
                                 .compressedSize(fileHeader.getCompressedSize())
                                 .uncompressedSize(fileHeader.getUncompressedSize())
                                 .build();
    }

}
