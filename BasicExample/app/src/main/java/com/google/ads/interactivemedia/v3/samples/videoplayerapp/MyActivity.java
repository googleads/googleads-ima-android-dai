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

package com.google.ads.interactivemedia.v3.samples.videoplayerapp;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.media3.ui.PlayerView;
import com.google.ads.interactivemedia.v3.samples.samplevideoplayer.SampleVideoPlayer;

/** Main Activity that plays media using {@link SampleVideoPlayer}. */
@SuppressLint("UnsafeOptInUsageError")
/* @SuppressLint is needed for new media3 APIs. */
public class MyActivity extends Activity {

  private static final String DEFAULT_STREAM_URL =
      "https://storage.googleapis.com/interactive-media-ads/media/bbb.m3u8";
  private static final String APP_LOG_TAG = "ImaDaiExample";

  protected SampleVideoPlayer sampleVideoPlayer;
  protected ImageButton playButton;

  private boolean contentHasStarted = false;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_my);
    View rootView = findViewById(R.id.videoLayout);
    sampleVideoPlayer =
        new SampleVideoPlayer(
            rootView.getContext(), (PlayerView) rootView.findViewById(R.id.playerView));
    sampleVideoPlayer.enableControls(false);
    playButton = (ImageButton) rootView.findViewById(R.id.playButton);
    final SampleAdsWrapper sampleAdsWrapper =
        new SampleAdsWrapper(
            this, sampleVideoPlayer, (ViewGroup) rootView.findViewById(R.id.adUiContainer));
    sampleAdsWrapper.setFallbackUrl(DEFAULT_STREAM_URL);

    final ScrollView scrollView = (ScrollView) findViewById(R.id.logScroll);
    final TextView textView = (TextView) findViewById(R.id.logText);

    sampleAdsWrapper.setLogger(
        new SampleAdsWrapper.Logger() {
          @Override
          public void log(String logMessage) {
            Log.i(APP_LOG_TAG, logMessage);
            if (textView != null) {
              textView.append(logMessage);
            }
            if (scrollView != null) {
              scrollView.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      scrollView.fullScroll(View.FOCUS_DOWN);
                    }
                  });
            }
          }
        });

    // Set up play button listener to play video then hide play button.
    playButton.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View view) {
            if (contentHasStarted) {
              sampleVideoPlayer.play();
            } else {
              contentHasStarted = true;
              sampleVideoPlayer.enableControls(true);
              sampleAdsWrapper.requestAndPlayAds();
            }
            playButton.setVisibility(View.GONE);
          }
        });
    orientVideoDescription(getResources().getConfiguration().orientation);
  }

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
      // Add logic to be called when coming back to the player's app.
    }
  }
}
