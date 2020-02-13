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
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import com.google.ads.interactivemedia.v3.api.StreamRequest.StreamFormat;

/**
 * Fragment for displaying a playlist of video thumbnails from which the user can select one to
 * play.
 */
public class VideoListFragment extends Fragment {

  private OnVideoSelectedListener mListener;

  public VideoListItem[] getVideoListItems() {
    return new VideoListItem[] {
      new VideoListItem(
          "Live Video - Big Buck Bunny",
          "sN_IYUG8STe1ZzhIIE_ksA",
          null,
          null,
          null,
          StreamFormat.HLS,
          null),
      new VideoListItem(
          "VOD - Google I/O", null, null, "19463", "googleio-highlights", StreamFormat.HLS, null),
      new VideoListItem(
          "VOD - Tears of Steel", null, null, "19463", "tears-of-steel", StreamFormat.HLS, null),
      new VideoListItem("VOD - DASH", null, null, "2474148", "bbb-clear", StreamFormat.DASH, null),
      new VideoListItem(
          "BBB-widevine",
          null,
          null,
          "2474148",
          "bbb-widevine",
          StreamFormat.DASH,
          "https://proxy.uat.widevine.com/proxy"),
    };
  }

  public void setOnVideoSelectedListener(OnVideoSelectedListener listener) {
    mListener = listener;
  }

  /**
   * Listener called when the user selects a video from the list. Container activity must implement
   * this interface.
   */
  public interface OnVideoSelectedListener {
    void onVideoSelected(VideoListItem videoListItem);
  }

  /**
   * Information about a video playlist item that the user will select in a playlist. Has info for
   * both VOD and live stream items.
   */
  public static class VideoListItem {

    private final String mTitle;
    private final String mAssetKey;
    private final String mApiKey;
    private final String mContentSourceId;
    private final String mVideoId;
    private final StreamFormat mStreamFormat;
    private final String mLicenseUrl;

    private final String mId;

    public String getTitle() {
      return mTitle;
    }

    public String getAssetKey() {
      return mAssetKey;
    }

    public String getApiKey() {
      return mApiKey;
    }

    public String getContentSourceId() {
      return mContentSourceId;
    }

    public String getVideoId() {
      return mVideoId;
    }

    public String getId() {
      return mId;
    }

    public StreamFormat getStreamFormat() {
      return mStreamFormat;
    }

    public String getLicenseUrl() {
      return mLicenseUrl;
    }

    public boolean isVod() {
      return mAssetKey == null;
    }

    public VideoListItem(
        String title,
        String assetKey,
        String apiKey,
        String contentSourceId,
        String videoId,
        StreamFormat streamFormat,
        String licenseUrl) {
      this.mTitle = title;
      this.mAssetKey = assetKey;
      this.mApiKey = apiKey;
      this.mContentSourceId = contentSourceId;
      this.mVideoId = videoId;
      this.mStreamFormat = streamFormat;
      this.mLicenseUrl = licenseUrl;
      this.mId = (assetKey == null) ? contentSourceId + videoId : assetKey;
    }
  }

  @Override
  public View onCreateView(
      LayoutInflater layoutInflater, final ViewGroup viewGroup, Bundle bundle) {
    View rootView = layoutInflater.inflate(R.layout.fragment_video_list, viewGroup, false);

    final ListView listView = (ListView) rootView.findViewById(R.id.videoListView);
    VideoListAdapter videoListAdapter =
        new VideoListAdapter(rootView.getContext(), R.layout.video_item, getVideoListItems());
    listView.setAdapter(videoListAdapter);

    listView.setOnItemClickListener(
        new AdapterView.OnItemClickListener() {
          @Override
          public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            VideoListItem item = (VideoListItem) listView.getItemAtPosition(position);
            if (mListener != null && item != null) {
              mListener.onVideoSelected(item);
            }
          }
        });

    return rootView;
  }

  /** Adapter for a list of video items. */
  public static class VideoListAdapter extends ArrayAdapter<VideoListItem> {

    public VideoListAdapter(Context context, int resource, VideoListItem[] objects) {
      super(context, resource, objects);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      // Get the data item for this position
      VideoListItem videoListItem = getItem(position);
      // Check if an existing view is being reused, otherwise inflate the view
      if (convertView == null) {
        convertView = LayoutInflater.from(getContext()).inflate(R.layout.video_item, parent, false);
      }
      // Lookup view for data population
      TextView title = (TextView) convertView.findViewById(R.id.videoItemText);

      // Populate the data into the template view using the data object
      title.setText(videoListItem.mTitle);

      // Return the completed view to render on screen
      return convertView;
    }
  }
}
