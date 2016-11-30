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

package org.adblockplus.libadblockplus.android.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.adblockplus.libadblockplus.android.AdblockEngine;
import org.adblockplus.libadblockplus.android.Utils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Adblock shared resources
 * (singleton)
 */
public class Adblock
{
  private static final String TAG = Utils.getTag(Adblock.class);

  /**
   * Suggested preference name
   */
  public static final String PREFERENCE_NAME = "ADBLOCK";

  // singleton
  protected Adblock()
  {
    // prevents instantiation
  }

  private static Adblock _instance;

  /**
   * Use to get Adblock instance
   * @return adblock instance
   */
  public static synchronized Adblock get()
  {
    if (_instance == null)
    {
      _instance = new Adblock();
    }

    return _instance;
  }

  private Context context;
  private boolean developmentBuild;
  private String preferenceName;

  private AdblockEngine engine;

  public AdblockEngine getEngine()
  {
    return engine;
  }

  private AdblockSettingsStorage storage;

  public AdblockSettingsStorage getStorage()
  {
    return storage;
  }

  /**
   * Init with context
   * @param context application context
   * @param developmentBuild debug or release?
   * @param preferenceName Shared Preferences name
   */
  public void init(Context context, boolean developmentBuild, String preferenceName)
  {
    this.context = context.getApplicationContext();
    this.developmentBuild = developmentBuild;
    this.preferenceName = preferenceName;
  }

  private CountDownLatch engineCreated;

  private void createAdblock()
  {
    Log.d(TAG, "Creating adblock engine ...");

    // read and apply current settings
    SharedPreferences prefs = context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE);
    storage = new AdblockSettingsSharedPrefsStorage(prefs);

    // latch is required for async (see `waitForReady()`)
    engineCreated = new CountDownLatch(1);

    engine = AdblockEngine.create(
      AdblockEngine.generateAppInfo(context, developmentBuild),
      context.getCacheDir().getAbsolutePath(),
      true); // `true` as we need element hiding
    Log.d(TAG, "Adblock engine created");

    AdblockSettings settings = storage.load();
    if (settings != null)
    {
      Log.d(TAG, "Applying saved adblock settings to adblock engine");
      // apply last saved settings to adblock engine

      // all the settings except `enabled` and whitelisted domains are saved by adblock engine itself
      engine.setEnabled(settings.isAdblockEnabled());
      engine.setWhitelistedDomains(settings.getWhitelistedDomains());
    }
    else
    {
      Log.w(TAG, "No saved adblock settings");
    }

    // unlock waiting client thread
    engineCreated.countDown();
  }

  /**
   * Wait until everything is ready (used for `retain(true)`)
   * Warning: locks current thread
   */
  public void waitForReady()
  {
    if (engineCreated == null)
    {
      throw new RuntimeException("Adblock Plus usage exception: call retain(...) first");
    }

    try
    {
      Log.d(TAG, "Waiting for ready ...");
      engineCreated.await();
      Log.d(TAG, "Ready");
    }
    catch (InterruptedException e)
    {
      Log.w(TAG, "Interrupted", e);
    }
  }

  private void disposeAdblock()
  {
    Log.w(TAG, "Disposing adblock engine");

    engine.dispose();
    engine = null;

    // to unlock waiting client in WaitForReady()
    engineCreated.countDown();
    engineCreated = null;

    storage = null;
  }

  /*
    Simple ARC management for AdblockEngine
    Use `retain` and `release`
   */

  private AtomicInteger referenceCounter = new AtomicInteger(0);

  /**
   * Get registered clients count
   * @return registered clients count
   */
  public int getCounter()
  {
    return referenceCounter.get();
  }

  /**
   * Register Adblock engine client
   * @param asynchronous If `true` engines will be created in background thread without locking of
   *                     current thread. Use waitForReady() before getEngine() later.
   *                     If `false` locks current thread.
   */
  public synchronized void retain(boolean asynchronous)
  {
    if (referenceCounter.getAndIncrement() == 0)
    {
      if (!asynchronous)
      {
        createAdblock();
      }
      else
      {
        new Thread(new Runnable()
        {
          @Override
          public void run()
          {
            createAdblock();
          }
        }).start();
      }
    }
  }

  /**
   * Unregister Adblock engine client
   */
  public synchronized void release()
  {
    if (referenceCounter.decrementAndGet() == 0)
    {
      waitForReady();
      disposeAdblock();
    }
  }
}
