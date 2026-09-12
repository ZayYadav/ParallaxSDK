// IParallaxUserManagerService.aidl
package com.Parallax.SDK.core.core.system.user;

// Declare any non-default types here with import statements
import com.Parallax.SDK.core.core.system.user.ParallaxUserInfo;
import java.util.List;


interface IParallaxUserManagerService {
    ParallaxUserInfo getUserInfo(int userId);
    boolean exists(int userId);
    ParallaxUserInfo createUser(int userId);
    List<ParallaxUserInfo> getUsers();
    void deleteUser(int userId);
}
