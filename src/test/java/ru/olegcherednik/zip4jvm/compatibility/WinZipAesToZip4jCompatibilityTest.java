package ru.olegcherednik.zip4jvm.compatibility;

import ru.olegcherednik.zip4jvm.TestUtils;
import ru.olegcherednik.zip4jvm.UnzipIt;
import ru.olegcherednik.zip4jvm.Zip4jSuite;
import de.idyl.winzipaes.AesZipFileEncrypter;
import de.idyl.winzipaes.impl.AESEncrypterJCA;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static ru.olegcherednik.zip4jvm.assertj.Zip4jAssertions.assertThatDirectory;

/**
 * @author Oleg Cherednik
 * @since 15.08.2019
 */
@Test
@SuppressWarnings({ "NewClassNamingConvention", "FieldNamingConvention" })
public class WinZipAesToZip4jCompatibilityTest {

    private static final Path rootDir = Zip4jSuite.generateSubDirNameWithTime(WinZipAesToZip4jCompatibilityTest.class);

    public void winZipAesShouldBeReadableForZip4j() throws IOException {
        Path zipFile = zipItWithWinZipAes();
        Path dir = unzipItWithZip4j(zipFile);

        Files.createDirectories(dir.resolve("empty_dir"));
        assertThatDirectory(dir).matches(TestUtils.dirAssert);
    }

    private static Path zipItWithWinZipAes() throws IOException {
        String password = new String(Zip4jSuite.password);
        Path zipFile = Zip4jSuite.subDirNameAsMethodName(rootDir).resolve("src.zip");
        Files.createDirectories(zipFile.getParent());

        AesZipFileEncrypter encrypter = new AesZipFileEncrypter(zipFile.toFile(), new AESEncrypterJCA());
        encrypter.setComment("password: " + password);

        for (Path file : getDirectoryEntries(Zip4jSuite.srcDir)) {
            if (Files.isDirectory(file))
                continue;

            String pathForEntry = Zip4jSuite.srcDir.relativize(file).toString();
            encrypter.add(file.toFile(), pathForEntry, password);
        }

        encrypter.close();

        return zipFile;
    }

    @SuppressWarnings("NewMethodNamingConvention")
    private static Path unzipItWithZip4j(Path zipFile) throws IOException {
        Path dir = Zip4jSuite.subDirNameAsMethodName(rootDir).resolve("unzip");
        UnzipIt unzip = UnzipIt.builder()
                               .zipFile(zipFile)
                               .password(Zip4jSuite.password)
                               .build();
        unzip.extract(dir);

        // WinZipAes does not support empty folders in zip
        Files.createDirectories(dir.resolve("empty_dir"));

        return dir;
    }

    private static List<Path> getDirectoryEntries(Path dir) {
        try {
            return Files.walk(dir)
                        .filter(path -> Files.isRegularFile(path) || Files.isDirectory(path))
                        .collect(Collectors.toList());
        } catch(IOException e) {
            return Collections.emptyList();
        }
    }

}
