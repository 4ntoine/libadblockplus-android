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

package org.adblockplus.libadblockplus.android.webview;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ReflectionUtils
{
  public static Object extractProperty(Object target, String field)
  {
    return extractProperty(target, target.getClass(), field);
  }

  public static Object extractProperty(Object target, Class clazz, String field)
  {
    try
    {
      Field f = clazz.getDeclaredField(field);
      if (!f.isAccessible())
      {
        f.setAccessible(true);
      }
      return f.get(target);
    }
    catch (NoSuchFieldException e)
    {
      return extractProperty(target, clazz.getSuperclass(), field);
    }
    catch (Exception e)
    {
      return null;
    }
  }

  public static Object extractProperty(Object target, String []fields)
  {
    Object eachTarget = target;
    for (int i = 0; i < fields.length; i++)
    {
      eachTarget = extractProperty(eachTarget, eachTarget.getClass(), fields[i]);
      if (eachTarget == null)
      {
        return null;
      }
    }
    return eachTarget;
  }

  public static boolean injectProperty(Object target, String field, Object value)
  {
    try
    {
      Field f = target.getClass().getDeclaredField(field);
      if (!f.isAccessible())
      {
        f.setAccessible(true);
      }
      f.set(target, value);
      return true;
    }
    catch (Exception e)
    {
      return false;
    }
  }

  public static boolean invokeMethod(Object target, String methodName, Object[] args)
  {
    Method[] methods = target.getClass().getDeclaredMethods();
    for (Method eachMethod : methods)
    {
      if (eachMethod.getName().equals(methodName))
      {
        try
        {
          if (!eachMethod.isAccessible())
          {
            eachMethod.setAccessible(true);
          }
          eachMethod.invoke(target, args);
          return true;
        }
        catch (Exception e)
        {
          return false;
        }
      }
    }
    return false;
  }
}
