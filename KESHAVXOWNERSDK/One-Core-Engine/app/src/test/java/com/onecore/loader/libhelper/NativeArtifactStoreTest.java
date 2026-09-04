package com.onecore.loader.libhelper;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;

import org.junit.Test;

public class NativeArtifactStoreTest {
    @Test
    public void installCommitsValidElfArtifact() throws Exception {
        File directory = Files.createTempDirectory("native-artifact-test").toFile();
        File source = new File(directory, "source.so");
        byte[] content = {0x7f, 'E', 'L', 'F', 2, 1, 1, 0};
        try (FileOutputStream output = new FileOutputStream(source)) {
            output.write(content);
        }

        File destination = new File(directory, "installed.so");
        assertTrue(NativeArtifactStore.install(source, destination));
        assertArrayEquals(content, Files.readAllBytes(destination.toPath()));
        assertTrue(destination.canRead());
        assertTrue(destination.canExecute());
    }

    @Test
    public void installRejectsNonElfAndLeavesDestinationUntouched() throws Exception {
        File directory = Files.createTempDirectory("native-artifact-test").toFile();
        File source = new File(directory, "invalid.so");
        try (FileOutputStream output = new FileOutputStream(source)) {
            output.write(new byte[]{1, 2, 3, 4});
        }

        File destination = new File(directory, "installed.so");
        assertThrows(IOException.class, () -> NativeArtifactStore.install(source, destination));
        assertFalse(destination.exists());
    }
}
