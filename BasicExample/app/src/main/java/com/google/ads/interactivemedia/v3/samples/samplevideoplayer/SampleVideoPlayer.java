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
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.Timeline.Period;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.MetadataOutput;
import com.google.android.exoplayer2.metadata.id3.TextInformationFrame;
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

  private Context mContext;

  private SimpleExoPlayer mPlayer;
  private PlayerView mPlayerView;
  private SampleVideoPlayerCallback mPlayerCallback;

  private Timeline.Period mPeriod = new Period();

  private String mStreamUrl;
  private Boolean mIsStreamRequested;
  private boolean mCanSeek;

  public SampleVideoPlayer(Context context, PlayerView playerView) {
    mContext = context;
    mPlayerView = playerView;
    mIsStreamRequested = false;
    mCanSeek = true;
  }

  private void initPlayer() {
    release();

    mPlayer = new SimpleExoPlayer.Builder(mContext).build();
    mPlayerView.setPlayer(mPlayer);
    mPlayerView.setControlDispatcher(
        new ControlDispatcher() {
          @Override
          public boolean dispatchSetPlayWhenReady(Player player, boolean playWhenReady) {
            player.setPlayWhenReady(playWhenReady);
            return true;
          }

          @Override
          public boolean dispatchSeekTo(Player player, int windowIndex, long positionMs) {
            if (mCanSeek) {
              if (mPlayerCallback != null) {
                mPlayerCallback.onSeek(windowIndex, positionMs);
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
    if (mIsStreamRequested) {
      // Stream requested, just resume.
      mPlayer.setPlayWhenReady(true);
      return;
    }
    initPlayer();

    DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(mContext, USER_AGENT);
    int type = Util.inferContentType(Uri.parse(mStreamUrl), null);
    MediaSource mediaSource;
    switch (type) {
      case C.TYPE_HLS:
        mediaSource =
            new HlsMediaSource.Factory(dataSourceFactory).createMediaSource(Uri.parse(mStreamUrl));
        break;
      case C.TYPE_DASH:
        mediaSource =
            new DashMediaSource.Factory(
                    new DefaultDashChunkSource.Factory(dataSourceFactory), dataSourceFactory)
                .createMediaSource(Uri.parse(mStreamUrl));
        break;
      default:
        Log.e(LOG_TAG, "Error! Invalid Media Source, exiting");
        return;
    }

    mPlayer.prepare(mediaSource);

    // Register for ID3 events.
    mPlayer.addMetadataOutput(
        new MetadataOutput() {
          @Override
          public void onMetadata(Metadata metadata) {
            for (int i = 0; i < metadata.length(); i++) {
              Metadata.Entry entry = metadata.get(i);
              if (entry instanceof TextInformationFrame) {
                TextInformationFrame textFrame = (TextInformationFrame) entry;
                if ("TXXX".equals(textFrame.id)) {
                  Log.d(LOG_TAG, "Received user text: " + textFrame.value);
                  if (mPlayerCallback != null) {
                    mPlayerCallback.onUserTextReceived(textFrame.value);
                  }
                }
              } else if (entry instanceof EventMessage) {
                EventMessage eventMessage = (EventMessage) entry;
                String eventMessageValue = new String(eventMessage.messageData);
                Log.d(LOG_TAG, "Received user text: " + eventMessageValue);
                if (mPlayerCallback != null) {
                  mPlayerCallback.onUserTextReceived(eventMessageValue);
                }
              }
            }
          }
        });

    mPlayer.setPlayWhenReady(true);
    mIsStreamRequested = true;
  }

  public void pause() {
    mPlayer.setPlayWhenReady(false);
  }

  public void seekTo(long positionMs) {
    mPlayer.seekTo(positionMs);
  }

  public void seekTo(int windowIndex, long positionMs) {
    mPlayer.seekTo(windowIndex, positionMs);
  }

  private void release() {
    if (mPlayer != null) {
      mPlayer.release();
      mPlayer = null;
      mIsStreamRequested = false;
    }
  }

  public void setStreamUrl(String streamUrl) {
    mStreamUrl = streamUrl;
    mIsStreamRequested = false; // request new stream on play
  }

  public void enableControls(boolean doEnable) {
    if (doEnable) {
      mPlayerView.showController();
    } else {
      mPlayerView.hideController();
    }
    mCanSeek = doEnable;
  }

  public boolean isStreamRequested() {
    return mIsStreamRequested;
  }

  // Methods for exposing player information.
  public void setSampleVideoPlayerCallback(SampleVideoPlayerCallback callback) {
    mPlayerCallback = callback;
  }

  public long getCurrentPositionPeriod() {
    // Adjust position to be relative to start of period rather than window, to account for DVR
    // window.
    long position = mPlayer.getCurrentPosition();
    Timeline currentTimeline = mPlayer.getCurrentTimeline();
    if (!currentTimeline.isEmpty()) {
      position -=
          currentTimeline
              .getPeriod(mPlayer.getCurrentPeriodIndex(), mPeriod)
              .getPositionInWindowMs();
    }
    return position;
  }

  public long getDuration() {
    return mPlayer.getDuration();
  }
}
