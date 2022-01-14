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
import android.util.Log;
import com.google.ads.interactivemedia.v3.api.player.VideoStreamPlayer;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ForwardingPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.emsg.EventMessage;
import com.google.android.exoplayer2.metadata.id3.TextInformationFrame;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.util.Util;

/** A video player that plays HLS or DASH streams using ExoPlayer. */
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
  private final StyledPlayerView playerView;
  private SampleVideoPlayerCallback playerCallback;

  @C.ContentType private int currentlyPlayingStreamType = C.TYPE_OTHER;

  private String streamUrl;
  private Boolean streamRequested;
  private boolean canSeek;

  public SampleVideoPlayer(Context context, StyledPlayerView playerView) {
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

    player.setMediaSource(mediaSource);
    player.prepare();

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
