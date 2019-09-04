package ru.olegcherednik.zip4jvm.assertj;

import java.util.zip.ZipEntry;

/**
 * @author Oleg Cherednik
 * @since 25.03.2019
 */
public class ZipEntryDirectoryAssert extends AbstractZipEntryDirectoryAssert<ZipEntryDirectoryAssert> {

    public ZipEntryDirectoryAssert(ZipEntry actual, ZipFileDecorator zipFile) {
        super(actual, ZipEntryDirectoryAssert.class, zipFile);
    }
}
