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

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;
import androidx.media3.common.C;
import androidx.media3.common.ForwardingPlayer;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Metadata;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.extractor.metadata.emsg.EventMessage;
import androidx.media3.extractor.metadata.id3.TextInformationFrame;
import androidx.media3.ui.PlayerView;

/** A video player that plays HLS or DASH streams using ExoPlayer. */
@SuppressLint("UnsafeOptInUsageError")
/* @SuppressLint is needed for new media3 APIs. */
public class SampleVideoPlayer {

  private static final String LOG_TAG = "SampleVideoPlayer";

  /** Video player callback to be called when TXXX ID3 tag is received or seeking occurs. */
  public interface SampleVideoPlayerCallback {
    void onUserTextReceived(String userText);

    void onSeek(int windowIndex, long positionMs);
  }

  private final Context context;
  private final PlayerView playerView;

  private ExoPlayer player;
  private SampleVideoPlayerCallback playerCallback;

  @C.ContentType private int currentlyPlayingStreamType = C.CONTENT_TYPE_OTHER;

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

    DataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(context);
    DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(dataSourceFactory);
    player = new ExoPlayer.Builder(context).setMediaSourceFactory(mediaSourceFactory).build();
    playerView.setPlayer(
        new ForwardingPlayer(player) {
          @Override
          public void seekToDefaultPosition() {
            seekToDefaultPosition(getCurrentMediaItemIndex());
          }

          @Override
          public void seekToDefaultPosition(int windowIndex) {
            seekTo(windowIndex, /* positionMs= */ C.TIME_UNSET);
          }

          @Override
          public void seekTo(long positionMs) {
            seekTo(getCurrentMediaItemIndex(), positionMs);
          }

          @Override
          public void seekTo(int windowIndex, long positionMs) {
            if (canSeek) {
              if (playerCallback != null) {
                playerCallback.onSeek(windowIndex, positionMs);
              } else {
                super.seekTo(windowIndex, positionMs);
              }
            }
          }
        });

    // Register for ID3 events.
    player.addListener(
        new Player.Listener() {
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
  }

  public void play() {
    if (streamRequested) {
      // Stream requested, just resume.
      player.setPlayWhenReady(true);
      return;
    }
    initPlayer();

    player.setMediaItem(MediaItem.fromUri(streamUrl));
    player.prepare();
    player.setPlayWhenReady(true);
    streamRequested = true;
  }

  public void pause() {
    player.setPlayWhenReady(false);
  }

  public void seekTo(long positionMs) {
    player.seekTo(positionMs);
  }

  public void seekTo(int windowIndex, long positionMs) {
    player.seekTo(windowIndex, positionMs);
  }

  public void release() {
    if (player != null) {
      player.release();
      player = null;
      streamRequested = false;
    }
    playerView.setPlayer(null);
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
    if (player == null) {
      return 0;
    }
    Timeline currentTimeline = player.getCurrentTimeline();
    if (currentTimeline.isEmpty()) {
      return player.getCurrentPosition();
    }
    Timeline.Window window = new Timeline.Window();
    player.getCurrentTimeline().getWindow(player.getCurrentMediaItemIndex(), window);
    if (window.isLive()) {
      return player.getCurrentPosition() + window.windowStartTimeMs;
    } else {
      return player.getCurrentPosition();
    }
  }

  public long getDuration() {
    if (player == null) {
      return 0;
    }

    return player.getDuration();
  }
}
