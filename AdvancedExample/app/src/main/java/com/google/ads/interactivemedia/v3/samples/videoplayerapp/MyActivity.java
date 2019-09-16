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

import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.ads.interactivemedia.v3.samples.samplevideoplayer.SampleVideoPlayer;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.gms.cast.framework.CastButtonFactory;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.util.HashMap;

/**
 * Main Activity that plays media using {@link SampleVideoPlayer}.
 */
public class MyActivity extends AppCompatActivity {

    private static final String PLAYLIST_FRAGMENT_TAG = "video_playlist_fragment_tag";
    private static final String VIDEO_FRAGMENT_TAG = "video_example_fragment_tag";

  private static final String FALLBACK_STREAM_URL =
      "https://storage.googleapis.com/testtopbox-public/video_content/bbb/master.m3u8";
    private static final String APP_LOG_TAG = "ImaDaiExample";

    private SampleVideoPlayer mVideoPlayer;
    private SampleAdsWrapper mSampleAdsWrapper;
    private ImageButton mPlayButton;

    private HashMap<String, Double> mBookmarks = new HashMap<>();
    private VideoListFragment.VideoListItem mVideoListItem;

    private CastApplication mCastApplication;
    private SeekBar mSeekBar;

    // Set up a default CookieManager to handle streams that requir cookies to be passed along to
    // subsequent requests.
    private static final CookieManager DEFAULT_COOKIE_MANAGER;
    static {
        DEFAULT_COOKIE_MANAGER = new CookieManager();
        DEFAULT_COOKIE_MANAGER.setCookiePolicy(CookiePolicy.ACCEPT_ORIGINAL_SERVER);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my);

        if (CookieHandler.getDefault() != DEFAULT_COOKIE_MANAGER) {
            CookieHandler.setDefault(DEFAULT_COOKIE_MANAGER);
        }

        VideoListFragment videoListFragment = new VideoListFragment();
        getSupportFragmentManager().beginTransaction()
                .add(R.id.video_example_container, videoListFragment, PLAYLIST_FRAGMENT_TAG)
                .commit();
        videoListFragment.setOnVideoSelectedListener(mVideoSelectedListener);

        mCastApplication = new CastApplication(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.menu_main, menu);
        CastButtonFactory.setUpMediaRouteButton(getApplicationContext(),
            menu, R.id.media_route_menu_item);
        return true;
    }

    @Override
    public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        orientVideoDescription(configuration.orientation);
    }

    private void orientVideoDescription(int orientation) {
        // If we are showing the video fragment, hide the extra content when in landscape,
        // so the video is as large as possible.
        VideoFragment videoFragment = (VideoFragment) getSupportFragmentManager()
                .findFragmentByTag(VIDEO_FRAGMENT_TAG);
        if (videoFragment == null || videoFragment.getView() == null) {
            return;
        }

        View descriptionView = videoFragment.getView().findViewById(R.id.descriptionLayout);
        if (descriptionView == null) {
            return;
        }

        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            descriptionView.setVisibility(View.GONE);
        } else {
            descriptionView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mVideoPlayer != null && mVideoPlayer.isPlaying()) {
            mVideoPlayer.pause();
        }
        mCastApplication.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mVideoPlayer != null && mVideoPlayer.isStreamRequested()
                && !mVideoPlayer.isPlaying()) {
            mVideoPlayer.play();
        }
        mCastApplication.onResume();
    }

    public SampleVideoPlayer getVideoPlayer() {
        return mVideoPlayer;
    }

    public SampleAdsWrapper getAdsWrapper() {
        return mSampleAdsWrapper;
    }

    public void hidePlayButton() {
        ImageButton button = (ImageButton) findViewById(R.id.playButton);
        if (button != null) {
            button.setVisibility(View.GONE);
        }
    }

    public SeekBar getSeekBar() {
        return mSeekBar;
    }

    private final VideoListFragment.OnVideoSelectedListener mVideoSelectedListener =
            new VideoListFragment.OnVideoSelectedListener() {
                @Override
                public void onVideoSelected(VideoListFragment.VideoListItem videoListItem) {
                    VideoListFragment videoListFragment =
                            (VideoListFragment) getSupportFragmentManager()
                                    .findFragmentByTag(PLAYLIST_FRAGMENT_TAG);
                    int videoPlaylistFragmentId = videoListFragment.getId();

                    VideoFragment videoFragment = new VideoFragment();
                    getSupportFragmentManager()
                            .beginTransaction()
                            .replace(videoPlaylistFragmentId, videoFragment, VIDEO_FRAGMENT_TAG)
                            .addToBackStack(null)
                            .commit();
                    videoFragment.setVideoFragmentListener(mVideoFragmentListener);
                    mVideoListItem = videoListItem;
                    mCastApplication.setVideoListItem(mVideoListItem);
                }
    };

    private VideoFragmentListener mVideoFragmentListener = new VideoFragmentListener() {
        @Override
        public void onVideoFragmentCreated(View rootView) {
            PlayerView videoView = rootView.findViewById(R.id.videoView);
            mVideoPlayer = new SampleVideoPlayer(rootView.getContext(), videoView);
            mVideoPlayer.enableControls(false);
            mSampleAdsWrapper = new SampleAdsWrapper(rootView.getContext(),
                    mVideoPlayer, (ViewGroup) rootView.findViewById(R.id.adUiContainer));
            mSampleAdsWrapper.setFallbackUrl(FALLBACK_STREAM_URL);

            final TextView descTextView = rootView.findViewById(R.id.playerDescription);
            final ScrollView scrollView = rootView.findViewById(R.id.logScroll);
            final TextView logTextView = rootView.findViewById(R.id.logText);

            if (descTextView != null) {
                descTextView.setText(mVideoListItem.getTitle());
            }

            mSampleAdsWrapper.setLogger(new SampleAdsWrapper.Logger() {
                @Override
                public void log(String logMessage) {
                    Log.i(APP_LOG_TAG, logMessage);
                    if (logTextView != null) {
                        logTextView.append(logMessage);
                    }
                    if (scrollView != null) {
                        scrollView.post(new Runnable() {
                            @Override
                            public void run() {
                                scrollView.fullScroll(View.FOCUS_DOWN);
                            }
                        });
                    }
                }
            });

            mPlayButton = (ImageButton) rootView.findViewById(R.id.playButton);
            // Set up play button listener to play video then hide play button.
            mPlayButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    double bookMarkTime = 0;
                    if (mBookmarks.containsKey(mVideoListItem.getId())) {
                        bookMarkTime = mBookmarks.get(mVideoListItem.getId());
                    }
                    mVideoPlayer.enableControls(true);
                    mVideoPlayer.setCanSeek(true);
                    mSampleAdsWrapper.requestAndPlayAds(mVideoListItem, bookMarkTime);
                    mPlayButton.setVisibility(View.GONE);
                }
            });

            orientVideoDescription(getResources().getConfiguration().orientation);
            mSeekBar = (SeekBar) rootView.findViewById(R.id.cast_seekbar);
            mCastApplication.autoplayOnCast();
        }

        @Override
        public void onVideoFragmentDestroyed() {
            if (mCastApplication != null) {
                mCastApplication.setVideoListItem(null);
            }
            mSampleAdsWrapper.release();
            mSampleAdsWrapper = null;
            mVideoPlayer = null;
            mSeekBar = null;
        }

        @Override
        public void onVideoFragmentPaused() {
            // Store content time for bookmarking feature.
            if (mSampleAdsWrapper != null) {
                mBookmarks.put(mVideoListItem.getId(), mSampleAdsWrapper.getContentTime());
            }
        }
    };

    /**
     * Interface for Activity to respond to Fragment lifecyle events.
     */
    public interface VideoFragmentListener {
        void onVideoFragmentCreated(View rootView);
        void onVideoFragmentDestroyed();
        void onVideoFragmentPaused();
    }

    /**
     * A class for encapsulating videos in Fragments.
     */
    public static class VideoFragment extends Fragment {

        private VideoFragmentListener mVideoFragmentListener;

        public void setVideoFragmentListener(VideoFragmentListener videoFragmentListener) {
            mVideoFragmentListener = videoFragmentListener;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_video, container, false);

            if (mVideoFragmentListener != null) {
                mVideoFragmentListener.onVideoFragmentCreated(rootView);
            }

            return rootView;
        }

        @Override
        public void onPause() {
            super.onPause();
            if (mVideoFragmentListener != null) {
                mVideoFragmentListener.onVideoFragmentPaused();
            }
        }

        @Override
        public void onDestroyView() {
            super.onDestroyView();
            if (mVideoFragmentListener != null) {
                mVideoFragmentListener.onVideoFragmentDestroyed();
            }
        }
    }
}
