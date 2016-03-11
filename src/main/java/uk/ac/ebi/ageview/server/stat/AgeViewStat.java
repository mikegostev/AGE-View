package uk.ac.ebi.ageview.server.stat;

import java.util.HashMap;
import java.util.Map;

public class AgeViewStat
{
 private int samples=0;
 private int groups=0;
 private int refGroups=0;
 private int refSamples=0;
 private int publications=0;
 private Map<String, AgeViewStat> topicStats;

 public void setGroups(int size)
 {
  groups=size;
 }

 public void addSamples(int sz)
 {
  samples+=sz;
 }

 public void addGroups(int i)
 {
  groups+=i;
 }

 public AgeViewStat getDataSourceStat(String ds)
 {
  if( topicStats == null )
  {
   topicStats = new HashMap<String, AgeViewStat>();
   
   AgeViewStat st = new AgeViewStat();
   topicStats.put(ds, st);
   
   return st;
  }
  
  AgeViewStat st = topicStats.get(ds);

  if( st == null )
  {
   st = new AgeViewStat();
   topicStats.put(ds, st);
  }
  
  return st;
 }

 public int getSamples()
 {
  return samples;
 }

 public int getGroups()
 {
  return groups;
 }

 public Map<String, AgeViewStat> getTopics()
 {
  return topicStats;
 }

 public int getRefGroups()
 {
  return refGroups;
 }

 public void setRefGroups(int refGroups)
 {
  this.refGroups = refGroups;
 }

 public int getRefSamples()
 {
  return refSamples;
 }

 public void setRefSamples(int refSamples)
 {
  this.refSamples = refSamples;
 }
 
 public void addRefSamples(int sz)
 {
  refSamples+=sz;
 }

 public int getPublications()
 {
  return publications;
 }

 public void setPublications(int publications)
 {
  this.publications = publications;
 }


}
