package com.Parallax.SDK.core.core.system.user;

import android.os.Binder;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Process;

/**
 * Representation of a user on the device.
 */
public final class ParallaxUserHandle implements Parcelable {
    // NOTE: keep logic in sync with system/core/libcutils/multiuser.c

    /**
     * @hide Range of uids allocated for a user.
     */
    public static final int PER_USER_RANGE = 100000;

    /**
     * @hide A user id to indicate all users on the device
     */
    public static final int USER_ALL = -1;

    /**
     * @hide A user handle to indicate all users on the device
     */
    public static final ParallaxUserHandle ALL = new ParallaxUserHandle(USER_ALL);

    /**
     * @hide A user id to indicate the currently active user
     */
    public static final int USER_CURRENT = -2;

    /**
     * @hide A user handle to indicate the current user of the device
     */
    public static final ParallaxUserHandle CURRENT = new ParallaxUserHandle(USER_CURRENT);

    /**
     * @hide A user id to indicate that we would like to send to the current
     * user, but if this is calling from a user process then we will send it
     * to the caller's user instead of failing with a security exception
     */
    public static final int USER_CURRENT_OR_SELF = -3;


    public static final int USER_XPOSED = -4;

    /**
     * @hide A user handle to indicate that we would like to send to the current
     * user, but if this is calling from a user process then we will send it
     * to the caller's user instead of failing with a security exception
     */
    public static final ParallaxUserHandle CURRENT_OR_SELF = new ParallaxUserHandle(USER_CURRENT_OR_SELF);

    /**
     * @hide An undefined user id
     */
    public static final int USER_NULL = -10000;

    /**
     * @hide A user id constant to indicate the "owner" user of the device
     * @deprecated Consider using either {@link ParallaxUserHandle#USER_SYSTEM} constant or
     * check the target user's flag {@link }.
     */
    @Deprecated
    public static final int USER_OWNER = 0;

    /**
     * @hide A user handle to indicate the primary/owner user of the device
     * @deprecated Consider using either {@link ParallaxUserHandle#SYSTEM} constant or
     * check the target user's flag {@link }.
     */
    @Deprecated
    public static final ParallaxUserHandle OWNER = new ParallaxUserHandle(USER_OWNER);

    /**
     * @hide A user id constant to indicate the "system" user of the device
     */
    public static final int USER_SYSTEM = 0;

    /**
     * @hide A user serial constant to indicate the "system" user of the device
     */
    public static final int USER_SERIAL_SYSTEM = 0;

    /**
     * @hide A user handle to indicate the "system" user of the device
     */
    public static final ParallaxUserHandle SYSTEM = new ParallaxUserHandle(USER_SYSTEM);

    /**
     * @hide Enable multi-user related side effects. Set this to false if
     * there are problems with single user use-cases.
     */
    public static final boolean MU_ENABLED = true;

    /**
     * @hide
     */
    public static final int ERR_GID = -1;
    /**
     * @hide
     */
    public static final int AID_ROOT = 0;
    /**
     * @hide
     */
    public static final int AID_APP_START = android.os.Process.FIRST_APPLICATION_UID;
    /**
     * @hide
     */
    public static final int AID_APP_END = android.os.Process.LAST_APPLICATION_UID;
    /**
     * @hide
     */
    public static final int AID_SHARED_GID_START = 50000;
    /**
     * @hide
     */
    public static final int AID_CACHE_GID_START = 20000;

    final int mHandle;

    /**
     * Checks to see if the user id is the same for the two uids, i.e., they belong to the same
     * user.
     *
     * @hide
     */
    public static boolean isSameUser(int uid1, int uid2) {
        return getUserId(uid1) == getUserId(uid2);
    }

    /**
     * Checks to see if both uids are referring to the same app id, ignoring the user id part of the
     * uids.
     *
     * @param uid1 uid to compare
     * @param uid2 other uid to compare
     * @return whether the appId is the same for both uids
     * @hide
     */
    public static boolean isSameApp(int uid1, int uid2) {
        return getAppId(uid1) == getAppId(uid2);
    }

    /**
     * Whether a UID belongs to a regular app. *Note* "Not a regular app" does not mean
     * "it's system", because of isolated UIDs. Use {@link #isCore} for that.
     *
     * @hide
     */
    public static boolean isApp(int uid) {
        if (uid > 0) {
            final int appId = getAppId(uid);
            return appId >= Process.FIRST_APPLICATION_UID && appId <= Process.LAST_APPLICATION_UID;
        } else {
            return false;
        }
    }

    /**
     * Whether a UID belongs to a system core component or not.
     *
     * @hide
     */
    public static boolean isCore(int uid) {
        if (uid >= 0) {
            final int appId = getAppId(uid);
            return appId < Process.FIRST_APPLICATION_UID;
        } else {
            return false;
        }
    }

    /**
     * Returns the user for a given uid.
     *
     * @param uid A uid for an application running in a particular user.
     * @return A {@link ParallaxUserHandle} for that user.
     */
    public static ParallaxUserHandle getUserHandleForUid(int uid) {
        return of(getUserId(uid));
    }

    /**
     * Returns the user id for a given uid.
     *
     * @hide
     */
    public static int getUserId(int uid) {
        if (MU_ENABLED) {
            return uid / PER_USER_RANGE;
        } else {
            return ParallaxUserHandle.USER_SYSTEM;
        }
    }

    /**
     * @hide
     */
    public static int getCallingUserId() {
        return getUserId(Binder.getCallingUid());
    }

    /**
     * @hide
     */
    public static int getCallingAppId() {
        return getAppId(Binder.getCallingUid());
    }

    /**
     * @hide
     */
    public static ParallaxUserHandle of(int userId) {
        return userId == USER_SYSTEM ? SYSTEM : new ParallaxUserHandle(userId);
    }

    /**
     * Returns the uid that is composed from the userId and the appId.
     *
     * @hide
     */
    public static int getUid(int userId, int appId) {
        if (MU_ENABLED) {
            return userId * PER_USER_RANGE + (appId % PER_USER_RANGE);
        } else {
            return appId;
        }
    }

    /**
     * Returns the app id (or base uid) for a given uid, stripping out the user id from it.
     *
     * @hide
     */
    public static int getAppId(int uid) {
        return uid % PER_USER_RANGE;
    }

    /**
     * Returns the gid shared between all apps with this userId.
     *
     * @hide
     */
    public static int getUserGid(int userId) {
        return getUid(userId, 9997 /*Process.SHARED_USER_GID*/);
    }

    /**
     * @hide
     */
    public static int getSharedAppGid(int uid) {
        return getSharedAppGid(getUserId(uid), getAppId(uid));
    }

    /**
     * @hide
     */
    public static int getSharedAppGid(int userId, int appId) {
        if (appId >= AID_APP_START && appId <= AID_APP_END) {
            return (appId - AID_APP_START) + AID_SHARED_GID_START;
        } else if (appId >= AID_ROOT && appId <= AID_APP_START) {
            return appId;
        } else {
            return -1;
        }
    }

    /**
     * Returns the app id for a given shared app gid. Returns -1 if the ID is invalid.
     * @hide
     */
//    public static int getAppIdFromSharedAppGid(int gid) {
//        final int appId = getAppId(gid) + Process.FIRST_APPLICATION_UID
//                - Process.FIRST_SHARED_APPLICATION_GID;
//        if (appId < 0 || appId >= Process.FIRST_SHARED_APPLICATION_GID) {
//            return -1;
//        }
//        return appId;
//    }

    /**
     * @hide
     */
    public static int getCacheAppGid(int uid) {
        return getCacheAppGid(getUserId(uid), getAppId(uid));
    }

    /**
     * @hide
     */
    public static int getCacheAppGid(int userId, int appId) {
        if (appId >= AID_APP_START && appId <= AID_APP_END) {
            return getUid(userId, (appId - AID_APP_START) + AID_CACHE_GID_START);
        } else {
            return -1;
        }
    }

    /**
     * @hide
     */
    public static int parseUserArg(String arg) {
        int userId;
        if ("all".equals(arg)) {
            userId = ParallaxUserHandle.USER_ALL;
        } else if ("current".equals(arg) || "cur".equals(arg)) {
            userId = ParallaxUserHandle.USER_CURRENT;
        } else {
            try {
                userId = Integer.parseInt(arg);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Bad user number: " + arg);
            }
        }
        return userId;
    }

    /**
     * Returns the user id of the current process
     *
     * @return user id of the current process
     * @hide
     */
    public static int myUserId() {
        return getUserId(Process.myUid());
    }

    /**
     * Returns true if this UserHandle refers to the owner user; false otherwise.
     *
     * @return true if this UserHandle refers to the owner user; false otherwise.
     * @hide
     * @deprecated please use {@link #isSystem()} or check for
     */
    @Deprecated
    public boolean isOwner() {
        return this.equals(OWNER);
    }

    /**
     * @return true if this UserHandle refers to the system user; false otherwise.
     * @hide
     */
    public boolean isSystem() {
        return this.equals(SYSTEM);
    }

    /**
     * @hide
     */
    public ParallaxUserHandle(int h) {
        mHandle = h;
    }

    /**
     * Returns the userId stored in this UserHandle.
     *
     * @hide
     */
    public int getIdentifier() {
        return mHandle;
    }

    @Override
    public String toString() {
        return "UserHandle{" + mHandle + "}";
    }

    @Override
    public boolean equals(Object obj) {
        try {
            if (obj != null) {
                ParallaxUserHandle other = (ParallaxUserHandle) obj;
                return mHandle == other.mHandle;
            }
        } catch (ClassCastException e) {
        }
        return false;
    }

    @Override
    public int hashCode() {
        return mHandle;
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mHandle);
    }

    /**
     * Write a UserHandle to a Parcel, handling null pointers.  Must be
     * read with {@link #readFromParcel(Parcel)}.
     *
     * @param h   The UserHandle to be written.
     * @param out The Parcel in which the UserHandle will be placed.
     * @see #readFromParcel(Parcel)
     */
    public static void writeToParcel(ParallaxUserHandle h, Parcel out) {
        if (h != null) {
            h.writeToParcel(out, 0);
        } else {
            out.writeInt(USER_NULL);
        }
    }

    /**
     * Read a UserHandle from a Parcel that was previously written
     * with {@link #writeToParcel(ParallaxUserHandle, Parcel)}, returning either
     * a null or new object as appropriate.
     *
     * @param in The Parcel from which to read the UserHandle
     * @return Returns a new UserHandle matching the previously written
     * object, or null if a null had been written.
     * @see #writeToParcel(ParallaxUserHandle, Parcel)
     */
    public static ParallaxUserHandle readFromParcel(Parcel in) {
        int h = in.readInt();
        return h != USER_NULL ? new ParallaxUserHandle(h) : null;
    }

    public static final Parcelable.Creator<ParallaxUserHandle> CREATOR
            = new Creator<ParallaxUserHandle>() {
        public ParallaxUserHandle createFromParcel(Parcel in) {
            return new ParallaxUserHandle(in);
        }

        public ParallaxUserHandle[] newArray(int size) {
            return new ParallaxUserHandle[size];
        }
    };

    /**
     * Instantiate a new UserHandle from the data in a Parcel that was
     * previously written with {@link #writeToParcel(Parcel, int)}.  Note that you
     * must not use this with data written by
     * {@link #writeToParcel(ParallaxUserHandle, Parcel)} since it is not possible
     * to handle a null UserHandle here.
     *
     * @param in The Parcel containing the previously written UserHandle,
     *           positioned at the location in the buffer where it was written.
     */
    public ParallaxUserHandle(Parcel in) {
        mHandle = in.readInt();
    }
}
