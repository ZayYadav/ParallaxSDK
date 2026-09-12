// IParallaxRemoteManager.aidl
package com.Parallax.SDK.runtime;

interface IParallaxRemoteManager {

    void activateSdk(String userkey);

    boolean getActivatedSdk();

    String getServerMessage();

    boolean getNetwork();
}