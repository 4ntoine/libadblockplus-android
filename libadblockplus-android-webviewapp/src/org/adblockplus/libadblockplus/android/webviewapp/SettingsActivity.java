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

import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.util.Log;

import org.adblockplus.libadblockplus.android.AdblockEngine;
import org.adblockplus.libadblockplus.android.Utils;
import org.adblockplus.libadblockplus.android.settings.Adblock;
import org.adblockplus.libadblockplus.android.settings.AdblockGeneralSettingsFragment;
import org.adblockplus.libadblockplus.android.settings.AdblockSettings;
import org.adblockplus.libadblockplus.android.settings.AdblockSettingsFragment;
import org.adblockplus.libadblockplus.android.settings.AdblockSettingsStorage;
import org.adblockplus.libadblockplus.android.settings.AdblockWhitelistedDomainsSettingsFragment;

public class SettingsActivity
  extends PreferenceActivity
  implements
    AdblockGeneralSettingsFragment.Provider,
    AdblockGeneralSettingsFragment.Listener,
    AdblockWhitelistedDomainsSettingsFragment.Listener
{
  private static final String TAG = Utils.getTag(SettingsActivity.class);

  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    Adblock.get().retain(MainActivity.ADBLOCKENGINE_RETAIN_ASYNC);

    insertGeneralFragment();
  }

  private void insertGeneralFragment()
  {
    getFragmentManager()
      .beginTransaction()
      .replace(
        android.R.id.content,
        AdblockGeneralSettingsFragment.newInstance())
      .commit();
  }

  private void insertWhitelistedFragment()
  {
    getFragmentManager()
      .beginTransaction()
      .replace(
        android.R.id.content,
        AdblockWhitelistedDomainsSettingsFragment.newInstance())
      .addToBackStack(AdblockWhitelistedDomainsSettingsFragment.class.getSimpleName())
      .commit();
  }

  // provider

  @Override
  public AdblockEngine getAdblockEngine()
  {
    // if it's retained asynchronously we have to wait until it's ready
    Adblock.get().waitForReady();
    return Adblock.get().getEngine();
  }

  @Override
  public AdblockSettingsStorage getAdblockSettingsStorage()
  {
    return Adblock.get().getStorage();
  }

  // listener

  @Override
  public void onAdblockSettingsChanged(AdblockSettingsFragment fragment)
  {
    Log.d(TAG, "Adblock setting changed:\n" + fragment.getSettings().toString());
  }

  @Override
  public void onWhitelistedDomainsClicked(AdblockGeneralSettingsFragment fragment)
  {
    insertWhitelistedFragment();
  }

  @Override
  public boolean isValidDomain(AdblockWhitelistedDomainsSettingsFragment fragment,
                               String domain,
                               AdblockSettings settings)
  {
    // show error here if domain is invalid
    return domain != null && domain.length() > 0;
  }

  @Override
  protected void onDestroy()
  {
    super.onDestroy();
    Adblock.get().release();
  }
}
