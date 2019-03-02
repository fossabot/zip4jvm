package net.lingala.zip4j.core;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.Context;
import net.lingala.zip4j.model.ZipParameters;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Oleg Cherednik
 * @since 02.03.2019
 */
@RequiredArgsConstructor
public class ZipFileNew {
    private final Path file;

    public void addFiles(@NonNull List<Path> files) throws ZipException {
        addFiles(files, Context.builder().build());
    }

    public void addFiles(@NonNull List<Path> files, @NonNull Context context) throws ZipException {
        ZipFile zipFile = new ZipFile(file.toFile());

        List<File> addFiles = files.stream()
                                   .map(Path::toFile)
                                   .collect(Collectors.toList());

        ZipParameters parameters = new ZipParameters();
        parameters.setCompressionMethod(context.getCompression().getVal());

        zipFile.addFiles(addFiles, parameters);
    }
}
