package com.Parallax.SDK.core.fake.frameworks;

import android.os.IBinder;
import android.os.RemoteException;

import java.util.ArrayList;
import java.util.List;

import com.Parallax.SDK.core.app.ParallaxActivityThread;
import com.Parallax.SDK.core.core.system.ParallaxServiceManager;
import com.Parallax.SDK.core.core.system.location.IParallaxLocationManagerService;
import com.Parallax.SDK.core.entity.location.ParallaxCell;
import com.Parallax.SDK.core.entity.location.ParallaxLocation;

/**
 * Created by BlackBoxing on 3/8/22.
 **/
public class ParallaxLocationManager extends ParallaxBlackManager<IParallaxLocationManagerService> {
    private static final ParallaxLocationManager sLocationManager = new ParallaxLocationManager();

    public static final int CLOSE_MODE = 0;
    public static final int GLOBAL_MODE = 1;
    public static final int OWN_MODE = 2;

    public static ParallaxLocationManager get() {
        return sLocationManager;
    }

    @Override
    protected String getServiceName() {
        return ParallaxServiceManager.LOCATION_MANAGER;
    }

    public static boolean isFakeLocationEnable() {
        return get().getPattern(ParallaxActivityThread.getUserId(), ParallaxActivityThread.getAppPackageName()) != CLOSE_MODE;
    }

    public static void disableFakeLocation(int userId,String pkg){
        get().setPattern(userId,pkg,CLOSE_MODE);
    }

    public void setPattern(int userId, String pkg, int pattern) {
        try {
            getService().setPattern(userId, pkg, pattern);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public int getPattern(int userId, String pkg) {
        try {
            return getService().getPattern(userId, pkg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return CLOSE_MODE;
    }

    public void setCell(int userId, String pkg, ParallaxCell cell) {
        try {
            getService().setCell(userId, pkg, cell);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void setAllCell(int userId, String pkg, List<ParallaxCell> cells) {
        try {
            getService().setAllCell(userId, pkg, cells);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public List<ParallaxCell> getNeighboringCell(int userId, String pkg) {
        try {
            return getService().getNeighboringCell(userId, pkg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<ParallaxCell> getGlobalNeighboringCell() {
        try {
            return getService().getGlobalNeighboringCell();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void setNeighboringCell(int userId, String pkg, List<ParallaxCell> cells) {
        try {
            getService().setNeighboringCell(userId, pkg, cells);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void setGlobalCell(ParallaxCell cell) {
        try {
            getService().setGlobalCell(cell);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void setGlobalAllCell(List<ParallaxCell> cells) {
        try {
            getService().setGlobalAllCell(cells);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void setGlobalNeighboringCell(List<ParallaxCell> cells) {
        try {
            getService().setGlobalNeighboringCell(cells);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public ParallaxCell getCell(int userId, String pkg) {
        try {
            return getService().getCell(userId, pkg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<ParallaxCell> getAllCell(int userId, String pkg) {
        try {
            return getService().getAllCell(userId, pkg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    public void setLocation(int userId, String pkg, ParallaxLocation location) {
        try {
            getService().setLocation(userId, pkg, location);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public ParallaxLocation getLocation(int userId, String pkg) {
        try {
            return getService().getLocation(userId, pkg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void setGlobalLocation(ParallaxLocation location) {
        try {
            getService().setGlobalLocation(location);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public ParallaxLocation getGlobalLocation() {
        try {
            return getService().getGlobalLocation();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void requestLocationUpdates(IBinder listener) {
        try {
            getService().requestLocationUpdates(listener, ParallaxActivityThread.getAppPackageName(), ParallaxActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void removeUpdates(IBinder listener) {
        try {
            getService().removeUpdates(listener);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
}
