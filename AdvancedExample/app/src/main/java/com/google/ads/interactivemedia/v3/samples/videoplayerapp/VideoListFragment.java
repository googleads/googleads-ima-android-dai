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

  private OnVideoSelectedListener listener;

  public VideoListItem[] getVideoListItems() {
    return new VideoListItem[] {
      new VideoListItem(
          "Live HLS Video - Big Buck Bunny",
          "slDkEpYpQCGczGfRXCrE9w",
          null,
          null,
          null,
          StreamFormat.HLS,
          null),
      new VideoListItem(
          "Live DASH Video - Tears of Steel",
          "PSzZMzAkSXCmlJOWDmRj8Q",
          null,
          null,
          null,
          StreamFormat.DASH,
          null),
      new VideoListItem(
          "VOD - Tears of Steel", null, null, "2528370", "tears-of-steel", StreamFormat.HLS, null),
      new VideoListItem("VOD - DASH", null, null, "2559737", "tos-dash", StreamFormat.DASH, null),
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
    this.listener = listener;
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

    private final String title;
    private final String assetKey;
    private final String apiKey;
    private final String contentSourceId;
    private final String videoId;
    private final StreamFormat streamFormat;
    private final String licenseUrl;

    private final String id;

    public String getTitle() {
      return title;
    }

    public String getAssetKey() {
      return assetKey;
    }

    public String getApiKey() {
      return apiKey;
    }

    public String getContentSourceId() {
      return contentSourceId;
    }

    public String getVideoId() {
      return videoId;
    }

    public String getId() {
      return id;
    }

    public StreamFormat getStreamFormat() {
      return streamFormat;
    }

    public String getLicenseUrl() {
      return licenseUrl;
    }

    public boolean isVod() {
      return assetKey == null;
    }

    public VideoListItem(
        String title,
        String assetKey,
        String apiKey,
        String contentSourceId,
        String videoId,
        StreamFormat streamFormat,
        String licenseUrl) {
      this.title = title;
      this.assetKey = assetKey;
      this.apiKey = apiKey;
      this.contentSourceId = contentSourceId;
      this.videoId = videoId;
      this.streamFormat = streamFormat;
      this.licenseUrl = licenseUrl;
      this.id = (assetKey == null) ? contentSourceId + videoId : assetKey;
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
            if (listener != null && item != null) {
              listener.onVideoSelected(item);
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
      TextView titleText = (TextView) convertView.findViewById(R.id.videoItemText);

      // Populate the data into the template view using the data object
      titleText.setText(videoListItem.title);

      // Return the completed view to render on screen
      return convertView;
    }
  }
}
