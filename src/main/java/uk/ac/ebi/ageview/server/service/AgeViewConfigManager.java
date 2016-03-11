package uk.ac.ebi.ageview.server.service;

import javax.servlet.ServletContext;

import uk.ac.ebi.age.admin.server.mng.AgeAdminConfigManager;


public class AgeViewConfigManager extends AgeAdminConfigManager
{
 public static final String ROOT_CLASS_NAME = "RootView";

 public static final String ACCS_ATTR_CLASS_NAME = "Accession";
 public static final String TITLE_ATTR_CLASS_NAME = "Title";
 
 public static final String ATTR_NAMES_FIELD_NAME = "aname";
 public static final String ATTR_VALUES_FIELD_NAME = "avalue";

 public static final String GROUP_ID_FIELD_NAME = "gid";
 public static final String SECTAGS_FIELD_NAME = "sectags";
 public static final String OWNER_FIELD_NAME = "owner";

 public static final String SAMPLEINGROUP_REL_CLASS_NAME = "belongsTo";
 
 public static final String REFERENCE_ATTR_CLASS_NAME = "Submission Reference Layer";
 public static final String DESCRIPTION_ATTR_CLASS_NAME = "Submission Description";
 public static final String COMMENT_ATTR_CLASS_NAME = "Comment";
 public static final String HAS_PUBLICATION_REL_CLASS_NAME = "hasPublication";
 public static final String CONTACT_OF_REL_CLASS_NAME = "contactOf";
 public static final String DATASOURCE_ATTR_CLASS_NAME = "Data Source";
 public static final String PUBLICATIONS_ATTR_CLASS_NAME = "Publications";
 public static final String PUBMEDID_ATTR_CLASS_NAME = "Publication PubMed ID";
 public static final String PUBDOI_ATTR_CLASS_NAME = "Publication DOI";


 public static final String FORMAT_PARAM = "format";

 
 private static AgeViewConfigManager instance = null;
 
 public AgeViewConfigManager(ServletContext servletContext)
 {
  super(servletContext);
 }
 
 public static void setInstance( AgeViewConfigManager inst )
 {
  instance=inst;
 }

 
 public static AgeViewConfigManager instance()
 {
  return instance;
 }


}
