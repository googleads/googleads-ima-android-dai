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

package com.google.ads.interactivemedia.v3.samples.samplehlsvideoplayer;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.MetadataRenderer.Output;
import com.google.android.exoplayer2.metadata.id3.TextInformationFrame;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.PlaybackControlView.ControlDispatcher;
import com.google.android.exoplayer2.ui.SimpleExoPlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;

/**
 * A video player that plays HLS streams; uses ExoPlayer.
 */
public class SampleHlsVideoPlayer {

    private static final String LOG_TAG = "HlsPlayer";
    private static final String USER_AGENT = "ImaSampleHlsPlayer (Linux;Android "
            + Build.VERSION.RELEASE + ") ImaSample/1.0";

    /**
     * Video player callback to be called when TXXX ID3 tag is received or seeking occurs.
     */
    public interface SampleHlsVideoPlayerCallback {
        void onUserTextReceived(String userText);
        void onSeek(int windowIndex, long positionMs);
    }

    private Context mContext;

    private SimpleExoPlayer mPlayer;
    private SimpleExoPlayerView mPlayerView;
    private SampleHlsVideoPlayerCallback mPlayerCallback;

    private Timeline.Period mPeriod = new Timeline.Period();

    private String mStreamUrl;
    private Boolean mIsStreamRequested;
    private boolean mCanSeek;

    public SampleHlsVideoPlayer(Context context, SimpleExoPlayerView playerView) {
        mContext = context;
        mPlayerView = playerView;
        mIsStreamRequested = false;
        mCanSeek = true;
    }

    private void initPlayer() {
        release();

        DefaultTrackSelector trackSelector = new DefaultTrackSelector();
        DefaultTrackSelector.Parameters params =
                new DefaultTrackSelector.Parameters().withPreferredTextLanguage("en");
        trackSelector.setParameters(params);


        mPlayer = ExoPlayerFactory.newSimpleInstance(new DefaultRenderersFactory(mContext),
                trackSelector, new DefaultLoadControl());
        mPlayerView.setPlayer(mPlayer);
        mPlayerView.setControlDispatcher(new ControlDispatcher() {
            @Override
            public boolean dispatchSetPlayWhenReady(ExoPlayer player, boolean playWhenReady) {
                player.setPlayWhenReady(playWhenReady);
                return true;
            }

            @Override
            public boolean dispatchSeekTo(ExoPlayer player, int windowIndex, long positionMs) {
                if (mCanSeek) {
                    if (mPlayerCallback != null) {
                        mPlayerCallback.onSeek(windowIndex, positionMs);
                    } else {
                        player.seekTo(windowIndex, positionMs);
                    }
                }
                return true;
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
        MediaSource hlsSource =
                new HlsMediaSource(Uri.parse(mStreamUrl), dataSourceFactory, new Handler(), null);
        mPlayer.prepare(hlsSource);

        // Register for ID3 events.
        mPlayer.setMetadataOutput(new Output() {
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
        this.seekTo(mPlayer.getCurrentWindowIndex(), positionMs);
    }

    public void seekTo(int windowIndex, long positionMs) {
        mPlayer.seekTo(windowIndex, positionMs);
    }

    public void release() {
        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
            mIsStreamRequested = false;
        }
    }

    public void setStreamUrl(String streamUrl) {
        mStreamUrl = streamUrl;
        mIsStreamRequested = false; //request new stream on play
    }

    public void enableControls(boolean doEnable) {
        if (doEnable) {
            mPlayerView.showController();
        } else {
            mPlayerView.hideController();
        }
    }

    public void setCanSeek(boolean canSeek) {
        mCanSeek = canSeek;
    }

    public boolean getCanSeek() {
        return mCanSeek;
    }

    public boolean isPlaying() {
        return mPlayer.getPlayWhenReady();
    }

    public boolean isStreamRequested() {
        return mIsStreamRequested;
    }

    // Methods for exposing player information.
    public void setSampleHlsVideoPlayerCallback(SampleHlsVideoPlayerCallback callback) {
        mPlayerCallback = callback;
    }

    public long getCurrentPositionPeriod() {
        // Adjust position to be relative to start of period rather than window, to account for DVR
        // window.
        long position = mPlayer.getCurrentPosition();
        Timeline currentTimeline = mPlayer.getCurrentTimeline();
        if (!currentTimeline.isEmpty()) {
            position -= currentTimeline.getPeriod(mPlayer.getCurrentPeriodIndex(), mPeriod)
                    .getPositionInWindowMs();
        }
        return position;
    }

    public long getDuration() {
        return mPlayer.getDuration();
    }
}
