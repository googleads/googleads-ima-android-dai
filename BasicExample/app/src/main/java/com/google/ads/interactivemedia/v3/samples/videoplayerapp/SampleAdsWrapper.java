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

package com.google.ads.interactivemedia.v3.samples.videoplayerapp;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.view.ViewGroup;
import android.webkit.WebView;

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
import com.google.ads.interactivemedia.v3.samples.samplehlsvideoplayer.SampleHlsVideoPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * This class adds ad-serving support to Sample HlsVideoPlayer
 */
public class SampleAdsWrapper implements AdEvent.AdEventListener, AdErrorEvent.AdErrorListener,
        AdsLoader.AdsLoadedListener {

    // Live stream asset key.
    private static final String TEST_ASSET_KEY = "sN_IYUG8STe1ZzhIIE_ksA";

    // VOD content source and video IDs.
    private static final String TEST_CONTENT_SOURCE_ID = "19463";
    private static final String TEST_VIDEO_ID = "googleio-highlights";

    private static final String PLAYER_TYPE = "DAISamplePlayer";

    /**
     * Log interface, so we can output the log commands to the UI or similar.
     */
    public interface Logger {
        void log(String logMessage);
    }

    private ImaSdkFactory mSdkFactory;
    private AdsLoader mAdsLoader;
    private StreamDisplayContainer mDisplayContainer;
    private StreamManager mStreamManager;
    private List<VideoStreamPlayer.VideoStreamPlayerCallback> mPlayerCallbacks;

    private SampleHlsVideoPlayer mVideoPlayer;
    private Context mContext;
    private ViewGroup mAdUiContainer;

    private String mFallbackUrl;
    private Logger mLogger;

    /**
     * Creates a new SampleAdsWrapper that implements IMA direct-ad-insertion.
     * @param context the app's context.
     * @param videoPlayer underlying HLS video player.
     * @param adUiContainer ViewGroup in which to display the ad's UI.
     */
    public SampleAdsWrapper(Context context, SampleHlsVideoPlayer videoPlayer,
                            ViewGroup adUiContainer) {
        mVideoPlayer = videoPlayer;
        mContext = context;
        mAdUiContainer = adUiContainer;
        mSdkFactory = ImaSdkFactory.getInstance();
        mPlayerCallbacks = new ArrayList<>();
        createAdsLoader();
        mDisplayContainer = mSdkFactory.createStreamDisplayContainer();
    }

    @TargetApi(19)
    private void enableWebViewDebugging() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
    }

    private void createAdsLoader() {
        ImaSdkSettings settings = new ImaSdkSettings();
        // Change any settings as necessary here.
        settings.setPlayerType(PLAYER_TYPE);
        enableWebViewDebugging();
        mAdsLoader = mSdkFactory.createAdsLoader(mContext);
    }

    public void requestAndPlayAds() {
        mAdsLoader.addAdErrorListener(this);
        mAdsLoader.addAdsLoadedListener(this);
        mAdsLoader.requestStream(buildStreamRequest());
    }

    private StreamRequest buildStreamRequest() {

        VideoStreamPlayer videoStreamPlayer = createVideoStreamPlayer();
        mVideoPlayer.setSampleHlsVideoPlayerCallback(
            new SampleHlsVideoPlayer.SampleHlsVideoPlayerCallback() {
                @Override
                public void onUserTextReceived(String userText) {
                    for (VideoStreamPlayer.VideoStreamPlayerCallback callback :
                        mPlayerCallbacks) {
                        callback.onUserTextReceived(userText);
                    }
                }
                @Override
                public void onSeek(int windowIndex, long positionMs) {
                    // See if we would seek past an ad, and if so, jump back to it.
                    long newSeekPositionMs = positionMs;
                    if (mStreamManager != null) {
                        CuePoint prevCuePoint  =
                                mStreamManager.getPreviousCuePointForStreamTime(positionMs / 1000);
                        if (prevCuePoint != null && !prevCuePoint.isPlayed()) {
                            newSeekPositionMs = (long) (prevCuePoint.getStartTime() * 1000);
                        }
                    }
                    mVideoPlayer.seekTo(windowIndex, newSeekPositionMs);
                }
            });
        mDisplayContainer.setVideoStreamPlayer(videoStreamPlayer);
        mDisplayContainer.setAdContainer(mAdUiContainer);

        // Live stream request.
        StreamRequest request = mSdkFactory.createLiveStreamRequest(
                TEST_ASSET_KEY, null, mDisplayContainer);

        // VOD request. Comment the createLiveStreamRequest() line above and uncomment this
        // createVodStreamRequest() below to switch from a live stream to a VOD stream.
        //StreamRequest request = mSdkFactory.createVodStreamRequest(TEST_CONTENT_SOURCE_ID,
        //        TEST_VIDEO_ID, null, mDisplayContainer);
        return request;
    }

    private VideoStreamPlayer createVideoStreamPlayer() {
        VideoStreamPlayer player = new VideoStreamPlayer() {
            @Override
            public void loadUrl(String url, List<HashMap<String, String>> subtitles) {
                mVideoPlayer.setStreamUrl(url);
                mVideoPlayer.play();
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
                mVideoPlayer.enableControls(false);
                log("Ad Break Started\n");
            }

            @Override
            public void onAdBreakEnded() {
                // Re-enable player controls.
                mVideoPlayer.enableControls(true);
                log("Ad Break Ended\n");
            }

            @Override
            public VideoProgressUpdate getContentProgress() {
                return new VideoProgressUpdate(mVideoPlayer.getCurrentPositionPeriod(),
                        mVideoPlayer.getDuration());
            }
        };
        return player;
    }

    /** AdErrorListener implementation **/
    @Override
    public void onAdError(AdErrorEvent event) {
        log(String.format("Error: %s\n", event.getError().getMessage()));
        // play fallback URL.
        log("Playing fallback Url\n");
        mVideoPlayer.setStreamUrl(mFallbackUrl);
        mVideoPlayer.enableControls(true);
        mVideoPlayer.play();
    }

    /** AdEventListener implementation **/
    @Override
    public void onAdEvent(AdEvent event) {
        switch (event.getType()) {
            case AD_PROGRESS:
                // Do nothing or else log will be filled by these messages.
                break;
            default:
                log(String.format("Event: %s\n", event.getType()));
                break;
        }
    }

    /** AdsLoadedListener implementation **/
    @Override
    public void onAdsManagerLoaded(AdsManagerLoadedEvent event) {
        mStreamManager = event.getStreamManager();
        mStreamManager.addAdErrorListener(this);
        mStreamManager.addAdEventListener(this);
        mStreamManager.init();
    }

    /** Sets fallback URL in case ads stream fails. **/
    void setFallbackUrl(String url) {
        mFallbackUrl = url;
    }

    /** Sets logger for displaying events to screen. Optional. **/
    void setLogger(Logger logger) {
        mLogger = logger;
    }

    private void log(String message) {
        if (mLogger != null) {
            mLogger.log(message);
        }
    }
}
