package com.google.ads.interactivemedia.v3.samples.videoplayerapp;

import static androidx.media3.common.C.CONTENT_TYPE_HLS;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.widget.TextView;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.ima.ImaServerSideAdInsertionMediaSource;
import androidx.media3.exoplayer.ima.ImaServerSideAdInsertionUriBuilder;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import androidx.multidex.MultiDex;
import com.google.ads.interactivemedia.v3.api.AdEvent;
import com.google.ads.interactivemedia.v3.api.ImaSdkFactory;
import com.google.ads.interactivemedia.v3.api.ImaSdkSettings;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Main Activity. */
@SuppressLint("UnsafeOptInUsageError")
/* @SuppressLint is needed for new media3 APIs. */
public class MyActivity extends Activity {

  private static final String KEY_ADS_LOADER_STATE = "ads_loader_state";
  private static final String SAMPLE_ASSET_KEY = "c-rArva4ShKVIAkNfy6HUQ";
  private static final String LOG_TAG = "ImaExoPlayerExample";

  private PlayerView playerView;
  private TextView logText;
  private ExoPlayer player;
  private ImaServerSideAdInsertionMediaSource.AdsLoader adsLoader;
  private ImaServerSideAdInsertionMediaSource.AdsLoader.State adsLoaderState;
  private ImaSdkSettings imaSdkSettings;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_my);
    MultiDex.install(this);

    // Initialize the IMA SDK as early as possible when the app starts. If your app already
    // overrides Application.onCreate(), call this method inside the onCreate() method.
    // https://developer.android.com/topic/performance/vitals/launch-time#app-creation
    ImaSdkFactory.getInstance().initialize(this, getImaSdkSettings());

    playerView = findViewById(R.id.player_view);

    // Checks if there is a saved AdsLoader state to be used later when initiating the AdsLoader.
    if (savedInstanceState != null) {
      Bundle adsLoaderStateBundle = savedInstanceState.getBundle(KEY_ADS_LOADER_STATE);
      if (adsLoaderStateBundle != null) {
        adsLoaderState =
            ImaServerSideAdInsertionMediaSource.AdsLoader.State.fromBundle(adsLoaderStateBundle);
      }
    }
  }

  @Override
  public void onStart() {
    super.onStart();
    if (Util.SDK_INT > 23) {
      initializePlayer();
      if (playerView != null) {
        playerView.onResume();
      }
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    if (Util.SDK_INT <= 23 || player == null) {
      initializePlayer();
      if (playerView != null) {
        playerView.onResume();
      }
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    if (Util.SDK_INT <= 23) {
      if (playerView != null) {
        playerView.onPause();
      }
      releasePlayer();
    }
  }

  @Override
  public void onStop() {
    super.onStop();
    if (Util.SDK_INT > 23) {
      if (playerView != null) {
        playerView.onPause();
      }
      releasePlayer();
    }
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    // Attempts to save the AdsLoader state to handle app backgrounding.
    if (adsLoaderState != null) {
      outState.putBundle(KEY_ADS_LOADER_STATE, adsLoaderState.toBundle());
    }
  }

  private void releasePlayer() {
    // Set the player references to null and release the player's resources.
    playerView.setPlayer(null);
    player.release();
    player = null;

    // Release the adsLoader state so that it can be initiated again.
    adsLoaderState = adsLoader.release();
  }

  // Create a server side ad insertion (SSAI) AdsLoader.
  private ImaServerSideAdInsertionMediaSource.AdsLoader createAdsLoader() {
    ImaServerSideAdInsertionMediaSource.AdsLoader.Builder adsLoaderBuilder =
        new ImaServerSideAdInsertionMediaSource.AdsLoader.Builder(this, playerView);

    // Attempts to set the AdsLoader state if available from a previous session.
    if (adsLoaderState != null) {
      adsLoaderBuilder.setAdsLoaderState(adsLoaderState);
    }

    Map<String, String> adTagParams = new HashMap<String, String>();
    // Update the adTagParams map with any parameters.
    // For more information, see https://support.google.com/admanager/answer/7320899
    adsLoader.replaceAdTagParameters(adTagParams);

    return adsLoaderBuilder
        .setAdEventListener(buildAdEventListener())
        .setImaSdkSettings(getImaSdkSettings())
        .build();
  }

  public AdEvent.AdEventListener buildAdEventListener() {
    logText = findViewById(R.id.logText);
    logText.setMovementMethod(new ScrollingMovementMethod());

    return event -> {
      AdEvent.AdEventType eventType = event.getType();
      if (eventType == AdEvent.AdEventType.AD_PROGRESS) {
        return;
      }
      String log = "IMA event: " + eventType;
      if (logText != null) {
        logText.append(log + "\n");
      }
      Log.i(LOG_TAG, log);
    };
  }

  private void initializePlayer() {
    adsLoader = createAdsLoader();

    // Set up the factory for media sources, passing the ads loader.
    DataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(this);

    DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(dataSourceFactory);

    // MediaSource.Factory to create the ad sources for the current player.
    ImaServerSideAdInsertionMediaSource.Factory adsMediaSourceFactory =
        new ImaServerSideAdInsertionMediaSource.Factory(adsLoader, mediaSourceFactory);

    // 'mediaSourceFactory' is an ExoPlayer component for the DefaultMediaSourceFactory.
    // 'adsMediaSourceFactory' is an ExoPlayer component for a MediaSource factory for IMA server
    // side inserted ad streams.
    mediaSourceFactory.setServerSideAdInsertionMediaSourceFactory(adsMediaSourceFactory);

    // Create a SimpleExoPlayer and set it as the player for content and ads.
    player = new ExoPlayer.Builder(this).setMediaSourceFactory(mediaSourceFactory).build();
    playerView.setPlayer(player);
    adsLoader.setPlayer(player);

    // Build an IMA SSAI media item to prepare the player with.
    Uri ssaiLiveUri =
        new ImaServerSideAdInsertionUriBuilder()
            .setAssetKey(SAMPLE_ASSET_KEY)
            .setFormat(CONTENT_TYPE_HLS) // Use CONTENT_TYPE_DASH for dash streams.
            .build();

    // Create the MediaItem to play, specifying the stream URI.
    MediaItem ssaiMediaItem = MediaItem.fromUri(ssaiLiveUri);

    // Prepare the content and ad to be played with the ExoPlayer.
    player.setMediaItem(ssaiMediaItem);
    player.prepare();

    // Set PlayWhenReady. If true, content and ads will autoplay.
    player.setPlayWhenReady(false);
  }

  private ImaSdkSettings getImaSdkSettings() {
    if (imaSdkSettings == null) {
      imaSdkSettings = ImaSdkFactory.getInstance().createImaSdkSettings();
      // Set any IMA SDK settings here.
    }
    return imaSdkSettings;
  }
}
