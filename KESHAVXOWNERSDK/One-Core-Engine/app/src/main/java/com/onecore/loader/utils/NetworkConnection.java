package com.onecore.loader.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkRequest;
import com.onecore.loader.BoxApplication;
import com.Jagdish.tastytoast.TastyToast;

public class NetworkConnection {
    
    public static class CheckInternet {
        Context context;
        boolean isShow = false;
        
        public CheckInternet(Context ctx) {
            context = ctx;
        }
        
        public void registerNetworkCallback() {
            try {
                ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkRequest.Builder builder = new NetworkRequest.Builder();
                connectivityManager.registerDefaultNetworkCallback(
                        new ConnectivityManager.NetworkCallback() {
                            @Override
                            public void onAvailable(Network network) {
                                isShow = false;
                                BoxApplication.get().setInternetAvailable(true);
                            }

                            @Override
                            public void onLost(Network network) {
                                BoxApplication.get().setInternetAvailable(false);
                                if (!isShow) {
                                    TastyToast.makeText(context, "No Internet Connection", TastyToast.LENGTH_LONG, TastyToast.ERROR);
                                    isShow = true;
                                }
                            }
                        });
                BoxApplication.get().setInternetAvailable(false);
            } catch (Exception e) {
                BoxApplication.get().setInternetAvailable(false);
            }
        }
    }
}