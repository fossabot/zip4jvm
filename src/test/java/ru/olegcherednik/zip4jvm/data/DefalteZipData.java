package ru.olegcherednik.zip4jvm.data;

import ru.olegcherednik.zip4jvm.Zip4jSuite;
import ru.olegcherednik.zip4jvm.ZipIt;
import ru.olegcherednik.zip4jvm.model.Compression;
import ru.olegcherednik.zip4jvm.model.CompressionLevel;
import ru.olegcherednik.zip4jvm.model.Encryption;
import ru.olegcherednik.zip4jvm.model.ZipParameters;
import lombok.experimental.UtilityClass;

import java.io.IOException;
import java.nio.file.Files;

import static ru.olegcherednik.zip4jvm.assertj.Zip4jAssertions.assertThatDirectory;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Oleg Cherednik
 * @since 06.08.2019
 */
@UtilityClass
public class DefalteZipData {

    public static void createDeflateZip() throws IOException {
        createDeflateSolidZip();
        createDeflateSplitZip();
        createDeflateSolidPkwareZip();
        createDeflateSolidAesZip();
    }

    private static void createDeflateSolidZip() throws IOException {
        ZipParameters parameters = ZipParameters.builder()
                                                .compression(Compression.DEFLATE)
                                                .compressionLevel(CompressionLevel.NORMAL).build();
        ZipIt zip = ZipIt.builder().zipFile(Zip4jSuite.deflateSolidZip).build();
        zip.add(Zip4jSuite.srcDir, parameters);

        assertThat(Files.exists(Zip4jSuite.deflateSolidZip)).isTrue();
        assertThat(Files.isRegularFile(Zip4jSuite.deflateSolidZip)).isTrue();
        assertThatDirectory(Zip4jSuite.deflateSolidZip.getParent()).exists().hasSubDirectories(0).hasFiles(1);
    }

    private static void createDeflateSplitZip() throws IOException {
        ZipParameters parameters = ZipParameters.builder()
                                                .compression(Compression.DEFLATE)
                                                .compressionLevel(CompressionLevel.NORMAL)
                                                .splitLength(1024 * 1024).build();
        ZipIt zip = ZipIt.builder().zipFile(Zip4jSuite.deflateSplitZip).build();
        zip.add(Zip4jSuite.srcDir, parameters);

        assertThat(Files.exists(Zip4jSuite.deflateSplitZip)).isTrue();
        assertThat(Files.isRegularFile(Zip4jSuite.deflateSplitZip)).isTrue();
        assertThatDirectory(Zip4jSuite.deflateSplitZip.getParent()).exists().hasSubDirectories(0).hasFiles(10);
    }

    private static void createDeflateSolidPkwareZip() throws IOException {
        ZipParameters parameters = ZipParameters.builder()
                                                .compression(Compression.DEFLATE)
                                                .compressionLevel(CompressionLevel.NORMAL)
                                                .encryption(Encryption.PKWARE)
                                                .comment("password: " + new String(Zip4jSuite.password))
                                                .password(Zip4jSuite.password).build();
        ZipIt zip = ZipIt.builder().zipFile(Zip4jSuite.deflateSolidPkwareZip).build();
        zip.add(Zip4jSuite.srcDir, parameters);

        assertThat(Files.exists(Zip4jSuite.deflateSolidPkwareZip)).isTrue();
        assertThat(Files.isRegularFile(Zip4jSuite.deflateSolidPkwareZip)).isTrue();
        assertThatDirectory(Zip4jSuite.deflateSolidPkwareZip.getParent()).exists().hasSubDirectories(0).hasFiles(1);
    }

    private static void createDeflateSolidAesZip() throws IOException {
        ZipParameters parameters = ZipParameters.builder()
                                                .compression(Compression.DEFLATE)
                                                .compressionLevel(CompressionLevel.NORMAL)
                                                .encryption(Encryption.AES_256)
                                                .comment("password: " + new String(Zip4jSuite.password))
                                                .password(Zip4jSuite.password).build();
        ZipIt zip = ZipIt.builder().zipFile(Zip4jSuite.deflateSolidAesZip).build();
        zip.add(Zip4jSuite.srcDir, parameters);

        assertThat(Files.exists(Zip4jSuite.deflateSolidAesZip)).isTrue();
        assertThat(Files.isRegularFile(Zip4jSuite.deflateSolidAesZip)).isTrue();
        assertThatDirectory(Zip4jSuite.deflateSolidAesZip.getParent()).exists().hasSubDirectories(0).hasFiles(1);
    }

}
