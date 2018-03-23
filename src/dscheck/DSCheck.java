/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dscheck;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;

/**
 *
 * @author rmfaller
 */
public class DSCheck extends Thread {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        BufferedReader br = null;
        String brdn;
        String dsobject;
        String dsbasedn;
        String[] instances = null;
        boolean validobject = true;
        boolean verbose = false;
        Long passed = new Long(0);
        Long failed = new Long(0);
        int repeatcheck = 1;
        int sleepcheck = 0;
        String[] cs;
        String host;
        Integer port;
        Connection[] dsc;
        LDAPConnectionFactory[] dscf;
        if (args.length < 3) {
            help(repeatcheck);
        } else {
            try {
                br = new BufferedReader(new FileReader(args[args.length - 1]));
            } catch (FileNotFoundException ex) {
                Logger.getLogger(DSCheck.class.getName()).log(Level.SEVERE, null, ex);
            }
            for (int i = 0; i < (args.length - 1); i++) {
                switch (args[i]) {
                    case "-i":
                    case "--instances":
                        instances = args[i + 1].split("~");
                        break;
                    case "-r":
                    case "--repeat":
                        repeatcheck = Integer.parseInt(args[i + 1]);
                    case "-s":
                    case "--sleep":
                        repeatcheck = Integer.parseInt(args[i + 1]);
                    case "-v":
                    case "--verbose":
                        verbose = true;
                    default:
                        break;
                }
            }
            if (instances == null) {
                help(repeatcheck);
            } else {
                try {
                    Checker[] checker = new Checker[instances.length];
                    dsc = new Connection[instances.length];
                    dscf = new LDAPConnectionFactory[instances.length];
                    for (int i = 0; i < instances.length; i++) {
                        cs = instances[i].split(":");
                        host = cs[0];
                        port = new Integer(cs[1]);
                        dscf[i] = new LDAPConnectionFactory(host, port);
                        try {
                            dsc[i] = dscf[i].getConnection();
                            dsc[i].bind("cn=Directory Manager", "password".toCharArray());
                            checker[i] = new Checker(i, dsc[i]);
                            checker[i].start();
                        } catch (ErrorResultException ex) {
                            Logger.getLogger(DSCheck.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                    while ((brdn = br.readLine()) != null) {
                        int x = brdn.indexOf(",");
                        dsobject = brdn.substring(0, x);
                        dsbasedn = brdn.substring((x + 1), brdn.length());
                        int checkit = 0;
//                        System.out.println("Thread: " + dsobject + " === " + dsbasedn + " = " + checker[0].getEtag());
                        validobject = true;
                        while (checkit < repeatcheck) {
                            for (int i = 0; i < checker.length; i++) {
                                checker[i].check(dsobject, dsbasedn);
                            }
                            for (int i = 0; i < checker.length; i++) {
                                checker[i].join();
                                if (i > 0) {
                                    if (checker[i].getEtag().compareTo(checker[i - 1].getEtag()) != 0) {
                                        validobject = false;
                                    }
                                }
                            }
                            if (validobject) {
//                            System.out.println("MATCHED:");
//                            for (int i = 0; i < checker.length; i++) {
//                                System.out.println("    Host:port=" + instances[i] + " dn=" + dsobject + "," + dsbasedn + " etag = " + checker[i].getEtag());
//                            }
                                checkit = repeatcheck;
                            } else {
                                checkit++;
                                if (checkit == repeatcheck) {
                                    System.out.println("vvvvvvvvvvvvv OBJECT DN = " + dsobject + "," + dsbasedn + " MISMATCH after " + checkit + " checks vvvvvvvvvvv\n");
                                    for (int i = 0; i < checker.length; i++) {
                                        System.out.println("Host:port=" + instances[i] + " etag = " + checker[i].getEtag());
                                        if (verbose) {
                                            checker[i].show(dsobject, dsbasedn);
                                        }
                                    }
                                    System.out.println("^^^^^^^^^^^^^ OBJECT DN = " + dsobject + "," + dsbasedn + " MISMATCH ^^^^^^^^^^^^^^^^^^^^^^^^^^\n");
                                } else {
//                                    System.out.println("Object mismatch dn = " + dsobject + "," + dsbasedn + " recheck = " + checkit);
                                    sleep(sleepcheck);
                                }
                            }
                        }
                        if (validobject) {
                            passed++;
                        } else {
                            failed++;
                        }
                    }
                    for (int i = 0; i < dsc.length; i++) {
                        dscf[i].close();
                        dsc[i].close();
                    }
                } catch (IOException | InterruptedException ex) {
                    Logger.getLogger(DSCheck.class.getName()).log(Level.SEVERE, null, ex);
                }
                System.out.println(passed + " objects that matched; " + failed + " objects that failed to match");
            }
        }
    }

    private static void help(int repeatcheck) {
        String help = "\nDSCheck usage:"
                + "\njava -jar ./dist/DSCheck.jar --instances INSTANCE0:PORT0~INSTANCEn:PORTn /path/to/DNs/file"
                + "\nrequired:"
                + "\n\t--instances  | -i in a specific format using a tilde \"~\" to separate instances i.e. ds0.example.com:1389~ds1.example.com:1389~ds2.example.com:1389"
                + "\n\t/path/to/DNs/file where each line in this file is a single DN"
                + "\noptions:"
                //                + "\n\t--quiet    | -q {default = off} minimizes output"
                + "\n\t--verbose  | -v {default = false} full display of objects that did not match"
                //                + "\n\t--csv      | -c {default = off} output in a comma delimited format"
                + "\n\t--repeat n | -r n (default n = " + repeatcheck + "} maximum number of times to check object validity if found not valid across all instances"
                + "\n\t--sleep t  | -s t {default t = 0} seconds to sleep until repeating validity check"
                //                + "\n\t--fails /path/to/directory | -f /path/to/directory location to write objects that are deemed to be different for a particular DN"
                + "\n\t--help     | -h this output\n"
                + "\nExamples:"
                //                + "\n\nRun 2 threads of load until stopped:"
                //                + "\njava -jar ./dist/CPULoader.jar --lapsedtime 20000 --maxthreads 2"
                //                + "\n\nIncrement thread count until threshold is exceeded:"
                //                + "\njava -jar ./dist/CPULoader.jar --threshold 5"
                //                + "\n\nIncrement thread count until threshold is exceeded and start again until stopped with output in csv format:"
                //                + "\njava -jar ./dist/CPULoader.jar --threshold 5 --forever --csv\n";
                + "";
        System.out.println(help);
        System.out.println("java -jar ${DSCHECKHOME}/dist/DSCheck.jar --instances ${instances} ${TMPFILES}dns.txt");
    }

}
