package uk.ac.ebi.ageview.server.service;

import uk.ac.ebi.ageview.server.stat.AgeViewStat;

public class UserCacheObject
{
 private String userName;
 
 private AgeViewStat statistics;
 
 private String allowTags;
 
 private String denyTags;

 public String getUserName()
 {
  return userName;
 }

 public void setUserName(String userName)
 {
  this.userName = userName;
 }

 public AgeViewStat getStatistics()
 {
  return statistics;
 }

 public void setStatistics(AgeViewStat statistics)
 {
  this.statistics = statistics;
 }

 public String getAllowTags()
 {
  return allowTags;
 }

 public void setAllowTags(String allowTags)
 {
  this.allowTags = allowTags;
 }

 public String getDenyTags()
 {
  return denyTags;
 }

 public void setDenyTags(String denyTags)
 {
  this.denyTags = denyTags;
 }
 
 
}
