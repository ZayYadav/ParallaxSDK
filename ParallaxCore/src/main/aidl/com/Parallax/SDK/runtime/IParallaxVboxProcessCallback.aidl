package com.Parallax.SDK.runtime;

interface IParallaxVboxProcessCallback {
    void onCreate(String packageName, String processName);
    void onProcessStart(String packageName);
    void onProcessStop(String packageName);
}