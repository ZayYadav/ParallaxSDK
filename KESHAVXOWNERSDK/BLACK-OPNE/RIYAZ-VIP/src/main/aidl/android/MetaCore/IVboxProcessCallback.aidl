package android.MetaCore;

interface IVboxProcessCallback {
    void onCreate(String packageName, String processName);
    void onProcessStart(String packageName);
    void onProcessStop(String packageName);
}