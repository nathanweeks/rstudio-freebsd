/*
 * RObjectEntry.java
 *
 * Copyright (C) 2009-12 by RStudio, Inc.
 *
 * Unless you have received this program directly from RStudio pursuant
 * to the terms of a commercial license agreement with RStudio, then
 * this program is licensed to you under the terms of version 3 of the
 * GNU Affero General Public License. This program is distributed WITHOUT
 * ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
 * AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
 *
 */

package org.rstudio.studio.client.workbench.views.environment.view;

import com.google.gwt.view.client.ProvidesKey;
import org.rstudio.studio.client.workbench.views.environment.model.RObject;

// represents an R object's entry in the environment pane view
public class RObjectEntry
{
   public static final ProvidesKey<RObjectEntry> KEY_PROVIDER =
           new ProvidesKey<RObjectEntry>() {
              public Object getKey(RObjectEntry item) {
                 return item.rObject.getName();
              }
           };

   // the classification of data in the pane
   public class Categories
   {
      public static final int Data = 0;
      public static final int Value = 1;
      public static final int Function = 2;
   }

   // make a new entry in the pane from an R object
   RObjectEntry(RObject obj)
   {
      rObject = obj;
      expanded = false;
      isCategoryLeader = false;
   }

   public boolean canExpand()
   {
      // objects with detail from the server can be expanded; so can long
      // string values
      return rObject.getContents().length() > 0
            || (rObject.getType().equals("character") &&
                rObject.getValue().length() > 128);
   }

   public int getCategory()
   {
      String type = rObject.getType();
      if (type.equals("data.frame")
         || type.equals("matrix")
         || type.equals("data.table")
         || type.equals("cast_df"))
      {
         return Categories.Data;
      }
      else if (type.equals("function"))
      {
         return Categories.Function;
      }

      return Categories.Value;
   }

   RObject rObject;
   boolean expanded;
   boolean isCategoryLeader;
}