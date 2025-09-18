package com.google.ads.interactivemedia.v3.samples.videoplayerapp;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.google.ads.interactivemedia.v3.api.Ad;
import com.google.ads.interactivemedia.v3.api.AdProgressInfo;
import com.google.ads.interactivemedia.v3.api.customui.CustomUi;
import com.google.ads.interactivemedia.v3.api.customui.UiButton;
import com.google.ads.interactivemedia.v3.api.customui.UiConfig;
import com.google.ads.interactivemedia.v3.api.customui.UiFallbackImage;
import com.google.ads.interactivemedia.v3.api.customui.UiLabel;
import com.google.ads.interactivemedia.v3.api.customui.UiSkip;
import com.google.ads.interactivemedia.v3.api.customui.UiVastIcon;
import com.google.ads.interactivemedia.v3.samples.videoplayerapp.SampleAdsWrapper.Logger;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Manages custom UI rendering for a single ad. */
final class CustomUiManager {

  private final Context context;
  private final ViewGroup adUiContainer;
  private final CustomUi customUi;
  private final Ad ad;
  private final @Nullable Logger logger;
  private Optional<View> adUiView = Optional.empty();
  private final Map<String, View> uiElements = new HashMap<>();
  private final Boolean renderFallbackImages = false;

  public CustomUiManager(
      Context context,
      ViewGroup adUiContainer,
      CustomUi customUi,
      Ad ad,
      Logger logger,
      Boolean renderFallbackImages) {
    this.context = context;
    this.adUiContainer = adUiContainer;
    this.customUi = customUi;
    this.ad = ad;
    this.logger = logger;
    this.renderFallbackImages = renderFallbackImages;
  }

  private void log(String message) {
    if (logger != null) {
      logger.log(message);
    }
  }

  public void render() {
    adUiContainer.post(
        () -> {
          UiConfig uiConfig = customUi.getConfig();

          LinearLayout adUiLayout = new LinearLayout(context);
          adUiLayout.setLayoutParams(
              new FrameLayout.LayoutParams(
                  ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
          adUiLayout.setOrientation(LinearLayout.VERTICAL);
          adUiLayout.setGravity(Gravity.CENTER | Gravity.CENTER);

          LinearLayout.LayoutParams childLayoutParams =
              new LinearLayout.LayoutParams(
                  ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

          UiButton callToAction = uiConfig.getCallToAction();
          if (callToAction != null) {
            Button callToActionButton = renderButton(callToAction, childLayoutParams);
            adUiLayout.addView(callToActionButton);
            uiElements.put(callToAction.getId(), callToActionButton);
          }

          if (uiConfig.getSkip() != null) {
            UiSkip skip = uiConfig.getSkip();
            TextView countdown = renderLabel(skip.getCountdown(), childLayoutParams);
            adUiLayout.addView(countdown);
            uiElements.put(skip.getCountdown().getId(), countdown);

            Button skipButton = renderButton(skip.getButton(), childLayoutParams);
            adUiLayout.addView(skipButton);
            skipButton.setVisibility(View.INVISIBLE);
            uiElements.put(skip.getButton().getId(), skipButton);
          }

          List<UiVastIcon> icons = uiConfig.getIcons();
          if (icons != null) {
            for (UiVastIcon icon : icons) {
              ImageView iconView = renderVastIcon(icon, childLayoutParams);
              adUiLayout.addView(iconView);
              uiElements.put(icon.getId(), iconView);
            }
          }

          adUiView = Optional.of(adUiLayout);
          adUiContainer.addView(adUiView.get());
          setVisibleElements();
        });
  }

  private ImageView renderVastIcon(
      UiVastIcon uiVastIcon, LinearLayout.LayoutParams childLayoutParams) {
    ImageView icon = new ImageView(context);
    icon.setLayoutParams(childLayoutParams);
    icon.setBackgroundColor(Color.argb(100, 0, 0, 255));

    String imageUrl = uiVastIcon.getImage().getUrl();
    int width = uiVastIcon.getImage().getWidth();
    int height = uiVastIcon.getImage().getHeight();
    Glide.with(context)
        .asBitmap()
        .load(new GlideUrl(imageUrl))
        .override(/* width= */ width, /* height= */ height)
        .into(icon);

    icon.setOnTouchListener(
        (view, event) -> {
          view.performClick();
          if (event.getAction() == MotionEvent.ACTION_DOWN) {
            customUi.onClick(uiVastIcon.getId(), event);
            // open a new browser window to the icon clickthrough URL.
            if (!renderFallbackImages && uiVastIcon.getClickUrl() != null) {
              launchUrlInBrowser(uiVastIcon.getClickUrl());
            } else {
              log("No web browser available to open clickthrough URL.");
              // Open a modal with the icon fallback image on platforms without a web browser.
              List<UiFallbackImage> fallbackImages = uiVastIcon.getFallbackImages();
              if (fallbackImages != null && fallbackImages.size() > 0) {
                renderFallbackImageDialog(fallbackImages.get(0));
              }
            }
            return true;
          } else {
            return false;
          }
        });
    return icon;
  }

  private Button renderButton(UiButton uiButton, LinearLayout.LayoutParams childLayoutParams) {
    Button button = new Button(context);
    button.setLayoutParams(childLayoutParams);
    button.setText(uiButton.getText());
    button.setOnTouchListener(
        (view, event) -> {
          view.performClick();
          if (event.getAction() == MotionEvent.ACTION_DOWN) {
            customUi.onClick(uiButton.getId(), event);
            return true;
          } else {
            return false;
          }
        });
    return button;
  }

  private TextView renderLabel(UiLabel uiLabel, LinearLayout.LayoutParams childLayoutParams) {
    TextView countdown = new TextView(context);
    countdown.setLayoutParams(childLayoutParams);
    countdown.setTextColor(Color.WHITE);
    countdown.setText(uiLabel.getText());
    return countdown;
  }

  private void renderFallbackImageDialog(UiFallbackImage fallbackImage) {
    Dialog dialog = new Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
    FrameLayout layout = new FrameLayout(context);

    ImageView imageView = new ImageView(context);
    Glide.with(context)
        .asBitmap()
        .load(new GlideUrl(fallbackImage.getUrl()))
        .override(fallbackImage.getWidth(), fallbackImage.getHeight())
        .into(imageView);
    layout.addView(imageView);

    Button closeButton = new Button(context);
    FrameLayout.LayoutParams buttonParams =
        new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    buttonParams.gravity = Gravity.TOP | Gravity.END;
    closeButton.setLayoutParams(buttonParams);
    closeButton.setText("X");
    closeButton.setOnClickListener(
        (view) -> {
          dialog.dismiss();
          if (uiElements.containsKey(fallbackImage.getId())) {
            uiElements.remove(fallbackImage.getId());
          }
          setVisibleElements();
        });
    layout.addView(closeButton);

    dialog.setContentView(layout);
    dialog.show();

    uiElements.put(fallbackImage.getId(), imageView);
    setVisibleElements();
  }

  private void setVisibleElements() {
    Map<String, View> visibleUiElements = new HashMap<>();
    uiElements.forEach(
        (id, view) -> {
          if (view.getVisibility() == View.VISIBLE) {
            visibleUiElements.put(id, view);
          }
        });
    customUi.setVisibleElements(visibleUiElements);
  }

  public void onProgress(AdProgressInfo adProgressInfo) {
    UiConfig uiConfig = customUi.getConfig();
    UiSkip uiSkip = uiConfig.getSkip();
    if (uiSkip != null) {
      View countdownView = uiElements.get(uiSkip.getCountdown().getId());
      View skipButtonView = uiElements.get(uiSkip.getButton().getId());
      if (countdownView instanceof TextView countdown && skipButtonView instanceof Button button) {
        double skipTimeOffset = ad.getSkipTimeOffset();
        double currentTime = adProgressInfo.getCurrentTime();
        if (currentTime > skipTimeOffset) {
          if (button.getVisibility() == View.INVISIBLE) {
            button.setEnabled(true);
            button.setVisibility(View.VISIBLE);
            countdown.setVisibility(View.INVISIBLE);
            setVisibleElements();
          }
        } else {
          countdown.setText(String.format(Locale.US, "%d", (int) (skipTimeOffset - currentTime)));
        }
      }
    }
  }

  private boolean launchUrlInBrowser(final String url) {
    Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
    if (!(context instanceof Activity)) {
      view.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }
    try {
      context.startActivity(view);
    } catch (ActivityNotFoundException e) {
      return false;
    }
    return true;
  }

  public void dispose() {
    adUiContainer.post(
        () -> {
          if (adUiView.isPresent()) {
            adUiContainer.removeView(adUiView.get());
            adUiView = Optional.empty();
          }
          uiElements.clear();
        });
  }
}
