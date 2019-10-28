package com.google.android.exoplayer2.demo;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.common.collect.ImmutableMap;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;
import org.json.JSONObject;

/** Helper class for requesting DAI streams and handling media verification. */
public class DaiHelper {

  private final ImmutableMap<String, String> offlineParams =
      ImmutableMap.of("osb", "1", "doaf", "1");
  private final RequestQueue requestQueue;
  private final DemoApplication application;

  private Set<String> unpingedVerifications;

  public DaiHelper(DemoApplication application) {
    this.application = application;
    this.requestQueue = Volley.newRequestQueue(application);
    SharedPreferences sharedPreferences =
        application.getSharedPreferences(
            application.getString(R.string.unpinged_set_key), Context.MODE_PRIVATE);
    this.unpingedVerifications =
        new HashSet<String>(
            sharedPreferences.getStringSet(
                application.getString(R.string.unpinged_set_key), new HashSet<>()));
  }

  /**
   * Requests a new DAI stream, saving the manifest and verification URLs.
   *
   * @param url The DAI stream request URL.
   * @param name A unique identifier for the stream.
   * @param activity The activity managing the download.
   */
  public void requestStream(String url, String name, Activity activity) {
    JsonObjectRequest jsonObjectRequest =
        new JsonObjectRequest(
            Request.Method.POST,
            url,
            new JSONObject(offlineParams),
            new Response.Listener<JSONObject>() {
              @Override
              public void onResponse(JSONObject response) {
                String streamManifest = response.optString("stream_manifest", "");
                storeStreamInfo(
                    name, streamManifest, application.getString(R.string.manifest_map_key));

                String mediaVerificationUrl = response.optString("media_verification_url", "");
                storeStreamInfo(
                    name,
                    mediaVerificationUrl,
                    application.getString(R.string.verification_map_key));

                Uri stream = Uri.parse(streamManifest);
                application.getDownloadTracker().toggleDownload(activity, name, stream, ".m3u8");
              }
            },
            new Response.ErrorListener() {
              @Override
              public void onErrorResponse(VolleyError error) {
                System.out.println(error);
              }
            });
    requestQueue.add(jsonObjectRequest);
  }

  /**
   * Store and persist information about the stream.
   * @param name The unique identifier for the stream.
   * @param info The info about the stream to save.
   * @param sharedPreferencesKey The key for the shared preferences file in which to save the info.
   */
  private void storeStreamInfo(String name, String info, String sharedPreferencesKey) {
    SharedPreferences sharedPreferences =
        application.getSharedPreferences(sharedPreferencesKey, Context.MODE_PRIVATE);
    SharedPreferences.Editor editor = sharedPreferences.edit();
    editor.putString(name, info);
    editor.commit();
  }

  /** Handles HLS metadata as playback occurs. */
  public void handleMetadata(String verificationUrl, Metadata metadata) {
    for (int i = 0; i < metadata.length(); i++) {
      Metadata.Entry entry = metadata.get(i);
      String metadataString = entry.toString();
      String mediaId = metadataString.substring(metadataString.indexOf("google_"));
      if (ConnectionUtils.isConnected(application.getApplicationContext())) {
        sendMediaVerification(verificationUrl, mediaId);
      } else {
        saveMediaVerification(verificationUrl, mediaId);
      }
    }
  }

  /** Sends a media verification ping to DAI servers. */
  private void sendMediaVerification(String verificationUrl, String mediaId) {
    StringRequest request =
        new StringRequest(
            Request.Method.GET,
            verificationUrl + mediaId,
            new Response.Listener<String>() {
              @Override
              public void onResponse(String response) {
                System.out.println("Media Verification Success for:");
                System.out.println(verificationUrl + mediaId);
              }
            },
            new Response.ErrorListener() {
              @Override
              public void onErrorResponse(VolleyError error) {
                System.out.println("Media Verification Error");
                System.out.println(error);
              }
            });
    requestQueue.add(request);
  }

  /** Creates and saves a media verification ping, to be sent when a connection is discovered. */
  private void saveMediaVerification(String verificationUrl, String mediaId) {
    TimeZone tz = TimeZone.getTimeZone("UTC");
    DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    df.setTimeZone(tz);
    String time = df.format(new Date());
    String verificationRequest = verificationUrl + mediaId + "?ts=" + time;

    unpingedVerifications.add(verificationRequest);
  }

  /**
   * Stores any unsent media verification pings in local storage. Should be called anytime the
   * application is closed.
   */
  public void storeUnpingedVerifications() {
    SharedPreferences sharedPreferences =
        application.getSharedPreferences(
            application.getString(R.string.unpinged_set_key), Context.MODE_PRIVATE);
    SharedPreferences.Editor editor = sharedPreferences.edit();
    editor.putStringSet(
        application.getString(R.string.unpinged_set_key), new HashSet<>(unpingedVerifications));
    editor.commit();
  }

  /** Sends any unsent media verification pings. */
  public void sendUnpingedVerifications() {
    if (!ConnectionUtils.isConnected(application)) {
      return;
    }
    for (String verification : unpingedVerifications) {
      StringRequest request =
          new StringRequest(
              Request.Method.GET,
              verification,
              new Response.Listener<String>() {
                @Override
                public void onResponse(String response) {
                  System.out.println("Media Verification Success for:");
                  System.out.println(verification);
                }
              },
              new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                  System.out.println("Media Verification Error:");
                  System.out.println(error);
                }
              });
      requestQueue.add(request);
    }
    unpingedVerifications = new HashSet<>();
  }
}
