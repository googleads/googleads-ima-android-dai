/*
 * Copyright 2024 Google LLC
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
import androidx.annotation.NonNull;
import com.google.ads.interactivemedia.v3.samples.samplevideoplayer.SampleVideoPlayer;

/** Main Activity that plays media using {@link SampleVideoPlayer}. */
@SuppressLint("UnsafeOptInUsageError")
/* @SuppressLint is needed for new media3 APIs. */
public class MyActivity extends Activity {

  private static final String DEFAULT_STREAM_URL =
      "https://storage.googleapis.com/interactive-media-ads/media/bbb.m3u8";
  private static final String APP_LOG_TAG = "ImaDaiExample";

  /** An interface defining how this class emits log messages. */
  public interface Logger {
    void log(String logMessage);
  }

  /** Initializes the logger for displaying events to screen. */
  private void initializeLogger() {
    final ScrollView scrollView = findViewById(R.id.logScroll);
    final TextView textView = findViewById(R.id.logText);

    this.logger =
        (logMessage -> {
          Log.i(APP_LOG_TAG, logMessage);
          if (textView != null) {
            textView.append(logMessage);
          }
          if (scrollView != null) {
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
          }
        });
  }

  private Logger logger;

  protected SampleVideoPlayer sampleVideoPlayer;
  protected ImageButton playButton;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_my);

    initializeLogger();

    sampleVideoPlayer = new SampleVideoPlayer(MyActivity.this, findViewById(R.id.playerView));
    playButton = findViewById(R.id.playButton);
    final SampleAdsWrapper sampleAdsWrapper =
        new SampleAdsWrapper(this, sampleVideoPlayer, findViewById(R.id.adUiContainer), logger);
    sampleAdsWrapper.setFallbackUrl(DEFAULT_STREAM_URL);

    // Set up play button listener to play video then hide play button.
    playButton.setOnClickListener(
        view -> {
          sampleVideoPlayer.enableControls(true);
          sampleAdsWrapper.requestAndPlayAds();
          playButton.setVisibility(View.GONE);
        });
    updateVideoDescriptionVisibility();
  }

  @Override
  public void onConfigurationChanged(@NonNull Configuration configuration) {
    super.onConfigurationChanged(configuration);
    // Hide the extra content when in landscape so the video is as large as possible.
    updateVideoDescriptionVisibility();
  }

  private void updateVideoDescriptionVisibility() {
    int orientation = getResources().getConfiguration().orientation;
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
    }
  }
}
