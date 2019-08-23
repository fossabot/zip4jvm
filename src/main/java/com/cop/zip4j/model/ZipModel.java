package com.cop.zip4j.model;

import com.cop.zip4j.exception.Zip4jException;
import com.cop.zip4j.model.activity.Activity;
import com.cop.zip4j.model.activity.PlainActivity;
import com.cop.zip4j.model.activity.Zip64Activity;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.ArrayUtils;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * zip64:
 * 1. Size of archive is over 4Gb (2^32)
 * 2. Total entries is over 65535 (2^16 - 1)
 * http://www.artpol-software.com/ZipArchive/KB/0610051629.aspx#limits
 *
 * @author Oleg Cherednik
 * @since 10.03.2019
 */
@Getter
@Setter
@RequiredArgsConstructor
public class ZipModel {

    public static final int NO_SPLIT = -1;
    // MIN_SPLIT_LENGTH = 64K bytes
    public static final int MIN_SPLIT_LENGTH = 64 * 1024;
    public static final long ZIP_64_LIMIT = 0xFFFF_FFFFL;

    @NonNull
    private final Path zipFile;
    @NonNull
    private final Charset charset;
    @NonNull
    private CentralDirectory centralDirectory = new CentralDirectory();
    @NonNull
    private EndCentralDirectory endCentralDirectory = new EndCentralDirectory();
    @NonNull
    private Zip64 zip64 = Zip64.NULL;
    private long splitLength = NO_SPLIT;
    private Activity activity = new PlainActivity();

    public void setEndCentralDirectory(@NonNull EndCentralDirectory endCentralDirectory) {
        this.endCentralDirectory = endCentralDirectory;
    }

    public void setSplitLength(long splitLength) {
        this.splitLength = splitLength < MIN_SPLIT_LENGTH ? NO_SPLIT : splitLength;
    }

    public boolean isSplitArchive() {
        return splitLength > 0 || endCentralDirectory.isSplitArchive();
    }

    public void setNoSplitArchive() {
        splitLength = NO_SPLIT;
        endCentralDirectory.setNoSplitArchive();
    }

    public boolean isZip64() {
        return zip64 != Zip64.NULL;
    }

    public void setZip64(Zip64 zip64) {
        this.zip64 = zip64;
        activity = zip64 == Zip64.NULL ? new PlainActivity() : new Zip64Activity();
    }

    public void zip64() {
        if (!isZip64())
            setZip64(Zip64.of(new Zip64.EndCentralDirectoryLocator(), new Zip64.EndCentralDirectory()));
    }

    public void setComment(String comment) {
        endCentralDirectory.setComment(comment);
    }

    public void updateZip64() {
        if (!isZip64())
            return;

        Zip64.EndCentralDirectory dir = zip64.getEndCentralDirectory();
        dir.setSize(Zip64.EndCentralDirectory.SIZE + ArrayUtils.getLength(dir.getExtensibleDataSector()));
        dir.setVersionMadeBy(isEmpty() ? CentralDirectory.FileHeader.VERSION : getFileHeaders().get(0).getVersionMadeBy());
        dir.setVersionNeededToExtract(isEmpty() ? CentralDirectory.FileHeader.VERSION : getFileHeaders().get(0).getVersionToExtract());
        dir.setDisk(endCentralDirectory.getSplitParts());
        dir.setStartDisk(endCentralDirectory.getStartDiskNumber());
        dir.setDiskEntries(countNumberOfFileHeaderEntriesOnDisk());
        dir.setTotalEntries(getFileHeaders().size());
        dir.setSize(endCentralDirectory.getSize());
        dir.setOffs(endCentralDirectory.getOffs());

        Zip64.EndCentralDirectoryLocator locator = zip64.getEndCentralDirectoryLocator();
        locator.setOffs(endCentralDirectory.getOffs() + endCentralDirectory.getSize());
    }

    private int countNumberOfFileHeaderEntriesOnDisk() {
        if (isSplitArchive())
            return getFileHeaders().size();

        return (int)getFileHeaders().stream()
                                    .filter(fileHeader -> fileHeader.getDiskNumber() == endCentralDirectory.getSplitParts())
                                    .count();
    }

    public boolean isEmpty() {
        return getTotalEntries() == 0;
    }

    public long getTotalEntries() {
        return isZip64() ? zip64.getEndCentralDirectory().getTotalEntries() : endCentralDirectory.getTotalEntries();
    }

    public List<String> getEntryNames() {
        return getFileHeaders().stream()
                               .map(CentralDirectory.FileHeader::getFileName)
                               .collect(Collectors.toList());
    }

    public long getCentralDirectoryOffs() {
        return isZip64() ? zip64.getEndCentralDirectory().getOffs() : endCentralDirectory.getOffs();
    }

    public static Path getSplitFilePath(Path zipFile, int count) {
        return zipFile.getParent().resolve(String.format("%s.z%02d", FilenameUtils.getBaseName(zipFile.toString()), count));
    }

    public Path getPartFile(int diskNumber) {
        return diskNumber == endCentralDirectory.getSplitParts() ? zipFile : getSplitFilePath(zipFile, diskNumber + 1);
    }

    @SuppressWarnings("MethodCanBeVariableArityMethod")
    public void convertToSolid(long[] fileSizeList) {
        long totalBytesWritten = Arrays.stream(fileSizeList).sum();

        setNoSplitArchive();
        updateFileHeaders(fileSizeList);
        updateEndCentralDirectory(totalBytesWritten);
        updateZip64EndCentralDirLocator(totalBytesWritten);
        updateZip64EndCentralDirRec(totalBytesWritten);
    }

    @SuppressWarnings("MethodCanBeVariableArityMethod")
    private void updateFileHeaders(long[] fileSizeList) {
        getFileHeaders().forEach(fileHeader -> {
            fileHeader.updateOffLocalHeaderRelative(Arrays.stream(fileSizeList, 0, fileHeader.getDiskNumber()).sum());
            fileHeader.setDiskNumber(0);
        });
    }

    private void updateEndCentralDirectory(long totalBytesWritten) throws Zip4jException {
        endCentralDirectory.setSplitParts(0);
        endCentralDirectory.setStartDiskNumber(0);
        endCentralDirectory.setTotalEntries(getFileHeaders().size());
        endCentralDirectory.setDiskEntries(getFileHeaders().size());
        endCentralDirectory.setOffs(totalBytesWritten);
    }

    private void updateZip64EndCentralDirLocator(long totalBytesWritten) throws Zip4jException {
        if (isZip64()) {
            Zip64.EndCentralDirectoryLocator locator = zip64.getEndCentralDirectoryLocator();
            locator.setStartDisk(0);
            locator.updateOffsetZip64EndOfCentralDirRec(totalBytesWritten);
            locator.setTotalDisks(1);
        }
    }

    private void updateZip64EndCentralDirRec(long totalBytesWritten) throws Zip4jException {
        if (isZip64()) {
            Zip64.EndCentralDirectory dir = zip64.getEndCentralDirectory();
            dir.setDisk(0);
            dir.setStartDisk(0);
            dir.setDiskEntries(endCentralDirectory.getTotalEntries());
            dir.updateOffsetStartCenDirWRTStartDiskNo(totalBytesWritten);
        }
    }

    public List<CentralDirectory.FileHeader> getFileHeaders() {
        return centralDirectory.getFileHeaders();
    }

    public void addFileHeader(CentralDirectory.FileHeader fileHeader) {
        centralDirectory.addFileHeader(fileHeader);
        endCentralDirectory.incTotalEntries();
        endCentralDirectory.incDiskEntries();
    }

    public void setFileHeaders(List<CentralDirectory.FileHeader> fileHeaders) {
        centralDirectory.setFileHeaders(fileHeaders);
        endCentralDirectory.setTotalEntries(fileHeaders.size());
        endCentralDirectory.setDiskEntries(fileHeaders.size());
    }

    @NonNull
    public ZipModel noSplitOnly() {
        if (Files.exists(zipFile) && isSplitArchive())
            throw new Zip4jException("Zip file already exists. Zip file format does not allow updating split/spanned files");

        return this;
    }

}
