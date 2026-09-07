package com.onecore.loader.security;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;

import org.lsposed.lsparanoid.Obfuscate;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Multi-stage fail-closed process termination. Release builds let R8 rename/repackage this class
 * and LSParanoid transform its constants. The final decision is independently armed in native
 * code so no single Java/Smali exit instruction is authoritative.
 */
@Obfuscate
final class TerminationGate {
    private static final AtomicBoolean LATCH = new AtomicBoolean(false);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final long K0 = 0x6A09E667F3BCC909L;
    private static final long K1 = 0xBB67AE8584CAA73BL;

    private TerminationGate() {
    }

    static void close(Activity activity, int reason) {
        final int pid = Process.myPid();
        final long stamp = SystemClock.elapsedRealtimeNanos()
                ^ (((long) pid) << 33)
                ^ (Thread.currentThread().getId() * K0)
                ^ K1;
        final int seed = fold((int) (stamp ^ (stamp >>> 32)) ^ pid ^ (reason * 0x45D9F3B));

        collapseSurface(activity, seed);

        if (!LATCH.compareAndSet(false, true)) {
            return;
        }

        final int ticket = arm(seed, stamp);
        Thread route = new Thread(() -> traverse(ticket, stamp, seed),
                "OneCore-" + Integer.toHexString(seed & 0xFFFF));
        route.setDaemon(false);
        route.start();

        // Independent Java-side fail-closed route. Native pulse A also starts its own detached
        // reaper, so patching either this delayed route or pulse B alone is insufficient.
        MAIN.postDelayed(() -> fallback(seed ^ ticket), 720L + (seed & 0x7F));
    }

    private static void collapseSurface(Activity activity, int seed) {
        if (activity == null) {
            return;
        }
        try {
            if ((fold(seed ^ Process.myPid()) & 1) == 0) {
                activity.finishAffinity();
            } else {
                activity.finishAndRemoveTask();
            }
        } catch (Throwable ignored) {
            try {
                activity.finishAffinity();
            } catch (Throwable ignoredAgain) {
                // Native and Java process routes remain armed independently.
            }
        }
    }

    private static int arm(int seed, long stamp) {
        try {
            return NativeLicenseGuard.pulseA(seed, stamp);
        } catch (Throwable ignored) {
            return fold(seed ^ (int) stamp ^ 0x51ED270B);
        }
    }

    private static void traverse(int ticket, long stamp, int seed) {
        int state = (fold(ticket ^ seed) & 3) + 1;
        int noise = seed;
        for (int hop = 0; hop < 9; hop++) {
            switch (state) {
                case 1:
                    noise = fold(noise ^ ticket ^ hop);
                    state = ((noise >>> 3) & 1) == 0 ? 3 : 4;
                    break;
                case 2:
                    try {
                        NativeLicenseGuard.pulseB(ticket, stamp);
                    } catch (Throwable ignored) {
                        // Continue to the independent Java route if native execution returns.
                    }
                    state = 5;
                    break;
                case 3:
                    Thread.yield();
                    noise ^= (int) (SystemClock.elapsedRealtimeNanos() >>> (hop & 7));
                    state = 2;
                    break;
                case 4:
                    noise = Integer.rotateLeft(noise ^ (int) stamp, (hop + 5) & 31);
                    state = ((noise ^ ticket) & 2) == 0 ? 2 : 1;
                    break;
                default:
                    fallback(noise ^ ticket);
                    return;
            }
        }
        fallback(noise ^ ticket ^ 0x7F4A7C15);
    }

    private static int fold(int value) {
        value ^= value >>> 16;
        value *= 0x7FEB352D;
        value ^= value >>> 15;
        value *= 0x846CA68B;
        value ^= value >>> 16;
        return value;
    }

    private static void fallback(int seed) {
        final int code = 32 + (fold(seed ^ Process.myPid()) & 0x3F);
        try {
            Runtime.getRuntime().halt(code);
        } catch (Throwable ignored) {
            try {
                Process.killProcess(Process.myPid());
            } catch (Throwable ignoredAgain) {
                // Nothing else in Java is trusted after a terminal security decision.
            }
        }
    }
}
