package com.onecore.loader.libhelper;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/** Performs validated, owner-only and atomic native artifact staging. */
final class NativeArtifactStore {
    private static final int BUFFER_SIZE = 32 * 1024;
    private static final byte[] ELF_MAGIC = {0x7f, 'E', 'L', 'F'};

    private NativeArtifactStore() {
    }

    static boolean install(File source, File destination) throws IOException {
        requireElf(source);

        File parent = destination.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
            throw new IOException("Native artifact destination is unavailable");
        }

        File staged = File.createTempFile(".native-artifact-", ".tmp", parent);
        boolean committed = false;
        try {
            copyAndSync(source, staged);
            requireElf(staged);
            setOwnerOnly(staged, true);

            if (destination.exists() && !destination.delete()) {
                throw new IOException("Unable to replace native artifact");
            }
            if (!staged.renameTo(destination)) {
                throw new IOException("Unable to commit native artifact");
            }
            committed = true;
            setOwnerOnly(destination, true);
            return true;
        } finally {
            if (!committed && staged.exists()) {
                // Best-effort cleanup of an incomplete internal staging file.
                staged.delete();
            }
        }
    }

    static void setOwnerOnly(File file, boolean executable) throws IOException {
        // Clearing permissions can report false when a platform has no matching bit
        // (notably host-side Windows tests), so validate the owner grants instead.
        file.setReadable(false, false);
        file.setWritable(false, false);
        file.setExecutable(false, false);
        boolean updated = file.setReadable(true, true) && file.setWritable(true, true);
        if (executable) {
            updated = file.setExecutable(true, true) && updated;
        }
        if (!updated) {
            throw new IOException("Unable to restrict native artifact permissions");
        }
    }

    private static void requireElf(File file) throws IOException {
        if (!file.isFile() || file.length() < ELF_MAGIC.length) {
            throw new IOException("Native artifact is missing or empty");
        }
        try (FileInputStream input = new FileInputStream(file)) {
            for (byte expected : ELF_MAGIC) {
                if (input.read() != (expected & 0xff)) {
                    throw new IOException("Native artifact has an invalid ELF header");
                }
            }
        }
    }

    private static void copyAndSync(File source, File destination) throws IOException {
        try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(source));
             FileOutputStream fileOutput = new FileOutputStream(destination);
             BufferedOutputStream output = new BufferedOutputStream(fileOutput)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            output.flush();
            fileOutput.getFD().sync();
        }
    }
}
