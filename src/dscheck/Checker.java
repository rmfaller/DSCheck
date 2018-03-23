/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dscheck;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ErrorResultIOException;
import org.forgerock.opendj.ldap.SearchResultReferenceIOException;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;
import org.forgerock.opendj.ldif.ConnectionEntryReader;
import org.forgerock.opendj.ldif.LDIFEntryWriter;

/**
 *
 * @author rmfaller
 */
class Checker extends Thread {

    private final int threadid;
    private final Connection dsc;
    private String dsetag = null;
    final LDIFEntryWriter writer = new LDIFEntryWriter(System.out);

    Checker(int threadid, Connection dsc) {
        this.threadid = threadid;
        this.dsc = dsc;
    }

    @Override
    public void start() {
    }

    public void check(String dsobject, String dsbasedn) {
        boolean validobject = false;
        final ConnectionEntryReader reader = this.dsc.search(dsbasedn, SearchScope.SINGLE_LEVEL, dsobject, "etag");
        try {
            while (reader.hasNext()) {
                if (reader.isEntry()) {
                    validobject = true;
                    final SearchResultEntry entry = reader.readEntry();
                    this.dsetag = entry.getAttribute("etag").firstValueAsString();
//                    System.out.println(this.threadid + ": " + dsobject + "," + dsbasedn + " = " + this .dsetag);
                } else {
                    this.dsetag = "referral-object";
                }
            }
        } catch (ErrorResultIOException | SearchResultReferenceIOException ex) {
            Logger.getLogger(Checker.class.getName()).log(Level.SEVERE, null, ex);
        }
        if (!validobject) {
            this.dsetag = "absent-object";
        }
    }

    String getEtag() {
        return (this.dsetag);
    }

    void show(String dsobject, String dsbasedn) {
        boolean validobject = false;
        final ConnectionEntryReader reader = this.dsc.search(dsbasedn, SearchScope.SINGLE_LEVEL, dsobject, "*");
        try {
            while (reader.hasNext()) {
                if (reader.isEntry()) {
                    validobject = true;
                    final SearchResultEntry entry = reader.readEntry();
                    writer.writeEntry(entry);
                    writer.flush();
//System.out.println(entry);
                } else {
                    System.out.println("Referral Object\n");
                }
            }
        } catch (ErrorResultIOException | SearchResultReferenceIOException ex) {
            Logger.getLogger(Checker.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(Checker.class.getName()).log(Level.SEVERE, null, ex);
        }
        if (!validobject) {
            System.out.println("absent-object\n");
        }
    }

}
