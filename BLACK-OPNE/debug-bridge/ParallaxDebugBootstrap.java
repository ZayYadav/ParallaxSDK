package parallax.virtual.debug;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
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

        if (total < 4L || !isElf(output)) {
            output.delete();
            return LoadResult.fail("selected file is not an ELF library");
        }

        try {
            System.load(output.getAbsolutePath());
            return LoadResult.loaded(output.getAbsolutePath());
        } catch (Throwable throwable) {
            return LoadResult.fail("System.load failed: " + throwable.getClass().getSimpleName());
        }
    }

    private static boolean isElf(File file) {
        try (FileInputStream input = new FileInputStream(file)) {
            return input.read() == 0x7f
                    && input.read() == 'E'
                    && input.read() == 'L'
                    && input.read() == 'F';
        } catch (Throwable ignored) {
            return false;
        }
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
