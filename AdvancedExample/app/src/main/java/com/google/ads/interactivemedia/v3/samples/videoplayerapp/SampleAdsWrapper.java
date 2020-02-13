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

/**
 * This class adds ad-serving support to Sample HlsVideoPlayer
 */
public class SampleAdsWrapper implements AdEvent.AdEventListener, AdErrorEvent.AdErrorListener,
        AdsLoader.AdsLoadedListener {

    private static final String PLAYER_TYPE = "DAISamplePlayer";

    /**
     * Log interface, so we can output the log commands to the UI or similar.
     */
    public interface Logger {
        void log(String logMessage);
    }

    private ImaSdkFactory mSdkFactory;
    private AdsLoader mAdsLoader;
    private StreamManager mStreamManager;
    private StreamDisplayContainer mDisplayContainer;
    private List<VideoStreamPlayer.VideoStreamPlayerCallback> mPlayerCallbacks;

    private SampleVideoPlayer mVideoPlayer;
    private Context mContext;
    private ViewGroup mAdUiContainer;

    private double mBookMarkContentTime; // Bookmarked content time, in seconds.
    private double mSnapBackTime; // Stream time to snap back to, in seconds.
    private boolean mAdsRequested;
    private String mFallbackUrl;
    private Logger mLogger;

    /**
     * Creates a new SampleAdsWrapper that implements IMA direct-ad-insertion.
     *
     * @param context       the app's context.
     * @param videoPlayer   underlying HLS video player.
     * @param adUiContainer ViewGroup in which to display the ad's UI.
     */
    public SampleAdsWrapper(Context context, SampleVideoPlayer videoPlayer,
                            ViewGroup adUiContainer) {
        mVideoPlayer = videoPlayer;
        mContext = context;
        mAdUiContainer = adUiContainer;
        mSdkFactory = ImaSdkFactory.getInstance();
        mPlayerCallbacks = new ArrayList<>();
        createAdsLoader();
    }

    private void createAdsLoader() {
        ImaSdkSettings settings = mSdkFactory.createImaSdkSettings();
        // Change any settings as necessary here.
        settings.setPlayerType(PLAYER_TYPE);
        VideoStreamPlayer videoStreamPlayer = createVideoStreamPlayer();
        mDisplayContainer = ImaSdkFactory.createStreamDisplayContainer(
            mAdUiContainer,
            videoStreamPlayer
        );
        mVideoPlayer.setSampleVideoPlayerCallback(
                new SampleVideoPlayer.SampleVideoPlayerCallback() {
                    @Override
                    public void onUserTextReceived(String userText) {
                        for (VideoStreamPlayer.VideoStreamPlayerCallback callback :
                                mPlayerCallbacks) {
                            callback.onUserTextReceived(userText);
                        }
                    }

                    @Override
                    public void onSeek(int windowIndex, long positionMs) {
                        double timeToSeek = positionMs;
                        if (mStreamManager != null) {
                            CuePoint cuePoint = mStreamManager.getPreviousCuePointForStreamTime(
                                    positionMs / 1000);
                            double bookMarkStreamTime = mStreamManager.getStreamTimeForContentTime(
                                    mBookMarkContentTime);
                            if (cuePoint != null && !cuePoint.isPlayed()
                                    && cuePoint.getEndTime() > bookMarkStreamTime) {
                                mSnapBackTime = timeToSeek / 1000.0; // Update snap back time.
                                // Missed cue point, so snap back to the beginning of cue point.
                                timeToSeek = cuePoint.getStartTime() * 1000;
                                Log.i("IMA", "SnapBack to " + timeToSeek);
                                mVideoPlayer.seekTo(windowIndex, Math.round(timeToSeek));
                                mVideoPlayer.setCanSeek(false);

                                return;
                            }
                        }
                        mVideoPlayer.seekTo(windowIndex, Math.round(timeToSeek));
                    }
                });
        mAdsLoader = mSdkFactory.createAdsLoader(mContext, settings, mDisplayContainer);
    }

    public void requestAndPlayAds(VideoListFragment.VideoListItem videoListItem,
                                  double bookMarkTime) {

        mBookMarkContentTime = bookMarkTime;
        mAdsLoader.addAdErrorListener(this);
        mAdsLoader.addAdsLoadedListener(this);
        mAdsLoader.requestStream(buildStreamRequest(videoListItem));
        mAdsRequested = true;
    }

    private StreamRequest buildStreamRequest(VideoListFragment.VideoListItem videoListItem) {
        // Set the license URL.
        mVideoPlayer.setLicenseUrl(videoListItem.getLicenseUrl());

        StreamRequest request;
        // Live stream request.
        if (videoListItem.getAssetKey() != null) {
            request = mSdkFactory.createLiveStreamRequest(
                    videoListItem.getAssetKey(), videoListItem.getApiKey());
        } else { // VOD request.
            request = mSdkFactory.createVodStreamRequest(videoListItem.getContentSourceId(),
                    videoListItem.getVideoId(), videoListItem.getApiKey());
        }
        // Set the stream format (HLS or DASH).
        request.setFormat(videoListItem.getStreamFormat());

        return request;
    }

    private VideoStreamPlayer createVideoStreamPlayer() {
        return new VideoStreamPlayer() {
            @Override
            public void loadUrl(String url, List<HashMap<String, String>> subtitles) {
                mVideoPlayer.setStreamUrl(url);
                mVideoPlayer.play();

                // Bookmarking
                if (mBookMarkContentTime > 0) {
                    double streamTime =
                            mStreamManager.getStreamTimeForContentTime(mBookMarkContentTime);
                    mVideoPlayer.seekTo((long) (streamTime * 1000.0)); // s to ms.
                }
            }

            @Override
            public int getVolume() {
                // Make the video player play at the current device volume.
                return 100;
            }

            @Override
            public void addCallback(VideoStreamPlayerCallback videoStreamPlayerCallback) {
                mPlayerCallbacks.add(videoStreamPlayerCallback);
            }

            @Override
            public void removeCallback(VideoStreamPlayerCallback videoStreamPlayerCallback) {
                mPlayerCallbacks.remove(videoStreamPlayerCallback);
            }

            @Override
            public void onAdBreakStarted() {
                // Disable player controls.
                mVideoPlayer.setCanSeek(false);
                mVideoPlayer.enableControls(false);
                log("Ad Break Started\n");
            }

            @Override
            public void onAdBreakEnded() {
                // Re-enable player controls.
                mVideoPlayer.setCanSeek(true);
                mVideoPlayer.enableControls(true);
                if (mSnapBackTime > 0) {
                    Log.i("IMA", "SampleAdsWrapper seeking " + mSnapBackTime);
                    mVideoPlayer.seekTo(Math.round(mSnapBackTime * 1000));
                }
                mSnapBackTime = 0;
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
                mVideoPlayer.seekTo(timeMs);
            }

            @Override
            public VideoProgressUpdate getContentProgress() {
                if (mVideoPlayer == null) {
                    return VideoProgressUpdate.VIDEO_TIME_NOT_READY;
                }
                return new VideoProgressUpdate(
                        mVideoPlayer.getCurrentPositionPeriod(), mVideoPlayer.getDuration());
            }
        };
    }

    public double getContentTime() {
        if (mStreamManager != null) {
            return mStreamManager.getContentTimeForStreamTime(
                    mVideoPlayer.getCurrentPositionPeriod() / 1000.0);
        }
        return 0.0;
    }

    public double getStreamTimeForContentTime(double contentTime) {
        if (mStreamManager != null) {
            return mStreamManager.getStreamTimeForContentTime(contentTime);
        }
        return 0.0;
    }

    public void setSnapBackTime(double snapBackTime) {
        mSnapBackTime = snapBackTime;
    }

    public boolean getAdsRequested() {
        return mAdsRequested;
    }

    /**
     * AdErrorListener implementation
     **/
    @Override
    public void onAdError(AdErrorEvent event) {
        log(String.format("Error: %s\n", event.getError().getMessage()));
        // play fallback URL.
        log("Playing fallback Url\n");
        mVideoPlayer.setStreamUrl(mFallbackUrl);
        mVideoPlayer.play();
    }

    /**
     * AdEventListener implementation
     **/
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

    /**
     * AdsLoadedListener implementation
     **/
    @Override
    public void onAdsManagerLoaded(AdsManagerLoadedEvent event) {
        mStreamManager = event.getStreamManager();
        mStreamManager.addAdErrorListener(this);
        mStreamManager.addAdEventListener(this);
        mStreamManager.init();
    }

    /**
     * Sets fallback URL in case ads stream fails.
     **/
    public void setFallbackUrl(String url) {
        mFallbackUrl = url;
    }

    /**
     * Sets logger for displaying events to screen. Optional.
     **/
    public void setLogger(Logger logger) {
        mLogger = logger;
    }

    private void log(String message) {
        if (mLogger != null) {
            mLogger.log(message);
        }
    }

    public void release() {
        if (mStreamManager != null) {
            mStreamManager.destroy();
            mStreamManager = null;
        }

        if (mVideoPlayer != null) {
            mVideoPlayer.release();
            mVideoPlayer = null;
        }

        if (mDisplayContainer != null) {
            mDisplayContainer.destroy();
            mDisplayContainer = null;
        }

        mAdsRequested = false;
    }
}
