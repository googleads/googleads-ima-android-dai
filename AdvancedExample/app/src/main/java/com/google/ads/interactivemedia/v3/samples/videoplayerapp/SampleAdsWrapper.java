/*
 * Copyright 2016 Google, Inc.
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

import android.content.Context;
import android.util.Log;
import android.view.ViewGroup;
import com.google.ads.interactivemedia.v3.api.AdErrorEvent;
import com.google.ads.interactivemedia.v3.api.AdEvent;
import com.google.ads.interactivemedia.v3.api.AdsLoader;
import com.google.ads.interactivemedia.v3.api.AdsManagerLoadedEvent;
import com.google.ads.interactivemedia.v3.api.CuePoint;
import com.google.ads.interactivemedia.v3.api.ImaSdkFactory;
import com.google.ads.interactivemedia.v3.api.ImaSdkSettings;
import com.google.ads.interactivemedia.v3.api.StreamDisplayContainer;
import com.google.ads.interactivemedia.v3.api.StreamManager;
import com.google.ads.interactivemedia.v3.api.StreamRequest;
import com.google.ads.interactivemedia.v3.api.player.VideoProgressUpdate;
import com.google.ads.interactivemedia.v3.api.player.VideoStreamPlayer;
import com.google.ads.interactivemedia.v3.samples.samplevideoplayer.SampleVideoPlayer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/** This class adds ad-serving support to Sample HlsVideoPlayer */
public class SampleAdsWrapper
    implements AdEvent.AdEventListener, AdErrorEvent.AdErrorListener, AdsLoader.AdsLoadedListener {

  private static final String PLAYER_TYPE = "DAISamplePlayer";

  /** Log interface, so we can output the log commands to the UI or similar. */
  public interface Logger {
    void log(String logMessage);
  }

  private final ImaSdkFactory sdkFactory;
  private AdsLoader adsLoader;
  private StreamManager streamManager;
  private final List<VideoStreamPlayer.VideoStreamPlayerCallback> playerCallbacks;

  private SampleVideoPlayer videoPlayer;
  private final Context context;
  private final ViewGroup adUiContainer;

  private long bookMarkContentTimeMs; // Bookmarked content time, in milliseconds.
  private long snapBackTimeMs; // Stream time to snap back to, in milliseconds.
  private boolean adsRequested;
  private String fallbackUrl;
  private Logger logger;

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

  private void createAdsLoader() {
    ImaSdkSettings settings = sdkFactory.createImaSdkSettings();
    // Change any settings as necessary here.
    settings.setPlayerType(PLAYER_TYPE);
    VideoStreamPlayer videoStreamPlayer = createVideoStreamPlayer();
    StreamDisplayContainer displayContainer =
        ImaSdkFactory.createStreamDisplayContainer(adUiContainer, videoStreamPlayer);
    videoPlayer.setSampleVideoPlayerCallback(
        new SampleVideoPlayer.SampleVideoPlayerCallback() {
          @Override
          public void onUserTextReceived(String userText) {
            for (VideoStreamPlayer.VideoStreamPlayerCallback callback : playerCallbacks) {
              callback.onUserTextReceived(userText);
            }
          }

          @Override
          public void onSeek(int windowIndex, long positionMs) {
            long timeToSeek = positionMs;
            if (streamManager != null) {
              CuePoint cuePoint = streamManager.getPreviousCuePointForStreamTimeMs(positionMs);
              long bookMarkStreamTimeMs =
                  streamManager.getStreamTimeMsForContentTimeMs(bookMarkContentTimeMs);
              if (cuePoint != null
                  && !cuePoint.isPlayed()
                  && cuePoint.getEndTimeMs() > bookMarkStreamTimeMs) {
                snapBackTimeMs = timeToSeek; // Update snap back time.
                // Missed cue point, so snap back to the beginning of cue point.
                timeToSeek = cuePoint.getStartTimeMs();
                Log.i("IMA", "SnapBack to " + timeToSeek + " ms.");
                videoPlayer.seekTo(windowIndex, Math.round(timeToSeek));
                videoPlayer.setCanSeek(false);

                return;
              }
            }
            videoPlayer.seekTo(windowIndex, Math.round(timeToSeek));
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
    adsLoader = sdkFactory.createAdsLoader(context, settings, displayContainer);
  }

  public void requestAndPlayAds(
      VideoListFragment.VideoListItem videoListItem, long bookMarkTimeMs) {

    bookMarkContentTimeMs = bookMarkTimeMs;
    adsLoader.addAdErrorListener(this);
    adsLoader.addAdsLoadedListener(this);
    adsLoader.requestStream(buildStreamRequest(videoListItem));
    adsRequested = true;
  }

  private StreamRequest buildStreamRequest(VideoListFragment.VideoListItem videoListItem) {
    // Set the license URL.
    videoPlayer.setLicenseUrl(videoListItem.getLicenseUrl());

    StreamRequest request;
    // Live stream request.
    if (videoListItem.getAssetKey() != null) {
      request =
          sdkFactory.createLiveStreamRequest(
              videoListItem.getAssetKey(), videoListItem.getApiKey());
    } else { // VOD request.
      request =
          sdkFactory.createVodStreamRequest(
              videoListItem.getContentSourceId(),
              videoListItem.getVideoId(),
              videoListItem.getApiKey());
    }
    // Set the stream format (HLS or DASH).
    request.setFormat(videoListItem.getStreamFormat());

    return request;
  }

  private VideoStreamPlayer createVideoStreamPlayer() {
    return new VideoStreamPlayer() {
      @Override
      public void loadUrl(String url, List<HashMap<String, String>> subtitles) {
        videoPlayer.setStreamUrl(url);
        videoPlayer.play();

        // Bookmarking
        if (bookMarkContentTimeMs > 0) {
          long streamTimeMs = streamManager.getStreamTimeMsForContentTimeMs(bookMarkContentTimeMs);
          videoPlayer.seekTo(streamTimeMs);
        }
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
        videoPlayer.setCanSeek(false);
        videoPlayer.enableControls(false);
        log("Ad Break Started\n");
      }

      @Override
      public void onAdBreakEnded() {
        // Re-enable player controls.
        if (videoPlayer != null) {
          videoPlayer.setCanSeek(true);
          videoPlayer.enableControls(true);
          if (snapBackTimeMs > 0) {
            Log.i("IMA", "SampleAdsWrapper seeking " + snapBackTimeMs + " ms.");
            videoPlayer.seekTo(Math.round(snapBackTimeMs));
          }
        }
        snapBackTimeMs = 0;
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
        log("Seek\n");
        videoPlayer.seekTo(timeMs);
      }

      @Override
      public VideoProgressUpdate getContentProgress() {
        if (videoPlayer == null) {
          return VideoProgressUpdate.VIDEO_TIME_NOT_READY;
        }

        return new VideoProgressUpdate(
            videoPlayer.getCurrentPositionMs(), videoPlayer.getDuration());
      }
    };
  }

  public long getContentTimeMs() {
    if (streamManager != null) {
      return streamManager.getContentTimeMsForStreamTimeMs(videoPlayer.getCurrentPositionMs());
    }
    return 0;
  }

  public long getStreamTimeMsForContentTimeMs(long contentTimeMs) {
    if (streamManager != null) {
      return streamManager.getStreamTimeMsForContentTimeMs(contentTimeMs);
    }
    return 0;
  }

  public void setSnapBackTime(long snapBackTimeMs) {
    this.snapBackTimeMs = snapBackTimeMs;
  }

  public boolean getAdsRequested() {
    return adsRequested;
  }

  /** AdErrorListener implementation */
  @Override
  public void onAdError(AdErrorEvent event) {
    log(String.format("Error: %s\n", event.getError().getMessage()));
    // play fallback URL.
    log("Playing fallback Url\n");
    videoPlayer.setStreamUrl(fallbackUrl);
    videoPlayer.play();
  }

  /** AdEventListener implementation */
  @Override
  public void onAdEvent(AdEvent event) {
    switch (event.getType()) {
      case AD_PROGRESS:
        break; // Do nothing
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
  public void setFallbackUrl(String url) {
    fallbackUrl = url;
  }

  /** Sets logger for displaying events to screen. Optional. */
  public void setLogger(Logger logger) {
    this.logger = logger;
  }

  private void log(String message) {
    if (logger != null) {
      logger.log(message);
    }
  }

  public void release() {
    if (streamManager != null) {
      streamManager.destroy();
      streamManager = null;
    }

    if (videoPlayer != null) {
      videoPlayer.release();
      videoPlayer = null;
    }

    adsLoader.release();

    adsRequested = false;
  }
}
