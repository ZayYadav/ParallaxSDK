package top.niunaijun.blackbox.app;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BActivityThreadServiceContextTest {

    @Test
    public void reusesBoundContextOnlyForTheCurrentVirtualPackage() {
        assertTrue(BActivityThread.isBoundComponentPackage(
                "com.pubg.imobile", "com.pubg.imobile"));

        assertFalse(BActivityThread.isBoundComponentPackage(
                "com.twitter.android", "com.pubg.imobile"));
        assertFalse(BActivityThread.isBoundComponentPackage(
                null, "com.pubg.imobile"));
        assertFalse(BActivityThread.isBoundComponentPackage(
                "com.pubg.imobile", null));
    }
}
