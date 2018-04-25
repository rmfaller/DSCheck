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
        boolean fulldisplay = false;
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
                    case "-f":
                    case "--fulldisplay":
                        fulldisplay = true;
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
                            checker[i] = new Checker(i, dsc, ((dncount / threads) * i), ((dncount / threads) * (i + 1)), dnfile, instances, repeatcheck, sleepcheck, threadtracker[i], instancetracker[i], fulldisplay);
                        } else {
                            checker[i] = new Checker(i, dsc, ((dncount / threads) * i), dncount, dnfile, instances, repeatcheck, sleepcheck, threadtracker[i], instancetracker[i], fulldisplay);
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
                    long endop = (long) new Date().getTime();
                    System.out.println("---------------------------------------------------");
                    System.out.println("   Total\t pass = " + passed + "\t fail = " + failed);
                    if (verbose) {
                        int ttx = 0;
                        System.out.println("\nStats from DSCheck perspective with lapsed clock time = " + (endop - startop) * 1000 + " seconds:");
                        for (int i = 0; i < instances.length; i++) {
                            int c = 0;
                            long t = 0;
                            for (int j = 0; j < checker.length; j++) {
                                c = c + instancetracker[j][i].called;
                                t = t + instancetracker[j][i].totaltime;
                            }
                            System.out.print(instancetracker[0][i].name + " called = " + c);
                            System.out.print(" times; Average time/call = " + (t / c) + "ms");
                            System.out.println("; tx rate = " + ((float) (c / (float) (endop - startop)) * 1000) + "/sec");
                            ttx = ttx + c;
                        }
                        System.out.println("Cumulative transaction rate across all instances = " + ((float) (ttx / (float) (endop - startop)) * 1000) + "/sec");
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
                + "\nrequired:"
                + "\n\t--instances    | -i in a specific format using a tilde \"~\" to separate instances i.e. ds0.example.com:1389~ds1.example.com:1389~ds2.example.com:1389"
                + "\n\t--bindDn       | -b identity to use when binding to DS i.e. cn=Directory Manager"
                + "\n\t--bindPassword | -p password to use when binding to DS"
                + "\n\t/path/to/DNs/file where each line in this file is a single DN - example: uid=user.1234,ou=people,dc=example,dc=com"
                + "\noptions:"
                + "\n\t--threads      | -t {default = 1} number of threads to run; suggest start with 4x number of cores of system running DSCheck"
                + "\n\t--verbose      | -v {default = false} display DSCheck runtime stats"
                + "\n\t--fulldisplay  | -f {default = false} display the entire object in question on standard error i.e. 2> error.out"
                + "\n\t--repeat n     | -r n (default n = " + repeatcheck + "} maximum number of times to check object validity if found not valid across all instances"
                + "\n\t--sleep t      | -s t {default t = 0} seconds to sleep until repeating validity check"
                + "\n\t--help         | -h this output\n"
                + "\nExamples:"
                + "";
        System.out.println(help);
        System.out.println("java -jar ${DSCHECKHOME}/dist/DSCheck.jar --bindDn \"cn=Directory Manager\" --bindPassword \"dmpassword\" --instances ds0.example.com:1389~ds1.example.com:1389~ds2.example.com:1389 ${DSCHECKHOME}/tmp/dns.txt");
    }

}
