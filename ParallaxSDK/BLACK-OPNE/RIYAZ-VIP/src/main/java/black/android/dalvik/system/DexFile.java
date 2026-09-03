package black.android.dalvik.system;

import top.niunaijun.blackreflection.annotation.BClassName;
import top.niunaijun.blackreflection.annotation.BMethod;
import top.niunaijun.blackreflection.annotation.BStaticField;

@BClassName("dalvik.system.DexFile")
public interface DexFile {

    @BMethod
    Object openDexFileNative(String sourceName, String outputName, int flags);

    @BMethod
    Object openDexFile(String sourceName, String outputName, int flags);

    @BMethod
    Object loadDex(String sourceName, String outputName, int flags);

    @BStaticField
    Object mCookie();
}
