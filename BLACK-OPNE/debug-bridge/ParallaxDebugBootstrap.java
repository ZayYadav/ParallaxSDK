package parallax.virtual.debug;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * Copy this class into a developer-owned app that you want to test inside
 * Parallax Virtual. Call loadFromLaunchIntent(activity, BuildConfig.DEBUG)
 * near the start of the launcher Activity.
 *
 * The target app performs System.load() itself. Parallax Virtual does not
 * inject code into arbitrary third-party processes.
 */
public final class ParallaxDebugBootstrap {

    private static final String EXTRA_ENABLED = "parallax.debug.enabled";
    private static final String EXTRA_URI = "parallax.debug.lib_uri";
    private static final String EXTRA_NAME = "parallax.debug.lib_name";
    private static final String EXTRA_SHA256 = "parallax.debug.lib_sha256";
    private static final String EXTRA_TARGET = "parallax.debug.target_package";
    private static final String EXTRA_SESSION = "parallax.debug.session_id";
    private static final String EXPECTED_AUTHORITY = "parallax.VIRTUAL.debugfiles";
    private static final long MAX_BYTES = 64L * 1024L * 1024L;

    private ParallaxDebugBootstrap() {
    }

    public static LoadResult loadFromLaunchIntent(Activity activity, boolean debugBuild) {
        if (activity == null) return LoadResult.skip("activity is null");
        if (!debugBuild) return LoadResult.skip("disabled in non-debug build");

        Intent intent = activity.getIntent();
        if (intent == null || !intent.getBooleanExtra(EXTRA_ENABLED, false)) {
            return LoadResult.skip("no Parallax debug request");
        }

        String target = intent.getStringExtra(EXTRA_TARGET);
        if (target == null || !activity.getPackageName().equals(target)) {
            return LoadResult.fail("target package mismatch");
        }

        String rawUri = intent.getStringExtra(EXTRA_URI);
        if (rawUri == null || rawUri.trim().isEmpty()) {
            return LoadResult.fail("debug library URI missing");
        }

        Uri uri;
        try {
            uri = Uri.parse(rawUri);
        } catch (Throwable throwable) {
            return LoadResult.fail("invalid debug library URI");
        }
        if (!"content".equalsIgnoreCase(uri.getScheme())) {
            return LoadResult.fail("only content:// debug libraries are accepted");
        }
        if (!EXPECTED_AUTHORITY.equals(uri.getAuthority())) {
            return LoadResult.fail("unexpected debug provider authority");
        }

        String requestedName = sanitize(intent.getStringExtra(EXTRA_NAME));
        if (!requestedName.toLowerCase(Locale.US).endsWith(".so")) {
            return LoadResult.fail("debug file is not a .so");
        }
        String session = sanitize(intent.getStringExtra(EXTRA_SESSION));
        if (session.isEmpty()) session = "session";
        String expectedSha256 = intent.getStringExtra(EXTRA_SHA256);
        if (expectedSha256 == null || !expectedSha256.matches("(?i)[0-9a-f]{64}")) {
            return LoadResult.fail("debug library digest missing or invalid");
        }

        File dir = new File(activity.getCodeCacheDir(), "parallax-debug");
        if (!dir.exists() && !dir.mkdirs()) {
            return LoadResult.fail("cannot create app-private debug directory");
        }
        File output = new File(dir, session + "-" + requestedName);

        long total = 0L;
        byte[] buffer = new byte[32 * 1024];
        try (InputStream input = activity.getContentResolver().openInputStream(uri);
             FileOutputStream stream = new FileOutputStream(output, false)) {
            if (input == null) return LoadResult.fail("cannot open debug library URI");
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_BYTES) {
                    output.delete();
                    return LoadResult.fail("debug library exceeds 64 MB");
                }
                stream.write(buffer, 0, read);
            }
            stream.flush();
        } catch (Throwable throwable) {
            output.delete();
            return LoadResult.fail("copy failed: " + throwable.getClass().getSimpleName());
        }

        String headerError = validateArm64SharedObject(output);
        if (headerError != null) {
            output.delete();
            return LoadResult.fail(headerError);
        }

        try {
            if (!expectedSha256.equalsIgnoreCase(sha256(output))) {
                output.delete();
                return LoadResult.fail("debug library digest mismatch");
            }
        } catch (Throwable throwable) {
            output.delete();
            return LoadResult.fail("cannot verify debug library digest");
        }

        try {
            System.load(output.getAbsolutePath());
            return LoadResult.loaded(output.getAbsolutePath());
        } catch (Throwable throwable) {
            return LoadResult.fail("System.load failed: " + throwable.getClass().getSimpleName());
        }
    }

    private static String validateArm64SharedObject(File file) {
        byte[] header = new byte[20];
        try (FileInputStream input = new FileInputStream(file)) {
            int offset = 0;
            while (offset < header.length) {
                int read = input.read(header, offset, header.length - offset);
                if (read < 0) break;
                offset += read;
            }
            if (offset != header.length) return "ELF header is truncated";
        } catch (Throwable ignored) {
            return "cannot read debug library";
        }

        if ((header[0] & 0xff) != 0x7f || header[1] != 'E'
                || header[2] != 'L' || header[3] != 'F') return "file is not ELF";
        if ((header[4] & 0xff) != 2) return "library is not 64-bit";
        if ((header[5] & 0xff) != 1) return "library uses unsupported byte order";
        if (u16le(header, 16) != 3) return "ELF is not a shared object";
        if (u16le(header, 18) != 183) return "library ABI is not arm64-v8a";
        return null;
    }

    private static int u16le(byte[] value, int offset) {
        return (value[offset] & 0xff) | ((value[offset + 1] & 0xff) << 8);
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[32 * 1024];
        try (FileInputStream input = new FileInputStream(file)) {
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        StringBuilder out = new StringBuilder(64);
        for (byte value : digest.digest()) {
            out.append(String.format(Locale.US, "%02x", value & 0xff));
        }
        return out.toString();
    }

    private static String sanitize(String value) {
        if (value == null) return "";
        String clean = value.replaceAll("[^A-Za-z0-9._-]", "_");
        return clean.length() > 96 ? clean.substring(clean.length() - 96) : clean;
    }

    public static final class LoadResult {
        public final boolean loaded;
        public final boolean attempted;
        public final String message;

        private LoadResult(boolean loaded, boolean attempted, String message) {
            this.loaded = loaded;
            this.attempted = attempted;
            this.message = message;
        }

        static LoadResult loaded(String path) {
            return new LoadResult(true, true, "loaded: " + path);
        }

        static LoadResult fail(String message) {
            return new LoadResult(false, true, message);
        }

        static LoadResult skip(String message) {
            return new LoadResult(false, false, message);
        }
    }
}
