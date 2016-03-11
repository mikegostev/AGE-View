package uk.ac.ebi.ageview.server.xmlexport;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import uk.ac.ebi.age.admin.server.mng.Configuration;
import uk.ac.ebi.age.admin.server.service.ServiceServlet;
import uk.ac.ebi.age.authz.ACR.Permit;
import uk.ac.ebi.age.authz.PermissionManager;
import uk.ac.ebi.age.authz.Session;
import uk.ac.ebi.age.ext.authz.SystemAction;
import uk.ac.ebi.ageview.server.service.AgeViewService;

public class BiosamplesXMLExport extends ServiceServlet
{
 private static final long serialVersionUID = 1L;

 @Override
 protected void service(HttpServletRequest req, HttpServletResponse resp, Session sess) throws ServletException, IOException
 {
  AgeViewService biosd = AgeViewService.getInstance();
  
  if( biosd == null )
  {
   resp.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
   return;
  }
  
  PermissionManager pm = Configuration.getDefaultConfiguration().getPermissionManager();
  
  if( pm.checkSystemPermission(SystemAction.EXPORT_GRAPH_DATA, sess.getUser()) != Permit.ALLOW )
  {
   resp.sendError(HttpServletResponse.SC_FORBIDDEN);
   return;
  }
  
  String groupIds = req.getParameter("group");
  String sinceParam = req.getParameter("since");
  
  String[] grpLst = null;
  
  if( groupIds != null )
   grpLst = groupIds.split(";");
  
  long since = 0;
  
  if( sinceParam != null )
  {
   try
   {
    since = Long.parseLong(sinceParam);
    
   }
   catch(Exception e)
   {
    resp.getWriter().print("Invalid 'since' parameter value. Should be long value (ms since 1970)");
    return;
   }
  }
  
  resp.setContentType("text/xml; charset=UTF-8");
  resp.setHeader("Content-Disposition", "attachment; filename=\"biosamples.xml\"");

  if( since != 0 )
   biosd.exportData(resp.getWriter(), since);
  else
   biosd.exportData( resp.getWriter(), grpLst );
 }

}
