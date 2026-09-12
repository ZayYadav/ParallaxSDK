package com.Parallax.SDK.runtime.Service;

import com.Parallax.SDK.runtime.IParallaxRemoteManager;
import com.Parallax.SDK.runtime.ParallaxRemoteManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;

public class ParallaxRemoteManagerService extends Service {
    
    private final IParallaxRemoteManager.Stub binder = new IParallaxRemoteManager.Stub() {
        @Override
        public void activateSdk(String userkey) throws RemoteException {
            ParallaxRemoteManager.getInstance().activateSdk(userkey);
        }

        @Override
        public boolean getActivatedSdk() throws RemoteException {
            return ParallaxRemoteManager.getInstance().getActivatedSdk();
        }

        @Override
        public String getServerMessage() throws RemoteException {
            return ParallaxRemoteManager.getInstance().getServerMessage();
        }

        @Override
        public boolean getNetwork() throws RemoteException {
            return ParallaxRemoteManager.getInstance().getNetwork();
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
}
