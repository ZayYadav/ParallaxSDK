package com.Parallax.SDK.core.core.system.location;

import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.AtomicFile;
import android.util.SparseArray;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import com.Parallax.SDK.mirror.android.location.BRILocationListener;
import com.Parallax.SDK.mirror.android.location.BRILocationListenerStub;
import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.core.env.ParallaxEnvironment;
import com.Parallax.SDK.core.core.system.IParallaxSystemService;
import com.Parallax.SDK.core.entity.location.ParallaxCell;
import com.Parallax.SDK.core.entity.location.ParallaxLocation;
import com.Parallax.SDK.core.entity.location.ParallaxLocationConfig;
import com.Parallax.SDK.core.fake.frameworks.ParallaxLocationManager;
import com.Parallax.SDK.core.utils.ParallaxCloseUtils;
import com.Parallax.SDK.core.utils.ParallaxFileUtils;
import com.Parallax.SDK.core.utils.ParallaxSlog;

/**
 * Fake location
 * plan1: only GPS invocation is valid and other methods like addressed by cells are intercepted at all.
 * plan2: mock fake neighboring cells from LBS database and modify the result of GPS invocation.
 * plan3: cheat internal application at being given permission to access location information but get data from BB.
 * the final testing condition requires UI demo.
 * Created by BlackBoxing on 3/8/22.
 **/
public class ParallaxLocationManagerService extends IParallaxLocationManagerService.Stub implements IParallaxSystemService {
    public static final String TAG = "ParallaxLocationManagerService";

    private static final ParallaxLocationManagerService sService = new ParallaxLocationManagerService();
    private final SparseArray<HashMap<String, ParallaxLocationConfig>> mLocationConfigs = new SparseArray<>();
    private final ParallaxLocationConfig mGlobalConfig = new ParallaxLocationConfig();
    private final Map<IBinder, ParallaxLocationRecord> mLocationListeners = new HashMap<>();
    private final Executor mThreadPool = Executors.newCachedThreadPool();

    public static ParallaxLocationManagerService get() {
        return sService;
    }

    private ParallaxLocationConfig getOrCreateConfig(int userId, String pkg) {
        synchronized (mLocationConfigs) {
            HashMap<String, ParallaxLocationConfig> pkgs = mLocationConfigs.get(userId);
            if (pkgs == null) {
                pkgs = new HashMap<>();
                mLocationConfigs.put(userId, pkgs);
            }
            ParallaxLocationConfig config = pkgs.get(pkg);
            if (config == null) {
                config = new ParallaxLocationConfig();
                config.pattern = ParallaxLocationManager.CLOSE_MODE;
                pkgs.put(pkg, config);
            }
            return config;
        }
    }

    public int getPattern(int userId, String pkg) {
        synchronized (mLocationConfigs) {
            ParallaxLocationConfig config = getOrCreateConfig(userId, pkg);
            return config.pattern;
        }
    }

    @Override
    public void setPattern(int userId, String pkg, int pattern) {
        synchronized (mLocationConfigs) {
            getOrCreateConfig(userId, pkg).pattern = pattern;
            save();
        }
    }

    @Override
    public void setCell(int userId, String pkg, ParallaxCell cell) {
        synchronized (mLocationConfigs) {
            getOrCreateConfig(userId, pkg).cell = cell;
            save();
        }
    }

    @Override
    public void setAllCell(int userId, String pkg, List<ParallaxCell> cells) {
        synchronized (mLocationConfigs) {
            getOrCreateConfig(userId, pkg).allCell = cells;
            save();
        }
    }

    @Override
    public void setNeighboringCell(int userId, String pkg, List<ParallaxCell> cells) {
        synchronized (mLocationConfigs) {
            getOrCreateConfig(userId, pkg).allCell = cells;
            save();
        }
    }

    @Override
    public List<ParallaxCell> getNeighboringCell(int userId, String pkg) {
        synchronized (mLocationConfigs) {
            return getOrCreateConfig(userId, pkg).allCell;
        }
    }

    @Override
    public void setGlobalCell(ParallaxCell cell) {
        synchronized (mGlobalConfig) {
            mGlobalConfig.cell = cell;
            save();
        }
    }

    @Override
    public void setGlobalAllCell(List<ParallaxCell> cells) {
        synchronized (mGlobalConfig) {
            mGlobalConfig.allCell = cells;
            save();
        }
    }

    @Override
    public void setGlobalNeighboringCell(List<ParallaxCell> cells) {
        synchronized (mGlobalConfig) {
            mGlobalConfig.neighboringCellInfo = cells;
            save();
        }
    }

    @Override
    public List<ParallaxCell> getGlobalNeighboringCell() {
        synchronized (mGlobalConfig) {
            return mGlobalConfig.neighboringCellInfo;
        }
    }

    @Override
    public ParallaxCell getCell(int userId, String pkg) {
        ParallaxLocationConfig config = getOrCreateConfig(userId, pkg);
        switch (config.pattern) {
            case ParallaxLocationManager.OWN_MODE:
                return config.cell;
            case ParallaxLocationManager.GLOBAL_MODE:
                return mGlobalConfig.cell;
            case ParallaxLocationManager.CLOSE_MODE:
            default:
                return null;
        }
    }

    @Override
    public List<ParallaxCell> getAllCell(int userId, String pkg) {
        ParallaxLocationConfig config = getOrCreateConfig(userId, pkg);
        switch (config.pattern) {
            case ParallaxLocationManager.OWN_MODE:
                return config.allCell;
            case ParallaxLocationManager.GLOBAL_MODE:
                return mGlobalConfig.allCell;
            case ParallaxLocationManager.CLOSE_MODE:
            default:
                return null;
        }
    }

    @Override
    public void setLocation(int userId, String pkg, ParallaxLocation location) {
        synchronized (mLocationConfigs) {
            getOrCreateConfig(userId, pkg).location = location;
            save();
        }
    }

    @Override
    public ParallaxLocation getLocation(int userId, String pkg) {
        ParallaxLocationConfig config = getOrCreateConfig(userId, pkg);
        switch (config.pattern) {
            case ParallaxLocationManager.OWN_MODE:
                return config.location;
            case ParallaxLocationManager.GLOBAL_MODE:
                return mGlobalConfig.location;
            case ParallaxLocationManager.CLOSE_MODE:
            default:
                return null;
        }
    }

    @Override
    public void setGlobalLocation(ParallaxLocation location) {
        synchronized (mGlobalConfig) {
            mGlobalConfig.location = location;
            save();
        }
    }

    @Override
    public ParallaxLocation getGlobalLocation() {
        synchronized (mGlobalConfig) {
            return mGlobalConfig.location;
        }
    }

    @Override
    public void requestLocationUpdates(IBinder listener, String packageName, int userId) throws RemoteException {
        if (listener == null || !listener.pingBinder()) {
            return;
        }
        if (mLocationListeners.containsKey(listener))
            return;
        listener.linkToDeath(new DeathRecipient() {
            @Override
            public void binderDied() {
                listener.unlinkToDeath(this, 0);
                mLocationListeners.remove(listener);
            }
        }, 0);
        ParallaxLocationRecord record = new ParallaxLocationRecord(packageName, userId);
        mLocationListeners.put(listener, record);
        addTask(listener);
    }

    @Override
    public void removeUpdates(IBinder listener) throws RemoteException {
        if (listener == null || !listener.pingBinder()) {
            return;
        }
        mLocationListeners.remove(listener);
    }

    private void addTask(IBinder locationListener) {
        mThreadPool.execute(() -> {
            ParallaxLocation lastLocation = null;
            long l = System.currentTimeMillis();
            while (locationListener.pingBinder()) {
                IInterface iInterface = BRILocationListenerStub.get().asInterface(locationListener);
                ParallaxLocationRecord locationRecord = mLocationListeners.get(locationListener);
                if (locationRecord == null)
                    continue;
                ParallaxLocation location = getLocation(locationRecord.userId, locationRecord.packageName);
                if (location == null)
                    continue;
                if (location.equals(lastLocation) && (System.currentTimeMillis() - l) < 3000) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ignored) {
                    }
                    continue;
                }
                lastLocation = location;
                l = System.currentTimeMillis();
                ParallaxCore.get().getHandler().post(() -> BRILocationListener.get(iInterface).onLocationChanged(location.convert2SystemLocation()));
            }
        });
    }

    public void save() {
        synchronized (mGlobalConfig) {
            synchronized (mLocationConfigs) {
                Parcel parcel = Parcel.obtain();
                AtomicFile atomicFile = new AtomicFile(ParallaxEnvironment.getFakeLocationConf());
                FileOutputStream fileOutputStream = null;
                try {
                    mGlobalConfig.writeToParcel(parcel, 0);

                    parcel.writeInt(mLocationConfigs.size());
                    for (int i = 0; i < mLocationConfigs.size(); i++) {
                        int tmpUserId = mLocationConfigs.keyAt(i);
                        HashMap<String, ParallaxLocationConfig> configArrayMap = mLocationConfigs.valueAt(i);
                        parcel.writeInt(tmpUserId);
                        parcel.writeMap(configArrayMap);
                    }
                    parcel.setDataPosition(0);
                    fileOutputStream = atomicFile.startWrite();
                    ParallaxFileUtils.writeParcelToOutput(parcel, fileOutputStream);
                    atomicFile.finishWrite(fileOutputStream);
                } catch (Throwable e) {
                    e.printStackTrace();
                    atomicFile.failWrite(fileOutputStream);
                } finally {
                    parcel.recycle();
                    ParallaxCloseUtils.close(fileOutputStream);
                }
            }
        }
    }

    public void loadConfig() {
        Parcel parcel = Parcel.obtain();
        InputStream is = null;
        try {
            File fakeLocationConf = ParallaxEnvironment.getFakeLocationConf();
            if (!fakeLocationConf.exists()) {
                return;
            }
            is = new FileInputStream(ParallaxEnvironment.getFakeLocationConf());
            byte[] bytes = ParallaxFileUtils.toByteArray(is);
            parcel.unmarshall(bytes, 0, bytes.length);
            parcel.setDataPosition(0);

            synchronized (mGlobalConfig) {
                mGlobalConfig.refresh(parcel);
            }

            synchronized (mLocationConfigs) {
                mLocationConfigs.clear();
                int size = parcel.readInt();
                for (int i = 0; i < size; i++) {
                    int userId = parcel.readInt();
                    HashMap<String, ParallaxLocationConfig> configArrayMap = parcel.readHashMap(ParallaxLocationConfig.class.getClassLoader());
                    mLocationConfigs.put(userId, configArrayMap);
                    ParallaxSlog.d(TAG, "load userId: " + userId + ", config: " + configArrayMap);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            ParallaxSlog.d(TAG, "bad config");
            ParallaxFileUtils.deleteDir(ParallaxEnvironment.getFakeLocationConf());
        } finally {
            parcel.recycle();
            ParallaxCloseUtils.close(is);
        }
    }

    @Override
    public void systemReady() {
        loadConfig();
        for (IBinder iBinder : mLocationListeners.keySet()) {
            addTask(iBinder);
        }
    }
}
