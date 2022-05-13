package com.google.ads.interactivemedia.v3.samples.videoplayerapp;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.ima.ImaServerSideAdInsertionMediaSource;
import androidx.media3.exoplayer.ima.ImaServerSideAdInsertionUriBuilder;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.util.EventLogger;
import androidx.media3.ui.PlayerView;
import androidx.multidex.MultiDex;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;

/** Main Activity. */
@OptIn(markerClass = UnstableApi.class)
public class MyActivity extends Activity {

  private static final String KEY_ADS_LOADER_STATE = "ads_loader_state";

  private PlayerView playerView;
  private ExoPlayer player;
  private ImaServerSideAdInsertionMediaSource.AdsLoader adsLoader;
  private ImaServerSideAdInsertionMediaSource.AdsLoader.State adsLoaderState;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_my);
    MultiDex.install(this);

    playerView = findViewById(R.id.player_view);

    // Checks if there is a saved AdsLoader state to be used later when initiating the AdsLoader.
    if (savedInstanceState != null) {
      Bundle adsLoaderStateBundle = savedInstanceState.getBundle(KEY_ADS_LOADER_STATE);
      if (adsLoaderStateBundle != null) {
        adsLoaderState =
            ImaServerSideAdInsertionMediaSource.AdsLoader.State.CREATOR.fromBundle(
                adsLoaderStateBundle);
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
    adsLoader.setPlayer(null);
    playerView.setPlayer(null);
    adsLoaderState = adsLoader.release();
    player.release();
    player = null;
  }

  private void initializePlayer() {
    CookieManager cookieManager = new CookieManager();
    cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ORIGINAL_SERVER);
    CookieHandler.setDefault(cookieManager);
    DefaultHttpDataSource.Factory httpDataSourceFactory = new DefaultHttpDataSource.Factory();

    // Set up the factory for media sources, passing the ads loader and ad view providers.
    DataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(this);

    // Create an SSAI AdsLoader.
    ImaServerSideAdInsertionMediaSource.AdsLoader.Builder adsLoaderBuilder =
        new ImaServerSideAdInsertionMediaSource.AdsLoader.Builder(/* context=*/ this, playerView);

    // Attempts to set the AdsLoader state if available from a previous session.
    if (adsLoaderState != null) {
      adsLoaderBuilder.setAdsLoaderState(adsLoaderState);
    }

    adsLoader = adsLoaderBuilder.build();

    DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(dataSourceFactory);

    // MediaSource.Factory to create the ad sources for the current player.
    ImaServerSideAdInsertionMediaSource.Factory adsMediaSourceFactory =
        new ImaServerSideAdInsertionMediaSource.Factory(adsLoader, mediaSourceFactory);

    mediaSourceFactory.setServerSideAdInsertionMediaSourceFactory(adsMediaSourceFactory);

    // Create a SimpleExoPlayer and set it as the player for content and ads.
    player = new ExoPlayer.Builder(this).setMediaSourceFactory(mediaSourceFactory).build();
    player.addAnalyticsListener(new EventLogger(/* trackSelector= */ null));
    playerView.setPlayer(player);
    adsLoader.setPlayer(player);

    // Build an IMA SSAI media item to prepare the player with.
    Uri ssaiLiveUri =
        new ImaServerSideAdInsertionUriBuilder()
            .setAssetKey(getString(R.string.asset_key))
            .setFormat(C.TYPE_HLS)
            .build();

    // Create the MediaItem to play, specifying the stream URI.
    MediaItem ssaiMediaItem = MediaItem.fromUri(ssaiLiveUri);

    // Prepare the content and ad to be played with the ExoPlayer.
    player.setMediaItem(ssaiMediaItem);
    player.prepare();

    // Set PlayWhenReady. If true, content and ads will autoplay.
    player.setPlayWhenReady(false);
  }
}
