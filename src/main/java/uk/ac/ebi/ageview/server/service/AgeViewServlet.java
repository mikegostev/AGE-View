package uk.ac.ebi.ageview.server.service;

import uk.ac.ebi.age.admin.server.service.SessionRemoteServiceServlet;
import uk.ac.ebi.age.ext.user.exception.NotAuthorizedException;
import uk.ac.ebi.age.ui.shared.imprint.ObjectId;
import uk.ac.ebi.age.ui.shared.imprint.ObjectImprint;
import uk.ac.ebi.ageview.client.AgeViewGWTService;
import uk.ac.ebi.ageview.client.query.Report;
import uk.ac.ebi.ageview.client.query.SampleList;
import uk.ac.ebi.ageview.client.shared.MaintenanceModeException;

public class AgeViewServlet extends SessionRemoteServiceServlet implements AgeViewGWTService
{
 private static final long serialVersionUID = -7453910333033735880L;

 @Override
 public Report selectSampleGroups(String value, boolean searchSmp, boolean searchGrp, boolean searchAttrNm, boolean searchAttrVl, boolean refOnly, int offs, int cnt)
   throws MaintenanceModeException
 {
  return AgeViewService.getInstance().selectSampleGroups(value, searchSmp, searchGrp, searchAttrNm, searchAttrVl, refOnly, offs, cnt);
 }

// @Override
// public SampleList getSamplesByGroup(String grpID, String query, boolean searchAtNames, boolean searchAtValues, int offs, int cnt)
//   throws MaintenanceModeException
// {
//  return BioSDService.getInstance().getSamplesByGroup(grpID, query, searchAtNames, searchAtValues, offs, cnt);
// }

 @Override
 public SampleList getSamplesByGroupAndQuery(String grpId, String query, boolean searchAtNames, boolean searchAtValues, int offs, int cnt)
   throws MaintenanceModeException
 {
  return AgeViewService.getInstance().getSamplesByGroupAndQuery(grpId, query, searchAtNames, searchAtValues, offs, cnt);
 }

 @Override
 public ObjectImprint getObjectImprint(ObjectId id) throws MaintenanceModeException, NotAuthorizedException
 {
  return AgeViewService.getInstance().getObjectImprint( id );
 }

}
