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

package org.adblockplus.libadblockplus.android.webviewapp;

import org.adblockplus.libadblockplus.android.webview.AdblockWebView;
import org.adblockplus.libadblockplus.android.AdblockEngine;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ProgressBar;

public class MainActivity extends Activity
{
  private static final boolean DEVELOPMENT_BUILD = true;

  // webView can create AdblockEngine instance itself if not passed with `webView.setAdblockEngine()`
  private final boolean USE_EXTERNAL_ADBLOCKENGINE = true;

  private ProgressBar progress;
  private EditText url;
  private Button ok;
  private Button back;
  private Button forward;
  private CheckBox abpEnabled;
  private CheckBox aaEnabled;

  private AdblockEngine adblockEngine;
  private AdblockWebView webView;

  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    bindControls();
    initControls();
  }

  private void bindControls()
  {
    url = (EditText) findViewById(R.id.main_url);
    ok = (Button) findViewById(R.id.main_ok);
    back = (Button) findViewById(R.id.main_back);
    forward = (Button) findViewById(R.id.main_forward);
    abpEnabled = (CheckBox) findViewById(R.id.main_abp_enabled);
    aaEnabled = (CheckBox) findViewById(R.id.main_aa_enabled);
    progress = (ProgressBar) findViewById(R.id.main_progress);
    webView = (AdblockWebView) findViewById(R.id.main_webview);
  }

  private void setProgressVisible(boolean visible)
  {
    progress.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
  }

  private WebViewClient webViewClient = new WebViewClient()
  {
    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon)
    {
      setProgressVisible(true);

      // show updated URL (because of possible redirection)
      MainActivity.this.url.setText(url);
    }

    @Override
    public void onPageFinished(WebView view, String url)
    {
      setProgressVisible(false);
      updateButtons();

      if (!USE_EXTERNAL_ADBLOCKENGINE)
      {
        // as the page is finished internal adblockEngine is created and we can get actual AA value
        aaEnabled.setChecked(webView.getAdblockEngine().isAcceptableAdsEnabled());
      }
    }

    @Override
    public void onReceivedError(WebView view, int errorCode, String description, String failingUrl)
    {
      updateButtons();
    }
  };

  private void updateButtons()
  {
    back.setEnabled(webView.canGoBack());
    forward.setEnabled(webView.canGoForward());
  }

  private WebChromeClient webChromeClient = new WebChromeClient()
  {
    @Override
    public void onProgressChanged(WebView view, int newProgress)
    {
      progress.setProgress(newProgress);
    }
  };

  private void initControls()
  {
    ok.setOnClickListener(new View.OnClickListener()
    {
      @Override
      public void onClick(View view)
      {
        loadUrl();
      }
    });

    back.setOnClickListener(new View.OnClickListener()
    {
      @Override
      public void onClick(View v)
      {
        loadPrev();
      }
    });

    forward.setOnClickListener(new View.OnClickListener()
    {
      @Override
      public void onClick(View v)
      {
        loadForward();
      }
    });

    initAdblockEngine();
    initAbp();
    initAcceptableAds();

    setProgressVisible(false);
    updateButtons();

    // to get debug/warning log output
    webView.setDebugMode(DEVELOPMENT_BUILD);

    // render as fast as we can
    webView.setAllowDrawDelay(0);

    // to show that external WebViewClient is still working
    webView.setWebViewClient(webViewClient);

    // to show that external WebChromeClient is still working
    webView.setWebChromeClient(webChromeClient);
  }

  private void initAdblockEngine()
  {
    if (USE_EXTERNAL_ADBLOCKENGINE)
    {
      adblockEngine = AdblockEngine.create(
        this,
        AdblockEngine.generateAppInfo(this, true),
        getCacheDir().getAbsolutePath(),
        true);
      webView.setAdblockEngine(adblockEngine); // external (activity-owned) adblockEngine
    }
  }

  private void initAbp()
  {
    abpEnabled.setChecked(webView.isAdblockEnabled());
    abpEnabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
    {
      @Override
      public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
      {
        webView.setAdblockEnabled(isChecked);
      }
    });
  }

  private void initAcceptableAds()
  {
    if (USE_EXTERNAL_ADBLOCKENGINE)
    {
      // we can't set this checkbox if not using external engine as internal one is not yet created
      // (it will be created during the first load)
      aaEnabled.setChecked(adblockEngine.isAcceptableAdsEnabled());
    }
    aaEnabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
    {
      @Override
      public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
      {
        // not using this.adblockEngine as it can be internal webView engine
        webView.getAdblockEngine().setAcceptableAdsEnabled(isChecked);
      }
    });
  }

  private void hideSoftwareKeyboard()
  {
    InputMethodManager imm = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
    imm.hideSoftInputFromWindow(url.getWindowToken(), 0);
  }

  private void loadPrev()
  {
    hideSoftwareKeyboard();
    if (webView.canGoBack())
    {
      webView.goBack();
    }
  }

  private void loadForward()
  {
    hideSoftwareKeyboard();
    if (webView.canGoForward())
    {
      webView.goForward();
    }
  }

  private String prepareUrl(String url)
  {
    if (!url.startsWith("http"))
      url = "http://" + url;

    // make sure url is valid URL
    return url;
  }

  private void loadUrl()
  {
    hideSoftwareKeyboard();
    webView.loadUrl(prepareUrl(url.getText().toString()));
  }

  @Override
  protected void onDestroy()
  {
    webView.dispose(new Runnable()
    {
      @Override
      public void run()
      {
        if (USE_EXTERNAL_ADBLOCKENGINE)
        {
          adblockEngine.dispose();
        }
      }
    });
    super.onDestroy();
  }
}
