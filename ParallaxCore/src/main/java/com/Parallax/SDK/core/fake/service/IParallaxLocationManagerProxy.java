package com.Parallax.SDK.core.fake.service;

import android.content.Context;
import android.location.LocationManager;
import android.os.IInterface;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;

import com.Parallax.SDK.mirror.android.location.BRILocationListener;
import com.Parallax.SDK.mirror.android.location.BRILocationManagerStub;
import com.Parallax.SDK.mirror.android.location.provider.BRProviderProperties;
import com.Parallax.SDK.mirror.android.location.provider.ProviderProperties;
import com.Parallax.SDK.mirror.android.os.BRServiceManager;
import com.Parallax.SDK.core.app.ParallaxActivityThread;
import com.Parallax.SDK.core.entity.location.ParallaxLocation;
import com.Parallax.SDK.core.fake.frameworks.ParallaxLocationManager;
import com.Parallax.SDK.core.fake.hook.ParallaxBinderInvocationStub;
import com.Parallax.SDK.core.fake.hook.ParallaxMethodHook;
import com.Parallax.SDK.core.fake.hook.ParallaxProxyMethod;
import com.Parallax.SDK.core.utils.ParallaxMethodParameterUtils;

/**
 * Created by @RIYAZXERO on 4/8/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class IParallaxLocationManagerProxy extends ParallaxBinderInvocationStub {
	public static final String TAG = "IParallaxLocationManagerProxy";

	public IParallaxLocationManagerProxy() {
		super(BRServiceManager.get().getService(Context.LOCATION_SERVICE));
	}

	@Override
	protected Object getWho() {
		return BRILocationManagerStub.get()
			.asInterface(BRServiceManager.get().getService(Context.LOCATION_SERVICE));
	}

	@Override
	protected void inject(Object baseInvocation, Object proxyInvocation) {
		replaceSystemService(Context.LOCATION_SERVICE);
	}

	@Override
	public boolean isBadEnv() {
		return false;
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		ParallaxMethodParameterUtils.replaceFirstAppPkg(args);
		return super.invoke(proxy, method, args);
	}

	@ParallaxProxyMethod("registerGnssStatusCallback")
	public static class RegisterGnssStatusCallback extends ParallaxMethodHook {

		@Override
		protected Object hook(Object who, Method method, Object[] args) throws Throwable {
			// todo
			return true;
		}
	}

	@ParallaxProxyMethod("getLastLocation")
	public static class GetLastLocation extends ParallaxMethodHook {

		@Override
		protected Object hook(Object who, Method method, Object[] args) throws Throwable {
			if (ParallaxLocationManager.isFakeLocationEnable()) {
				return ParallaxLocationManager.get().getLocation(ParallaxActivityThread.getUserId(), ParallaxActivityThread.getAppPackageName()).convert2SystemLocation();
			}
			return method.invoke(who, args);
		}
	}

	@ParallaxProxyMethod("getLastKnownLocation")
	public static class GetLastKnownLocation extends ParallaxMethodHook {

		@Override
		protected Object hook(Object who, Method method, Object[] args) throws Throwable {
			if (ParallaxLocationManager.isFakeLocationEnable()) {
				return ParallaxLocationManager.get().getLocation(ParallaxActivityThread.getUserId(), ParallaxActivityThread.getAppPackageName()).convert2SystemLocation();
			}
			return method.invoke(who, args);
		}
	}

	@ParallaxProxyMethod("requestLocationUpdates")
	public static class RequestLocationUpdates extends ParallaxMethodHook {

		@Override
		protected Object hook(Object who, Method method, Object[] args) throws Throwable {
			if (ParallaxLocationManager.isFakeLocationEnable()) {
				if (args[1] instanceof IInterface) {
					IInterface listener = (IInterface) args[1];
					ParallaxLocationManager.get().requestLocationUpdates(listener.asBinder());
					return 0;
				}
			}
			return method.invoke(who, args);
		}
	}

	@ParallaxProxyMethod("removeUpdates")
	public static class RemoveUpdates extends ParallaxMethodHook {

		@Override
		protected Object hook(Object who, Method method, Object[] args) throws Throwable {
			if (args[0] instanceof IInterface) {
				IInterface listener = (IInterface) args[0];
				ParallaxLocationManager.get().removeUpdates(listener.asBinder());
				return 0;
			}
			return method.invoke(who, args);
		}
	}

	@ParallaxProxyMethod("getProviderProperties")
	public static class GetProviderProperties extends ParallaxMethodHook {

		@Override
		protected Object hook(Object who, Method method, Object[] args) throws Throwable {
			Object providerProperties = method.invoke(who, args);
			if (ParallaxLocationManager.isFakeLocationEnable()) {
				BRProviderProperties.get(providerProperties)._set_mHasNetworkRequirement(false);
				if (ParallaxLocationManager.get().getCell(ParallaxActivityThread.getUserId(), ParallaxActivityThread.getAppPackageName()) == null) {
					BRProviderProperties.get(providerProperties)._set_mHasCellRequirement(false);
				}
			}
			return method.invoke(who, args);
		}
	}

	@ParallaxProxyMethod("removeGpsStatusListener")
	public static class RemoveGpsStatusListener extends ParallaxMethodHook {

		@Override
		protected Object hook(Object who, Method method, Object[] args) throws Throwable {
			// todo
			return 0;
		}
	}

	@ParallaxProxyMethod("getBestProvider")
	public static class GetBestProvider extends ParallaxMethodHook {

		@Override
		protected Object hook(Object who, Method method, Object[] args) throws Throwable {
			if (ParallaxLocationManager.isFakeLocationEnable()) {
				return LocationManager.GPS_PROVIDER;
			}
			return method.invoke(who, args);
		}
	}

	@ParallaxProxyMethod("getAllProviders")
	public static class GetAllProviders extends ParallaxMethodHook {

		@Override
		protected Object hook(Object who, Method method, Object[] args) throws Throwable {
			return Arrays.asList(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER);
		}
	}

	@ParallaxProxyMethod("isProviderEnabledForUser")
	public static class isProviderEnabledForUser extends ParallaxMethodHook {

		@Override
		protected Object hook(Object who, Method method, Object[] args) throws Throwable {
			String provider = (String) args[0];
			return Objects.equals(provider, LocationManager.GPS_PROVIDER);
		}
	}

	@ParallaxProxyMethod("setExtraLocationControllerPackageEnabled")
	public static class setExtraLocationControllerPackageEnabled extends ParallaxMethodHook {

		@Override
		protected Object hook(Object who, Method method, Object[] args) throws Throwable {
			return 0;
		}
	}
}
