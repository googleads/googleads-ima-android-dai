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

import android.app.UiModeManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.fragment.app.Fragment;
import com.google.ads.interactivemedia.v3.samples.samplevideoplayer.SampleVideoPlayer;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.gms.cast.framework.CastButtonFactory;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.util.HashMap;

/** Main Activity that plays media using {@link SampleVideoPlayer}. */
public class MyActivity extends AppCompatActivity {

  private static final String PLAYLIST_FRAGMENT_TAG = "video_playlist_fragment_tag";
  private static final String VIDEO_FRAGMENT_TAG = "video_example_fragment_tag";

  private static final String FALLBACK_STREAM_URL =
      "https://storage.googleapis.com/testtopbox-public/video_content/bbb/master.m3u8";
  private static final String APP_LOG_TAG = "ImaDaiExample";

  private SampleVideoPlayer videoPlayer;
  private SampleAdsWrapper sampleAdsWrapper;
  private ImageButton playButton;

  private HashMap<String, Long> bookmarks = new HashMap<>();
  private VideoListFragment.VideoListItem videoListItem;
  private boolean contentHasStarted = false;

  private CastApplication castApplication;
  private SeekBar seekBar;

  // Set up a default CookieManager to handle streams that require cookies to be passed along to
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
    getSupportFragmentManager()
        .beginTransaction()
        .add(R.id.video_example_container, videoListFragment, PLAYLIST_FRAGMENT_TAG)
        .commit();
    videoListFragment.setOnVideoSelectedListener(mVideoSelectedListener);

    UiModeManager uiModeManager = (UiModeManager) getSystemService(UI_MODE_SERVICE);
    if (uiModeManager.getCurrentModeType() != Configuration.UI_MODE_TYPE_TELEVISION) {
      // Only create a cast application on devices that support cast.
      castApplication = new CastApplication(this);
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);
    getMenuInflater().inflate(R.menu.menu_main, menu);
    CastButtonFactory.setUpMediaRouteButton(
        getApplicationContext(), menu, R.id.media_route_menu_item);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    int id = item.getItemId();
    if (id == R.id.action_settings) {
      SampleVideoPlayer player = getVideoPlayer();
      player.enableClosedCaptioning();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  public void onConfigurationChanged(Configuration configuration) {
    super.onConfigurationChanged(configuration);
    orientVideoDescription(configuration.orientation);
  }

  private void orientVideoDescription(int orientation) {
    // If we are showing the video fragment, hide the extra content when in landscape,
    // so the video is as large as possible.
    VideoFragment videoFragment =
        (VideoFragment) getSupportFragmentManager().findFragmentByTag(VIDEO_FRAGMENT_TAG);
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
    if (videoPlayer != null && videoPlayer.isPlaying()) {
      videoPlayer.pause();
    }

    if (castApplication != null) {
      castApplication.onPause();
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    if (videoPlayer != null && videoPlayer.isStreamRequested() && !videoPlayer.isPlaying()) {
      videoPlayer.play();
    }

    if (castApplication != null) {
      castApplication.onResume();
    }
  }

  public SampleVideoPlayer getVideoPlayer() {
    return videoPlayer;
  }

  public SampleAdsWrapper getAdsWrapper() {
    return sampleAdsWrapper;
  }

  public void hidePlayButton() {
    ImageButton button = (ImageButton) findViewById(R.id.playButton);
    if (button != null) {
      button.setVisibility(View.INVISIBLE);
    }
  }

  public SeekBar getSeekBar() {
    return seekBar;
  }

  private final VideoListFragment.OnVideoSelectedListener mVideoSelectedListener =
      new VideoListFragment.OnVideoSelectedListener() {
        @Override
        public void onVideoSelected(VideoListFragment.VideoListItem videoItem) {
          VideoListFragment videoListFragment =
              (VideoListFragment)
                  getSupportFragmentManager().findFragmentByTag(PLAYLIST_FRAGMENT_TAG);
          int videoPlaylistFragmentId = videoListFragment.getId();

          VideoFragment videoFragment = new VideoFragment();
          getSupportFragmentManager()
              .beginTransaction()
              .replace(videoPlaylistFragmentId, videoFragment, VIDEO_FRAGMENT_TAG)
              .addToBackStack(null)
              .commit();
          videoFragment.setVideoFragmentListener(mVideoFragmentListener);
          videoListItem = videoItem;

          if (castApplication != null) {
            castApplication.setVideoListItem(videoListItem);
          }
        }
      };

  private VideoFragmentListener mVideoFragmentListener =
      new VideoFragmentListener() {
        @Override
        public void onVideoFragmentCreated(View rootView) {
          // Boolean 'contentHasStarted' is set to false by default, but it also needs
          // to be set to false here, in the case where multiple test cases are watched in
          // single session.
          contentHasStarted = false;
          PlayerView videoView = rootView.findViewById(R.id.videoView);
          playButton = (ImageButton) rootView.findViewById(R.id.playButton);
          videoPlayer = new SampleVideoPlayer(rootView.getContext(), videoView);
          videoPlayer.enableControls(false);
          sampleAdsWrapper =
              new SampleAdsWrapper(
                  rootView.getContext(),
                  videoPlayer,
                  (ViewGroup) rootView.findViewById(R.id.adUiContainer));
          sampleAdsWrapper.setFallbackUrl(FALLBACK_STREAM_URL);

          final TextView descTextView = rootView.findViewById(R.id.playerDescription);
          final TextView logTextView = rootView.findViewById(R.id.logText);

          if (descTextView != null) {
            descTextView.setText(videoListItem.getTitle());
          }

          ScrollView container = rootView.findViewById(R.id.container);
          container.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
          container.setSmoothScrollingEnabled(true);

          // Make the dummyScrollContent height the size of the screen height.
          DisplayMetrics displayMetrics = new DisplayMetrics();
          MyActivity.this.getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
          ConstraintLayout constraintLayout = rootView.findViewById(R.id.constraintLayout);
          ConstraintSet forceHeight = new ConstraintSet();
          forceHeight.clone(constraintLayout);
          forceHeight.constrainHeight(R.id.dummyScrollContent, displayMetrics.heightPixels);
          forceHeight.applyTo(constraintLayout);

          sampleAdsWrapper.setLogger(
              new SampleAdsWrapper.Logger() {
                @Override
                public void log(String logMessage) {
                  Log.i(APP_LOG_TAG, logMessage);
                  if (logTextView != null) {
                    logTextView.append(logMessage);
                  }
                }
              });

          // Set up play button listener to play video then hide play button.
          playButton.setOnClickListener(
              new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                  hidePlayButton();
                  if (contentHasStarted) {
                    videoPlayer.play();
                    return;
                  }
                  contentHasStarted = true;
                  long bookMarkTime = 0;
                  if (bookmarks.containsKey(videoListItem.getId())) {
                    bookMarkTime = bookmarks.get(videoListItem.getId());
                  }
                  videoPlayer.enableControls(true);
                  videoPlayer.setCanSeek(true);
                  sampleAdsWrapper.requestAndPlayAds(videoListItem, bookMarkTime);
                }
              });

          orientVideoDescription(getResources().getConfiguration().orientation);
          seekBar = (SeekBar) rootView.findViewById(R.id.cast_seekbar);

          if (castApplication != null) {
            castApplication.autoplayOnCast();
          }
        }

        @Override
        public void onVideoFragmentDestroyed() {
          if (castApplication != null) {
            castApplication.setVideoListItem(null);
          }
          sampleAdsWrapper.release();
          sampleAdsWrapper = null;
          videoPlayer = null;
          seekBar = null;
        }

        @Override
        public void onVideoFragmentPaused() {
          // Store content time for bookmarking feature.
          if (sampleAdsWrapper != null) {
            bookmarks.put(videoListItem.getId(), sampleAdsWrapper.getContentTimeMs());
          }
        }
      };

  /** Interface for Activity to respond to Fragment lifecyle events. */
  public interface VideoFragmentListener {
    void onVideoFragmentCreated(View rootView);

    void onVideoFragmentDestroyed();

    void onVideoFragmentPaused();
  }

  /** A class for encapsulating videos in Fragments. */
  public static class VideoFragment extends Fragment {

    private VideoFragmentListener mVideoFragmentListener;

    public void setVideoFragmentListener(VideoFragmentListener videoFragmentListener) {
      mVideoFragmentListener = videoFragmentListener;
    }

    @Override
    public View onCreateView(
        LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
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
