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
import com.google.ads.interactivemedia.v3.api.AdErrorEvent;
import com.google.ads.interactivemedia.v3.api.AdEvent;
import com.google.ads.interactivemedia.v3.api.AdsLoader;
import com.google.ads.interactivemedia.v3.api.AdsManagerLoadedEvent;
import com.google.ads.interactivemedia.v3.api.AdsRenderingSettings;
import com.google.ads.interactivemedia.v3.api.CuePoint;
import com.google.ads.interactivemedia.v3.api.ImaSdkFactory;
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

/** This class implements IMA to add Cloud Video Stitcher support to SampleVideoPlayer */
@SuppressLint("UnsafeOptInUsageError")
/* @SuppressLint is needed for new media3 APIs. */
public class SampleAdsWrapper
    implements AdEvent.AdEventListener, AdErrorEvent.AdErrorListener, AdsLoader.AdsLoadedListener {

  // Set up the Cloud Video Stitcher variables.
  private static final String NETWORK_CODE = "";
  private static final String LOCATION = "";
  private static final String PROJECT_NUMBER = "";
  private static final String OAUTH_TOKEN = "";
  private static final StreamFormat STREAM_FORMAT = StreamFormat.HLS;

  // Livestream variables.
  private static final String ASSET_KEY = "";
  private static final String LIVE_CONFIG_ID = "";

  // VOD variables.
  private static final String VOD_CONFIG_ID = "";

  private enum StreamType {
    LIVESTREAM,
    VOD,
  }

  // Change this enum to make either a live or VOD stream request.
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
      Context context, SampleVideoPlayer videoPlayer, ViewGroup adUiContainer, Logger logger) {
    this.context = context;
    this.videoPlayer = videoPlayer;
    this.adUiContainer = adUiContainer;
    this.logger = logger;
    playerCallbacks = new ArrayList<>();
    sdkFactory = ImaSdkFactory.getInstance();
    adsLoader = createAdsLoader();
  }

  private AdsLoader createAdsLoader() {
    VideoStreamPlayer videoStreamPlayer = createVideoStreamPlayer();
    StreamDisplayContainer displayContainer =
        ImaSdkFactory.createStreamDisplayContainer(adUiContainer, videoStreamPlayer);
    videoPlayer.setSampleVideoPlayerCallback(
        new SampleVideoPlayerCallback() {
          @Override
          public void onUserTextReceived(String userText) {
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
    AdsLoader adsLoader =
        sdkFactory.createAdsLoader(context, MyActivity.getImaSdkSettings(), displayContainer);
    adsLoader.addAdErrorListener(SampleAdsWrapper.this);
    adsLoader.addAdsLoadedListener(SampleAdsWrapper.this);
    return adsLoader;
  }

  public void requestAndPlayAds() {
    StreamRequest request;
    switch (CONTENT_STREAM_TYPE) {
      case LIVESTREAM:
        // Livestream Cloud Video Stitcher stream request.
        request =
            sdkFactory.createVideoStitcherLiveStreamRequest(
                NETWORK_CODE, ASSET_KEY, LIVE_CONFIG_ID, LOCATION, PROJECT_NUMBER, OAUTH_TOKEN);
        break;
      case VOD:
        // VOD Cloud Video Stitcher stream request.
        request =
            sdkFactory.createVideoStitcherVodStreamRequest(
                NETWORK_CODE, LOCATION, PROJECT_NUMBER, OAUTH_TOKEN, VOD_CONFIG_ID);
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
      public void loadUrl(String streamUrl, List<HashMap<String, String>> subtitles) {
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
      public void addCallback(VideoStreamPlayerCallback videoStreamPlayerCallback) {
        playerCallbacks.add(videoStreamPlayerCallback);
      }

      @Override
      public void removeCallback(VideoStreamPlayerCallback videoStreamPlayerCallback) {
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
      public void seek(long timeMs) {
        // An ad was skipped. Skip to the content time.
        videoPlayer.seekTo(timeMs);
        logger.log("seek\n");
      }

      @Override
      public VideoProgressUpdate getContentProgress() {
        return new VideoProgressUpdate(
            videoPlayer.getCurrentPositionMs(), videoPlayer.getDuration());
      }
    };
  }

  /** AdErrorListener implementation */
  @Override
  public void onAdError(AdErrorEvent event) {
    logger.log(String.format("Error: %s\n", event.getError().getMessage()));
    // play fallback URL.
    logger.log("Playing fallback Url\n");
    videoPlayer.setStreamUrl(fallbackUrl);
    videoPlayer.enableControls(true);
    videoPlayer.play();
  }

  /** AdEventListener implementation */
  @Override
  public void onAdEvent(AdEvent event) {
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
  public void onAdsManagerLoaded(AdsManagerLoadedEvent event) {
    streamManager = event.getStreamManager();
    streamManager.addAdErrorListener(this);
    streamManager.addAdEventListener(this);

    AdsRenderingSettings adsRenderingSettings = sdkFactory.createAdsRenderingSettings();
    // Add any ads rendering settings here.
    // This init() only loads the UI rendering settings locally.
    streamManager.init(adsRenderingSettings);
  }

  /** Sets fallback URL in case ads stream fails. */
  void setFallbackUrl(String url) {
    fallbackUrl = url;
  }
}
