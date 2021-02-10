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

package com.google.ads.interactivemedia.v3.samples.samplevideoplayer;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ControlDispatcher;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.MetadataOutput;
import com.google.android.exoplayer2.metadata.emsg.EventMessage;
import com.google.android.exoplayer2.metadata.id3.TextInformationFrame;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;

/** A video player that plays HLS or DASH streams using ExoPlayer. */
public class SampleVideoPlayer {

  private static final String LOG_TAG = "SampleVideoPlayer";
  private static final String USER_AGENT =
      "ImaSamplePlayer (Linux;Android " + Build.VERSION.RELEASE + ") ImaSample/1.0";

  /** Video player callback to be called when TXXX ID3 tag is received or seeking occurs. */
  public interface SampleVideoPlayerCallback {
    void onUserTextReceived(String userText);

    void onSeek(int windowIndex, long positionMs);
  }

  private final Context context;
  private final PlayerView playerView;

  private SimpleExoPlayer simpleExoPlayer;
  private SampleVideoPlayerCallback playerCallback;

  @C.ContentType private int currentlyPlayingStreamType = C.TYPE_OTHER;

  private String streamUrl;
  private Boolean streamRequested;
  private boolean canSeek;

  public SampleVideoPlayer(Context context, PlayerView playerView) {
    this.context = context;
    this.playerView = playerView;
    streamRequested = false;
    canSeek = true;
  }

  private void initPlayer() {
    release();

    simpleExoPlayer = new SimpleExoPlayer.Builder(context).build();
    playerView.setPlayer(simpleExoPlayer);
    playerView.setControlDispatcher(
        new ControlDispatcher() {

          @Override
          public boolean dispatchSetPlayWhenReady(Player player, boolean playWhenReady) {
            player.setPlayWhenReady(playWhenReady);
            return true;
          }

          @Override
          public boolean isRewindEnabled() {
            return false;
          }

          @Override
          public boolean isFastForwardEnabled() {
            return false;
          }

          @Override
          public boolean dispatchFastForward(Player player) {
            return false;
          }

          @Override
          public boolean dispatchRewind(Player player) {
            return false;
          }

          @Override
          public boolean dispatchNext(Player player) {
            return false;
          }

          @Override
          public boolean dispatchPrevious(Player player) {
            return false;
          }

          @Override
          public boolean dispatchSeekTo(Player player, int windowIndex, long positionMs) {
            if (canSeek) {
              if (playerCallback != null) {
                playerCallback.onSeek(windowIndex, positionMs);
              } else {
                player.seekTo(windowIndex, positionMs);
              }
            }
            return true;
          }

          @Override
          public boolean dispatchSetRepeatMode(Player player, int repeatMode) {
            return false;
          }

          @Override
          public boolean dispatchSetShuffleModeEnabled(Player player, boolean shuffleModeEnabled) {
            return false;
          }

          @Override
          public boolean dispatchStop(Player player, boolean reset) {
            return false;
          }
        });
  }

  public void play() {
    if (streamRequested) {
      // Stream requested, just resume.
      simpleExoPlayer.setPlayWhenReady(true);
      return;
    }
    initPlayer();

    DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(context, USER_AGENT);
    DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(dataSourceFactory);
    mediaSourceFactory.setAdViewProvider(playerView);

    // Create the MediaItem to play, specifying the content URI.
    Uri contentUri = Uri.parse(streamUrl);
    MediaItem mediaItem = new MediaItem.Builder().setUri(contentUri).build();

    MediaSource mediaSource;
    currentlyPlayingStreamType = Util.inferContentType(Uri.parse(streamUrl));
    switch (currentlyPlayingStreamType) {
      case C.TYPE_HLS:
        mediaSource = new HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem);
        break;
      case C.TYPE_DASH:
        mediaSource =
            new DashMediaSource.Factory(
                    new DefaultDashChunkSource.Factory(dataSourceFactory), dataSourceFactory)
                .createMediaSource(mediaItem);
        break;
      default:
        throw new UnsupportedOperationException("Unknown stream type.");
    }

    simpleExoPlayer.setMediaSource(mediaSource);
    simpleExoPlayer.prepare();

    // Register for ID3 events.
    simpleExoPlayer.addMetadataOutput(
        new MetadataOutput() {
          @Override
          public void onMetadata(Metadata metadata) {
            for (int i = 0; i < metadata.length(); i++) {
              Metadata.Entry entry = metadata.get(i);
              if (entry instanceof TextInformationFrame) {
                TextInformationFrame textFrame = (TextInformationFrame) entry;
                if ("TXXX".equals(textFrame.id)) {
                  Log.d(LOG_TAG, "Received user text: " + textFrame.value);
                  if (playerCallback != null) {
                    playerCallback.onUserTextReceived(textFrame.value);
                  }
                }
              } else if (entry instanceof EventMessage) {
                EventMessage eventMessage = (EventMessage) entry;
                String eventMessageValue = new String(eventMessage.messageData);
                Log.d(LOG_TAG, "Received user text: " + eventMessageValue);
                if (playerCallback != null) {
                  playerCallback.onUserTextReceived(eventMessageValue);
                }
              }
            }
          }
        });

    simpleExoPlayer.setPlayWhenReady(true);
    streamRequested = true;
  }

  public void pause() {
    simpleExoPlayer.setPlayWhenReady(false);
  }

  public void seekTo(long positionMs) {
    simpleExoPlayer.seekTo(positionMs);
  }

  public void seekTo(int windowIndex, long positionMs) {
    simpleExoPlayer.seekTo(windowIndex, positionMs);
  }

  private void release() {
    if (simpleExoPlayer != null) {
      simpleExoPlayer.release();
      simpleExoPlayer = null;
      streamRequested = false;
    }
  }

  public void setStreamUrl(String streamUrl) {
    this.streamUrl = streamUrl;
    streamRequested = false; // request new stream on play
  }

  public void enableControls(boolean doEnable) {
    if (doEnable) {
      playerView.showController();
    } else {
      playerView.hideController();
    }
    canSeek = doEnable;
  }

  public boolean isStreamRequested() {
    return streamRequested;
  }

  // Methods for exposing player information.
  public void setSampleVideoPlayerCallback(SampleVideoPlayerCallback callback) {
    playerCallback = callback;
  }

  /** Returns current offset position of the playhead in milliseconds for DASH and HLS stream. */
  public long getCurrentPositionMs() {
    if (simpleExoPlayer == null) {
      return 0;
    }
    Timeline currentTimeline = simpleExoPlayer.getCurrentTimeline();
    if (currentTimeline.isEmpty()) {
      return simpleExoPlayer.getCurrentPosition();
    }
    Timeline.Window window = new Timeline.Window();
    simpleExoPlayer.getCurrentTimeline().getWindow(simpleExoPlayer.getCurrentWindowIndex(), window);
    if (window.isLive) {
      return simpleExoPlayer.getCurrentPosition() + window.windowStartTimeMs;
    } else {
      return simpleExoPlayer.getCurrentPosition();
    }
  }

  public long getDuration() {
    if (simpleExoPlayer == null) {
      return 0;
    }

    return simpleExoPlayer.getDuration();
  }
}
