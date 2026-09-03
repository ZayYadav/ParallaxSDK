// IRemoteManager.aidl
package android.MetaCore;

interface IRemoteManager {

    void activateSdk(String userkey);

    boolean getActivatedSdk();

    String getServerMessage();

    boolean getNetwork();
}