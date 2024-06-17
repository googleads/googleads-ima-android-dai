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
import android.content.Context;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import com.google.ads.interactivemedia.v3.api.AdErrorEvent;
import com.google.ads.interactivemedia.v3.api.AdEvent;
import com.google.ads.interactivemedia.v3.api.AdsLoader;
import com.google.ads.interactivemedia.v3.api.AdsManagerLoadedEvent;
import com.google.ads.interactivemedia.v3.api.AdsRenderingSettings;
import com.google.ads.interactivemedia.v3.api.CuePoint;
import com.google.ads.interactivemedia.v3.api.ImaSdkFactory;
import com.google.ads.interactivemedia.v3.api.ImaSdkSettings;
import com.google.ads.interactivemedia.v3.api.StreamDisplayContainer;
import com.google.ads.interactivemedia.v3.api.StreamManager;
import com.google.ads.interactivemedia.v3.api.StreamRequest;
import com.google.ads.interactivemedia.v3.api.StreamRequest.StreamFormat;
import com.google.ads.interactivemedia.v3.api.player.VideoProgressUpdate;
import com.google.ads.interactivemedia.v3.api.player.VideoStreamPlayer;
import com.google.ads.interactivemedia.v3.samples.samplevideoplayer.SampleVideoPlayer;
import com.google.ads.interactivemedia.v3.samples.samplevideoplayer.SampleVideoPlayer.SampleVideoPlayerCallback;
import com.google.ads.interactivemedia.v3.samples.videoplayerapp.MyActivity.Logger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** This class implements IMA to add pod ad-serving support to SampleVideoPlayer */
@SuppressLint("UnsafeOptInUsageError")
/* @SuppressLint is needed for new media3 APIs. */
public class SampleAdsWrapper
    implements AdEvent.AdEventListener, AdErrorEvent.AdErrorListener, AdsLoader.AdsLoadedListener {

  // Set up the pod serving variables.
  private static final String NETWORK_CODE = "";
  private static final String CUSTOM_ASSET_KEY = "";
  private static final String API_KEY = "";
  private static final String STREAM_URL = "";
  private static final StreamFormat STREAM_FORMAT = StreamFormat.HLS;

  private enum StreamType {
    LIVESTREAM,
    VOD,
  }

  // Change this enum to make either a live or VOD pod stream request.
  private static final StreamType CONTENT_STREAM_TYPE = StreamType.LIVESTREAM;

  private final ImaSdkFactory sdkFactory;
  private final AdsLoader adsLoader;
  private StreamManager streamManager;
  private final List<VideoStreamPlayer.VideoStreamPlayerCallback> playerCallbacks;

  private final SampleVideoPlayer videoPlayer;
  private final Context context;
  private final ViewGroup adUiContainer;
  private final Logger logger;
  private String fallbackUrl;

  /**
   * Creates a new SampleAdsWrapper that implements IMA Dynamic Ad Insertion.
   *
   * @param context the app's context.
   * @param videoPlayer underlying ExoPlayer wrapped by the SampleVideoPlayer.
   * @param adUiContainer ViewGroup that displays the ad UI (ad timer, skip button, adChoices icon).
   * @param logger Logger to log messages to.
   */
  public SampleAdsWrapper(
      @NonNull Context context,
      @NonNull SampleVideoPlayer videoPlayer,
      @NonNull ViewGroup adUiContainer,
      @NonNull Logger logger) {
    this.context = context;
    this.videoPlayer = videoPlayer;
    this.adUiContainer = adUiContainer;
    this.logger = logger;
    playerCallbacks = new ArrayList<>();
    sdkFactory = ImaSdkFactory.getInstance();
    adsLoader = createAdsLoader();
  }

  private AdsLoader createAdsLoader() {
    ImaSdkSettings settings = sdkFactory.createImaSdkSettings();
    // Change any settings as necessary here.
    settings.setDebugMode(true);
    VideoStreamPlayer videoStreamPlayer = createVideoStreamPlayer();
    StreamDisplayContainer displayContainer =
        ImaSdkFactory.createStreamDisplayContainer(adUiContainer, videoStreamPlayer);
    videoPlayer.setSampleVideoPlayerCallback(
        new SampleVideoPlayerCallback() {
          @Override
          public void onUserTextReceived(@NonNull String userText) {
            for (VideoStreamPlayer.VideoStreamPlayerCallback callback : playerCallbacks) {
              callback.onUserTextReceived(userText);
            }
          }

          @Override
          public void onSeek(int windowIndex, long positionMs) {
            // See if we would seek past an ad, and if so, jump back to it.
            long newSeekPositionMs = positionMs;
            if (streamManager != null) {
              CuePoint prevCuePoint = streamManager.getPreviousCuePointForStreamTimeMs(positionMs);
              if (prevCuePoint != null && !prevCuePoint.isPlayed()) {
                newSeekPositionMs = prevCuePoint.getStartTimeMs();
              }
            }
            videoPlayer.seekTo(windowIndex, newSeekPositionMs);
          }

          @Override
          public void onContentComplete() {
            for (VideoStreamPlayer.VideoStreamPlayerCallback callback : playerCallbacks) {
              callback.onContentComplete();
            }
          }

          @Override
          public void onPause() {
            for (VideoStreamPlayer.VideoStreamPlayerCallback callback : playerCallbacks) {
              callback.onPause();
            }
          }

          @Override
          public void onResume() {
            for (VideoStreamPlayer.VideoStreamPlayerCallback callback : playerCallbacks) {
              callback.onResume();
            }
          }

          @Override
          public void onVolumeChanged(int percentage) {
            for (VideoStreamPlayer.VideoStreamPlayerCallback callback : playerCallbacks) {
              callback.onVolumeChanged(percentage);
            }
          }
        });
    AdsLoader adsLoader = sdkFactory.createAdsLoader(context, settings, displayContainer);
    adsLoader.addAdErrorListener(SampleAdsWrapper.this);
    adsLoader.addAdsLoadedListener(SampleAdsWrapper.this);
    return adsLoader;
  }

  public void requestAndPlayAds() {
    StreamRequest request;
    switch (CONTENT_STREAM_TYPE) {
      case LIVESTREAM:
        // Live pod stream request.
        request = sdkFactory.createPodStreamRequest(NETWORK_CODE, CUSTOM_ASSET_KEY, API_KEY);
        break;
      case VOD:
        // VOD pod stream request.
        request = sdkFactory.createPodVodStreamRequest(NETWORK_CODE);
        break;
      default:
        throw new IllegalStateException("Unexpected value: " + CONTENT_STREAM_TYPE);
    }
    request.setFormat(STREAM_FORMAT);
    adsLoader.requestStream(request);
  }

  private VideoStreamPlayer createVideoStreamPlayer() {
    return new VideoStreamPlayer() {
      @Override
      public void loadUrl(
          @NonNull String streamUrl, @NonNull List<HashMap<String, String>> subtitles) {
        // For VOD stream only, the IMA DAI SDK calls loadUrl() when it completes
        // loading the ad metadata.
        videoPlayer.setStreamUrl(streamUrl);
        videoPlayer.play();
      }

      @Override
      public void pause() {
        // Pause player.
        videoPlayer.pause();
      }

      @Override
      public void resume() {
        // Resume player.
        videoPlayer.play();
      }

      @Override
      public int getVolume() {
        // Make the video player play at the current device volume.
        return 100;
      }

      @Override
      public void addCallback(@NonNull VideoStreamPlayerCallback videoStreamPlayerCallback) {
        playerCallbacks.add(videoStreamPlayerCallback);
      }

      @Override
      public void removeCallback(@NonNull VideoStreamPlayerCallback videoStreamPlayerCallback) {
        playerCallbacks.remove(videoStreamPlayerCallback);
      }

      @Override
      public void onAdBreakStarted() {
        // Disable player controls.
        videoPlayer.enableControls(false);
        logger.log("Ad Break Started\n");
      }

      @Override
      public void onAdBreakEnded() {
        // Re-enable player controls.
        if (videoPlayer != null) {
          videoPlayer.enableControls(true);
        }
        logger.log("Ad Break Ended\n");
      }

      @Override
      public void onAdPeriodStarted() {
        logger.log("Ad Period Started\n");
      }

      @Override
      public void onAdPeriodEnded() {
        logger.log("Ad Period Ended\n");
      }

      @Override
      public void seek(@NonNull long timeMs) {
        // An ad was skipped. Skip to the content time.
        videoPlayer.seekTo(timeMs);
        logger.log("seek\n");
      }

      @NonNull
      @Override
      public VideoProgressUpdate getContentProgress() {
        return new VideoProgressUpdate(
            videoPlayer.getCurrentPositionMs(), videoPlayer.getDuration());
      }
    };
  }

  /** AdErrorListener implementation */
  @Override
  public void onAdError(@NonNull AdErrorEvent event) {
    logger.log(String.format("Error: %s\n", event.getError().getMessage()));
    // play fallback URL.
    logger.log("Playing fallback Url\n");
    videoPlayer.setStreamUrl(fallbackUrl);
    videoPlayer.enableControls(true);
    videoPlayer.play();
  }

  /** AdEventListener implementation */
  @Override
  public void onAdEvent(@NonNull AdEvent event) {
    switch (event.getType()) {
      case AD_PROGRESS:
        // Do nothing or else log will be filled by these messages.
        break;
      default:
        logger.log(String.format("Event: %s\n", event.getType()));
        break;
    }
  }

  /** AdsLoadedListener implementation */
  @Override
  public void onAdsManagerLoaded(@NonNull AdsManagerLoadedEvent event) {
    streamManager = event.getStreamManager();
    streamManager.addAdErrorListener(this);
    streamManager.addAdEventListener(this);

    AdsRenderingSettings adsRenderingSettings = sdkFactory.createAdsRenderingSettings();
    // Add any ads rendering settings here.
    // This init() only loads the UI rendering settings locally.
    streamManager.init(adsRenderingSettings);

    // To enable ad pod streams
    String streamID = streamManager.getStreamId();
    String streamUrl = STREAM_URL.replace("[[STREAMID]]", streamID);
    switch (CONTENT_STREAM_TYPE) {
      case LIVESTREAM:
        // Play the live pod stream.
        videoPlayer.setStreamUrl(streamUrl);
        videoPlayer.play();
        // The SDK doesn't call the loadUrl() function for livestream.
        break;
      case VOD:
        // Refer to your Video Tech Partner (VTP) or video stitching guide to fetch the stream URL
        // and the subtitles for a the ad stitched VOD stream.
        List<Map<String, String>> subtitles = new ArrayList<>();
        streamManager.loadThirdPartyStream(streamUrl, subtitles);
        break;
    }
  }

  /** Sets fallback URL in case ads stream fails. */
  void setFallbackUrl(String url) {
    fallbackUrl = url;
  }
}
