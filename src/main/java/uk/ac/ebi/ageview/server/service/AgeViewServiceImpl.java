package uk.ac.ebi.ageview.server.service;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.NullFragmenter;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;

import uk.ac.ebi.age.admin.server.mng.AgeAdmin;
import uk.ac.ebi.age.admin.server.mng.Configuration;
import uk.ac.ebi.age.annotation.AnnotationManager;
import uk.ac.ebi.age.annotation.Topic;
import uk.ac.ebi.age.authz.ACR.Permit;
import uk.ac.ebi.age.authz.BuiltInUsers;
import uk.ac.ebi.age.authz.PermissionManager;
import uk.ac.ebi.age.authz.SecurityChangedListener;
import uk.ac.ebi.age.ext.annotation.AnnotationDBException;
import uk.ac.ebi.age.ext.authz.SystemAction;
import uk.ac.ebi.age.ext.authz.TagRef;
import uk.ac.ebi.age.ext.entity.Entity;
import uk.ac.ebi.age.ext.submission.SubmissionDBException;
import uk.ac.ebi.age.ext.submission.SubmissionMeta;
import uk.ac.ebi.age.ext.submission.SubmissionQuery;
import uk.ac.ebi.age.ext.submission.SubmissionReport;
import uk.ac.ebi.age.ext.user.exception.NotAuthorizedException;
import uk.ac.ebi.age.model.AgeAttribute;
import uk.ac.ebi.age.model.AgeAttributeClass;
import uk.ac.ebi.age.model.AgeClass;
import uk.ac.ebi.age.model.AgeObject;
import uk.ac.ebi.age.model.AgeObjectAttribute;
import uk.ac.ebi.age.model.AgeRelation;
import uk.ac.ebi.age.model.AgeRelationClass;
import uk.ac.ebi.age.model.Attributed;
import uk.ac.ebi.age.model.DataType;
import uk.ac.ebi.age.query.AgeQuery;
import uk.ac.ebi.age.query.ClassOrSuperclassNameExpression;
import uk.ac.ebi.age.storage.AgeStorage;
import uk.ac.ebi.age.storage.DataChangeListener;
import uk.ac.ebi.age.storage.MaintenanceModeListener;
import uk.ac.ebi.age.storage.exeption.IndexIOException;
import uk.ac.ebi.age.storage.index.Selection;
import uk.ac.ebi.age.storage.index.TextFieldExtractor;
import uk.ac.ebi.age.storage.index.TextIndex;
import uk.ac.ebi.age.storage.index.TextValueExtractor;
import uk.ac.ebi.age.ui.server.imprint.ImprintBuilder;
import uk.ac.ebi.age.ui.server.imprint.ImprintingHint;
import uk.ac.ebi.age.ui.server.imprint.StringProcessor;
import uk.ac.ebi.age.ui.shared.imprint.AttributeImprint;
import uk.ac.ebi.age.ui.shared.imprint.ClassImprint;
import uk.ac.ebi.age.ui.shared.imprint.ObjectId;
import uk.ac.ebi.age.ui.shared.imprint.ObjectImprint;
import uk.ac.ebi.age.ui.shared.imprint.ObjectValue;
import uk.ac.ebi.age.ui.shared.imprint.Value;
import uk.ac.ebi.ageview.client.query.AttributedImprint;
import uk.ac.ebi.ageview.client.query.GroupImprint;
import uk.ac.ebi.ageview.client.query.Report;
import uk.ac.ebi.ageview.client.query.SampleList;
import uk.ac.ebi.ageview.client.shared.MaintenanceModeException;
import uk.ac.ebi.ageview.server.stat.AgeViewStat;
import uk.ac.ebi.mg.assertlog.Log;
import uk.ac.ebi.mg.assertlog.LogFactory;

import com.pri.util.StringUtils;
import com.pri.util.collection.FilterIterator;
import com.pri.util.collection.Predicate;

public class AgeViewServiceImpl extends AgeViewService implements SecurityChangedListener
{
 private static Log log = LogFactory.getLog(AgeViewServiceImpl.class);
 
 private AgeStorage storage;
 
 private static final String ROOTOBJ_INDEX_NAME="ROOTOBJINDEX";
 
 private TextIndex rootObjIndex;
 
 private AgeQuery rootSelectQuery;
// private List<AgeObject> groupList;
 
 private AgeClass rootClass;
 
 private AgeRelationClass objToPublicationRelClass;
 private AgeRelationClass objToContactRelClass;
 
 private AgeAttributeClass titleAttributeClass;
 private AgeAttributeClass desciptionAttributeClass;

 
 private AgeViewStat statistics;
 
 private volatile boolean  maintenanceMode = false;
 
 private final WeakHashMap<String, UserCacheObject> userCache  = new WeakHashMap<String, UserCacheObject>();
 
 private final Analyzer analizer = new StandardAnalyzer();
 private final QueryParser queryParser = new QueryParser( AgeViewConfigManager.ATTR_VALUES_FIELD_NAME, analizer );
 private final SimpleHTMLFormatter htmlFormatter = new SimpleHTMLFormatter("<span class='sHL'>","</span>");

 private ImprintingHint objConvHint;
 

 private static class GroupKey
 {
  String grpName;
  boolean refGroup;
 }
 
 private final Comparator<GroupKey> groupComparator = new Comparator<GroupKey>()
 {
  @Override
  public int compare(GroupKey o1, GroupKey o2)
  {
 
   if( o1.refGroup == o2.refGroup )
    return StringUtils.naturalCompare(o1.grpName, o1.grpName);

   return o1.refGroup?-1:1;
   
  }
 };
 

 private final StringProcessor htmlEscProc = new StringProcessor()
 {
  @Override
  public String process(String str)
  {
   return StringUtils.htmlEscaped(str);
  }
 };
 
 public AgeViewServiceImpl( AgeStorage stor ) throws AgeViewInitException
 {
  long startTime=0;
  
  storage=stor;
  
  rootClass = storage.getSemanticModel().getDefinedAgeClass( AgeViewConfigManager.ROOT_CLASS_NAME );
  titleAttributeClass = storage.getSemanticModel().getDefinedAgeAttributeClass( AgeViewConfigManager.TITLE_ATTR_CLASS_NAME );
  desciptionAttributeClass = storage.getSemanticModel().getDefinedAgeAttributeClass( AgeViewConfigManager.DESCRIPTION_ATTR_CLASS_NAME );
  
  objToPublicationRelClass = storage.getSemanticModel().getDefinedAgeRelationClass(AgeViewConfigManager.HAS_PUBLICATION_REL_CLASS_NAME);
  objToContactRelClass = storage.getSemanticModel().getDefinedAgeRelationClass(AgeViewConfigManager.CONTACT_OF_REL_CLASS_NAME).getInverseRelationClass();
  
  if( rootClass == null )
  {
   System.out.println("Can't find Sample class");
   return;
  }

 
  if( desciptionAttributeClass == null )
  {
   System.out.println("Can't find "+AgeViewConfigManager.DESCRIPTION_ATTR_CLASS_NAME+" class");
   return;
  }
 
  if( titleAttributeClass == null )
  {
   System.out.println("Can't find "+AgeViewConfigManager.TITLE_ATTR_CLASS_NAME+" relation");
   return;
  }
  
 
  objConvHint = new ImprintingHint();
  objConvHint.setConvertRelations(true);
  objConvHint.setConvertAttributes(true);
  objConvHint.setQualifiersDepth(2);
  objConvHint.setResolveObjectAttributesTarget(true);
  objConvHint.setResolveRelationsTarget(false);
  objConvHint.setConvertImplicitRelations(false);
  
  ClassOrSuperclassNameExpression clsExp = new ClassOrSuperclassNameExpression();
  clsExp.setClassName( AgeViewConfigManager.ROOT_CLASS_NAME );

//  orExp.addExpression(clsExp);

  
  rootSelectQuery = AgeQuery.create(clsExp);
  
  ArrayList<TextFieldExtractor> extr = new ArrayList<TextFieldExtractor>(4);
  
  TagsExtractor tagExtr = new TagsExtractor();
  OwnerExtractor ownExtr = new OwnerExtractor();

  extr.add( new TextFieldExtractor(AgeViewConfigManager.SECTAGS_FIELD_NAME, tagExtr));
  extr.add( new TextFieldExtractor(AgeViewConfigManager.OWNER_FIELD_NAME, ownExtr) );
  
  extr.add( new TextFieldExtractor(AgeViewConfigManager.ATTR_NAMES_FIELD_NAME, new AttrNamesExtractor()) );
  extr.add( new TextFieldExtractor(AgeViewConfigManager.ATTR_VALUES_FIELD_NAME, new AttrValuesExtractor()) );

  
  assert ( startTime = System.currentTimeMillis() ) != 0;

  long idxTime=0;

  try
  {
   assert ( idxTime = System.currentTimeMillis() ) != 0;

   rootObjIndex = storage.createTextIndex(ROOTOBJ_INDEX_NAME, rootSelectQuery, extr);

   assert log.info("Group index building time: "+StringUtils.millisToString(System.currentTimeMillis()-idxTime));

  }
  catch(IndexIOException e)
  {
   throw new AgeViewInitException("Init failed. Can't create group index",e);
  }

  
  assert log.info("Indices building time: "+StringUtils.millisToString(System.currentTimeMillis()-startTime));

  
  
  
  storage.addDataChangeListener( new DataChangeListener() 
  {
   @Override
   public void dataChanged()
   {
    collectStats();
   }
  } );
  

  storage.addMaintenanceModeListener(new MaintenanceModeListener()
  {
   @Override
   public void exitMaintenanceMode()
   {
    maintenanceMode = false;
   }
   
   @Override
   public void enterMaintenanceMode()
   {
    maintenanceMode = true;
   }
  });

  assert ( startTime = System.currentTimeMillis() ) != 0;
  
  collectStats();

  assert log.info("Stats collecting time: "+(System.currentTimeMillis()-startTime)+"ms");

 }

 private void collectStats()
 {
  try
  {
   storage.lockRead();

   AgeAttributeClass pubsClass = storage.getSemanticModel().getDefinedAgeAttributeClass(AgeViewConfigManager.PUBLICATIONS_ATTR_CLASS_NAME);
   AgeAttributeClass pubMedIdClass = storage.getSemanticModel().getDefinedAgeAttributeClass(AgeViewConfigManager.PUBMEDID_ATTR_CLASS_NAME);
   AgeAttributeClass pubDOIClass = storage.getSemanticModel().getDefinedAgeAttributeClass(AgeViewConfigManager.PUBDOI_ATTR_CLASS_NAME);

   Map<String, String> pubMedMap = new HashMap<String, String>();
   Map<String, String> doiMap = new HashMap<String, String>();

   statistics = new AgeViewStat();

   List< ? extends AgeObject> groupList = rootObjIndex.getObjectList();

   statistics.setGroups(groupList.size());

   int refGrp = 0;

   for(AgeObject grp : groupList)
   {
    Collection< ? extends AgeAttribute> ref = grp.getAttributesByClass(referenceAttributeClass, false);

    boolean isRef = ref != null && ref.size() > 0 && ref.iterator().next().getValueAsBoolean();

    if(isRef)
     refGrp++;

    int samples = 0;
    for(AgeRelation rel : grp.getRelations())
    {
     if(rel.getAgeElClass() == groupToSampleRelClass)
      samples++;
    }

    //    Collection<? extends AgeRelation> smpRels = grp.getRelationsByClass(groupToSampleRelClass, true);
    //    
    //    
    //    if( smpRels != null )
    //     samples = smpRels.size();

    statistics.addSamples(samples);

    if(isRef)
     statistics.addRefSamples(samples);

    Object dsVal = grp.getAttributeValue(dataSourceAttributeClass);

    String ds = null;

    if(dsVal != null)
     ds = dsVal.toString();

    if(ds != null)
    {
     AgeViewStat dsStat = statistics.getDataSourceStat(ds);
     dsStat.addGroups(1);
     dsStat.addSamples(samples);

     if(isRef)
      dsStat.addRefSamples(samples);
    }

    Collection< ? extends AgeAttribute> pubs = grp.getAttributesByClass(pubsClass, false);

    if(pubs != null)
    {
     for(AgeAttribute pub : pubs)
     {
      AgeObject pubObj = ((AgeObjectAttribute) pub).getValue();

      Object val = pubObj.getAttributeValue(pubMedIdClass);

      String pmId = null;

      if(val == null)
       pmId = null;
      else
      {
       pmId = val.toString();

       if(pmId.length() == 0)
        pmId = null;
      }

      val = pubObj.getAttributeValue(pubDOIClass);

      String doi = null;

      if(val == null)
       doi = null;
      else
      {
       doi = val.toString();

       if(doi.length() == 0)
        doi = null;
      }

      if(pmId != null)
       pubMedMap.put(pmId, doi);

      if(doi != null && doiMap.get(doi) == null)
       doiMap.put(doi, pmId);
     }
    }
   }

   int doiCount = 0;
   for(Map.Entry<String, String> me : doiMap.entrySet())
    if(me.getValue() == null)
     doiCount++;

   statistics.setRefGroups(refGrp);
   statistics.setPublications(pubMedMap.size() + doiCount);
  }
  finally
  {
   storage.unlockRead();
  }

 }
 
 @Override
 
 public Report selectRootObjects(String query, boolean searchAttrNm, boolean searchAttrVl, int offset, int count) throws MaintenanceModeException
 {
  
  Highlighter highlighter = null;

  try
  {
   if( query != null )
   {
    highlighter = new Highlighter(htmlFormatter, new QueryScorer( queryParser.parse(query) ) );
    highlighter.setTextFragmenter(new NullFragmenter());
   }
  }
  catch(ParseException e)
  {
   Report rep = new Report();
   rep.setObjects(new ArrayList<GroupImprint>());
   rep.setTotalGroups(0);
   rep.setTotalSamples(0);
  
   return rep;
  }
  
  
  
  if( maintenanceMode )
   throw new MaintenanceModeException();
 
  String user = Configuration.getDefaultConfiguration().getSessionManager().getEffectiveUser();

  
  if( query == null )
   query = "";
  else
   query=query.trim();
  
  
  StringBuilder sb = new StringBuilder();
  
  if( query.length() > 0  )
  {
   sb.append("( ");

   if(searchAttrNm)
    sb.append(AgeViewConfigManager.ATTR_NAMES_FIELD_NAME).append(":(").append(query).append(") OR ");

   if(searchAttrVl)
    sb.append(AgeViewConfigManager.ATTR_VALUES_FIELD_NAME).append(":(").append(query).append(") OR ");

   sb.setLength(sb.length() - 4);

   sb.append(" ) AND ");
  }
  

  if( ! BuiltInUsers.SUPERVISOR.getName().equals(user) )
  {
   UserCacheObject uco = getUserCacheobject(user);

   sb.append("(").append(AgeViewConfigManager.SECTAGS_FIELD_NAME).append(":(").append(uco.getAllowTags()).append(") OR ")
   .append(AgeViewConfigManager.OWNER_FIELD_NAME).append(":(").append(user).append("))").append(" AND ");

   if(uco.getDenyTags().length() > 0)
    sb.append("NOT ").append(AgeViewConfigManager.SECTAGS_FIELD_NAME).append(":(").append(uco.getDenyTags()).append(") AND ");
  }
  
  int qLen = sb.length()-5;
 

  sb.setLength(sb.length() - 5);
  
  String lucQuery = sb.toString(); 
  
  assert log.debug("Query: "+lucQuery);
  
  Selection sel = null;
  Report rep = new Report();
  
  try
  {
   storage.lockRead();

   sel = rootObjIndex.select(lucQuery, offset, count, Collections.singletonList(AgeViewConfigManager.GROUP_SAMPLES_FIELD_NAME));

   List<GroupImprint> res = new ArrayList<GroupImprint>();

   List<AgeObject> groups = sel.getObjects();
   
   if(count > groups.size())
    count = groups.size();

   for(int i = 0; i < count; i++)
   {
    GroupImprint gr = createGroupObject(groups.get(i), searchGrp ? highlighter : null, searchAttrNm, searchAttrVl);

    if(searchSmp && query.length() > 0)
    {
     sb.setLength(qLen);

     sb.append(" AND " + AgeViewConfigManager.GROUP_ID_FIELD_NAME).append(":").append(gr.getId());
     gr.setMatchedCount(samplesIndex.count(sb.toString()));
    }

    res.add(gr);
   }

   rep.setObjects(res);
   rep.setTotalGroups(sel.getTotalCount());
   rep.setTotalSamples(sel.getAggregator(AgeViewConfigManager.GROUP_SAMPLES_FIELD_NAME));
  }
  finally
  {
   storage.unlockRead();
  }
  
  return rep;
 }
 
 private UserCacheObject getUserCacheobject(String user)
 {
  synchronized(userCache)
  {
   UserCacheObject uco = userCache.get(user);
   
   if( uco != null )
    return uco;
   
   uco = new UserCacheObject();
   uco.setUserName(user);

   StringBuilder sb = new StringBuilder(100);
   
   Collection<TagRef> tags = Configuration.getDefaultConfiguration().getPermissionManager().getAllowTags(SystemAction.READ, user);
   
   if( tags != null )
   {
    for( TagRef tr : tags )
     sb.append(tr.getClassiferName().length()).append(tr.getClassiferName()).append(tr.getTagName()).append(" ");
   }
   
   if( sb.length() > 0 )
    sb.setLength(sb.length()-1);
   else
    sb.append("XXX");
   
   uco.setAllowTags(sb.toString());
   
   sb.setLength(0);
   
   tags = Configuration.getDefaultConfiguration().getPermissionManager().getDenyTags(SystemAction.READ, user);

   if( tags != null )
   {
    for( TagRef tr : tags )
     sb.append(tr.getClassiferName().length()).append(tr.getClassiferName()).append(tr.getTagName()).append(" ");
   }
   
   if( sb.length() > 0 )
    sb.setLength(sb.length()-1);
   
   uco.setDenyTags(sb.toString());

   userCache.put(user, uco);
   
   return uco;
  }
 }

 private String highlight( Highlighter hlighter, Object str )
 {
  if( str == null )
   return null;
  
  String s = str.toString();

  if( hlighter == null )
   return s;
  
  if( s.startsWith("http://") )
   return s;
  
  try
  {
   String out = hlighter.getBestFragment(analizer, "", s);
   
   if( out == null )
    return s;
   
   return out;
  }
  catch(Exception e)
  {
  }
 
  return s;
 }
 
 private GroupImprint createGroupObject( AgeObject obj, Highlighter hlighter, boolean hlName, boolean hlValue )
 {
  GroupImprint sgRep = new GroupImprint();

  String strN = null;
  String strV = null;
  
  strV=obj.getId();
  
  sgRep.setId(strV );

//  if( hlValue )  
//   strV = highlight(hlighter, obj.getId());

//  sgRep.addAttribute("Submission ID", obj.getSubmission().getId(), true, 0);
  sgRep.addAttribute("__ID", strV, true, 0);
  
  Object descVal = obj.getAttributeValue(desciptionAttributeClass);
  
  if( descVal != null  )
  {
   strV = StringUtils.htmlEscaped( descVal.toString() );
   
   if( hlValue )  
    strV = highlight(hlighter, strV);
    
   sgRep.setDescription( strV );
  }
  
 
  for( AgeAttribute atr : obj.getAttributes() )
  {
   AgeAttributeClass atCls = atr.getAgeElClass();
   
   if( atCls.isClassOrSubclass( commentAttributeClass ) )
   {
    strN = StringUtils.htmlEscaped( atCls.getName() );
    
    if( hlName )  
     strN = highlight(hlighter, strN);

    strV = atr.getValue()!=null? StringUtils.htmlEscaped(atr.getValue().toString()):null;
    
    if( hlValue )  
     strV = highlight(hlighter, strV );
    
    sgRep.addOtherInfo( strN, strV );
   }
   else if( atCls.getDataType() == DataType.OBJECT )
   {
    strN = StringUtils.htmlEscaped( atCls.getName() );
    
    if( hlName )  
     strN = highlight(hlighter, strN);

    
    sgRep.attachObjects( strN, createAttributedObject( ((AgeObjectAttribute)atr).getValue(), hlighter, hlName, hlValue)  );
   } 
   else
   {
    strN = StringUtils.htmlEscaped( atCls.getName() );
    
    if( hlName )  
     strN = highlight(hlighter, strN);

    strV = atr.getValue()!=null? StringUtils.htmlEscaped(atr.getValue().toString()):null;
    
    if( hlValue )  
     strV = highlight(hlighter, strV );

     sgRep.addAttribute(strN, strV, atr.getAgeElClass().isCustom(),atr.getOrder());
   }
  }
  
  Collection<? extends AgeRelation> pubRels =  obj.getRelationsByClass(objToPublicationRelClass, false);

  if( pubRels != null )
  {
   if( hlName )  
    strN = highlight(hlighter, "Publications");
   else
    strN = "Publications";

   for( AgeRelation pRel : pubRels )
    sgRep.attachObjects( strN, createAttributedObject(pRel.getTargetObject(), hlighter, hlName, hlValue ) );
  }

  Collection<? extends AgeRelation> persRels =  obj.getRelationsByClass(objToContactRelClass, false);

  if( persRels != null )
  {
   if( hlName )  
    strN = highlight(hlighter, "Contacts");
   else
    strN = "Contacts";

   for( AgeRelation pRel : persRels )
    sgRep.attachObjects( "Contacts", createAttributedObject(pRel.getTargetObject(), hlighter, hlName, hlValue ) );
  }

  
  Collection<? extends AgeRelation> rels =  obj.getRelationsByClass(groupToSampleRelClass, false);
  
  int sCount=0;
  if( rels != null )
  {
   for( AgeRelation rl : rels )
    if( rl.getTargetObject().getAgeElClass() == sampleClass )
     sCount++;
  }
  
  sgRep.setRefCount( sCount );
  
  return sgRep;
 }

 private AttributedImprint createAttributedObject(AgeObject ageObj, Highlighter hlighter, boolean hlName, boolean hlValue )
 {
  String strN;
  String strV;
  
  AttributedImprint obj = new AttributedImprint();
  
  if( ageObj.getAttributes() != null )
  {
   for( AgeAttribute attr : ageObj.getAttributes() )
   {
    strN = StringUtils.htmlEscaped( attr.getAgeElClass().getName() );
    
    if( hlName )  
     strN = highlight(hlighter, strN);

    strV = attr.getValue()!=null? StringUtils.htmlEscaped(attr.getValue().toString()):null;
    
    if( hlValue )  
     strV = highlight( hlighter, strV );

    obj.addAttribute(strN, strV, attr.getAgeElClass().isCustom(), attr.getOrder());
   }
  }
  
  return obj;
 }
 
// private AgeObject getGroupForSample(AgeObject obj)
// {
//  for(AgeRelation rel : obj.getRelations() )
//  {
//   if( rel.getAgeElClass() == sampleInGroupRelClass && rel.getTargetObject().getAgeElClass() == groupClass )
//    return rel.getTargetObject();
//  }
//  
//  return null;
// }



 @Override
 public void shutdown()
 {
  storage.shutdown();
 }
 
 class TagsExtractor implements TextValueExtractor
 {
  private final PermissionManager permMngr = Configuration.getDefaultConfiguration().getPermissionManager();

  @Override
  public String getValue(AgeObject ao)
  {
   
   Collection<TagRef> tags = permMngr.getEffectiveTags( ao );
   
   if( tags == null )
    return "";
   
   StringBuilder sb = new StringBuilder();
   
   for( TagRef tr : tags )
    sb.append(tr.getClassiferName().length()).append(tr.getClassiferName()).append(tr.getTagName()).append(" ");
    
   String val = sb.toString(); 
   
   return val;
  }
  
 }
 
 class OwnerExtractor implements TextValueExtractor
 {
  private final AnnotationManager annorMngr = Configuration.getDefaultConfiguration().getAnnotationManager();

  @Override
  public String getValue(AgeObject ao)
  {
   Entity entId = ao;
   
   String own = null;
   
   while( entId != null )
   {
    try
    {
     own = (String)annorMngr.getAnnotation(Topic.OWNER, entId, true);
    }
    catch(AnnotationDBException e)
    {
     e.printStackTrace();
     return "";
    }
    
    if( own != null )
     break;
    
    entId = entId.getParentEntity();
   }
   
   if( own == null )
   {
    own = Configuration.getDefaultConfiguration().getSessionManager().getEffectiveUser();
    
    if( own == null )
     own = "";
   }

   return own;
  }
  
 }
 
 class GrpAttrValuesExtractor implements TextValueExtractor
 {
  @Override
  public String getValue(AgeObject gobj)
  {
   StringBuilder sb = new StringBuilder();
   Set<String> tokSet = new HashSet<String>();;
   
   getTokens(gobj, tokSet);
    
   for( String tk : tokSet )
    sb.append( tk ).append(' ');
    
    
   String res = sb.toString();
   
   return res;
  }
  
  private void getTokens( AgeObject obj, Set<String> tokSet )
  {
   for( AgeAttribute attr : obj.getAttributes() )
   {
    Object objval = attr.getValue();
    
    if(objval == null)
     continue;
    
    String strVal=null;
    
    if( attr.getAgeElClass().getDataType().isTextual() )
     strVal = objval.toString();
    
    if( strVal != null && strVal.length() > 0 )
    {
     tokSet.add( strVal );
     
     if( attr.getAgeElClass().getName().equals("Submission Identifier") )
      tokSet.addAll(StringUtils.splitString(strVal, '-'));

     continue;
    }
    
    if( attr.getAgeElClass().getDataType() == DataType.OBJECT )
     getTokens((AgeObject)objval,tokSet);
     
    if( attr.getAttributes() != null )
    {
     for( AgeAttribute qual : attr.getAttributes() )
     {
      Object qval = qual.getValue();
      
      if( qual.getAgeElClass().getDataType().isTextual() )
       tokSet.add( qval.toString() );
     }
    }

   }
  }
 }
 
 
 class RefGroupExtractor implements TextValueExtractor
 {
  @Override
  public String getValue(AgeObject gobj)
  {
   for( AgeAttribute attr : gobj.getAttributes() )
   {
    if( attr.getAgeElClass() == referenceAttributeClass && attr.getValueAsBoolean() )
     return "true";
   }
   
   return "";
  }
 }
 
 class SamplesCountExtractor implements TextValueExtractor
 {
  @Override
  public String getValue(AgeObject gobj)
  {
   int count = 0;
   
   for( AgeRelation rel : gobj.getRelations() )
   {
    if( rel.getAgeElClass() == groupToSampleRelClass )
     count++;
   }
   
   return String.valueOf(count);
  }
 }

 
 
 abstract static class AttrExtractor implements TextValueExtractor
 {
  public abstract void set(AgeAttribute attr, Set<String> tokSet);
  
  private void collectTokens(AgeObject gobj, Set<AgeObject> objSet, Set<String> tokSet )
  {
   objSet.add(gobj);
   
   if( gobj.getAttributes() != null  )
   {
    for( AgeAttribute attr : gobj.getAttributes() )
    {
     if( attr.getAgeElClass().getDataType() == DataType.OBJECT )
     {
      AgeObject obj = (AgeObject)attr.getValue();
      
      if( obj != null && ! objSet.contains(obj) && obj.getDataModule() == gobj.getDataModule() )
       collectTokens((AgeObject)attr.getValue(), objSet, tokSet);
     }

     set(attr, tokSet);
     
    }
   }
   
   if( gobj.getRelations() != null )
   {
    for( AgeRelation rl : gobj.getRelations() )
    {
     AgeObject robj = rl.getTargetObject();
     
     if( robj != null && ! objSet.contains(robj) && robj.getDataModule() == gobj.getDataModule() )
      collectTokens(robj, objSet, tokSet);
    }
    
   }
   
  }
  
  
  @Override
  public String getValue(AgeObject gobj)
  {
   StringBuilder sb = new StringBuilder();
   Set<String> tokSet = new HashSet<String>();
   Set<AgeObject> objSet = new HashSet<AgeObject>();

   collectTokens(gobj,objSet,tokSet);
   
   for( String tk : tokSet )
    sb.append( tk ).append(' ');

   String res = sb.toString();
  
   return res;
  }
 }
 
 static class AttrNamesExtractor extends AttrExtractor
 {
  @Override
  public void set(AgeAttribute attr, Set<String> tokSet)
  {
   tokSet.add( attr.getAgeElClass().getName() );
  }
 }
 
 static class AttrValuesExtractor extends AttrExtractor
 {
  @Override
  public void set(AgeAttribute attr, Set<String> tokSet)
  {
   Object val = attr.getValue();
   
   if( attr.getAgeElClass().getDataType().isTextual() )
   {
    String txt = val.toString();

    if( txt == null )
     return;
    
    for( String str : StringUtils.splitString(txt, ' ') )
    {
     str = str.trim();
     
     if( str.length() > 0 )
      tokSet.add(str);
    }
   }
  }
 }

 
 class GroupIDExtractor implements TextValueExtractor
 {
 
  @Override
  public String getValue(AgeObject gobj)
  {
   for(AgeRelation rel : gobj.getRelations())
   {
    if(rel.getAgeElClass() == sampleInGroupRelClass)
    {
     return rel.getTargetObject().getId();
    }
   }
   return "";
  }
 }

 
 @Override
 public ObjectImprint getObjectImprint( ObjectId id ) throws MaintenanceModeException, NotAuthorizedException
 {
  if( maintenanceMode )
   throw new MaintenanceModeException();
  
  
  String user = Configuration.getDefaultConfiguration().getSessionManager().getEffectiveUser();
  
  try
  {
   storage.lockRead();

   AgeObject obj = storage.getObject( id.getClusterId(), id.getModuleId(), id.getObjectId() );
   
   if( obj == null )
    return null;
   
   if( ! BuiltInUsers.SUPERVISOR.getName().equals(user) )
   {
    if( Configuration.getDefaultConfiguration().getPermissionManager().checkPermission(SystemAction.READ, user, obj) != Permit.ALLOW  )
     throw new NotAuthorizedException();
   }
   
   ImprintBuilder ibld = new ImprintBuilder( htmlEscProc, htmlEscProc, null, null);
   
   return ibld.convert(obj, objConvHint);
  }
  finally
  {
   storage.unlockRead();
  }
 }
 
 private SampleList createSampleReportXX(List<AgeObject> samples, Highlighter hlite, boolean hlNm, boolean hlVl )
 {
  SampleList sl = new SampleList();
  
  class LinkCount
  {
   ClassImprint imprint;
   int counter;
  }
  
  Map<ClassImprint, LinkCount > clsMap = new HashMap<ClassImprint, LinkCount>();
  
  
  
  ImprintBuilder iBld = new ImprintBuilder( htmlEscProc, htmlEscProc, null, null);
  
  for( AgeObject smpl : samples )
  {
   ObjectImprint imp = iBld.convert(smpl,objConvHint);
   
   sl.addSample( imp );
   
//   sl.addSample( convertAttributed(smpl, hlite, hlNm, hlVl) );

   int ord=0;
   
   for( AttributeImprint attr : imp.getAttributes() )
   {
    ord++;
    
    LinkCount cCnt = clsMap.get(attr.getClassImprint());
    
    if( cCnt == null )
    {
     cCnt = new LinkCount();
     cCnt.counter=5000-ord;
     cCnt.imprint=attr.getClassImprint();
     clsMap.put(cCnt.imprint, cCnt);
    }
    else
     cCnt.counter+=5000-ord;
    
//    AttributeClassReport atCls = new AttributeClassReport();
//     
//    atCls.setCustom( ageAtCls.isCustom() );
//     
//    String cName = ageAtCls.getName();
//     
//    if( hlNm )
//     cName = highlight(hlite, cName);
//     
//    atCls.setName( cName );
////     atCls.setId("AttrClass"+(id++));
//    atCls.setId((ageAtCls.isCustom()?"CC:":"DC:")+ageAtCls.getName()+"$"+cCnt.value);
//     
//    atCls.addValue(attrval);
    
   }
   
  }
  
  List<LinkCount> clsLst = new ArrayList<LinkCount>( clsMap.size() );
  clsLst.addAll(clsMap.values());
  
  Collections.sort(clsLst, new Comparator<LinkCount>()
  {
   @Override
   public int compare(LinkCount o1, LinkCount o2)
   {
    if( AgeViewConfigManager.SAMPLE_ACCS_ATTR_CLASS_NAME.equals( o1.imprint.getName() ) )
    {
     if( AgeViewConfigManager.SAMPLE_ACCS_ATTR_CLASS_NAME.equals( o2.imprint.getName() ) )
      return 0;
     else
      return -1;
    }
    else if( AgeViewConfigManager.SAMPLE_ACCS_ATTR_CLASS_NAME.equals( o2.imprint.getName() ) )
     return 1;
    
    return o2.counter-o1.counter;
   }
  });
  
  for( LinkCount lc : clsLst )
  {
   sl.addHeader( lc.imprint );
  
   if( hlNm )
    lc.imprint.setName( highlight(hlite, lc.imprint.getName()) );
  }
  
  if( hlVl && hlite != null)
  {
   for( ObjectImprint obj : sl.getSamples() )
    highlightAttributedImprint(hlite, obj);
  }
  
  return sl;
 }
 
 private void highlightAttributedImprint( Highlighter hlite, uk.ac.ebi.age.ui.shared.imprint.AttributedImprint obj )
 {
  if( obj.getAttributes() == null )
   return;
  
  for( AttributeImprint atImp : obj.getAttributes() )
  {
   if( atImp.getClassImprint().getType() == uk.ac.ebi.age.ui.shared.imprint.ClassType.ATTR_STRING)
   {
    for( Value v : atImp.getValues() )
    {
     ((uk.ac.ebi.age.ui.shared.imprint.StringValue)v).setValue( highlight(hlite, v.getStringValue() ) );
     
     highlightAttributedImprint( hlite, v );
    }
   }
   else if( atImp.getClassImprint().getType() == uk.ac.ebi.age.ui.shared.imprint.ClassType.ATTR_OBJECT )
   {
    for( Value v : atImp.getValues() )
    {
     if( ((ObjectValue)v).getObjectImprint() != null )
      highlightAttributedImprint( hlite, ((ObjectValue)v).getObjectImprint() );
     
     highlightAttributedImprint( hlite, v );
    }
   }
  }
 }

 
 @Override
 public void exportData( PrintWriter out, long since )
 {
 
  try
  {
   storage.lockRead();

   SubmissionQuery sq = new SubmissionQuery();
   
   sq.setModifiedFrom( since );
   sq.setTotal(1);
   sq.setLimit(Integer.MAX_VALUE);
   
   SubmissionReport  subs = AgeAdmin.getDefaultInstance().getSubmissions(sq);
   
   final HashSet<String> clustSet = new HashSet<String>();
   
   for( SubmissionMeta sm : subs.getSubmissions() )
   {
//    System.out.println(sm.getModificationTime());
    clustSet.add(sm.getId());
   }
   
   Iterator<? extends AgeObject> grpIt = new FilterIterator<AgeObject>(rootObjIndex.getObjectList().iterator(), new Predicate<AgeObject>()
    {
     @Override
     public boolean evaluate(AgeObject ao)
     {
      return clustSet.contains(ao.getModuleKey().getClusterId());
     }
    });
   
   exportDataAsXML(out, grpIt);

  }
  catch(SubmissionDBException e)
  {
   out.print("ERROR: "+e.getMessage());
  }
  finally
  {
   storage.unlockRead();
  }
 }

 
 @Override
 public void exportData( PrintWriter out, final String[] grps )
 {
  try
  {
   storage.lockRead();

   Iterator<? extends AgeObject> grpIt=null;
   
   if( grps != null )
    grpIt = new FilterIterator<AgeObject>(rootObjIndex.getObjectList().iterator(), new Predicate<AgeObject>()
    {
     @Override
     public boolean evaluate(AgeObject ao)
     {
      for(String grp : grps)
       if( ao.getId().equals(grp) )
        return true;
     
      return false;
     }
    });
   else
    grpIt = rootObjIndex.getObjectList().iterator();
   
   exportDataAsXML(out, grpIt);

  }
  finally
  {
   storage.unlockRead();
  }

 }
 
 private void exportDataAsXML( PrintWriter out, Iterator<? extends AgeObject> grpsIT )
 {

  out.println("<Biosamples>");

  while(grpsIT.hasNext())
  {
   AgeObject ao = grpsIT.next();
   
   Set<AgeAttributeClass> attrset = new HashSet<AgeAttributeClass>();

   String grpId = StringUtils.xmlEscaped(ao.getId());

   out.print("<SampleGroup id=\"");
   out.print(grpId);
   out.println("\">");

   exportAttributed(ao, out, null);

   for(AgeRelation rel : ao.getRelations())
   {
//    if(rel.getAgeElClass() == groupToSampleRelClass)
//     exportSample(rel.getTargetObject(), grpId, out, attrset, false);
   }

   out.println("<SampleAttributes>");

   for(AgeAttributeClass aac : attrset)
   {
    out.print("<attribute class=\"");
    out.print(StringUtils.xmlEscaped(aac.getName()));
    out.println("\" classDefined=\"" + (aac.isCustom() ? "false" : "true") + "\" dataType=\"" + aac.getDataType().name() + "\"/>");
   }

   out.println("</SampleAttributes>");

   out.println("</SampleGroup>");
  }

  out.println("</Biosamples>");

 }

 
 private void exportAttributed( Attributed ao,PrintWriter out, Set<AgeAttributeClass> atset )
 {
  for( AgeAttributeClass aac : ao.getAttributeClasses() )
  {
   if( atset != null )
    atset.add(aac);
   
   out.print("<attribute class=\"");
   out.print(StringUtils.xmlEscaped(aac.getName()));
   out.println("\" classDefined=\""+(aac.isCustom()?"false":"true")+"\" dataType=\""+aac.getDataType().name()+"\">");

   for( AgeAttribute attr : ao.getAttributesByClass(aac, false) )
   {
    if( aac.getDataType() == DataType.OBJECT )
     out.print("<objectValue>");
    else
     out.print("<simpleValue>");
    
    exportAttributed( attr, out, null );

    if( aac.getDataType() != DataType.OBJECT )
    {
     out.print("<value>");
     out.print(StringUtils.xmlEscaped(attr.getValue().toString()));
     out.print("</value>");
    }
    else
    {
     AgeObject tgtObj = (AgeObject)attr.getValue();
     
     out.print("<object id=\""+StringUtils.xmlEscaped(tgtObj.getId())+"\" class=\"");
     out.print(StringUtils.xmlEscaped(tgtObj.getAgeElClass().getName()));
     out.println("\" classDefined=\""+(tgtObj.getAgeElClass().isCustom()?"false":"true")+"\">");
 
     exportAttributed( tgtObj, out, null );
    
     out.println("</object>");
    }

    if( aac.getDataType() == DataType.OBJECT )
     out.println("</objectValue>");
    else
     out.println("</simpleValue>");
   }

   out.println("</attribute>");
  }
 }
 
 @Override
 public void exportSample( AgeObject ao, String grpId, PrintWriter out, Set<AgeAttributeClass> atset, boolean showRels )
 {
  out.print("<Sample id=\"");
  out.print(StringUtils.xmlEscaped(ao.getId()));
  out.println("\" groupId=\"" +grpId +"\">");

  exportAttributed( ao, out, atset );

  if( showRels && ao.getRelations() != null )
  {
   for( AgeRelation rl : ao.getRelations() )
   {
    if( rl.isInferred() )
     continue;

    String clsName = rl.getAgeElClass().getName();

    out.print("<relation class=\"");
    out.print(StringUtils.xmlEscaped(clsName));
    out.print("\" targetId=\"");
    out.print(StringUtils.xmlEscaped(rl.getTargetObjectId()));
    out.print("\" targetClass=\"" +rl.getTargetObject());
    out.print(StringUtils.xmlEscaped(rl.getTargetObject().getAgeElClass().getName()));
    out.println("\" />");
    
   }
   
  }
   
  
  out.println("</Sample>");
 }
 
 @Override
 public void exportGroup( AgeObject ao, PrintWriter out, boolean showRels )
 {
  out.print("<SampleGroup xmlns=\"http://www.ebi.ac.uk/biosamples/SampleGroupExportV1\" id=\"");
  out.print(StringUtils.xmlEscaped(ao.getId()));
  out.println("\">");

  exportAttributed( ao, out, null );

  if( showRels && ao.getRelations() != null )
  {
   for( AgeRelation rl : ao.getRelations() )
   {
    String clsName = rl.getAgeElClass().getName();

//    if( rl.getInverseRelation().getAgeElClass() == sampleInGroupRelClass )
//     clsName = "containedIn";
//    else if( rl.isInferred() )
//     continue;


    out.print("<relation class=\"");
    out.print(StringUtils.xmlEscaped(clsName));
    out.print("\" targetId=\"");
    out.print(StringUtils.xmlEscaped(rl.getTargetObjectId()));
    out.print("\" targetClass=\"" +rl.getTargetObject());
    out.print(StringUtils.xmlEscaped(rl.getTargetObject().getAgeElClass().getName()));
    out.println("\" />");
    
   }
   
  }
  
  out.println("</SampleGroup>");
 }


 
// @Override
// public Report getAllGroups(int offset, int count, boolean refOnly )
// {
//  
//  int lim = offset+count;
//  
//  List<? extends AgeObject> groupList = groupsIndex.getObjectList();
//  
//  int last = refOnly?getStatistics().getRefGroups():groupList.size();
//  
//  if( lim > last )
//   lim=last;
//  
//  List<GroupImprint> res = new ArrayList<GroupImprint>(count);
//  
//  String user = Configuration.getDefaultConfiguration().getSessionManager().getEffectiveUser();
//
//  
//  if( ! BuiltInUsers.SUPERVISOR.getName().equals(user) )
//  {
//   UserCacheObject uco = getUserCacheobject(user);
//
//   sb.append(" AND (").append(BioSDConfigManager.SECTAGS_FIELD_NAME).append(":(").append(uco.getAllowTags()).append(") OR ")
//   .append(BioSDConfigManager.OWNER_FIELD_NAME).append(":(").append(user).append("))");
//
//   if(uco.getDenyTags().length() > 0)
//    sb.append(" NOT ").append(BioSDConfigManager.SECTAGS_FIELD_NAME).append(":(").append(uco.getDenyTags()).append(")");
//  }
//  else
//  {
//   for( ; offset < lim; offset++)
//    res.add( createGroupObject(groupList.get(offset)) );
//  }
//
//  
//  
//  Report rep = new Report();
//  rep.setObjects(res);
//  rep.setTotalGroups(refOnly?getStatistics().getRefGroups():getStatistics().getGroups());
//  rep.setTotalSamples(refOnly?getStatistics().getRefSamples():getStatistics().getSamples());
//  
//  return rep;
// }

 @Override
 public AgeViewStat getStatistics()
 {
  return statistics;
 }

 @Override
 public void securityChanged()
 {
  synchronized(userCache)
  {
   userCache.clear();
  }
 }


 @Override
 public AgeObject getRootObject(String groupId) throws MaintenanceModeException
 {
  if( maintenanceMode )
   throw new MaintenanceModeException();

  try
  {
   storage.lockRead();
   
   AgeObject smpObj = storage.getGlobalObject(groupId);
   
   if( smpObj == null || ! smpObj.getAgeElClass().isClassOrSubclass(rootClass) )
    return null;
   
   return smpObj;
  }
  finally
  {
   storage.unlockRead();
  }
 }

}
