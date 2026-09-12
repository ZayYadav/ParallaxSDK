package com.Parallax.SDK.core.utils.compat;

import android.content.pm.PackageParser;
import android.content.pm.PackageParser.Package;
import android.os.Build;
import android.util.DisplayMetrics;

import java.io.File;

import com.Parallax.SDK.mirror.android.content.pm.BRPackageParser;
import com.Parallax.SDK.mirror.android.content.pm.BRPackageParserLollipop;
import com.Parallax.SDK.mirror.android.content.pm.BRPackageParserLollipop22;
import com.Parallax.SDK.mirror.android.content.pm.BRPackageParserMarshmallow;
import com.Parallax.SDK.mirror.android.content.pm.BRPackageParserNougat;
import com.Parallax.SDK.mirror.android.content.pm.BRPackageParserPie;

import static android.os.Build.VERSION_CODES.LOLLIPOP;
import static android.os.Build.VERSION_CODES.LOLLIPOP_MR1;
import static android.os.Build.VERSION_CODES.M;
import static android.os.Build.VERSION_CODES.N;

public class ParallaxPackageParserCompat {

    public static final int[] GIDS = new int[]{};
    private static final int API_LEVEL = Build.VERSION.SDK_INT;
    private static final int myUserId = 0;


    public static PackageParser createParser(File packageFile) {
        if (API_LEVEL >= M) {
            return BRPackageParserMarshmallow.get()._new();
        } else if (API_LEVEL >= LOLLIPOP_MR1) {
            return BRPackageParserLollipop22.get()._new();
        } else if (API_LEVEL >= LOLLIPOP) {
            return BRPackageParserLollipop.get()._new();
        }
        return null;
    }

    public static Package parsePackage(PackageParser parser, File packageFile, int flags) throws Throwable {
        if (API_LEVEL >= M) {
            return BRPackageParserMarshmallow.getWithException(parser).parsePackage(packageFile, flags);
        } else if (API_LEVEL >= LOLLIPOP_MR1) {
            return BRPackageParserLollipop22.getWithException(parser).parsePackage(packageFile, flags);
        } else if (API_LEVEL >= LOLLIPOP) {
            return BRPackageParserLollipop.getWithException(parser).parsePackage(packageFile, flags);
        } else {
            return BRPackageParser.getWithException(parser).parsePackage(packageFile, null,new DisplayMetrics(), flags);
        }
    }

    public static void collectCertificates(PackageParser parser, Package p, int flags) throws Throwable {
        if (ParallaxBuildCompat.isPie()) {
            BRPackageParserPie.getWithException().collectCertificates(p, true/*skipVerify*/);
        } else if (API_LEVEL >= N) {
            BRPackageParserNougat.getWithException().collectCertificates(p, flags);
        } else if (API_LEVEL >= M) {
            BRPackageParserMarshmallow.getWithException(parser).collectCertificates(p, flags);
        } else if (API_LEVEL >= LOLLIPOP_MR1) {
            BRPackageParserLollipop22.getWithException(parser).collectCertificates(p, flags);
        } else if (API_LEVEL >= LOLLIPOP) {
            BRPackageParserLollipop.getWithException(parser).collectCertificates(p, flags);
        } else {
            BRPackageParser.get(parser).collectCertificates(p, flags);
        }
    }
}
