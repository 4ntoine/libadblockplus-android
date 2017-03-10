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

import android.net.ConnectivityManager;

public enum ConnectionType {

  // All WiFi networks
  WIFI(
    "wifi",
    new ConnectionChecker()
    {
      @Override
      public boolean isRequiredConnection(ConnectivityManager manager)
      {
        return manager.getActiveNetworkInfo().getType() == ConnectivityManager.TYPE_WIFI;
      }
    }),

  // Non-metered WiFi networks
  WIFI_NON_METERED(
    "wifi_non_metered",
    new ConnectionChecker()
    {
      @Override
      public boolean isRequiredConnection(ConnectivityManager manager)
      {
        return
          manager.getActiveNetworkInfo().getType() == ConnectivityManager.TYPE_WIFI &&
          !manager.isActiveNetworkMetered();
    }
  }),

  // Any connection
  ANY(
    "any",
    new ConnectionChecker()
    {
      @Override
      public boolean isRequiredConnection(ConnectivityManager manager)
      {
        return true;
      }
    });

  private String value;
  private ConnectionChecker checker;

  public String getValue()
  {
    return value;
  }

  public ConnectionChecker getChecker()
  {
    return checker;
  }

  ConnectionType(String value, ConnectionChecker checker)
  {
    this.value = value;
    this.checker = checker;
  }

  interface ConnectionChecker
  {
    boolean isRequiredConnection(ConnectivityManager manager);
  }

  public static ConnectionType findByValue(String value)
  {
    if (value == null)
    {
      return null;
    }

    for (ConnectionType eachConnectionType : ConnectionType.values())
    {
      if (eachConnectionType.getValue().equals(value))
      {
        return eachConnectionType;
      }
    }

    // not found
    return null;
  }
}
