/*
# The code is provided on an "as is" basis, without warranty of any kind, 
# to the fullest extent permitted by law. 
#
# ForgeRock does not warrant or guarantee the individual success 
# developers may have in implementing the code on their 
# platforms or in production configurations.
#
# ForgeRock does not warrant, guarantee or make any representations 
# regarding the use, results of use, accuracy, timeliness or completeness
# of any data or information relating to the release of unsupported code.
# ForgeRock disclaims all warranties, expressed or implied, and in particular,
# disclaims all warranties of merchantability, and warranties related to
# the code, or any service or software related thereto.
#
# ForgeRock shall not be liable for any direct, indirect or consequential damages
# or costs of any type arising out of any action taken by you or others related to the code.
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
import org.forgerock.opendj.ldap.ErrorResultIOException;
import org.forgerock.opendj.ldap.MultipleEntriesFoundException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;
import org.forgerock.opendj.ldif.ConnectionEntryReader;
import org.forgerock.opendj.ldif.LDIFEntryWriter;

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
    private final String dsetag = null;
    private String dsobject = null;
    private String dsbasedn = null;
    private final int repeatcheck;
    private final int sleepcheck;
    private final Tracker threadtracker;
    private final Tracker instancetracker[];
    private boolean fulldisplay = false;
    final LDIFEntryWriter dswriter = new LDIFEntryWriter(System.err);

    Checker(int threadid, Connection[] dsc, long sp, long ep, String dnfile, String[] instances, int repeatcheck, int sleepcheck, Tracker threadtracker, Tracker[] instancetracker, boolean fulldisplay) {
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
        this.fulldisplay = fulldisplay;
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
                    boolean hadissue = false;
                    String objectstate = "valid";
                    String[] etags = new String[dsc.length];
                    int x = brdn.indexOf(",");
                    dsobject = brdn.substring(0, x);
                    dsbasedn = brdn.substring((x + 1), brdn.length());
                    int chex = 0;
                    int checkcount = 0;
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
                            checkcount++;
                            hadissue = true;
                            if (chex == repeatcheck) {
                                threadtracker.failed++;
                            } else {
                                sleep(sleepcheck);
                                validobject = true;
                            }
                        }
                    }
                    long endop = (long) new Date().getTime();
                    threadtracker.totaltime = threadtracker.totaltime + (endop - startop);
                    if (!validobject) {
                        String response = "Object " + dsobject + "," + dsbasedn + " checked " + checkcount + " time(s) = " + objectstate + "\n";
                        for (i = 0; i < etags.length; i++) {
                            response = response + "Instance: " + instances[i] + " etag = " + etags[i] + "\n";
                        }
                        System.out.println(response);
                        if (fulldisplay) {
                            fulldisplay();
                        }
                    }
                    if ((hadissue) && (validobject)) {
                        String response = "Object " + dsobject + "," + dsbasedn + " checked " + checkcount + " time(s) due to inconsistencies but is now MATCHED \n";
                        for (i = 0; i < etags.length; i++) {
                            response = response + "Instance: " + instances[i] + " etag = " + etags[i] + "\n";
                        }
                        System.out.println(response);
                        if (fulldisplay) {
                            fulldisplay();
                        }
                    }
                }
            }
            br.close();
        } catch (IOException | InterruptedException ex) {
            Logger.getLogger(Checker.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    private void fulldisplay() {
        for (int i = 0; i < dsc.length; i++) {
            try {
                final ConnectionEntryReader reader = dsc[i].search(dsbasedn, SearchScope.SINGLE_LEVEL, dsobject, "*");
                while (reader.hasNext()) {
                    if (!reader.isReference()) {
                        final SearchResultEntry entry = reader.readEntry();
                        dswriter.writeComment("Search result entry in " + instances[i] + ": " + entry.getName().toString());
                        dswriter.writeEntry(entry);
                    } else {
                        final SearchResultReference ref = reader.readReference();
                        dswriter.writeComment("Search result reference: " + ref.getURIs().toString());
                    }
                }
                dswriter.flush();
            } catch (final ErrorResultIOException e) {
                System.err.println(e.getMessage());
                System.exit(e.getCause().getResult().getResultCode().intValue());
                return;
            } catch (final IOException e) {
                System.err.println(e.getMessage());
                System.exit(ResultCode.CLIENT_SIDE_LOCAL_ERROR.intValue());
                return;
            }
        }
    }
}
