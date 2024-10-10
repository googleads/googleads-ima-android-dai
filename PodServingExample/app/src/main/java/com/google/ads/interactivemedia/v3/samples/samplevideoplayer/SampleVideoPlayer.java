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

package com.google.ads.interactivemedia.v3.samples.samplevideoplayer;

import static androidx.media3.common.C.CONTENT_TYPE_DASH;
import static androidx.media3.common.C.CONTENT_TYPE_HLS;
import static androidx.media3.common.C.TIME_UNSET;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.util.Log;
import androidx.media3.common.ForwardingPlayer;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Metadata;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.dash.DefaultDashChunkSource;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.extractor.metadata.emsg.EventMessage;
import androidx.media3.extractor.metadata.id3.TextInformationFrame;
import androidx.media3.ui.PlayerView;
import com.google.ads.interactivemedia.v3.api.player.VideoStreamPlayer;

/** A video player that plays HLS or DASH streams using ExoPlayer. */
@SuppressLint("UnsafeOptInUsageError")
/* @SuppressLint is needed for new media3 APIs. */
public class SampleVideoPlayer {

  private static final String LOG_TAG = "SampleVideoPlayer";

  /**
   * Video player callback interface that extends IMA's VideoStreamPlayerCallback by adding the
   * onSeek() callback to support ad snapback.
   */
  public interface SampleVideoPlayerCallback extends VideoStreamPlayer.VideoStreamPlayerCallback {
    void onSeek(int windowIndex, long positionMs);
  }

  private final Context context;

  private ExoPlayer player;
  private final PlayerView playerView;
  private SampleVideoPlayerCallback playerCallback;
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

    player = new ExoPlayer.Builder(context).build();
    playerView.setPlayer(
        new ForwardingPlayer(player) {
          @Override
          public void seekToDefaultPosition() {
            seekToDefaultPosition(getCurrentMediaItemIndex());
          }

          @Override
          public void seekToDefaultPosition(int windowIndex) {
            seekTo(windowIndex, /* positionMs= */ TIME_UNSET);
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
  }

  public void play() {
    if (streamRequested) {
      // Stream requested, just resume.
      player.setPlayWhenReady(true);
      if (playerCallback != null) {
        playerCallback.onResume();
      }
      return;
    }
    initPlayer();

    DataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(context);

    // Create the MediaItem to play, specifying the content URI.
    Uri contentUri = Uri.parse(streamUrl);
    MediaItem mediaItem = new MediaItem.Builder().setUri(contentUri).build();

    MediaSource mediaSource;
    switch (Util.inferContentType(Uri.parse(streamUrl))) {
      case CONTENT_TYPE_HLS:
        mediaSource = new HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem);
        break;
      case CONTENT_TYPE_DASH:
        mediaSource =
            new DashMediaSource.Factory(
                    new DefaultDashChunkSource.Factory(dataSourceFactory), dataSourceFactory)
                .createMediaSource(mediaItem);
        break;
      default:
        throw new UnsupportedOperationException("Unknown stream type.");
    }

    player.setMediaSource(mediaSource);
    player.prepare();

    // Register for ID3 events.
    player.addListener(
        new Player.Listener() {
          @Override
          public void onMetadata(Metadata metadata) {
            for (int i = 0; i < metadata.length(); i++) {
              Metadata.Entry entry = metadata.get(i);
              if (entry instanceof TextInformationFrame textFrame) {
                if ("TXXX".equals(textFrame.id)) {
                  Log.d(LOG_TAG, "Received user text: " + textFrame.values.get(0));
                  if (playerCallback != null) {
                    playerCallback.onUserTextReceived(textFrame.values.get(0));
                  }
                }
              } else if (entry instanceof EventMessage eventMessage) {
                String eventMessageValue = new String(eventMessage.messageData);
                Log.d(LOG_TAG, "Received user text: " + eventMessageValue);
                if (playerCallback != null) {
                  playerCallback.onUserTextReceived(eventMessageValue);
                }
              }
            }
          }
        });

    player.setPlayWhenReady(true);
    streamRequested = true;
  }

  public void pause() {
    player.setPlayWhenReady(false);
    if (playerCallback != null) {
      playerCallback.onPause();
    }
  }

  public void seekTo(long positionMs) {
    player.seekTo(positionMs);
  }

  public void seekTo(int windowIndex, long positionMs) {
    player.seekTo(windowIndex, positionMs);
  }

  private void release() {
    if (player != null) {
      player.release();
      player = null;
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

  public void setVolume(int percentage) {
    player.setVolume(percentage);
  }
}
