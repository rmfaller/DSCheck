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
        Boolean validobject = true;
        Long passed = new Long(0);
        Long failed = new Long(0);
        int oi = 0;
        String[] cs;
        String host;
        Integer port;
        Connection[] dsc;
        LDAPConnectionFactory[] dscf;
        if (args.length < 2) {
            help();
        } else {
            try {
                br = new BufferedReader(new FileReader(args[args.length - 1]));
            } catch (FileNotFoundException ex) {
                Logger.getLogger(DSCheck.class.getName()).log(Level.SEVERE, null, ex);
            }
            for (int i = 0; i < (args.length - 1); i++) {
                switch (args[i]) {
                    case "--instances":
                        instances = args[i + 1].split("~");
//                        System.out.println(args[args.length - 1] + "--" + instances[0]);
                        break;
                    default:
                        break;
                }
            }
            if (instances == null) {
                help();
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
//                        System.out.println("Thread: " + dsobject + " === " + dsbasedn + " = " + checker[0].getEtag());
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
                            passed++;
                        } else {
                            System.out.println("OBJECT MISMATCH:");
                            for (int i = 0; i < checker.length; i++) {
                                System.out.println("    Host:port=" + instances[i] + " dn=" + dsobject + "," + dsbasedn + " etag = " + checker[i].getEtag());
                            }
                            failed++;
                            validobject = true;
                        }
                    }
                    for (int i = 0; i < dsc.length; i++) {
                        dscf[i].close();
                        dsc[i].close();
                    }
                } catch (IOException | InterruptedException ex) {
                    Logger.getLogger(DSCheck.class.getName()).log(Level.SEVERE, null, ex);
                }
                System.out.println(passed + " objects that matched; " + failed + " objects failed to match");
            }
        }
    }

    private static void help() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}
