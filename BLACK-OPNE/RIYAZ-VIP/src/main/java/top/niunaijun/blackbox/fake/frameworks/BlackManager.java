package top.niunaijun.blackbox.fake.frameworks;

import android.os.IBinder;
import android.os.IInterface;

import java.lang.reflect.ParameterizedType;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.utils.Reflector;

/**
 * Created by BlackBox on 2022/3/23.
 */
public abstract class BlackManager<Service extends IInterface> {
    public static final String TAG = "BlackManager";

    private final Object mServiceLock = new Object();
    private volatile Service mService;
    private IBinder mServiceBinder;

    /**
     * Keep exactly one death recipient per manager instance. Creating a new anonymous
     * recipient on every service lookup leaks Binder weak-global JNI references when a
     * service is temporarily unavailable/frozen and getService() is called repeatedly.
     */
    private final IBinder.DeathRecipient mDeathRecipient = new IBinder.DeathRecipient() {
        @Override
        public void binderDied() {
            synchronized (mServiceLock) {
                // A callback from a previously unlinked binder must not invalidate a
                // newer live service that may already have replaced it.
                if (mServiceBinder == null || !mServiceBinder.isBinderAlive()) {
                    mServiceBinder = null;
                    mService = null;
                }
            }
        }
    };

    protected abstract String getServiceName();

    public Service getService() {
        Service cached = mService;
        if (isServiceAlive(cached)) {
            return cached;
        }

        synchronized (mServiceLock) {
            cached = mService;
            if (isServiceAlive(cached)) {
                return cached;
            }

            unlinkDeathRecipientLocked();
            mService = null;

            try {
                IBinder binder = BlackBoxCore.get().getService(getServiceName());
                if (binder == null) {
                    return null;
                }

                Service service = Reflector.on(getTClass().getName() + "$Stub")
                        .method("asInterface", IBinder.class)
                        .call(binder);
                if (service == null || service.asBinder() == null) {
                    return null;
                }

                IBinder serviceBinder = service.asBinder();
                serviceBinder.linkToDeath(mDeathRecipient, 0);

                // Publish the binder before the service so readers never observe a
                // cached service without its matching death-monitor state.
                mServiceBinder = serviceBinder;
                mService = service;

                // Do not recurse back into getService(). The previous implementation
                // recursively re-linked a fresh DeathRecipient whenever pingBinder()
                // failed, eventually exhausting ART's 51,200 weak-global-ref table.
                return service;
            } catch (Throwable e) {
                mServiceBinder = null;
                mService = null;
                e.printStackTrace();
                return null;
            }
        }
    }

    private boolean isServiceAlive(Service service) {
        if (service == null) {
            return false;
        }
        try {
            IBinder binder = service.asBinder();
            return binder != null && binder.isBinderAlive();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void unlinkDeathRecipientLocked() {
        IBinder oldBinder = mServiceBinder;
        mServiceBinder = null;
        if (oldBinder == null) {
            return;
        }
        try {
            oldBinder.unlinkToDeath(mDeathRecipient, 0);
        } catch (Throwable ignored) {
            // The old binder may already be dead. Either way it must not stay cached.
        }
    }

    private Class<Service> getTClass() {
        return (Class<Service>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
    }
}
