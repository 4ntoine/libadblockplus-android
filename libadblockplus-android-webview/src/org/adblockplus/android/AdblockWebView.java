/*
 * This file is part of Adblock Plus <https://adblockplus.org/>,
 * Copyright (C) 2006-2016 Eyeo GmbH
 *
 * Adblock Plus is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as
 * published by the Free Software Foundation.
 *
 * Adblock Plus is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Adblock Plus.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.adblockplus.android;

import org.adblockplus.libadblockplus.AppInfo;
import org.adblockplus.libadblockplus.Filter;
import org.adblockplus.libadblockplus.FilterEngine;
import org.adblockplus.libadblockplus.JsEngine;
import org.adblockplus.libadblockplus.Subscription;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.http.SslError;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.GeolocationPermissions;
import android.webkit.HttpAuthHandler;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;  // makes android min version to be 21
import android.webkit.WebResourceResponse;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.JavascriptInterface; // makes android min version to be 17
import android.webkit.WebViewClient;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * WebView with ad blocking
 */
public class AdblockWebView extends WebView
{
  private static final String TAG = Utils.getTag(AdblockWebView.class);

  private volatile boolean addDomListener = true;

  /**
   * Warning: do not rename (used in injected JS by method name)
   * @param value set if one need to set DOM listener
   */
  @JavascriptInterface
  public void setAddDomListener(boolean value)
  {
    this.addDomListener = value;
  }

  @JavascriptInterface
  public boolean getAddDomListener()
  {
    return addDomListener;
  }

  private WebChromeClient extWebChromeClient;

  @Override
  public void setWebChromeClient(WebChromeClient client)
  {
    extWebChromeClient = client;
  }

  private boolean debugMode;

  public boolean isDebugMode()
  {
    return debugMode;
  }

  /**
   * Set to true to see debug log output int AdblockWebView and JS console
   * @param debugMode is debug mode
   */
  public void setDebugMode(boolean debugMode)
  {
    this.debugMode = debugMode;
  }

  private void d(String message)
  {
    if (debugMode)
    {
      Log.d(TAG, message);
    }
  }

  private void w(String message)
  {
    if (debugMode)
    {
      Log.w(TAG, message);
    }
  }

  private void e(String message, Throwable t)
  {
    Log.e(TAG, message, t);
  }

  private void e(String message)
  {
    Log.e(TAG, message);
  }

  private static final String BRIDGE_TOKEN = "{{BRIDGE}}";
  private static final String DEBUG_TOKEN = "{{DEBUG}}";
  private static final String HIDE_TOKEN = "{{HIDE}}";
  private static final String BRIDGE = "jsBridge";

  private String readScriptFile(String filename) throws IOException
  {
    return Utils
      .readAssetAsString(getContext(), filename)
      .replace(BRIDGE_TOKEN, BRIDGE)
      .replace(DEBUG_TOKEN, (debugMode ? "" : "//"));
  }

  private void runScript(String script)
  {
    if (Build.VERSION.SDK_INT >= 19)
    {
      evaluateJavascript(script, null);
    }
    else
    {
      loadUrl("javascript:" + script);
    }
  }

  private Subscription acceptableAdsSubscription;

  private boolean acceptableAdsEnabled = true;

  public boolean isAcceptableAdsEnabled()
  {
    return acceptableAdsEnabled;
  }

  /**
   * Enable or disable Acceptable Ads
   * @param enabled enabled
   */
  public void setAcceptableAdsEnabled(boolean enabled)
  {
    this.acceptableAdsEnabled = enabled;

    if (filterEngine != null)
    {
      applyAcceptableAds();
    }
  }

  private final static String EXCEPTIONS_URL = "subscriptions_exceptionsurl";

  private void applyAcceptableAds()
  {
    if (acceptableAdsEnabled)
    {
      if (acceptableAdsSubscription == null)
      {
        String url = filterEngine.getPref(EXCEPTIONS_URL).toString();
        if (url == null)
        {
          w("no AA subscription url");
          return;
        }

        acceptableAdsSubscription = filterEngine.getSubscription(url);
        acceptableAdsSubscription.addToList();
        d("AA subscription added (" + url + ")");
      }
    }
    else
    {
      if (acceptableAdsSubscription != null)
      {
        removeAcceptableAdsSubscription();
      }
    }
  }

  private void removeAcceptableAdsSubscription()
  {
    acceptableAdsSubscription.removeFromList();
    acceptableAdsSubscription = null;
    d("AA subscription removed");
  }

  private boolean disposeFilterEngine;

  private JsEngine jsEngine;

  public JsEngine getJsEngine()
  {
    return jsEngine;
  }

  private FilterEngine filterEngine;

  public FilterEngine getFilterEngine()
  {
    return filterEngine;
  }

  private Integer loadError;

  /**
   * Set external filter engine. A new (internal) is created automatically if not set
   * Don't forget to invoke {@link #dispose()} if not using external filter engine
   * @param newFilterEngine external filter engine
   */
  public void setFilterEngine(FilterEngine newFilterEngine)
  {
    if (filterEngine != null && newFilterEngine != null && newFilterEngine == filterEngine)
    {
      return;
    }

    if (filterEngine != null)
    {
      if (acceptableAdsEnabled && acceptableAdsSubscription != null)
      {
        removeAcceptableAdsSubscription();
      }

      if (disposeFilterEngine)
      {
        filterEngine.dispose();
      }
    }

    filterEngine = newFilterEngine;
    disposeFilterEngine = false;

    if (filterEngine != null)
    {
      applyAcceptableAds();

      if (jsEngine != null)
      {
        jsEngine.dispose();
      }
    }

    jsEngine = null;
  }

  private WebChromeClient intWebChromeClient = new WebChromeClient()
  {
    @Override
    public void onReceivedTitle(WebView view, String title)
    {
      if (extWebChromeClient != null)
      {
        extWebChromeClient.onReceivedTitle(view, title);
      }
    }

    @Override
    public void onReceivedIcon(WebView view, Bitmap icon)
    {
      if (extWebChromeClient != null)
      {
        extWebChromeClient.onReceivedIcon(view, icon);
      }
    }

    @Override
    public void onReceivedTouchIconUrl(WebView view, String url, boolean precomposed)
    {
      if (extWebChromeClient != null)
      {
        extWebChromeClient.onReceivedTouchIconUrl(view, url, precomposed);
      }
    }

    @Override
    public void onShowCustomView(View view, CustomViewCallback callback)
    {
      if (extWebChromeClient != null)
      {
        extWebChromeClient.onShowCustomView(view, callback);
      }
    }

    @Override
    public void onShowCustomView(View view, int requestedOrientation, CustomViewCallback callback)
    {
      if (extWebChromeClient != null)
      {
        extWebChromeClient.onShowCustomView(view, requestedOrientation, callback);
      }
    }

    @Override
    public void onHideCustomView()
    {
      if (extWebChromeClient != null)
      {
        extWebChromeClient.onHideCustomView();
      }
    }

    @Override
    public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture,
                                  Message resultMsg)
    {
      if (extWebChromeClient != null)
      {
        return extWebChromeClient.onCreateWindow(view, isDialog, isUserGesture, resultMsg);
      }
      else
      {
        return super.onCreateWindow(view, isDialog, isUserGesture, resultMsg);
      }
    }

    @Override
    public void onRequestFocus(WebView view)
    {
      if (extWebChromeClient != null)
      {
        extWebChromeClient.onRequestFocus(view);
      }
    }

    @Override
    public void onCloseWindow(WebView window)
    {
      if (extWebChromeClient != null)
      {
        extWebChromeClient.onCloseWindow(window);
      }
    }

    @Override
    public boolean onJsAlert(WebView view, String url, String message, JsResult result)
    {
      if (extWebChromeClient != null)
      {
        return extWebChromeClient.onJsAlert(view, url, message, result);
      }
      else
      {
        return super.onJsAlert(view, url, message, result);
      }
    }

    @Override
    public boolean onJsConfirm(WebView view, String url, String message, JsResult result)
    {
      if (extWebChromeClient != null)
      {
        return extWebChromeClient.onJsConfirm(view, url, message, result);
      }
      else
      {
        return super.onJsConfirm(view, url, message, result);
      }
    }

    @Override
    public boolean onJsPrompt(WebView view, String url, String message, String defaultValue,
                              JsPromptResult result)
    {
      if (extWebChromeClient != null)
      {
        return extWebChromeClient.onJsPrompt(view, url, message, defaultValue, result);
      }
      else
      {
        return super.onJsPrompt(view, url, message, defaultValue, result);
      }
    }

    @Override
    public boolean onJsBeforeUnload(WebView view, String url, String message, JsResult result)
    {
      if (extWebChromeClient != null)
      {
        return extWebChromeClient.onJsBeforeUnload(view, url, message, result);
      }
      else
      {
        return super.onJsBeforeUnload(view, url, message, result);
      }
    }

    @Override
    public void onExceededDatabaseQuota(String url, String databaseIdentifier, long quota,
                                        long estimatedDatabaseSize, long totalQuota,
                                        WebStorage.QuotaUpdater quotaUpdater)
    {
      if (extWebChromeClient != null)
      {
        extWebChromeClient.onExceededDatabaseQuota(url, databaseIdentifier, quota,
          estimatedDatabaseSize, totalQuota, quotaUpdater);
      }
      else
      {
        super.onExceededDatabaseQuota(url, databaseIdentifier, quota,
          estimatedDatabaseSize, totalQuota, quotaUpdater);
      }
    }

    @Override
    public void onReachedMaxAppCacheSize(long requiredStorage, long quota,
                                         WebStorage.QuotaUpdater quotaUpdater)
    {
      if (extWebChromeClient != null)
      {
        extWebChromeClient.onReachedMaxAppCacheSize(requiredStorage, quota, quotaUpdater);
      }
      else
      {
        super.onReachedMaxAppCacheSize(requiredStorage, quota, quotaUpdater);
      }
    }

    @Override
    public void onGeolocationPermissionsShowPrompt(String origin,
                                                   GeolocationPermissions.Callback callback)
    {
      if (extWebChromeClient != null)
      {
        extWebChromeClient.onGeolocationPermissionsShowPrompt(origin, callback);
      }
      else
      {
        super.onGeolocationPermissionsShowPrompt(origin, callback);
      }
    }

    @Override
    public void onGeolocationPermissionsHidePrompt()
    {
      if (extWebChromeClient != null)
      {
        extWebChromeClient.onGeolocationPermissionsHidePrompt();
      }
      else
      {
        super.onGeolocationPermissionsHidePrompt();
      }
    }

    @Override
    public boolean onJsTimeout()
    {
      if (extWebChromeClient != null)
      {
        return extWebChromeClient.onJsTimeout();
      }
      else
      {
        return super.onJsTimeout();
      }
    }

    @Override
    public void onConsoleMessage(String message, int lineNumber, String sourceID)
    {
      if (extWebChromeClient != null)
      {
        extWebChromeClient.onConsoleMessage(message, lineNumber, sourceID);
      }
      else
      {
        super.onConsoleMessage(message, lineNumber, sourceID);
      }
    }

    @Override
    public boolean onConsoleMessage(ConsoleMessage consoleMessage)
    {
      if (extWebChromeClient != null)
      {
        return extWebChromeClient.onConsoleMessage(consoleMessage);
      }
      else
      {
        return super.onConsoleMessage(consoleMessage);
      }
    }

    @Override
    public Bitmap getDefaultVideoPoster()
    {
      if (extWebChromeClient != null)
      {
        return extWebChromeClient.getDefaultVideoPoster();
      }
      else
      {
        return super.getDefaultVideoPoster();
      }
    }

    @Override
    public View getVideoLoadingProgressView()
    {
      if (extWebChromeClient != null)
      {
        return extWebChromeClient.getVideoLoadingProgressView();
      }
      else
      {
        return super.getVideoLoadingProgressView();
      }
    }

    @Override
    public void getVisitedHistory(ValueCallback<String[]> callback)
    {
      if (extWebChromeClient != null)
      {
        extWebChromeClient.getVisitedHistory(callback);
      }
      else
      {
        super.getVisitedHistory(callback);
      }
    }

    @Override
    public void onProgressChanged(WebView view, int newProgress)
    {
      d("Loading progress=" + newProgress + "%");

      // addDomListener is changed to 'false' in `setAddDomListener` invoked from injected JS
      if (getAddDomListener() && loadError == null && injectJs != null)
      {
        d("Injecting script");
        runScript(injectJs);

        if (allowDraw && loading)
        {
          startPreventDrawing();
        }
      }

      if (extWebChromeClient != null)
      {
        extWebChromeClient.onProgressChanged(view, newProgress);
      }
    }
  };

  /**
   * Default (in some conditions) start redraw delay after DOM modified with injected JS (millis)
   */
  public static final int ALLOW_DRAW_DELAY = 200;
  /*
     The value could be different for devices and completely unclear why we need it and
     how to measure actual value
  */

  private int allowDrawDelay = ALLOW_DRAW_DELAY;

  public int getAllowDrawDelay()
  {
    return allowDrawDelay;
  }

  /**
   * Set start redraw delay after DOM modified with injected JS
   * (used to prevent flickering after 'DOM ready')
   * @param allowDrawDelay delay (in millis)
   */
  public void setAllowDrawDelay(int allowDrawDelay)
  {
    if (allowDrawDelay < 0)
      throw new IllegalArgumentException("Negative value is not allowed");

    this.allowDrawDelay = allowDrawDelay;
  }

  private WebViewClient extWebViewClient;

  @Override
  public void setWebViewClient(WebViewClient client)
  {
    extWebViewClient = client;
  }

  private static final Pattern RE_JS = Pattern.compile("\\.js$", Pattern.CASE_INSENSITIVE);
  private static final Pattern RE_CSS = Pattern.compile("\\.css$", Pattern.CASE_INSENSITIVE);
  private static final Pattern RE_IMAGE = Pattern.compile("\\.(?:gif|png|jpe?g|bmp|ico)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern RE_FONT = Pattern.compile("\\.(?:ttf|woff)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern RE_HTML = Pattern.compile("\\.html?$", Pattern.CASE_INSENSITIVE);

  private WebViewClient intWebViewClient;

  /**
   * WebViewClient for API pre 21
   * (does not have Referers information)
   */
  class AdblockWebViewClient extends WebViewClient
  {
    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url)
    {
      if (extWebViewClient != null)
      {
        return extWebViewClient.shouldOverrideUrlLoading(view, url);
      }
      else
      {
        return super.shouldOverrideUrlLoading(view, url);
      }
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon)
    {
      if (loading)
      {
        stopAbpLoading();
      }

      startAbpLoading(url);

      if (extWebViewClient != null)
      {
        extWebViewClient.onPageStarted(view, url, favicon);
      }
      else
      {
        super.onPageStarted(view, url, favicon);
      }
    }

    @Override
    public void onPageFinished(WebView view, String url)
    {
      loading = false;
      if (extWebViewClient != null)
      {
        extWebViewClient.onPageFinished(view, url);
      }
      else
      {
        super.onPageFinished(view, url);
      }
    }

    @Override
    public void onLoadResource(WebView view, String url)
    {
      if (extWebViewClient != null)
      {
        extWebViewClient.onLoadResource(view, url);
      }
      else
      {
        super.onLoadResource(view, url);
      }
    }

    @Override
    public void onTooManyRedirects(WebView view, Message cancelMsg, Message continueMsg)
    {
      if (extWebViewClient != null)
      {
        extWebViewClient.onTooManyRedirects(view, cancelMsg, continueMsg);
      }
      else
      {
        super.onTooManyRedirects(view, cancelMsg, continueMsg);
      }
    }

    @Override
    public void onReceivedError(WebView view, int errorCode, String description, String failingUrl)
    {
      e("Load error:" +
        " code=" + errorCode +
        " with description=" + description +
        " for url=" + failingUrl);
      loadError = errorCode;

      stopAbpLoading();

      if (extWebViewClient != null)
      {
        extWebViewClient.onReceivedError(view, errorCode, description, failingUrl);
      }
      else
      {
        super.onReceivedError(view, errorCode, description, failingUrl);
      }
    }

    @Override
    public void onFormResubmission(WebView view, Message dontResend, Message resend)
    {
      if (extWebViewClient != null)
      {
        extWebViewClient.onFormResubmission(view, dontResend, resend);
      }
      else
      {
        super.onFormResubmission(view, dontResend, resend);
      }
    }

    @Override
    public void doUpdateVisitedHistory(WebView view, String url, boolean isReload)
    {
      if (extWebViewClient != null)
      {
        extWebViewClient.doUpdateVisitedHistory(view, url, isReload);
      }
      else
      {
        super.doUpdateVisitedHistory(view, url, isReload);
      }
    }

    @Override
    public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error)
    {
      if (extWebViewClient != null)
      {
        extWebViewClient.onReceivedSslError(view, handler, error);
      }
      else
      {
        super.onReceivedSslError(view, handler, error);
      }
    }

    @Override
    public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler, String host, String realm)
    {
      if (extWebViewClient != null)
      {
        extWebViewClient.onReceivedHttpAuthRequest(view, handler, host, realm);
      }
      else
      {
        super.onReceivedHttpAuthRequest(view, handler, host, realm);
      }
    }

    @Override
    public boolean shouldOverrideKeyEvent(WebView view, KeyEvent event)
    {
      if (extWebViewClient != null)
      {
        return extWebViewClient.shouldOverrideKeyEvent(view, event);
      }
      else
      {
        return super.shouldOverrideKeyEvent(view, event);
      }
    }

    @Override
    public void onUnhandledKeyEvent(WebView view, KeyEvent event)
    {
      if (extWebViewClient != null)
      {
        extWebViewClient.onUnhandledKeyEvent(view, event);
      }
      else
      {
        super.onUnhandledKeyEvent(view, event);
      }
    }

    @Override
    public void onScaleChanged(WebView view, float oldScale, float newScale)
    {
      if (extWebViewClient != null)
      {
        extWebViewClient.onScaleChanged(view, oldScale, newScale);
      }
      else
      {
        super.onScaleChanged(view, oldScale, newScale);
      }
    }

    @Override
    public void onReceivedLoginRequest(WebView view, String realm, String account, String args)
    {
      if (extWebViewClient != null)
      {
        extWebViewClient.onReceivedLoginRequest(view, realm, account, args);
      }
      else
      {
        super.onReceivedLoginRequest(view, realm, account, args);
      }
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, String url)
    {
      // if dispose() was invoke, but the page is still loading then just let it go
      if (filterEngine == null)
      {
        e("FilterEngine already disposed");
        return null;
      }

      // Determine the content
      FilterEngine.ContentType contentType = null;
      if (RE_JS.matcher(url).find())
      {
        contentType = FilterEngine.ContentType.SCRIPT;
      }
      else if (RE_CSS.matcher(url).find())
      {
        contentType = FilterEngine.ContentType.STYLESHEET;
      }
      else if (RE_IMAGE.matcher(url).find())
      {
        contentType = FilterEngine.ContentType.IMAGE;
      }
      else if (RE_FONT.matcher(url).find())
      {
        contentType = FilterEngine.ContentType.FONT;
      }
      else if (RE_HTML.matcher(url).find())
      {
        contentType = FilterEngine.ContentType.SUBDOCUMENT;
      }
      else
      {
        contentType = FilterEngine.ContentType.OTHER;
      }
      // Check if we should block ... we sadly do not have the referrer chain here,
      // might also be hard to get as we do not have HTTP headers here
      Filter filter = filterEngine.matches(url, contentType, new String[0]);
      if (filter != null && filter.getType().equals(Filter.Type.BLOCKING))
      {
        w("Blocked loading " + url);
        // If we should block, return empty response which results in a 404
        return new WebResourceResponse("text/plain", "UTF-8", null);
      }

      d("Allowed loading " + url);

      // Otherwise, continue by returning null
      return null;
    }
  }

  private void clearReferers()
  {
    d("Clearing referers");
    url2Referer.clear();
  }

  /**
   * WebViewClient for API 21 and newer
   * (has Referer since it overrides `shouldInterceptRequest(..., request)` with referer)
   */
  class AdblockWebViewClient21 extends AdblockWebViewClient
  {
    @Override
    // makes android min version to be 21
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request)
    {
      // here we just trying to fill url -> referer map
      // blocking/allowing loading will happen in `shouldInterceptRequest(WebView,String)`

      String url = request.getUrl().toString();
      String referer = request.getRequestHeaders().get("Referer");

      if (referer != null)
      {
        d("Remember referer " + referer + " for " + url);
        url2Referer.put(url, referer);
      }
      else
      {
        w("No referer for " + url);

        if (request.isForMainFrame() && AdblockWebView.this.url != null)
        {
          d("Request is for main frame, adding current url " + AdblockWebView.this.url + " as referer");
          url2Referer.put(url, AdblockWebView.this.url);
        }
      }

      return super.shouldInterceptRequest(view, request);
    }
  }

  private Map<String, String> url2Referer = Collections.synchronizedMap(new HashMap<String, String>());

  private void initAbp()
  {
    addJavascriptInterface(this, BRIDGE);

    if (Build.VERSION.SDK_INT >= 21)
    {
      intWebViewClient = new AdblockWebViewClient21();
    }
    else
    {
      intWebViewClient = new AdblockWebViewClient();
    }

    super.setWebChromeClient(intWebChromeClient);
    super.setWebViewClient(intWebViewClient);
  }

  /**
   * Build app info using Android package information
   * @param context context
   * @param developmentBuild if it's dev build
   * @return app info required to build JsEngine
   */
  public static AppInfo buildAppInfo(final Context context, boolean developmentBuild)
  {
    String version = "0";
    try
    {
      final PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
      version = info.versionName;
      if (developmentBuild)
        version += "." + info.versionCode;
    }
    catch (final PackageManager.NameNotFoundException e)
    {
      Log.e(TAG, "Failed to get the application version number", e);
    }
    final String sdkVersion = String.valueOf(Build.VERSION.SDK_INT);
    final String locale = Locale.getDefault().toString().replace('_', '-');

    return AppInfo.builder()
      .setVersion(version)
      .setApplicationVersion(sdkVersion)
      .setLocale(locale)
      .setDevelopmentBuild(developmentBuild)
      .build();
  }

  /**
   * Build JsEngine required to build FilterEngine
   * @param context context
   * @param developmentBuild if it's dev build
   * @return JsEngine
   */
  public static JsEngine buildJsEngine(Context context, boolean developmentBuild)
  {
    JsEngine jsEngine = new JsEngine(buildAppInfo(context, developmentBuild));
    jsEngine.setDefaultFileSystem(context.getCacheDir().getAbsolutePath());
    jsEngine.setWebRequest(new AndroidWebRequest(true)); // 'true' because we need element hiding
    return jsEngine;
  }

  private void createFilterEngine()
  {
    w("Creating FilterEngine");
    jsEngine = buildJsEngine(getContext(), debugMode);
    filterEngine = new FilterEngine(jsEngine);
    applyAcceptableAds();
    d("FilterEngine created");
  }

  private String url;
  private String domain;
  private String injectJs;
  private CountDownLatch elemHideLatch;
  private String elemHideSelectorsString;
  private Object elemHideThreadLockObject = new Object();
  private ElemHideThread elemHideThread;

  private class ElemHideThread extends Thread
  {
    private String selectorsString;
    private CountDownLatch finishedLatch;
    private AtomicBoolean isCancelled;

    public ElemHideThread(CountDownLatch finishedLatch)
    {
      this.finishedLatch = finishedLatch;
      isCancelled = new AtomicBoolean(false);
    }

    private String[] EMPTY_ARRAY = new String[] {};

    @Override
    public void run()
    {
      try
      {
        if (filterEngine == null)
        {
          w("FilterEngine already disposed");
          selectorsString = EMPTY_ELEMHIDE_ARRAY_STRING;
        }
        else
        {

          String[] referers = EMPTY_ARRAY;
          if (url != null)
          {
            String referer = url2Referer.get(url);
            if (referer != null)
            {
              referers = new String[] { referer };
            }
          }

          d("Check whitelisting for " + url + " with " +
            (referers.length > 0
              ? "referer " + referers[0]
              : "no referer"));

          if (filterEngine.isDocumentWhitelisted(url, referers) ||
            filterEngine.isElemhideWhitelisted(url, referers))
          {
            w("Whitelisted " + url);
            selectorsString = EMPTY_ELEMHIDE_ARRAY_STRING;
          }
          else
          {
            d("Listed subscriptions: " + filterEngine.getListedSubscriptions().size());

            d("Requesting elemhide selectors from FilterEngine for " + domain);
            List<String> selectors = filterEngine.getElementHidingSelectors(domain);
            d("Finished requesting elemhide selectors, got " + selectors.size());
            selectorsString = Utils.stringListToJsonArray(selectors);
          }
        }
      }
      finally
      {
        if (!isCancelled.get())
        {
          finish(selectorsString);
        }
        else
        {
          w("This thread is cancelled, exiting silently " + this);
        }
      }
    }

    private void onFinished()
    {
      finishedLatch.countDown();
      synchronized (finishedRunnableLockObject)
      {
        if (finishedRunnable != null)
        {
          finishedRunnable.run();
        }
      }
    }

    private void finish(String result)
    {
      d("Setting elemhide string " + result.length() + " bytes");
      elemHideSelectorsString = result;
      onFinished();
    }

    private Object finishedRunnableLockObject = new Object();
    private Runnable finishedRunnable;

    public void setFinishedRunnable(Runnable runnable)
    {
      synchronized (finishedRunnableLockObject)
      {
        this.finishedRunnable = runnable;
      }
    }

    public void cancel()
    {
      w("Cancelling elemhide thread " + this);
      isCancelled.set(true);

      finish(EMPTY_ELEMHIDE_ARRAY_STRING);
    }
  }

  private Runnable elemHideThreadFinishedRunnable = new Runnable()
  {
    @Override
    public void run()
    {
      synchronized (elemHideThreadLockObject)
      {
        w("elemHideThread set to null");
        elemHideThread = null;
      }
    }
  };

  private boolean loading;

  private void initAbpLoading()
  {
    getSettings().setJavaScriptEnabled(true);
    buildInjectJs();

    if (filterEngine == null)
    {
      createFilterEngine();
      disposeFilterEngine = true;
    }
  }

  private void startAbpLoading(String newUrl)
  {
    d("Start loading " + newUrl);

    loading = true;
    addDomListener = true;
    elementsHidden = false;
    loadError = null;
    url = newUrl;

    if (url != null)
    {
      try
      {
        domain = Utils.getDomain(url);
      }
      catch (URISyntaxException e)
      {
        domain = null;
        e("Failed to extract domain for " + url);
      }

      elemHideLatch = new CountDownLatch(1);
      elemHideThread = new ElemHideThread(elemHideLatch);
      elemHideThread.setFinishedRunnable(elemHideThreadFinishedRunnable);
      elemHideThread.start();
    }
    else
    {
      elemHideLatch = null;
    }
  }

  private void buildInjectJs()
  {
    try
    {
      if (injectJs == null)
      {
        injectJs = readScriptFile("inject.js").replace(HIDE_TOKEN, readScriptFile("css.js"));
      }
    }
    catch (IOException e)
    {
      e("Failed to read script", e);
    }
  }

  @Override
  public void goBack()
  {
    if (loading)
    {
      stopAbpLoading();
    }

    super.goBack();
  }

  @Override
  public void goForward()
  {
    if (loading)
    {
      stopAbpLoading();
    }

    super.goForward();
  }

  @Override
  public void loadUrl(String url)
  {
    initAbpLoading();

    if (loading)
    {
      stopAbpLoading();
    }

    super.loadUrl(url);
  }

  @Override
  public void loadUrl(String url, Map<String, String> additionalHttpHeaders)
  {
    initAbpLoading();

    if (loading)
    {
      stopAbpLoading();
    }

    super.loadUrl(url, additionalHttpHeaders);
  }

  @Override
  public void loadData(String data, String mimeType, String encoding)
  {
    initAbpLoading();

    if (loading)
    {
      stopAbpLoading();
    }

    super.loadData(data, mimeType, encoding);
  }

  @Override
  public void loadDataWithBaseURL(String baseUrl, String data, String mimeType, String encoding,
                                  String historyUrl)
  {
    initAbpLoading();

    if (loading)
    {
      stopAbpLoading();
    }

    super.loadDataWithBaseURL(baseUrl, data, mimeType, encoding, historyUrl);
  }

  @Override
  public void stopLoading()
  {
    stopAbpLoading();
    super.stopLoading();
  }

  private void stopAbpLoading()
  {
    d("Stop abp loading");

    loading = false;
    stopPreventDrawing();
    clearReferers();

    synchronized (elemHideThreadLockObject)
    {
      if (elemHideThread != null)
      {
        elemHideThread.cancel();
      }
    }
  }

  private volatile boolean elementsHidden = false;

  // warning: do not rename (used in injected JS by method name)
  @JavascriptInterface
  public void setElementsHidden(boolean value)
  {
    // invoked with 'true' by JS callback when DOM is loaded
    elementsHidden = value;

    // fired on worker thread, but needs to be invoked on main thread
    if (value)
    {
//     handler.post(allowDrawRunnable);
//     should work, but it's not working:
//     the user can see element visible even though it was hidden on dom event

      if (allowDrawDelay > 0)
      {
        d("Scheduled 'allow drawing' invocation in " + allowDrawDelay + " ms");
      }
      handler.postDelayed(allowDrawRunnable, allowDrawDelay);
    }
  }

  // warning: do not rename (used in injected JS by method name)
  @JavascriptInterface
  public boolean isElementsHidden()
  {
    return elementsHidden;
  }

  @Override
  public void onPause()
  {
    handler.removeCallbacks(allowDrawRunnable);
    super.onPause();
  }

  public AdblockWebView(Context context)
  {
    super(context);
    initAbp();
  }

  public AdblockWebView(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    initAbp();
  }

  public AdblockWebView(Context context, AttributeSet attrs, int defStyle)
  {
    super(context, attrs, defStyle);
    initAbp();
  }

  // used to prevent user see flickering for elements to hide
  // for some reason it's rendered even if element is hidden on 'dom ready' event
  private volatile boolean allowDraw = true;

  @Override
  protected void onDraw(Canvas canvas)
  {
    if (allowDraw)
    {
      super.onDraw(canvas);
    }
    else
    {
      w("Prevent drawing");
      drawEmptyPage(canvas);
    }
  }

  private void drawEmptyPage(Canvas canvas)
  {
    // assuming default color is WHITE
    canvas.drawColor(Color.WHITE);
  }

  private Handler handler = new Handler();

  protected void startPreventDrawing()
  {
    w("Start prevent drawing");

    allowDraw = false;
  }

  protected void stopPreventDrawing()
  {
    d("Stop prevent drawing, invalidating");

    allowDraw = true;
    invalidate();
  }

  private Runnable allowDrawRunnable = new Runnable()
  {
    @Override
    public void run()
    {
      stopPreventDrawing();
    }
  };

  private static final String EMPTY_ELEMHIDE_ARRAY_STRING = "[]";

  // warning: do not rename (used in injected JS by method name)
  @JavascriptInterface
  public String getElemhideSelectors()
  {
    if (elemHideLatch == null)
    {
      return EMPTY_ELEMHIDE_ARRAY_STRING;
    }
    else
    {
      try
      {
        // elemhide selectors list getting is started in startAbpLoad() in background thread
        d("Waiting for elemhide selectors to be ready");
        elemHideLatch.await();
        d("Elemhide selectors ready, " + elemHideSelectorsString.length() + " bytes");

        clearReferers();

        return elemHideSelectorsString;
      }
      catch (InterruptedException e)
      {
        w("Interrupted, returning empty selectors list");
        return EMPTY_ELEMHIDE_ARRAY_STRING;
      }
    }
  }

  private void doDispose()
  {
    w("Disposing jsEngine");
    jsEngine.dispose();
    jsEngine = null;

    w("Disposing filterEngine");
    filterEngine.dispose();
    filterEngine = null;

    disposeFilterEngine = false;
  }

  public void dispose()
  {
    d("Dispose invoked");

    removeJavascriptInterface(BRIDGE);

    if (disposeFilterEngine)
    {
      synchronized (elemHideThreadLockObject)
      {
        if (elemHideThread != null)
        {
          w("Busy with elemhide selectors, delayed disposing scheduled");
          elemHideThread.setFinishedRunnable(new Runnable()
          {
            @Override
            public void run()
            {
              doDispose();
            }
          });
        }
        else
        {
          doDispose();
        }
      }
    }
  }
}
