package ru.olegcherednik.zip4jvm;

import org.assertj.core.api.Assertions;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Ignore;
import org.testng.annotations.Test;
import ru.olegcherednik.zip4jvm.assertj.Zip4jAssertions;
import ru.olegcherednik.zip4jvm.model.Compression;
import ru.olegcherednik.zip4jvm.model.CompressionLevel;
import ru.olegcherednik.zip4jvm.model.ZipParameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author Oleg Cherednik
 * @since 14.03.2019
 */
@SuppressWarnings("FieldNamingConvention")
public class ZipFolderNoSplitTest {

    private static final Path rootDir = Zip4jSuite.generateSubDirNameWithTime(ZipFolderNoSplitTest.class);
    private static final Path zipFile = rootDir.resolve("src.zip");

    @BeforeClass
    public static void createDir() throws IOException {
        Files.createDirectories(rootDir);
    }

    @AfterClass(enabled = Zip4jSuite.clear)
    public static void removeDir() throws IOException {
        Zip4jSuite.removeDir(rootDir);
    }

    @Test
    public void shouldCreateNewZipWithFolder() throws IOException {
        ZipParameters parameters = ZipParameters.builder()
                                                .compression(Compression.DEFLATE, CompressionLevel.NORMAL)
                                                .defaultFolderPath(Zip4jSuite.srcDir).build();

        ZipIt zipIt = ZipIt.builder().zipFile(zipFile).build();
        zipIt.add(Zip4jSuite.carsDir, parameters);

        Zip4jAssertions.assertThatDirectory(zipFile.getParent()).exists().hasSubDirectories(0).hasFiles(1);
        Zip4jAssertions.assertThatZipFile(zipFile).exists().rootEntry().hasSubDirectories(1).hasFiles(0);
        Zip4jAssertions.assertThatZipFile(zipFile).directory("cars/").matches(TestUtils.zipCarsDirAssert);
    }

    @Test(dependsOnMethods = "shouldCreateNewZipWithFolder")
    @Ignore
    public void shouldAddFolderToExistedZip() throws IOException {
        Assertions.assertThat(Files.exists(zipFile)).isTrue();
        Assertions.assertThat(Files.isRegularFile(zipFile)).isTrue();

        ZipParameters parameters = ZipParameters.builder()
                                                .compression(Compression.DEFLATE, CompressionLevel.NORMAL)
                                                .defaultFolderPath(Zip4jSuite.srcDir).build();

        ZipIt zip = ZipIt.builder().zipFile(zipFile).build();
        zip.add(Zip4jSuite.starWarsDir, parameters);

        Zip4jAssertions.assertThatDirectory(zipFile.getParent()).exists().hasSubDirectories(0).hasFiles(1);
        Zip4jAssertions.assertThatZipFile(zipFile).exists().rootEntry().hasSubDirectories(2).hasFiles(0);
        Zip4jAssertions.assertThatZipFile(zipFile).directory("cars/").matches(TestUtils.zipCarsDirAssert);
        Zip4jAssertions.assertThatZipFile(zipFile).directory("Star Wars/").matches(TestUtils.zipStarWarsDirAssert);
    }

    @Test(dependsOnMethods = "shouldAddFolderToExistedZip")
    @Ignore
    public void shouldAddEmptyDirectoryToExistedZip() throws IOException {
        Assertions.assertThat(Files.exists(zipFile)).isTrue();
        Assertions.assertThat(Files.isRegularFile(zipFile)).isTrue();

        ZipParameters parameters = ZipParameters.builder()
                                                .compression(Compression.DEFLATE, CompressionLevel.NORMAL)
                                                .defaultFolderPath(Zip4jSuite.srcDir).build();

        ZipIt zip = ZipIt.builder().zipFile(zipFile).build();
        zip.add(Zip4jSuite.emptyDir, parameters);

        Zip4jAssertions.assertThatDirectory(zipFile.getParent()).exists().hasSubDirectories(0).hasFiles(1);
        Zip4jAssertions.assertThatZipFile(zipFile).exists().rootEntry().hasSubDirectories(3).hasFiles(0);
        Zip4jAssertions.assertThatZipFile(zipFile).directory("cars/").matches(TestUtils.zipCarsDirAssert);
        Zip4jAssertions.assertThatZipFile(zipFile).directory("Star Wars/").matches(TestUtils.zipStarWarsDirAssert);
        Zip4jAssertions.assertThatZipFile(zipFile).directory("empty_dir/").matches(TestUtils.zipEmptyDirAssert);
    }

}
