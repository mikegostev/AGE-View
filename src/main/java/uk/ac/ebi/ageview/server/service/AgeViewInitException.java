package uk.ac.ebi.ageview.server.service;


public class AgeViewInitException extends Exception
{

 private static final long serialVersionUID = 1L;

 public AgeViewInitException( String msg )
 {
  super(msg);
 }
 
 public AgeViewInitException( String msg, Throwable t )
 {
  super(msg, t);
 }
}
