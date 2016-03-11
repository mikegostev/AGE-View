package uk.ac.ebi.ageview.client.ui;

import com.google.gwt.i18n.client.Dictionary;

public class SourceIconBundle
{
 private static Dictionary dict = Dictionary.getDictionary("dataSourceIcons");
 
 public static String getIcon( String srcName )
 {
  try
  { 
   return dict.get(srcName);
  }
  catch (Exception e) 
  {
   return null;
  }
 }
}
