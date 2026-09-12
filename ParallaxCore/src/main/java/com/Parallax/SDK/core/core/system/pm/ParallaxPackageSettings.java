package com.Parallax.SDK.core.core.system.pm;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.AtomicFile;

import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.Parallax.SDK.mirror.android.content.pm.BRFrameworkPackageUserState;
import com.Parallax.SDK.core.core.env.ParallaxEnvironment;
import com.Parallax.SDK.core.core.system.user.ParallaxUserHandle;
import com.Parallax.SDK.core.entity.pm.ParallaxInstallOption;
import com.Parallax.SDK.core.utils.ParallaxCloseUtils;
import com.Parallax.SDK.core.utils.ParallaxFileUtils;
import com.Parallax.SDK.core.utils.compat.ParallaxBuildCompat;

/**
 * Created by @RIYAZXERO on 4/21/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class ParallaxPackageSettings implements Parcelable {
    public ParallaxPackage pkg;
    public int appId;
    public ParallaxInstallOption installOption;
    public Map<Integer, ParallaxPackageUserState> userState = new HashMap<>();
    static final ParallaxPackageUserState DEFAULT_USER_STATE = new ParallaxPackageUserState();

    public ParallaxPackageSettings() {
    }

    public List<ParallaxPackageUserState> getUserState() {
        return new ArrayList<>(userState.values());
    }

    public List<Integer> getUserIds() {
        return new ArrayList<>(userState.keySet());
    }

    public void setInstalled(boolean inst, int userId) {
        modifyUserState(userId).installed = inst;
    }

    public boolean getInstalled(int userId) {
        return readUserState(userId).installed;
    }

    public boolean getStopped(int userId) {
        return readUserState(userId).stopped;
    }

    public void setStopped(boolean stop, int userId) {
        modifyUserState(userId).stopped = stop;
    }

    public boolean getHidden(int userId) {
        return readUserState(userId).hidden;
    }

    public void setHidden(boolean hidden, int userId) {
        modifyUserState(userId).hidden = hidden;
    }

    public void removeUser(int userId) {
        userState.remove(userId);
    }

    public ParallaxPackageUserState readUserState(int userId) {
        ParallaxPackageUserState state = userState.get(userId);
        if (state == null) {
            state = new ParallaxPackageUserState();
        }
        state = new ParallaxPackageUserState(state);
        // xp模块所有用户可见、如果开启的话
        if (installOption.isFlag(ParallaxInstallOption.FLAG_XPOSED) &&
                ParallaxXposedManagerService.get().isModuleEnable(pkg.packageName) &&
                ParallaxXposedManagerService.get().isXPEnable()) {
            state.installed = true;
        }
        if (userId == ParallaxUserHandle.USER_ALL) {
            state.installed = true;
        }
        return state;
    }

    private ParallaxPackageUserState modifyUserState(int userId) {
        ParallaxPackageUserState state = userState.get(userId);
        if (state == null) {
            state = new ParallaxPackageUserState();
            userState.put(userId, state);
        }
        return state;
    }

    public boolean save() {
        synchronized (this) {
            Parcel parcel = Parcel.obtain();
            AtomicFile atomicFile = new AtomicFile(ParallaxEnvironment.getPackageConf(pkg.packageName));
            FileOutputStream fileOutputStream = null;
            try {
                writeToParcel(parcel, 0);
                parcel.setDataPosition(0);
                fileOutputStream = atomicFile.startWrite();
                ParallaxFileUtils.writeParcelToOutput(parcel, fileOutputStream);
                atomicFile.finishWrite(fileOutputStream);
                return true;
            } catch (Throwable e) {
                e.printStackTrace();
                atomicFile.failWrite(fileOutputStream);
                return false;
            } finally {
                parcel.recycle();
                ParallaxCloseUtils.close(fileOutputStream);
            }
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(this.pkg, flags);
        dest.writeInt(this.appId);
        dest.writeParcelable(this.installOption, flags);
        dest.writeInt(this.userState.size());
        for (Map.Entry<Integer, ParallaxPackageUserState> entry : this.userState.entrySet()) {
            dest.writeValue(entry.getKey());
            dest.writeParcelable(entry.getValue(), flags);
        }
    }

    protected ParallaxPackageSettings(Parcel in) {
        this.pkg = in.readParcelable(ParallaxPackage.class.getClassLoader());
        this.appId = in.readInt();
        this.installOption = in.readParcelable(ParallaxInstallOption.class.getClassLoader());
        int userStateSize = in.readInt();
        this.userState = new HashMap<Integer, ParallaxPackageUserState>(userStateSize);
        for (int i = 0; i < userStateSize; i++) {
            Integer key = (Integer) in.readValue(Integer.class.getClassLoader());
            ParallaxPackageUserState value = in.readParcelable(ParallaxPackageUserState.class.getClassLoader());
            this.userState.put(key, value);
        }
    }

    public static final Creator<ParallaxPackageSettings> CREATOR = new Creator<ParallaxPackageSettings>() {
        @Override
        public ParallaxPackageSettings createFromParcel(Parcel source) {
            return new ParallaxPackageSettings(source);
        }

        @Override
        public ParallaxPackageSettings[] newArray(int size) {
            return new ParallaxPackageSettings[size];
        }
    };
}
