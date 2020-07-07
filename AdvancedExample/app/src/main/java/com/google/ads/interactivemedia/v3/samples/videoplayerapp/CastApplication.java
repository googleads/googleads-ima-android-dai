package com.google.ads.interactivemedia.v3.samples.videoplayerapp;

import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import com.google.ads.interactivemedia.v3.samples.samplevideoplayer.SampleVideoPlayer;
import com.google.ads.interactivemedia.v3.samples.videoplayerapp.VideoListFragment.VideoListItem;
import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaLoadRequestData;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.android.gms.common.images.WebImage;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import org.json.JSONException;
import org.json.JSONObject;

/** Manages Google Cast interactions. */
public class CastApplication
    implements Cast.MessageReceivedCallback, RemoteMediaClient.ProgressListener {

  private CastSession castSession;
  private SessionManager sessionManager;
  private SessionManagerListener<CastSession> sessionManagerListener;

  private VideoListItem videoListItem;
  private MyActivity activity;

  private double position; // content time in secs.

  private static final String NAMESPACE = "urn:x-cast:com.google.ads.interactivemedia.dai.cast";
  private static final String TAG = "IMA Cast Example";

  private TextView timeStart;
  private TextView timeEnd;

  public CastApplication(final MyActivity activity) {
    this.activity = activity;
    sessionManager = CastContext.getSharedInstance(activity).getSessionManager();

    sessionManagerListener =
        new SessionManagerListener<CastSession>() {
          @Override
          public void onSessionStarted(CastSession castSession, String s) {
            onApplicationConnected(castSession);
          }

          @Override
          public void onSessionResumed(CastSession castSession, boolean b) {
            onApplicationConnected(castSession);
          }

          @Override
          public void onSessionStartFailed(CastSession castSession, int i) {
            onApplicationDisconnected();
          }

          @Override
          public void onSessionEnded(CastSession castSession, int i) {
            onApplicationDisconnected();
          }

          @Override
          public void onSessionResumeFailed(CastSession castSession, int i) {
            onApplicationDisconnected();
          }

          @Override
          public void onSessionSuspended(CastSession castSession, int i) {}

          @Override
          public void onSessionStarting(CastSession castSession) {}

          @Override
          public void onSessionResuming(CastSession castSession, String s) {}

          @Override
          public void onSessionEnding(CastSession castSession) {
            castSession.getRemoteMediaClient().removeProgressListener(CastApplication.this);
          }

          private void onApplicationConnected(CastSession castSession) {
            this.castSession = castSession;
            try {
              this.castSession.setMessageReceivedCallbacks(NAMESPACE, CastApplication.this);
            } catch (IOException e) {
              Log.e(TAG, "Exception when creating channel", e);
            }
            if (videoListItem != null) {
              try {
                loadMedia((long) activity.getAdsWrapper().getContentTime());

                activity.getVideoPlayer().pause();
                activity.hidePlayButton();
              } catch (JSONException e) {
                Log.e(TAG, "JSONException " + e.getLocalizedMessage());
              }
            }
            activity.invalidateOptionsMenu();
          }

          private void onApplicationDisconnected() {
            SampleAdsWrapper adsWrapper = activity.getAdsWrapper();
            SampleVideoPlayer videoPlayer = activity.getVideoPlayer();
            if (videoPlayer == null || adsWrapper == null) {
              return;
            }
            if (!adsWrapper.getAdsRequested()) {
              adsWrapper.requestAndPlayAds(videoListItem, position);
              activity.hidePlayButton();
            } else {
              double streamTime = adsWrapper.getStreamTimeForContentTime(position);
              if (videoPlayer.getCanSeek()) {
                videoPlayer.seekTo(Math.round(streamTime * 1000));
              } else {
                adsWrapper.setSnapBackTime(streamTime);
              }
              videoPlayer.play();
              activity.invalidateOptionsMenu();
            }
            SeekBar seekBar = activity.getSeekBar();
            if (seekBar != null) {
              View parentView = (View) seekBar.getParent();
              parentView.setVisibility(View.GONE);
            }
            this.castSession = null;
          }
        };
  }

  public void onResume() {
    castSession = sessionManager.getCurrentCastSession();
    sessionManager.addSessionManagerListener(sessionManagerListener, CastSession.class);
  }

  public void onPause() {
    sessionManager.removeSessionManagerListener(sessionManagerListener, CastSession.class);
    castSession = null;
  }

  public void setVideoListItem(VideoListItem videoListItem) {
    this.videoListItem = videoListItem;
  }

  public void autoplayOnCast() {
    if (castSession != null && castSession.getRemoteMediaClient() != null) {
      try {
        loadMedia(0);
        activity.hidePlayButton();
      } catch (JSONException e) {
        Log.e(TAG, "JSONException " + e.getLocalizedMessage());
      }
    }
  }

  public void loadMedia(long position) throws JSONException {

    JSONObject streamRequest = new JSONObject();
    streamRequest.put("assetKey", videoListItem.getAssetKey());
    streamRequest.put("contentSourceId", videoListItem.getContentSourceId());
    streamRequest.put("videoId", videoListItem.getVideoId());
    streamRequest.put("apiKey", videoListItem.getApiKey());
    // turn off pre-roll for LIVE.
    streamRequest.put("attemptPreroll", videoListItem.isVod());
    streamRequest.put("startTime", position);
    streamRequest.put("format", videoListItem.getStreamFormat().toString().toLowerCase());

    MediaMetadata mediaMetadata = new MediaMetadata();
    mediaMetadata.addImage(new WebImage(Uri.parse("https://www.example.com")));

    int streamTypeId =
        videoListItem.getAssetKey() != null
            ? MediaInfo.STREAM_TYPE_LIVE
            : MediaInfo.STREAM_TYPE_BUFFERED;

    MediaInfo mediaInfo =
        new MediaInfo.Builder("https://www.example.com")
            .setCustomData(streamRequest)
            .setMetadata(mediaMetadata)
            .setContentType(
                videoListItem.getStreamFormat().equals(StreamRequest.StreamFormat.HLS)
                    ? "application/x-mpegurl"
                    : "application/dash+xml")
            .setStreamType(streamTypeId)
            .build();

    RemoteMediaClient remoteMediaClient = castSession.getRemoteMediaClient();
    remoteMediaClient.addProgressListener(this, 1000); // 1 sec period.

    MediaLoadRequestData mediaLoadRequestData =
        new MediaLoadRequestData.Builder().setMediaInfo(mediaInfo).setAutoplay(true).build();

    remoteMediaClient.load(mediaLoadRequestData);

    SeekBar seekBar = activity.getSeekBar();
    if (seekBar != null) {
      View parentView = (View) seekBar.getParent();
      parentView.setVisibility(View.VISIBLE);
      timeStart = (TextView) parentView.findViewById(R.id.time_start);
      timeEnd = (TextView) parentView.findViewById(R.id.time_end);
      seekBar.setOnSeekBarChangeListener(
          new OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
              if (fromUser) {
                castSession.getRemoteMediaClient().seek(progress);
              }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
          });
    }
  }

  @Override
  public void onMessageReceived(CastDevice castDevice, String namespace, String message) {
    Log.d(TAG, "onMessageReceived: " + message);
    if (message.startsWith("contentTime,")) {
      String timeString = message.substring("contentTime,".length());
      try {
        position = Double.valueOf(timeString);
      } catch (NumberFormatException e) {
        Log.e(TAG, "can't parse content time" + e);
      }
    }
  }

  private void sendMessage(String message) {
    try {
      castSession.sendMessage(NAMESPACE, message);
    } catch (Exception e) {
      Log.e(TAG, "Exception while sending message", e);
    }
  }

  @Override
  public void onProgressUpdated(long progressMs, long durationMs) {
    if (castSession == null) {
      return;
    }
    SeekBar seekBar = activity.getSeekBar();
    if (seekBar != null) {
      Log.i(TAG, "SeekBar: " + progressMs + ":" + durationMs);
      seekBar.setMax((int) durationMs);
      timeEnd.setText(millisToTimeString(durationMs));
      seekBar.setProgress((int) progressMs);
      timeStart.setText(millisToTimeString(progressMs));
    }

    sendMessage("getContentTime,");
  }

  private String millisToTimeString(long millis) {
    return new SimpleDateFormat("mm:ss", Locale.US).format(new Date(millis));
  }
}
