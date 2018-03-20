/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dscheck;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ErrorResultIOException;
import org.forgerock.opendj.ldap.SearchResultReferenceIOException;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;
import org.forgerock.opendj.ldif.ConnectionEntryReader;

/**
 *
 * @author rmfaller
 */
class Checker extends Thread {

    private final int threadid;
    private final Connection dsc;
    private String dsetag = null;


    Checker(int threadid, Connection dsc) {
        this.threadid = threadid;
        this.dsc = dsc;
    }

    @Override
    public void start() {
    }
    
    public void check(String dsobject, String dsbasedn) {
        boolean validobject = false;
        final ConnectionEntryReader reader = this.dsc.search(dsbasedn, SearchScope.SINGLE_LEVEL, dsobject , "etag");
        try {
            while (reader.hasNext()) {
                if (reader.isEntry()) {
                    validobject = true;
                    final SearchResultEntry entry0 = reader.readEntry();
                    this.dsetag = entry0.getAttribute("etag").firstValueAsString();
//                    System.out.println(this.threadid + ": " + dsobject + "," + dsbasedn + " = " + this .dsetag);
                } else {
                    // Got a continuation reference.
                    final SearchResultReference ref = reader.readReference();
                    System.out.println("Search result reference: " + ref.getURIs().toString());
                }
            }
        } catch (ErrorResultIOException | SearchResultReferenceIOException ex) {
            Logger.getLogger(Checker.class.getName()).log(Level.SEVERE, null, ex);
        }
        if (!validobject) {
            this.dsetag = "absentobject";
        }
    }

    String getEtag() {
        return (this.dsetag);
    }

}
