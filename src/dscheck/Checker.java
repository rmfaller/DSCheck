/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dscheck;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.EntryNotFoundException;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.MultipleEntriesFoundException;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;

/**
 *
 * @author rmfaller
 */
// class Checker extends Thread {
class Checker extends Thread {

    SearchResultEntry sse = null;
    private final int threadid;
    private final long sp;
    private final long ep;
    private final String dnfile;
    private final String[] instances;
    private final Connection[] dsc;
    private String dsetag = null;
    private String dsobject = null;
    private String dsbasedn = null;
    private int repeatcheck;
    private final int sleepcheck;
    private Tracker threadtracker;
    private Tracker instancetracker[];

    Checker(int threadid, Connection[] dsc, long sp, long ep, String dnfile, String[] instances, int repeatcheck, int sleepcheck, Tracker threadtracker, Tracker[] instancetracker) {
        this.dsc = dsc;
        this.threadid = threadid;
        this.sp = sp;
        this.ep = ep;
        this.dnfile = dnfile;
        this.instances = instances;
        this.repeatcheck = repeatcheck;
        this.sleepcheck = sleepcheck;
        this.threadtracker = threadtracker;
        this.instancetracker = instancetracker;
    }

    @Override
    public void run() {
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(dnfile));
            String brdn;
            long linecount = -1;
            while ((brdn = br.readLine()) != null) {
                linecount++;
                if ((linecount >= sp) && (linecount < ep)) {
                    long startop = (long) new Date().getTime();
                    long starttx = 0;
                    long endtx = 0;
                    boolean validobject = true;
                    String objectstate = "valid";
                    String[] etags = new String[dsc.length];
                    int x = brdn.indexOf(",");
                    dsobject = brdn.substring(0, x);
                    dsbasedn = brdn.substring((x + 1), brdn.length());
                    int chex = 0;
                    int i;
                    while (chex < repeatcheck) {
                        for (i = 0; i < dsc.length; i++) {
                            starttx = (long) new Date().getTime();
                            try {
                                sse = dsc[i].searchSingleEntry(dsbasedn, SearchScope.SINGLE_LEVEL, dsobject, "etag");
                                etags[i] = sse.getAttribute("etag").firstValueAsString();
                                if ((i > 0) && (validobject)) {
                                    if (etags[i].compareTo(etags[i - 1]) != 0) {
                                        validobject = false;
                                        objectstate = "NOT MATCHING";
                                    }
                                }
                            } catch (EntryNotFoundException ex) {
                                validobject = false;
                                objectstate = "MISSING";
                                etags[i] = "NULL";
                            } catch (MultipleEntriesFoundException ex) {
                                validobject = false;
                                objectstate = "DUPLICATE";
                            } catch (ErrorResultException ex) {
                                Logger.getLogger(Checker.class.getName()).log(Level.SEVERE, null, ex);
                            }
                            endtx = (long) new Date().getTime();
                            instancetracker[i].called++;
                            instancetracker[i].totaltime = instancetracker[i].totaltime + (endtx - starttx);
                        }
                        if (validobject) {
                            chex = repeatcheck;
                            threadtracker.passed++;
                        } else {
                            chex++;
                            if (chex == repeatcheck) {
                                threadtracker.failed++;
                            } else {
                                sleep(sleepcheck);
                            }
                        }
                    }
                    long endop = (long) new Date().getTime();
                    threadtracker.totaltime = threadtracker.totaltime + (endop - startop);
                    if (!validobject) {
                        String response = "Object " + dsobject + "," + dsbasedn + " checked " + chex + " time(s) = " + objectstate + "\n";
                        for (i = 0; i < etags.length; i++) {
                            response = response + "Instance: " + instances[i] + " etag = " + etags[i] + "\n";
                        }
                        System.out.println(response);
                    }
                }
            }
            br.close();
        } catch (IOException | InterruptedException ex) {
            Logger.getLogger(Checker.class.getName()).log(Level.SEVERE, null, ex);
        }

    }
}
