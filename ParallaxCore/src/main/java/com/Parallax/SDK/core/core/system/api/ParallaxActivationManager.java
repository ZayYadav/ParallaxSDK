// Ye alag file mein rahega - simple version
package com.Parallax.SDK.core.core.system.api;

import com.Parallax.SDK.runtime.ParallaxRemoteManager;
import org.lsposed.lsparanoid.Obfuscate;

@Obfuscate
public class ParallaxActivationManager {
    
    /* ================= ACTIVATE SDK ================= */
    public static void activateSdk(final String userkey) {
        try {
            ParallaxRemoteManager.getInstance().activateSdk(userkey);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    /* ================= GET SERVER MESSAGE ================= */
    public static String getServerMessage() {
        try {
            return ParallaxRemoteManager.getInstance().getServerMessage();
        } catch (Throwable e) {
            e.printStackTrace();
            return "ERROR: FAILED TO GET SERVER MESSAGE";
        }
    }

    /* ================= CHECK SDK STATUS ================= */
    public static boolean getActivatedStatus() {
        try {
            return ParallaxRemoteManager.getInstance().getActivatedSdk();
        } catch (Throwable e) {
            e.printStackTrace();
            return false;
        }
    }
}