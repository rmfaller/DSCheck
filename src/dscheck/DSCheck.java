/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dscheck;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;

/**
 *
 * @author rmfaller
 */
public class DSCheck {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        long dncount = 0;
        String dnfile = null;
        int threads = 1;
        String[] instances = null;
        String bindDn = null;
        String bindPassword = null;
        String[] cs;
        Connection[] dsc;
        LDAPConnectionFactory[] dscf;
        Tracker[] threadtracker;
        Tracker[][] instancetracker;

        boolean verbose = false;
        int passed = 0;
        int failed = 0;
        int repeatcheck = 1;
        int sleepcheck = 0;

        if (args.length < 3) {
            help(repeatcheck);
        } else {
            dnfile = args[args.length - 1];
            for (int i = 0; i < (args.length - 1); i++) {
                switch (args[i]) {
                    case "-b":
                    case "--bindDn":
                        bindDn = (args[i + 1]);
                        break;
                    case "-p":
                    case "--bindPassword":
                        bindPassword = (args[i + 1]);
                        break;
                    case "-i":
                    case "--instances":
                        instances = args[i + 1].split("~");
                        break;
                    case "-r":
                    case "--repeat":
                        repeatcheck = Integer.parseInt(args[i + 1]);
                        break;
                    case "-s":
                    case "--sleep":
                        sleepcheck = Integer.parseInt(args[i + 1]);
                        break;
                    case "-t":
                    case "--threads":
                        threads = Integer.parseInt(args[i + 1]);
                        break;
                    case "-v":
                    case "--verbose":
                        verbose = true;
                        break;
                    default:
                        break;
                }
            }
            if ((instances == null) || (bindDn == null) || (bindPassword == null) || (dnfile == null)) {
                help(repeatcheck);
            } else {
                dsc = new Connection[instances.length];
                dscf = new LDAPConnectionFactory[instances.length];
                Checker[] checker = new Checker[threads];
                threadtracker = new Tracker[threads];
                instancetracker = new Tracker[checker.length][instances.length];
                try {
                    dncount = Files.lines(Paths.get(new File(dnfile).getPath())).count();
                    for (int i = 0; i < instances.length; i++) {
                        cs = instances[i].split(":");
                        dscf[i] = new LDAPConnectionFactory(cs[0], new Integer(cs[1]));
                        dsc[i] = dscf[i].getConnection();
                        dsc[i].bind(bindDn, bindPassword.toCharArray());
                    }
                    if (verbose) {
                        System.out.println("Starting " + DSCheck.class.getName() + " with " + threads + " threads");
                        System.out.println("Checking " + dncount + " objects listed in " + dnfile + " in the following instances:");
                        for (int i = 0; i < instances.length; i++) {
                            System.out.println("\t" + instances[i]);
                        }
                        System.out.println("=============================================\n");
                    }
                    long startop = (long) new Date().getTime();
                    for (int i = 0; i < checker.length; i++) {
                        threadtracker[i] = new Tracker("thread-" + i);
                        for (int j = 0; j < instances.length; j++) {
                            instancetracker[i][j] = new Tracker(instances[j]);
                        }
                        if (i < (checker.length - 1)) {
                            checker[i] = new Checker(i, dsc, ((dncount / threads) * i), ((dncount / threads) * (i + 1)), dnfile, instances, repeatcheck, sleepcheck, threadtracker[i], instancetracker[i]);
                        } else {
                            checker[i] = new Checker(i, dsc, ((dncount / threads) * i), dncount, dnfile, instances, repeatcheck, sleepcheck, threadtracker[i], instancetracker[i]);
                        }
                        checker[i].start();
                    }
                    for (int i = 0; i < checker.length; i++) {
                        checker[i].join();
                        if (verbose) {
                            System.out.print(threadtracker[i].name + " pass = " + threadtracker[i].passed + " fail = " + threadtracker[i].failed);
                            System.out.println("; Average time per check = " + (threadtracker[i].totaltime / (threadtracker[i].passed + threadtracker[i].failed)) + "ms against " + instances.length + " instances");
                        }
                        passed = passed + threadtracker[i].passed;
                        failed = failed + threadtracker[i].failed;
                    }
                    System.out.println("---------------------------------------------------");
                    System.out.println("   Total\t pass = " + passed + "\t fail = " + failed);
                    if (verbose) {
                        System.out.println("\nPerformane stats per instances:");
                        for (int i = 0; i < instances.length; i++) {
                            int c = 0;
                            long t = 0;
                            for (int j = 0; j < checker.length; j++) {
                                c = c + instancetracker[j][i].called;
                                t = t + instancetracker[j][i].totaltime;
                            }
                            System.out.print(instancetracker[0][i].name + " called = " + c);
                            System.out.println(" times; Average time per check = " + (t / c) + "ms");
                        }
                    }
                } catch (ErrorResultException | InterruptedException | IOException ex) {
                    Logger.getLogger(DSCheck.class.getName()).log(Level.SEVERE, null, ex);
                } finally {
                    for (int i = 0; i < dsc.length; i++) {
                        if (dsc[i] != null) {
                            dsc[i].close();
                        }
                        if (dscf[i] != null) {
                            dscf[i].close();
                        }
                    }
                }
            }
        }
    }

    private static void help(int repeatcheck) {
        String help = "\nDSCheck usage:"
                + "\njava -jar ./dist/DSCheck.jar --instances INSTANCE0:PORT0~INSTANCEn:PORTn /path/to/DNs/file"
                + "\nrequired:"
                + "\n\t--instances    | -i in a specific format using a tilde \"~\" to separate instances i.e. ds0.example.com:1389~ds1.example.com:1389~ds2.example.com:1389"
                + "\n\t--bindDn       | -b identity to use when binding to DS i.e. cn=Directory Manager"
                + "\n\t--bindPassword | -p password to use when binding to DS"
                + "\n\t/path/to/DNs/file where each line in this file is a single DN"
                + "\noptions:"
                + "\n\t--threads      | -t {default = 1} number of threads to run; suggest start with 4x number of cores of system running DSCheck"
                + "\n\t--verbose      | -v {default = false} full display of objects that did not match"
                //                + "\n\t--csv      | -c {default = off} output in a comma delimited format"
                + "\n\t--repeat n     | -r n (default n = " + repeatcheck + "} maximum number of times to check object validity if found not valid across all instances"
                + "\n\t--sleep t      | -s t {default t = 0} seconds to sleep until repeating validity check"
                //                + "\n\t--fails /path/to/directory | -f /path/to/directory location to write objects that are deemed to be different for a particular DN"
                + "\n\t--help         | -h this output\n"
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
