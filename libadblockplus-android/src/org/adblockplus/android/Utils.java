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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.adblockplus.libadblockplus.JsValue;
import org.adblockplus.libadblockplus.Subscription;
import org.apache.commons.lang.StringUtils;
import org.json.JSONArray;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

public final class Utils
{
  private Utils()
  {
    //
  }

  public static String getTag(final Class<?> clazz)
  {
    return clazz.getSimpleName();
  }

  public static String capitalizeString(final String s)
  {
    if (s == null || s.length() == 0)
    {
      return "";
    }

    final char first = s.charAt(0);

    return Character.isUpperCase(first) ? s : Character.toUpperCase(first) + s.substring(1);
  }

  public static void appendRawTextFile(final Context context, final StringBuilder text, final int id)
  {
    try
    {
      final BufferedReader buf = new BufferedReader(new InputStreamReader(context.getResources().openRawResource(id)));

      try
      {
        String line;
        while ((line = buf.readLine()) != null)
        {
          text.append(line);
          text.append('\n');
        }
      }
      finally
      {
        buf.close();
      }

    }
    catch (final Exception e)
    {
      // Ignored for now
    }
  }

  public static String stringListToJsonArray(List<String> list)
  {
    JSONArray array = new JSONArray();

    if (list != null)
    {
      for (String eachString : list)
      {
        if (eachString != null)
        {
          array.put(eachString);
        }
      }
    }

    return array.toString();
  }

  /**
   * Extract domain from URL
   * @param url valid URL (with scheme)
   * @return domain
   * @throws URISyntaxException
   */
  public static String getDomain(String url) throws URISyntaxException
  {
    URI uri = new URI(url);
    String domain = uri.getHost();
    if (domain == null)
      throw new URISyntaxException(url, "Invalid url");
    return domain.startsWith("www.") ? domain.substring(4) : domain;
  }

  public static String readAssetAsString(Context context, String filename) throws IOException
  {
    BufferedReader in = null;
    try {
      StringBuilder buf = new StringBuilder();
      InputStream is = context.getAssets().open(filename);
      in = new BufferedReader(new InputStreamReader(is));

      String str;
      boolean isFirst = true;
      while ( (str = in.readLine()) != null ) {
        if (isFirst)
          isFirst = false;
        else
          buf.append('\n');
        buf.append(str);
      }
      return buf.toString();
    } finally {
      if (in != null) {
        try {
          in.close();
        } catch (IOException e) {
          // ignored
        }
      }
    }
  }
}
