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

// [START sample_ads_wrapper_initialization]
package com.google.ads.interactivemedia.v3.samples.videoplayerapp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.ViewGroup;
import android.webkit.WebView;
import androidx.annotation.Nullable;
import com.google.ads.interactivemedia.v3.api.AdErrorEvent;
import com.google.ads.interactivemedia.v3.api.AdEvent;
import com.google.ads.interactivemedia.v3.api.AdsLoader;
import com.google.ads.interactivemedia.v3.api.AdsManagerLoadedEvent;
import com.google.ads.interactivemedia.v3.api.CuePoint;
import com.google.ads.interactivemedia.v3.api.CustomUiOptions;
import com.google.ads.interactivemedia.v3.api.ImaSdkFactory;
import com.google.ads.interactivemedia.v3.api.StreamDisplayContainer;
import com.google.ads.interactivemedia.v3.api.StreamManager;
import com.google.ads.interactivemedia.v3.api.StreamRequest;
import com.google.ads.interactivemedia.v3.api.StreamRequest.StreamFormat;
import com.google.ads.interactivemedia.v3.api.customui.CustomUi;
import com.google.ads.interactivemedia.v3.api.player.VideoProgressUpdate;
import com.google.ads.interactivemedia.v3.api.player.VideoStreamPlayer;
import com.google.ads.interactivemedia.v3.samples.samplevideoplayer.SampleVideoPlayer;
import com.google.ads.interactivemedia.v3.samples.samplevideoplayer.SampleVideoPlayer.SampleVideoPlayerCallback;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/** This class adds ad-serving support to Sample HlsVideoPlayer */
@SuppressLint("UnsafeOptInUsageError")
/* @SuppressLint is needed for new media3 APIs. */
public class SampleAdsWrapper
    implements AdEvent.AdEventListener, AdErrorEvent.AdErrorListener, AdsLoader.AdsLoadedListener {

  // Live HLS stream asset key.
  private static final String TEST_HLS_ASSET_KEY = "c-rArva4ShKVIAkNfy6HUQ";

  // Live DASH stream asset key.
  private static final String TEST_DASH_ASSET_KEY = "PSzZMzAkSXCmlJOWDmRj8Q";

  // VOD HLS content source and video IDs.
  private static final String TEST_HLS_CONTENT_SOURCE_ID = "2483977";
  private static final String TEST_HLS_VIDEO_ID = "ima-vod-skippable-test";

  // VOD DASH content source and video IDs.
  private static final String TEST_DASH_CONTENT_SOURCE_ID = "2559737";
  private static final String TEST_DASH_VIDEO_ID = "tos-dash";

  private static final String NETWORK_CODE = "21775744923";

  private static final String PLAYER_TYPE = "DAISamplePlayer";

  private enum ContentType {
    LIVE_HLS,
    LIVE_DASH,
    VOD_HLS,
    VOD_DASH,
  }

  // Set CONTENT_TYPE to the associated enum for the stream type you would like to test.
  private static final ContentType CONTENT_TYPE = ContentType.VOD_HLS;

  /** Log interface, so we can output the log commands to the UI or similar. */
  public interface Logger {
    void log(String logMessage);
  }

  private final ImaSdkFactory sdkFactory;
  private AdsLoader adsLoader;
  private StreamManager streamManager;
  private final List<VideoStreamPlayer.VideoStreamPlayerCallback> playerCallbacks;

  private final SampleVideoPlayer videoPlayer;
  private final Context context;
  private final ViewGroup adUiContainer;

  private String fallbackUrl;
  private Logger logger;

  @Nullable private CustomUiManager customUiManager;

  /**
   * Creates a new SampleAdsWrapper that implements IMA direct-ad-insertion.
   *
   * @param context the app's context.
   * @param videoPlayer underlying HLS video player.
   * @param adUiContainer ViewGroup in which to display the ad's UI.
   */
  public SampleAdsWrapper(Context context, SampleVideoPlayer videoPlayer, ViewGroup adUiContainer) {
    this.videoPlayer = videoPlayer;
    this.context = context;
    this.adUiContainer = adUiContainer;
    sdkFactory = ImaSdkFactory.getInstance();
    playerCallbacks = new ArrayList<>();
    createAdsLoader();
  }

  private void enableWebViewDebugging() {
    WebView.setWebContentsDebuggingEnabled(true);
  }

  private void createAdsLoader() {
    enableWebViewDebugging();
    VideoStreamPlayer videoStreamPlayer = createVideoStreamPlayer();
    StreamDisplayContainer displayContainer =
        ImaSdkFactory.createStreamDisplayContainer(adUiContainer, videoStreamPlayer);
    videoPlayer.setSampleVideoPlayerCallback(createSampleVideoPlayerCallback());
    adsLoader =
        sdkFactory.createAdsLoader(context, MyActivity.getImaSdkSettings(), displayContainer);
  }

  public void requestAndPlayAds() {
    adsLoader.addAdErrorListener(this);
    adsLoader.addAdsLoadedListener(this);
    StreamRequest request = buildStreamRequest();
    CustomUiOptions customUiOptions = ImaSdkFactory.createCustomUiOptions();
    customUiOptions.setSkippableSupport(true);
    customUiOptions.setAboutThisAdSupport(true);
    request.setCustomUiOptions(customUiOptions);
    adsLoader.requestStream(request);
  }

  // [END sample_ads_wrapper_initialization]

  // [START create_sample_video_player_callback]
  private SampleVideoPlayerCallback createSampleVideoPlayerCallback() {
    return new SampleVideoPlayerCallback() {
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
    };
  }

  // [END create_sample_video_player_callback]

  // [START build_stream_request]
  @Nullable
  private StreamRequest buildStreamRequest() {
    StreamRequest request;
    switch (CONTENT_TYPE) {
      case LIVE_HLS:
        // Live HLS stream request.
        return sdkFactory.createLiveStreamRequest(TEST_HLS_ASSET_KEY, null, NETWORK_CODE);
      case LIVE_DASH:
        // Live DASH stream request.
        return sdkFactory.createLiveStreamRequest(TEST_DASH_ASSET_KEY, null, NETWORK_CODE);
      case VOD_HLS:
        // VOD HLS request.
        request =
            sdkFactory.createVodStreamRequest(
                TEST_HLS_CONTENT_SOURCE_ID, TEST_HLS_VIDEO_ID, null, NETWORK_CODE);
        request.setFormat(StreamFormat.HLS);
        return request;
      case VOD_DASH:
        // VOD DASH request.
        request =
            sdkFactory.createVodStreamRequest(
                TEST_DASH_CONTENT_SOURCE_ID, TEST_DASH_VIDEO_ID, null, NETWORK_CODE);
        request.setFormat(StreamFormat.DASH);
        return request;
    }
    // Content type not selected.
    return null;
  }

  // [END build_stream_request]

  // [START create_video_stream_player]
  private VideoStreamPlayer createVideoStreamPlayer() {
    return new VideoStreamPlayer() {
      @Override
      public void loadUrl(String url, List<HashMap<String, String>> subtitles) {
        videoPlayer.setStreamUrl(url);
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
        log("Ad Break Started\n");
      }

      @Override
      public void onAdBreakEnded() {
        // Re-enable player controls.
        if (videoPlayer != null) {
          videoPlayer.enableControls(true);
        }
        log("Ad Break Ended\n");
      }

      @Override
      public void onAdPeriodStarted() {
        log("Ad Period Started\n");
      }

      @Override
      public void onAdPeriodEnded() {
        log("Ad Period Ended\n");
      }

      @Override
      public void seek(long timeMs) {
        // An ad was skipped. Skip to the content time.
        videoPlayer.seekTo(timeMs);
        log("seek");
      }

      @Override
      public VideoProgressUpdate getContentProgress() {
        return new VideoProgressUpdate(
            videoPlayer.getCurrentPositionMs(), videoPlayer.getDuration());
      }
    };
  }

  // [END create_video_stream_player]

  // [START ad_listeners]
  /** AdErrorListener implementation */
  @Override
  public void onAdError(AdErrorEvent event) {
    log(String.format("Error: %s\n", event.getError().getMessage()));
    // play fallback URL.
    log("Playing fallback Url\n");
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
        if (customUiManager != null) {
          customUiManager.onProgress(event.getAdProgressInfo());
        }
        break;
      case SHOW_AD_UI:
        log("Event: SHOW_AD_UI\n");
        CustomUi customUi = event.getCustomUi();
        if (customUi != null) {
          customUiManager =
              new CustomUiManager(context, adUiContainer, customUi, event.getAd(), logger);
          customUiManager.render();
        }
        break;
      case HIDE_AD_UI:
        log("Event: HIDE_AD_UI\n");
        if (customUiManager != null) {
          customUiManager.dispose();
        }
        break;
      default:
        log(String.format("Event: %s\n", event.getType()));
        break;
    }
  }

  /** AdsLoadedListener implementation */
  @Override
  public void onAdsManagerLoaded(AdsManagerLoadedEvent event) {
    streamManager = event.getStreamManager();
    streamManager.addAdErrorListener(this);
    streamManager.addAdEventListener(this);
    streamManager.init();
  }

  /** Sets fallback URL in case ads stream fails. */
  void setFallbackUrl(String url) {
    fallbackUrl = url;
  }

  // [END ad_listeners]

  // [START app_logging]
  /** Sets logger for displaying events to screen. Optional. */
  void setLogger(Logger logger) {
    this.logger = logger;
  }

  private void log(String message) {
    if (logger != null) {
      logger.log(message);
    }
  }
  // [END app_logging]
}
