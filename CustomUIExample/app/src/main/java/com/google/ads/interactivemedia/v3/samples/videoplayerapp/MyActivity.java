/*
 * Copyright 2015 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// [START my_activity_on_create]
package com.google.ads.interactivemedia.v3.samples.videoplayerapp;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.ads.interactivemedia.v3.api.ImaSdkFactory;
import com.google.ads.interactivemedia.v3.api.ImaSdkSettings;
import com.google.ads.interactivemedia.v3.samples.samplevideoplayer.SampleVideoPlayer;

/** Main Activity that plays media using {@link SampleVideoPlayer}. */
@SuppressLint("UnsafeOptInUsageError")
/* @SuppressLint is needed for new media3 APIs. */
public class MyActivity extends Activity {

  private static final String DEFAULT_STREAM_URL =
      "https://storage.googleapis.com/interactive-media-ads/media/bbb.m3u8";
  private static final String APP_LOG_TAG = "ImaDaiExample";
  private static final String PLAYER_TYPE = "DAISamplePlayer";
  private static ImaSdkSettings imaSdkSettings;

  protected SampleVideoPlayer sampleVideoPlayer;
  protected ImageButton playButton;

  private boolean contentHasStarted = false;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_my);

    // Initialize the IMA SDK as early as possible when the app starts. If your app already
    // overrides Application.onCreate(), call this method inside the onCreate() method.
    // https://developer.android.com/topic/performance/vitals/launch-time#app-creation
    ImaSdkFactory.getInstance().initialize(this, getImaSdkSettings());

    View rootView = findViewById(R.id.videoLayout);
    sampleVideoPlayer =
        new SampleVideoPlayer(rootView.getContext(), rootView.findViewById(R.id.playerView));
    sampleVideoPlayer.enableControls(false);
    playButton = rootView.findViewById(R.id.playButton);
    final SampleAdsWrapper sampleAdsWrapper =
        new SampleAdsWrapper(this, sampleVideoPlayer, rootView.findViewById(R.id.adUiContainer));
    sampleAdsWrapper.setFallbackUrl(DEFAULT_STREAM_URL);

    final ScrollView scrollView = findViewById(R.id.logScroll);
    final TextView textView = findViewById(R.id.logText);

    sampleAdsWrapper.setLogger(
        logMessage -> {
          Log.i(APP_LOG_TAG, logMessage);
          if (textView != null) {
            textView.append(logMessage);
          }
          if (scrollView != null) {
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
          }
        });

    // Set up play button listener to play video then hide play button.
    playButton.setOnClickListener(
        view -> {
          if (contentHasStarted) {
            sampleVideoPlayer.play();
          } else {
            contentHasStarted = true;
            sampleVideoPlayer.enableControls(true);
            sampleAdsWrapper.requestAndPlayAds();
          }
          playButton.setVisibility(View.GONE);
        });
    orientVideoDescription(getResources().getConfiguration().orientation);
  }

  // [END my_activity_on_create]

  @Override
  public void onConfigurationChanged(Configuration configuration) {
    super.onConfigurationChanged(configuration);
    orientVideoDescription(configuration.orientation);
  }

  private void orientVideoDescription(int orientation) {
    // Hide the extra content when in landscape so the video is as large as possible.
    if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
      findViewById(R.id.descriptionLayout).setVisibility(View.GONE);
    } else {
      findViewById(R.id.descriptionLayout).setVisibility(View.VISIBLE);
    }
  }

  // Needed to pause/resume app from background.
  @Override
  public void onPause() {
    super.onPause();
    if (sampleVideoPlayer != null && sampleVideoPlayer.isStreamRequested()) {
      sampleVideoPlayer.pause();
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    if (sampleVideoPlayer != null && sampleVideoPlayer.isStreamRequested()) {
      sampleVideoPlayer.play();
      sampleVideoPlayer.enableControls(false);
    }
  }

  // [START get_ima_settings]
  public static ImaSdkSettings getImaSdkSettings() {
    if (imaSdkSettings == null) {
      imaSdkSettings = ImaSdkFactory.getInstance().createImaSdkSettings();
      imaSdkSettings.setPlayerType(PLAYER_TYPE);
      // Set any additional IMA SDK settings here.
    }
    return imaSdkSettings;
  }
  // [END get_ima_settings]
}
