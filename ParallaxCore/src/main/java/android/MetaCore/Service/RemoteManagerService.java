package android.MetaCore.Service;

import android.MetaCore.IRemoteManager;
import android.MetaCore.RemoteManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;

public class RemoteManagerService extends Service {
    
    private final IRemoteManager.Stub binder = new IRemoteManager.Stub() {
        @Override
        public void activateSdk(String userkey) throws RemoteException {
            RemoteManager.getInstance().activateSdk(userkey);
        }

        @Override
        public boolean getActivatedSdk() throws RemoteException {
            return RemoteManager.getInstance().getActivatedSdk();
        }

        @Override
        public String getServerMessage() throws RemoteException {
            return RemoteManager.getInstance().getServerMessage();
        }

        @Override
        public boolean getNetwork() throws RemoteException {
            return RemoteManager.getInstance().getNetwork();
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
}
