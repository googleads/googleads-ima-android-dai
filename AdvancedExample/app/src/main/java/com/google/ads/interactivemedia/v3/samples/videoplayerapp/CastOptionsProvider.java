package com.google.ads.interactivemedia.v3.samples.videoplayerapp;

import android.content.Context;
import com.google.android.gms.cast.framework.CastOptions;
import com.google.android.gms.cast.framework.OptionsProvider;
import com.google.android.gms.cast.framework.SessionProvider;
import java.util.List;

/** Implements OptionsProvider for this app. */
public class CastOptionsProvider implements OptionsProvider {

  @Override
  public CastOptions getCastOptions(Context context) {
    return new CastOptions.Builder()
        .setReceiverApplicationId(context.getString(R.string.app_id))
        .build();
  }

  @Override
  public List<SessionProvider> getAdditionalSessionProviders(Context context) {
    return null;
  }
}
