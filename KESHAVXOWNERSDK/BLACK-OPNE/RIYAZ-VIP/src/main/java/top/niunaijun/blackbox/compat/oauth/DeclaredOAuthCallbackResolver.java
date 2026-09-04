package top.niunaijun.blackbox.compat.oauth;

import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageParser;
import android.net.Uri;
import android.os.PatternMatcher;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import top.niunaijun.blackbox.fake.frameworks.BPackageManager;
import top.niunaijun.blackbox.utils.compat.PackageParserCompat;

/**
 * Reads OAuth/deep-link callback declarations from the virtual APK itself.
 *
 * This is intentionally manifest-only. It never observes network requests,
 * cookies, authorization headers, request tokens, access tokens, or passwords.
 * The returned URIs are only candidate shapes and are validated again through
 * the virtual PackageManager before an OAuth callback is dispatched.
 */
final class DeclaredOAuthCallbackResolver {
    private static final int MAX_CANDIDATES = 64;
    private static final int MAX_CACHE_ENTRIES = 32;
    private static final Map<String, List<Uri>> CACHE = new ConcurrentHashMap<>();

    private DeclaredOAuthCallbackResolver() {
    }

    static List<Uri> findCandidates(String virtualPackage, int userId) {
        if (virtualPackage == null || virtualPackage.trim().isEmpty() || userId < 0) {
            return Collections.emptyList();
        }

        try {
            ApplicationInfo appInfo = BPackageManager.get().getApplicationInfo(
                    virtualPackage, 0, userId);
            if (appInfo == null) {
                return Collections.emptyList();
            }

            String source = appInfo.sourceDir;
            if (source == null || source.trim().isEmpty()) {
                source = appInfo.publicSourceDir;
            }
            if (source == null || source.trim().isEmpty()) {
                return Collections.emptyList();
            }

            File apk = new File(source);
            if (!apk.isFile() || !apk.canRead()) {
                return Collections.emptyList();
            }

            String cacheKey = virtualPackage + ':' + userId + ':' + source
                    + ':' + apk.length() + ':' + apk.lastModified();
            List<Uri> cached = CACHE.get(cacheKey);
            if (cached != null) {
                return cached;
            }

            List<Uri> parsed = parseCandidates(apk, virtualPackage);
            if (CACHE.size() >= MAX_CACHE_ENTRIES) {
                CACHE.clear();
            }
            CACHE.put(cacheKey, parsed);
            return parsed;
        } catch (Throwable ignored) {
            return Collections.emptyList();
        }
    }

    private static List<Uri> parseCandidates(File apk, String virtualPackage) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        try {
            PackageParser parser = PackageParserCompat.createParser(apk);
            if (parser == null) {
                return Collections.emptyList();
            }
            PackageParser.Package parsed = PackageParserCompat.parsePackage(parser, apk, 0);
            if (parsed == null || !virtualPackage.equals(parsed.packageName)
                    || parsed.activities == null) {
                return Collections.emptyList();
            }

            for (PackageParser.Activity activity : parsed.activities) {
                if (values.size() >= MAX_CANDIDATES) {
                    break;
                }
                if (activity == null || activity.info == null
                        || !virtualPackage.equals(activity.info.packageName)
                        || activity.intents == null) {
                    continue;
                }

                for (PackageParser.ActivityIntentInfo info : activity.intents) {
                    if (values.size() >= MAX_CANDIDATES) {
                        break;
                    }
                    if (info == null
                            || !info.hasAction(Intent.ACTION_VIEW)
                            || !info.hasCategory(Intent.CATEGORY_BROWSABLE)) {
                        continue;
                    }
                    appendFilterCandidates(info, values);
                }
            }
        } catch (Throwable ignored) {
            return Collections.emptyList();
        }

        ArrayList<Uri> result = new ArrayList<>(values.size());
        for (String value : values) {
            try {
                Uri uri = Uri.parse(value);
                if (isCustomScheme(uri.getScheme())) {
                    result.add(uri);
                }
            } catch (Throwable ignored) {
            }
        }
        return Collections.unmodifiableList(result);
    }

    private static void appendFilterCandidates(
            IntentFilter filter, LinkedHashSet<String> out) {
        int schemeCount = filter.countDataSchemes();
        if (schemeCount <= 0) {
            return;
        }

        for (int schemeIndex = 0;
             schemeIndex < schemeCount && out.size() < MAX_CANDIDATES;
             schemeIndex++) {
            String scheme = filter.getDataScheme(schemeIndex);
            if (!isCustomScheme(scheme)) {
                continue;
            }

            int authorityCount = filter.countDataAuthorities();
            if (authorityCount <= 0) {
                add(out, scheme + "://callback");
                add(out, scheme + "://authorize");
                add(out, scheme + "://oauth");
                continue;
            }

            for (int authorityIndex = 0;
                 authorityIndex < authorityCount && out.size() < MAX_CANDIDATES;
                 authorityIndex++) {
                IntentFilter.AuthorityEntry authority =
                        filter.getDataAuthority(authorityIndex);
                if (authority == null) {
                    continue;
                }
                String host = authority.getHost();
                if (host == null || host.trim().isEmpty() || host.indexOf('*') >= 0) {
                    continue;
                }

                StringBuilder base = new StringBuilder()
                        .append(scheme)
                        .append("://")
                        .append(host);
                int port = authority.getPort();
                if (port >= 0) {
                    base.append(':').append(port);
                }

                boolean addedLiteralPath = false;
                int pathCount = filter.countDataPaths();
                for (int pathIndex = 0;
                     pathIndex < pathCount && out.size() < MAX_CANDIDATES;
                     pathIndex++) {
                    PatternMatcher path = filter.getDataPath(pathIndex);
                    if (path == null || path.getPath() == null) {
                        continue;
                    }
                    if (path.getType() == PatternMatcher.PATTERN_LITERAL) {
                        String literal = path.getPath();
                        if (!literal.startsWith("/")) {
                            literal = "/" + literal;
                        }
                        add(out, base.toString() + literal);
                        addedLiteralPath = true;
                    } else if (path.getType() == PatternMatcher.PATTERN_PREFIX) {
                        String prefix = path.getPath();
                        if (!prefix.startsWith("/")) {
                            prefix = "/" + prefix;
                        }
                        add(out, base.toString() + prefix + "callback");
                    }
                }

                add(out, base.toString());
                if (!addedLiteralPath) {
                    add(out, base.toString() + "/callback");
                    add(out, base.toString() + "/authorize");
                }
            }
        }
    }

    private static void add(LinkedHashSet<String> out, String value) {
        if (out.size() < MAX_CANDIDATES && value != null && value.length() <= 2048) {
            out.add(value);
        }
    }

    private static boolean isCustomScheme(String value) {
        String scheme = value == null ? "" : value.toLowerCase(Locale.US).trim();
        if (scheme.isEmpty()
                || "http".equals(scheme)
                || "https".equals(scheme)
                || "file".equals(scheme)
                || "content".equals(scheme)
                || "javascript".equals(scheme)
                || "data".equals(scheme)
                || "intent".equals(scheme)) {
            return false;
        }
        return scheme.matches("^[a-z][a-z0-9+.-]{1,127}$");
    }
}
