package com.Parallax.SDK.core.fake.service;

import android.content.Context;
import android.os.IBinder;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import com.Parallax.SDK.mirror.android.os.BRServiceManager;
import com.Parallax.SDK.mirror.com.android.internal.telephony.BRITelephonyStub;
import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.app.ParallaxActivityThread;
import com.Parallax.SDK.core.entity.location.ParallaxCell;
import com.Parallax.SDK.core.fake.frameworks.ParallaxLocationManager;
import com.Parallax.SDK.core.fake.hook.ParallaxBinderInvocationStub;
import com.Parallax.SDK.core.fake.hook.ParallaxMethodHook;
import com.Parallax.SDK.core.fake.hook.ParallaxProxyMethod;
import com.Parallax.SDK.core.utils.ParallaxMd5Utils;

public class IParallaxTelephonyManagerProxy extends ParallaxBinderInvocationStub {
	public static final String TAG = "IParallaxTelephonyManagerProxy";

	public IParallaxTelephonyManagerProxy() {
		super(BRServiceManager.get().getService(Context.TELEPHONY_SERVICE));
	}

	@Override
	protected Object getWho() {
		IBinder telephony = BRServiceManager.get().getService(Context.TELEPHONY_SERVICE);
		return BRITelephonyStub.get().asInterface(telephony);
	}

	@Override
	protected void inject(Object baseInvocation, Object proxyInvocation) {
		replaceSystemService(Context.TELEPHONY_SERVICE);
	}

	@Override
	public boolean isBadEnv() {
		return false;
	}

	@ParallaxProxyMethod("getDeviceId")
	public static class GetDeviceId extends ParallaxMethodHook {
		@Override
		protected Object hook(Object who, Method method, Object[] args) throws Throwable {
			return ParallaxMd5Utils.md5(ParallaxCore.getHostPkg());
		}
	}

	@ParallaxProxyMethod("getImeiForSlot")
	public static class getImeiForSlot extends ParallaxMethodHook {
		@Override
		protected Object hook(Object who, Method method, Object[] args) throws Throwable {
			return ParallaxMd5Utils.md5(ParallaxCore.getHostPkg());
		}
	}

	@ParallaxProxyMethod("getMeidForSlot")
	public static class GetMeidForSlot extends ParallaxMethodHook {
		@Override
		protected Object hook(Object who, Method method, Object[] args) throws Throwable {
			return ParallaxMd5Utils.md5(ParallaxCore.getHostPkg());
		}
	}

	@ParallaxProxyMethod("isUserDataEnabled")
	public static class IsUserDataEnabled extends ParallaxMethodHook {
		@Override
		protected Object hook(Object who, Method method, Object[] args) throws Throwable {
			return true;
		}
	}

	@ParallaxProxyMethod("getLine1NumberForDisplay")
	public static class getLine1NumberForDisplay extends ParallaxMethodHook {
		@Override
		protected Object hook(Object who, Method method, Object[] args) throws Throwable {
			return null;
		}
	}

	@ParallaxProxyMethod("getSubscriberId")
	public static class GetSubscriberId extends ParallaxMethodHook {
		@Override
		protected Object hook(Object who, Method method, Object[] args) throws Throwable {
			return ParallaxMd5Utils.md5(ParallaxCore.getHostPkg());
		}
	}

	@ParallaxProxyMethod("getDeviceIdWithFeature")
	public static class GetDeviceIdWithFeature extends ParallaxMethodHook {
		@Override
		protected Object hook(Object who, Method method, Object[] args) throws Throwable {
			return ParallaxMd5Utils.md5(ParallaxCore.getHostPkg());
		}
	}

	@ParallaxProxyMethod("getCellLocation")
	public static class GetCellLocation extends ParallaxMethodHook {
		@Override
		protected Object hook(Object who, Method method, Object[] args) throws Throwable {
			Log.d(TAG, "getCellLocation");
			if (ParallaxLocationManager.isFakeLocationEnable()) {
				ParallaxCell cell = ParallaxLocationManager.get().getCell(ParallaxActivityThread.getUserId(), ParallaxActivityThread.getAppPackageName());
				if (cell != null) {
					// TODO Transfer ParallaxCell to CdmaCellLocation/GsmCellLocation
					return null;
				}
			}
			return method.invoke(who, args);
		}
	}

	@ParallaxProxyMethod("getAllCellInfo")
	public static class GetAllCellInfo extends ParallaxMethodHook {
		@Override
		protected Object hook(Object who, Method method, Object[] args) throws Throwable {
			if (ParallaxLocationManager.isFakeLocationEnable()) {
				List<ParallaxCell> cell = ParallaxLocationManager.get().getAllCell(ParallaxActivityThread.getUserId(), ParallaxActivityThread.getAppPackageName());
				// TODO Transfer ParallaxCell to CdmaCellLocation/GsmCellLocation
				return cell;
			}
			try {
				return method.invoke(who, args);
			} catch (Throwable e) {
				return null;
			}
		}
	}

	@ParallaxProxyMethod("getNetworkOperator")
	public static class GetNetworkOperator extends ParallaxMethodHook {
		@Override
		protected Object hook(Object who, Method method, Object[] args) throws Throwable {
			Log.d(TAG, "getNetworkOperator");
			return method.invoke(who, args);
		}
	}

	@ParallaxProxyMethod("getNetworkTypeForSubscriber")
	public static class GetNetworkTypeForSubscriber extends ParallaxMethodHook {
		@Override
		protected Object hook(Object who, Method method, Object[] args) throws Throwable {
			try {
				return method.invoke(who, args);
			} catch (Throwable e) {
				return 0;
			}
		}
	}

	@ParallaxProxyMethod("getNeighboringCellInfo")
	public static class GetNeighboringCellInfo extends ParallaxMethodHook {
		@Override
		protected Object hook(Object who, Method method, Object[] args) throws Throwable {
			Log.d(TAG, "getNeighboringCellInfo");
			if (ParallaxLocationManager.isFakeLocationEnable()) {
				List<ParallaxCell> cell = ParallaxLocationManager.get().getNeighboringCell(ParallaxActivityThread.getUserId(), ParallaxActivityThread.getAppPackageName());
				// TODO Transfer ParallaxCell to CdmaCellLocation/GsmCellLocation
				return null;
			}
			return method.invoke(who, args);
		}
	}
}
