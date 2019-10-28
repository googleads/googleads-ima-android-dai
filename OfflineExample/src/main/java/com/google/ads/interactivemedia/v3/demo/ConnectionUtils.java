package com.google.android.exoplayer2.demo;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

/** Utils class for detecting if the device is connected to the internet. */
public class ConnectionUtils {

  /** Returns true if an internet connection is detected. False otherwise. */
  static boolean isConnected(Context context) {
    ConnectivityManager connectivityManager =
        (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
    return activeNetwork != null && activeNetwork.isConnected();
  }
}
