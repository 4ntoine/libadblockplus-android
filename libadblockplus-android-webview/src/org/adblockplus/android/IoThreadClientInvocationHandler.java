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

import android.util.Log;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class IoThreadClientInvocationHandler implements InvocationHandler
{
  private final String TAG = Utils.getTag(IoThreadClientInvocationHandler.class);

  public static transient Boolean isMainFrame;
  private Object wrappedObject;

  public IoThreadClientInvocationHandler(Object wrappedObject)
  {
    this.wrappedObject = wrappedObject;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
  {
    // intercept invocation and store 'isMainFrame' argument
    if (method.getName().startsWith("shouldInterceptRequest") && args.length == 2)
    {
      String url = (String)args[0];
      isMainFrame = (Boolean)args[1];

      Log.d(TAG, "isMainFrame=" + isMainFrame + " for " + url);
    }
    return method.invoke(wrappedObject, args);
  }
}
