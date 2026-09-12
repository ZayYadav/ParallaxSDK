// IFakeLocationManager.aidl
package com.Parallax.SDK.core.core.system.location;

import com.Parallax.SDK.core.entity.location.ParallaxLocation;
import com.Parallax.SDK.core.entity.location.ParallaxCell;

import java.util.List;


interface IParallaxLocationManagerService {
    int getPattern(int userId, String pkg);

    void setPattern(int userId, String pkg, int mode);

    void setCell(int userId, String pkg,in  ParallaxCell cell);

    void setAllCell(int userId, String pkg,in  List<ParallaxCell> cell);

    void setNeighboringCell(int userId, String pkg,in  List<ParallaxCell> cells);
    List<ParallaxCell> getNeighboringCell(int userId, String pkg);

    void setGlobalCell(in ParallaxCell cell);

    void setGlobalAllCell(in List<ParallaxCell> cell);

    void setGlobalNeighboringCell(in List<ParallaxCell> cell);

    List<ParallaxCell> getGlobalNeighboringCell();

    ParallaxCell getCell(int userId, String pkg);

    List<ParallaxCell> getAllCell(int userId, String pkg);

    void setLocation(int userId, String pkg,in  ParallaxLocation location);

    ParallaxLocation getLocation(int userId, String pkg);

    void setGlobalLocation(in ParallaxLocation location);

    ParallaxLocation getGlobalLocation();

    void requestLocationUpdates(in IBinder listener, String packageName, int userId);

    void removeUpdates(in IBinder listener);
}