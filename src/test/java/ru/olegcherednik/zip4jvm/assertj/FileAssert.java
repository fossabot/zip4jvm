package ru.olegcherednik.zip4jvm.assertj;

import java.nio.file.Path;

/**
 * @author Oleg Cherednik
 * @since 28.03.2019
 */
public class FileAssert extends AbstractFileExtAssert<FileAssert> {

    public FileAssert(Path actual) {
        super(actual, FileAssert.class);
    }
}
