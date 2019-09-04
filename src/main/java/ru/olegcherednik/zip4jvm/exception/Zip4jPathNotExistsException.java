package ru.olegcherednik.zip4jvm.exception;

import java.nio.file.Path;

/**
 * @author Oleg Cherednik
 * @since 11.08.2019
 */
public class Zip4jPathNotExistsException extends Zip4jException {

    private static final long serialVersionUID = 6634130368683535775L;

    public Zip4jPathNotExistsException(Path path) {
        super("Path not exists: " + path, ErrorCode.PATH_NOT_EXISTS);
    }
}
