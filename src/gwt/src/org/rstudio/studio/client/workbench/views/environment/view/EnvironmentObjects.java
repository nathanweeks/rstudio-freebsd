/*
 * EnvironmentObjects.java
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

import com.google.gwt.cell.client.ClickableTextCell;
import com.google.gwt.cell.client.FieldUpdater;
import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.dom.builder.shared.TableCellBuilder;
import com.google.gwt.dom.builder.shared.TableRowBuilder;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.text.shared.AbstractSafeHtmlRenderer;
import com.google.gwt.text.shared.SafeHtmlRenderer;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.AbstractCellTableBuilder;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.DataGrid;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.ListDataProvider;
import com.google.gwt.view.client.NoSelectionModel;
import org.rstudio.core.client.theme.res.ThemeStyles;
import org.rstudio.studio.client.workbench.views.environment.model.RObject;

import java.util.*;

public class EnvironmentObjects extends Composite
{
   // provide access to styles defined in associated CSS file
   interface Style extends CssResource
   {
      String expandCol();
      String nameCol();
      String valueCol();
      String detailRow();
      String categoryHeaderRow();
      String emptyEnvironmentPanel();
      String emptyEnvironmentName();
      String emptyEnvironmentMessage();
      String wrappingDetailText();
      String expandIcon();
   }

   // methods implemented by the owning presenter to edit and view objects
   public interface Observer
   {
      void editObject(String objectName);
      void viewObject(String objectName);
   }

   // builds individual rows of the object table
   private class ObjectTableBuilder extends AbstractCellTableBuilder<RObjectEntry>
   {
      public ObjectTableBuilder()
      {
         super(objectList);
      }

      // (re)build the given row
      public void buildRowImpl(RObjectEntry rowValue, int absRowIndex)
      {

         // build the header for the row (if any)
         buildRowHeader(rowValue, absRowIndex);

         TableRowBuilder row = startRow();

         if (rowValue.getCategory() == RObjectEntry.Categories.Data)
         {
            row.className(ThemeStyles.INSTANCE.workspaceDataFrameRow());
         }

         // build the column containing the expand/collapse command
         TableCellBuilder expandCol = row.startTD();
         expandCol.className(style.expandCol());
         renderCell(expandCol, createContext(0), objectExpandColumn_, rowValue);
         expandCol.endTD();

         // build the column containing the name of the object
         TableCellBuilder nameCol = row.startTD();
         nameCol.className(style.nameCol());
         nameCol.title(
                 rowValue.rObject.getName() +
                 " (" + rowValue.rObject.getType() + ")");
         renderCell(nameCol, createContext(0), objectNameColumn_, rowValue);
         nameCol.endTD();

         // build the column containing the description of the object
         TableCellBuilder descCol = row.startTD();
         String title = rowValue.rObject.getValue();
         if (!title.equals("NO_VALUE"))
         {
            descCol.title(rowValue.rObject.getValue());
         }
         descCol.className(style.valueCol());

         // if the row is expanded but doesn't have contents, that means the
         // value is being show in the detail row--don't duplicate it here
         if (!(rowValue.expanded &&
               rowValue.rObject.getContents().length() == 0))
         {
            renderCell(descCol,
                       createContext(1),
                       objectDescriptionColumn_,
                       rowValue);
         }
         descCol.endTD();

         row.endTR();

         // if the row is expanded, draw its content
         if (rowValue.expanded)
         {
            buildExpandedContentRow(rowValue);
         }
      }

      private void buildRowHeader(RObjectEntry rowValue, int absRowIndex)
      {
         // if building the first row, we need to add a dummy row to the top.
         // since the grid uses a fixed table layout, the first row sets the
         // column widths, so we can't let the first row be a spanning header.
         if (absRowIndex == 0)
         {
            TableRowBuilder widthSettingRow = startRow();
            widthSettingRow.startTD().className(style.expandCol()).endTD();
            widthSettingRow.startTD().className(style.nameCol()).endTD();
            widthSettingRow.startTD().className(style.valueCol()).endTD();
            widthSettingRow.endTR();
         }

         // if this row is the first of its category, draw the category header
         if (rowValue.isCategoryLeader)
         {
            String categoryTitle;
            switch (rowValue.getCategory())
            {
               case RObjectEntry.Categories.Data:
                  categoryTitle = "Data";
                  break;
               case RObjectEntry.Categories.Function:
                  categoryTitle = "Functions";
                  break;
               default:
                  categoryTitle = "Values";
                  break;
            }
            TableRowBuilder leaderRow = startRow().className(style.categoryHeaderRow());
            TableCellBuilder objectHeader = leaderRow.startTD();
            objectHeader.colSpan(3)
                    .text(categoryTitle)
                    .endTD();
            leaderRow.endTR();
         }
      }

      // draw additional rows when the row has been expanded
      private void buildExpandedContentRow(RObjectEntry rowValue)
      {
         JsArrayString contents = rowValue.rObject.getContents();

         // if the contents are empty, assume we want to expand the value,
         // and don't wrap it.
         if (contents.length() == 0)
         {
            TableRowBuilder valueRow = startRow().className(style.detailRow());
            valueRow.startTD().endTD();
            valueRow.startTD()
                       .colSpan(2)
                       .className(style.wrappingDetailText())
                       .text(rowValue.rObject.getValue())
                       .endTD();
            valueRow.endTR();
         }

         // contents are not empty; render a row for each entry
         else
         {
            String objectType = rowValue.rObject.getType();
            Boolean isListOrFrame = objectType.equals("list")
                                    || objectType.equals("data.frame");

            // ignore the first line of output for lists and data frames
            // (it's the same size information we're already showing in the grid)
            for (int idx = isListOrFrame ? 1 : 0; idx < contents.length(); idx++)
            {
               TableRowBuilder detail = startRow().className(style.detailRow());
               detail.startTD().endTD();
               TableCellBuilder objectDetail = detail.startTD();
               String content = contents.get(idx);
               // ignore the first two characters of output for lists and frames
               // ("$ value:" becomes "value:")
               if (isListOrFrame)
               {
                  content = content.substring(2, content.length()).trim();
               }
               objectDetail.colSpan(2)
                       .title(content)
                       .text(content)
                       .endTD();
               detail.endTR();
            }
         }
      }
   }

   interface Binder extends UiBinder<Widget, EnvironmentObjects>
   {
   }

   public void setContextDepth(int contextDepth)
   {
      // this is where we'll set UI to debug mode it contextDepth is > 0
   }

   public void addObject(RObject obj)
   {
      int idx = indexOfExistingObject(obj.getName());
      RObjectEntry newEntry = new RObjectEntry(obj);
      boolean added = false;

      // if the object is already in the environment, just update the value
      if (idx >= 0)
      {
         RObjectEntry oldEntry = objectDataProvider_.getList().get(idx);

         if (oldEntry.rObject.getType().equals(obj.getType()))
         {
            // type did not change; update in-place and preserve expansion flag
            newEntry.expanded = oldEntry.expanded;
            objectDataProvider_.getList().set(idx, newEntry);
            added = true;
         }
         else
         {
            // types did change, do a full add/remove
            objectDataProvider_.getList().remove(idx);
         }
      }
      if (!added)
      {
         RObjectEntry entry = new RObjectEntry(obj);
         objectDataProvider_.getList().add(indexOfNewObject(entry), entry);
      }
      updateCategoryLeaders(true);
   }

   // bulk add for objects--used on init or environment switch
   public void addObjects(JsArray<RObject> objects)
   {
      // create an entry for each object and sort the array
      int numObjects = objects.length();
      ArrayList<RObjectEntry> objectEntryList = new ArrayList<RObjectEntry>();
      for (int i = 0; i < numObjects; i++)
      {
         objectEntryList.add(new RObjectEntry(objects.get(i)));
      }
      Collections.sort(objectEntryList, new RObjectEntrySort());

      // push the list into the UI and update category leaders
      objectDataProvider_.getList().addAll(objectEntryList);
      updateCategoryLeaders(false);
   }

   public void removeObject(String objName)
   {
      int idx = indexOfExistingObject(objName);
      if (idx >= 0)
      {
         objectDataProvider_.getList().remove(idx);
      }

      updateCategoryLeaders(true);
   }
   
   public void clearObjects()
   {
      objectDataProvider_.getList().clear();
   }

   public void setObserver(Observer observer)
   {
      observer_ = observer;
   }

   public void setEnvironmentName(String environmentName)
   {
      environmentName_.setText(environmentName);
   }

   public EnvironmentObjects()
   {
      // initialize the data grid and hook it up to the list of R objects in
      // the environment pane
      objectList = new DataGrid<RObjectEntry>(RObjectEntry.KEY_PROVIDER);
      objectDataProvider_ = new ListDataProvider<RObjectEntry>();
      objectDataProvider_.addDataDisplay(objectList);
      createColumns();
      objectList.addColumn(objectExpandColumn_);
      objectList.addColumn(objectNameColumn_);
      objectList.addColumn(objectDescriptionColumn_);
      objectList.setTableBuilder(new ObjectTableBuilder());
      objectList.setSkipRowHoverCheck(true);

      // make the grid fill the pane
      objectList.setWidth("100%");
      objectList.setHeight("100%");
      objectList.setSelectionModel(new NoSelectionModel<RObjectEntry>(RObjectEntry.KEY_PROVIDER));
      initWidget(GWT.<Binder>create(Binder.class).createAndBindUi(this));

      // these need to be done post-initWidget since they reference objects
      // created by initWidget
      objectList.setEmptyTableWidget(BuildEmptyGridMessage());
      environmentContents.add(objectList);
   }

   private int indexOfExistingObject(String objectName)
   {
      List<RObjectEntry> objects = objectDataProvider_.getList();

      // find the position of the object in the list
      int index;
      boolean foundObject = false;
      for (index = 0; index < objects.size(); index++)
      {
         if (objects.get(index).rObject.getName() == objectName)
         {
            foundObject = true;
            break;
         }
      }

      return foundObject ? index : -1;
   }


   // returns the position a new object entry should occupy in the table
   private int indexOfNewObject(RObjectEntry obj)
   {
      List<RObjectEntry> objects = objectDataProvider_.getList();
      RObjectEntrySort sort = new RObjectEntrySort();
      int numObjects = objects.size();
      int idx;
      // consider: can we use binary search here?
      for (idx = 0; idx < numObjects; idx++)
      {
         if (sort.compare(obj, objects.get(idx)) < 0)
         {
            break;
         }
      }
      return idx;
   }

   // create each column for the data grid
   private void createColumns()
   {
      objectNameColumn_ = new Column<RObjectEntry, String>(new TextCell()) {
         @Override
         public String getValue(RObjectEntry object) {
            return object.rObject.getName();
         }
      };

      objectDescriptionColumn_ = new Column<RObjectEntry, String>(new ClickableTextCell()) {
         @Override
         public String getValue(RObjectEntry object) {
            String val = object.rObject.getValue();
            return val == "NO_VALUE" ? object.rObject.getDescription() : val;
         }
      };
      objectDescriptionColumn_.setFieldUpdater(new FieldUpdater<RObjectEntry, String>()
      {
         @Override
         public void update(int index, RObjectEntry object, String value)
         {
            if (object.getCategory() == RObjectEntry.Categories.Data)
            {
               observer_.viewObject(object.rObject.getName());
            }
            else
            {
               observer_.editObject(object.rObject.getName());
            }
         }
      });

      SafeHtmlRenderer<String> expanderRenderer = new AbstractSafeHtmlRenderer<String>()
      {
         @Override
         public SafeHtml render(String object)
         {
            SafeHtmlBuilder sb = new SafeHtmlBuilder();
            if (!object.equals(""))
            {
               sb.appendHtmlConstant("<input type=\"image\" src=\"")
                       .appendEscaped(object)
                       .appendHtmlConstant("\" class=\"")
                       .appendEscaped(style.expandIcon())
                       .appendHtmlConstant("\" />");
            }
            else
            {
               sb.appendHtmlConstant("&nbsp;");
            }
            return sb.toSafeHtml();
         }
      };

      objectExpandColumn_ = new Column<RObjectEntry, String>(new ClickableTextCell(expanderRenderer))
      {
         @Override
         public String getValue(RObjectEntry object)
         {
            if (object.canExpand())
            {
               ImageResource expandImage = object.expanded ?
                         EnvironmentResources.INSTANCE.collapseIcon() :
                         EnvironmentResources.INSTANCE.expandIcon();

               return expandImage.getSafeUri().asString();
            }
            else
            {
               return "";
            }
         }
      };
      objectExpandColumn_.setFieldUpdater(new FieldUpdater<RObjectEntry, String>()
      {
         @Override
         public void update(int index, RObjectEntry object, String value)
         {
            if (object.canExpand())
            {
               object.expanded = !object.expanded;
               objectList.redrawRow(index);
            }
         }
      });
   }

   // after adds or removes, we need to tag the new category-leading objects
   private void updateCategoryLeaders(boolean redrawUpdatedRows)
   {
      List<RObjectEntry> objects = objectDataProvider_.getList();

      // whether or not we've found a leader for each category
      Boolean[] leaders = { false, false, false };

      for (int i = 0; i < objects.size(); i++)
      {
         RObjectEntry entry = objects.get(i);
         int category = entry.getCategory();
         Boolean leader = entry.isCategoryLeader;
         // if we haven't found a leader for this category yet, make this object
         // the leader if it isn't already
         if (!leaders[category])
         {
            leaders[category] = true;
            if (!leader)
            {
               entry.isCategoryLeader = true;
            }
         }
         // if this object is marked as the leader but we've already found a
         // leader, unmark it
         else if (leader)
         {
            entry.isCategoryLeader = false;
         }

         // if we changed the leader flag, redraw the row
         if (leader != entry.isCategoryLeader
             && redrawUpdatedRows)
         {
            objectList.redrawRow(i);
         }
      }
   }

   private Widget BuildEmptyGridMessage()
   {
      HTMLPanel messagePanel = new HTMLPanel("");
      messagePanel.setStyleName(style.emptyEnvironmentPanel());
      environmentName_ = new Label("Global");
      environmentName_.setStyleName(style.emptyEnvironmentName());
      Label emptyMessage = new Label("Environment is empty");
      emptyMessage.setStyleName(style.emptyEnvironmentMessage());
      messagePanel.add(environmentName_);
      messagePanel.add(emptyMessage);
      return messagePanel;
   }

   private class RObjectEntrySort implements Comparator<RObjectEntry>
   {
      public int compare(RObjectEntry first, RObjectEntry second)
      {
         int result = first.getCategory() - second.getCategory();
         if (result == 0)
         {
            result = localeCompare(first.rObject.getName(), second.rObject.getName());
         }
         return result;
      }

      private native int localeCompare(String first, String second) /*-{
          return first.localeCompare(second);
      }-*/;
   }

   @UiField HTMLPanel environmentContents;
   @UiField Style style;

   DataGrid<RObjectEntry> objectList;
   Label environmentName_;

   private Column<RObjectEntry, String> objectExpandColumn_;
   private Column<RObjectEntry, String> objectNameColumn_;
   private Column<RObjectEntry, String> objectDescriptionColumn_;
   private ListDataProvider<RObjectEntry> objectDataProvider_;

   private Observer observer_;
}