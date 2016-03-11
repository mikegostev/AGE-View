package uk.ac.ebi.ageview.client.ui;

import uk.ac.ebi.ageview.client.query.Report;

public interface ResultRenderer
{
 void showResult( Report rep, String query, boolean sSmp, boolean sGrp, boolean sAtrNm, boolean sAtrVl, int cpage );
}
