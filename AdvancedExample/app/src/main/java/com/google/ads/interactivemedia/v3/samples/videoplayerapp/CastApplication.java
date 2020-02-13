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

  private CastSession mCastSession;
  private SessionManager mSessionManager;
  private SessionManagerListener<CastSession> mSessionManagerListener;

  private VideoListItem mVideoListItem;
  private MyActivity mActivity;

  private double mPosition; // content time in secs.

  private static final String NAMESPACE = "urn:x-cast:com.google.ads.interactivemedia.dai.cast";
  private static final String TAG = "IMA Cast Example";

  private TextView mTimeStart;
  private TextView mTimeEnd;

  public CastApplication(final MyActivity activity) {
    mActivity = activity;
    mSessionManager = CastContext.getSharedInstance(activity).getSessionManager();

    mSessionManagerListener =
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
            mCastSession = castSession;
            try {
              mCastSession.setMessageReceivedCallbacks(NAMESPACE, CastApplication.this);
            } catch (IOException e) {
              Log.e(TAG, "Exception when creating channel", e);
            }
            if (mVideoListItem != null) {
              try {
                loadMedia((long) mActivity.getAdsWrapper().getContentTime());

                mActivity.getVideoPlayer().pause();
                mActivity.hidePlayButton();
              } catch (JSONException e) {
                Log.e(TAG, "JSONException " + e.getLocalizedMessage());
              }
            }
            mActivity.invalidateOptionsMenu();
          }

          private void onApplicationDisconnected() {
            SampleAdsWrapper adsWrapper = mActivity.getAdsWrapper();
            SampleVideoPlayer videoPlayer = mActivity.getVideoPlayer();
            if (videoPlayer == null || adsWrapper == null) {
              return;
            }
            if (!adsWrapper.getAdsRequested()) {
              adsWrapper.requestAndPlayAds(mVideoListItem, mPosition);
              mActivity.hidePlayButton();
            } else {
              double streamTime = adsWrapper.getStreamTimeForContentTime(mPosition);
              if (videoPlayer.getCanSeek()) {
                videoPlayer.seekTo(Math.round(streamTime * 1000));
              } else {
                adsWrapper.setSnapBackTime(streamTime);
              }
              videoPlayer.play();
              mActivity.invalidateOptionsMenu();
            }
            SeekBar seekBar = mActivity.getSeekBar();
            if (seekBar != null) {
              View parentView = (View) seekBar.getParent();
              parentView.setVisibility(View.GONE);
            }
            mCastSession = null;
          }
        };
  }

  public void onResume() {
    mCastSession = mSessionManager.getCurrentCastSession();
    mSessionManager.addSessionManagerListener(mSessionManagerListener, CastSession.class);
  }

  public void onPause() {
    mSessionManager.removeSessionManagerListener(mSessionManagerListener, CastSession.class);
    mCastSession = null;
  }

  public void setVideoListItem(VideoListItem videoListItem) {
    mVideoListItem = videoListItem;
  }

  public void autoplayOnCast() {
    if (mCastSession != null && mCastSession.getRemoteMediaClient() != null) {
      try {
        loadMedia(0);
        mActivity.hidePlayButton();
      } catch (JSONException e) {
        Log.e(TAG, "JSONException " + e.getLocalizedMessage());
      }
    }
  }

  public void loadMedia(long position) throws JSONException {

    JSONObject streamRequest = new JSONObject();
    streamRequest.put("assetKey", mVideoListItem.getAssetKey());
    streamRequest.put("contentSourceId", mVideoListItem.getContentSourceId());
    streamRequest.put("videoId", mVideoListItem.getVideoId());
    streamRequest.put("apiKey", mVideoListItem.getApiKey());
    // turn off pre-roll for LIVE.
    streamRequest.put("attemptPreroll", mVideoListItem.isVod());
    streamRequest.put("startTime", position);

    MediaMetadata mediaMetadata = new MediaMetadata();
    mediaMetadata.addImage(new WebImage(Uri.parse("https://www.example.com")));

    int streamTypeId =
        mVideoListItem.getAssetKey() != null
            ? MediaInfo.STREAM_TYPE_LIVE
            : MediaInfo.STREAM_TYPE_BUFFERED;

    MediaInfo mediaInfo =
        new MediaInfo.Builder("https://www.example.com")
            .setCustomData(streamRequest)
            .setMetadata(mediaMetadata)
            .setContentType("application/x-mpegurl")
            .setStreamType(streamTypeId)
            .build();

    RemoteMediaClient remoteMediaClient = mCastSession.getRemoteMediaClient();
    remoteMediaClient.addProgressListener(this, 1000); // 1 sec period.
    remoteMediaClient.load(mediaInfo, true, 0);

    SeekBar seekBar = mActivity.getSeekBar();
    if (seekBar != null) {
      View parentView = (View) seekBar.getParent();
      parentView.setVisibility(View.VISIBLE);
      mTimeStart = (TextView) parentView.findViewById(R.id.time_start);
      mTimeEnd = (TextView) parentView.findViewById(R.id.time_end);
      seekBar.setOnSeekBarChangeListener(
          new OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
              if (fromUser) {
                mCastSession.getRemoteMediaClient().seek(progress);
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
        mPosition = Double.valueOf(timeString);
      } catch (NumberFormatException e) {
        Log.e(TAG, "can't parse content time" + e);
      }
    }
  }

  private void sendMessage(String message) {
    try {
      mCastSession.sendMessage(NAMESPACE, message);
    } catch (Exception e) {
      Log.e(TAG, "Exception while sending message", e);
    }
  }

  @Override
  public void onProgressUpdated(long progressMs, long durationMs) {
    if (mCastSession == null) {
      return;
    }
    SeekBar seekBar = mActivity.getSeekBar();
    if (seekBar != null) {
      Log.i(TAG, "SeekBar: " + progressMs + ":" + durationMs);
      seekBar.setMax((int) durationMs);
      mTimeEnd.setText(millisToTimeString(durationMs));
      seekBar.setProgress((int) progressMs);
      mTimeStart.setText(millisToTimeString(progressMs));
    }

    sendMessage("getContentTime,");
  }

  private String millisToTimeString(long millis) {
    return new SimpleDateFormat("mm:ss", Locale.US).format(new Date(millis));
  }
}
