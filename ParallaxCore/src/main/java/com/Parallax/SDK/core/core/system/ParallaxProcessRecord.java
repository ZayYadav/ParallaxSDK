package com.Parallax.SDK.core.core.system;

import android.content.pm.ApplicationInfo;
import android.os.Binder;
import android.os.ConditionVariable;
import android.os.IInterface;
import android.os.Process;

import android.system.ErrnoException;
import android.system.OsConstants;
import java.io.FileNotFoundException;
import java.util.Arrays;

import com.Parallax.SDK.core.core.IParallaxActivityThread;
import com.Parallax.SDK.core.entity.ParallaxAppConfig;
import com.Parallax.SDK.core.proxy.ParallaxProxyManifest;

public class ParallaxProcessRecord extends Binder {
    public final ApplicationInfo info;
    final public String processName;
    public IParallaxActivityThread bActivityThread;
    public IInterface appThread;
    public int uid;
    public int pid;
    public int buid;
    public int bpid;
    public int callingBUid;
    public int userId;

    public ConditionVariable initLock = new ConditionVariable();

    public ParallaxProcessRecord(ApplicationInfo info, String processName) {
        this.info = info;
        this.processName = processName;
    }

    public int getCallingBUid() {
        return callingBUid;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(new Object[]{processName, pid, buid, bpid, uid, pid, userId});
    }

    public String getProviderAuthority() {
        return ParallaxProxyManifest.getProxyAuthorities(bpid);
    }

    public ParallaxAppConfig getClientConfig() {
        ParallaxAppConfig config = new ParallaxAppConfig();
        config.packageName = info.packageName;
        config.processName = processName;
        config.bpid = bpid;
        config.buid = buid;
        config.uid = uid;
        config.callingBUid = callingBUid;
        config.userId = userId;
        config.token = this;
        return config;
    }
    
    public void kill() {
        if (pid > 0) {
            try {
                Process.killProcess(pid);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }
    
    private void handleErrno(ErrnoException e) {
        if (e.errno == OsConstants.ENOENT) {
        } else {
            e.printStackTrace();
        }
    }

    public String getPackageName() {
        return info.packageName;
    }
}
