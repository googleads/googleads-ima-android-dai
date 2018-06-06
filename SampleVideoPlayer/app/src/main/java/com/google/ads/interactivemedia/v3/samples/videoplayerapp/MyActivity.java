/*
 * Copyright (C) 2015 Google, Inc.
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
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageButton;

import com.google.ads.interactivemedia.v3.samples.samplevideoplayer.SampleVideoPlayer;
import com.google.android.exoplayer2.ui.PlayerView;

/**
 * Main Activity that plays media using {@link SampleVideoPlayer}.
 */
public class MyActivity extends AppCompatActivity {

    private static final String DEFAULT_STREAM_URL =
            "http://storage.googleapis.com/testtopbox-public/video_content/bbb/master.m3u8";
    private static final String APP_LOG_TAG = "ImaDaiExample";

    protected SampleVideoPlayer mVideoPlayer;
    protected ImageButton mPlayButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my);
        View rootView = findViewById(R.id.videoLayout);
        mVideoPlayer = new SampleVideoPlayer(rootView.getContext(),
                (PlayerView) rootView.findViewById(R.id.playerView));
        mVideoPlayer.enableControls(false);
        mVideoPlayer.setStreamUrl(DEFAULT_STREAM_URL);

        mPlayButton = rootView.findViewById(R.id.playButton);
        // Set up play button listener to play video then hide play button.
        mPlayButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mVideoPlayer.enableControls(true);
                mVideoPlayer.play();
                mPlayButton.setVisibility(View.GONE);
            }
        });
        orientVideoDescription(getResources().getConfiguration().orientation);
    }

    @Override
    public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        orientVideoDescription(configuration.orientation);
    }

    private void orientVideoDescription(int orientation) {
        // Hide the extra content when in landscape so the video is as large as possible.
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            findViewById(R.id.descriptionLayout).setVisibility(View.GONE);
        } else {
            findViewById(R.id.descriptionLayout).setVisibility(View.VISIBLE);
        }
    }

    // Needed to pause/resume app from background.
    @Override
    public void onPause() {
        super.onPause();
        if (mVideoPlayer != null && mVideoPlayer.isStreamRequested()) {
            mVideoPlayer.pause();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mVideoPlayer != null && mVideoPlayer.isStreamRequested()) {
            mVideoPlayer.play();
        }
    }
}
