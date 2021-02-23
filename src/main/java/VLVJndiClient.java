/**
 * VLVJndiClient.java
 * Sample code to demostrate how Virtual List View (VLV) Control works.
 * Note:
 * 1) Note: JNDI Boost package is required for this example to run.
 * 2) VLV Control MUST be used in conjunction with Sort Control.
 * Otherwise, you will be braced by:  [LDAP: error code 60 - VLV Control]
 * 3) SunOne Directory Server supports VLV & Microsoft supports VLV since AD2003
 */

import com.sun.jndi.ldap.ctl.SortControl;
import com.sun.jndi.ldap.ctl.VirtualListViewControl;
import com.sun.jndi.ldap.ctl.VirtualListViewResponseControl;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Hashtable;
import java.util.Properties;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.Control;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.SortResponseControl;

public class VLVJndiClient {

    static final String VLV_CONTROL_OID = "2.16.840.1.113730.3.4.9";

    public static void main(String[] args) throws IOException {

        System.out.println("\n============  Test is started  ================");
        Properties prop = getProperties(args[0]);
        Hashtable env = prepareEnv(prop);

        try {
            LdapContext ctx = getLdapContext(env, prop);

            SearchControls ctl = new SearchControls();
//            ctl.setReturningAttributes(new String[]{"uuid", "uid"});
            ctl.setSearchScope(SearchControls.SUBTREE_SCOPE);

            long t1 = System.currentTimeMillis();
            /* Perform search */
            NamingEnumeration answer = ctx.search(prop.getProperty("base_dn"), prop.getProperty("search_filter"), ctl);
            long t2 = System.currentTimeMillis();
            System.out.println("== LDAP Search done: " + (t2 - t1) + "ms ==");

            /* Enumerate search results */
            while (answer.hasMore()) {
                SearchResult si = (SearchResult) answer.next();
                System.out.println(si.getName());
            }
            long t3 = System.currentTimeMillis();
            System.out.println("== Results printing done: " + (t3 - t2) + "ms ==");

            /* examine the response controls (if any) */
            printControls(ctx.getResponseControls());

            ctx.close();

        } catch (NamingException e) {
            e.printStackTrace();
        }
        System.out.println("=====  Test is finished =====");
    }

    private static Hashtable prepareEnv(Properties prop) {

        Hashtable env = new Hashtable();

        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");

        env.put(Context.PROVIDER_URL, prop.getProperty("connection_url"));
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, prop.getProperty("connection_name"));
        env.put(Context.SECURITY_CREDENTIALS, prop.getProperty("connection_password"));
        return env;
    }

    private static Properties getProperties(String filePath) {

        Properties prop = new Properties();
        try (InputStream input = new FileInputStream(filePath)) {
            // load a properties file
            prop.load(input);

        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return prop;
    }

    private static LdapContext getLdapContext(Hashtable env, Properties prop) throws NamingException, IOException {
        /* Create initial context with no connection request controls */
        LdapContext ctx = new InitialLdapContext(env, null);

        /* Query the server to see if the VLV Control is supported */
//        if (!isVLVControlSupported(ctx)) {
//            System.out.println(
//                    "The server does not support Virtual List View (VLV) Control.");
//            System.exit(1);
//        }

        com.sun.jndi.ldap.ctl.SortKey sortKey;
        if(prop.getProperty("matching_rule_id") != null) {
            sortKey = new com.sun.jndi.ldap.ctl.SortKey(prop.getProperty("sort_attribute"), true, prop.getProperty("matching_rule_id"));
        } else {
            sortKey = new com.sun.jndi.ldap.ctl.SortKey(prop.getProperty("sort_attribute"));
        }

        com.sun.jndi.ldap.ctl.SortKey[] sortKeys = new com.sun.jndi.ldap.ctl.SortKey[1];
        sortKeys[0] = sortKey;

        /* Sort Control is required for VLV to work */
        SortControl sctl = new SortControl(
                sortKeys, // sort by cn
                Control.CRITICAL
        );

        /* VLV that returns the first 20 answers */
        VirtualListViewControl vctl =
                new VirtualListViewControl(Integer.parseInt(prop.getProperty("start_index")), 0, 0,
                        Integer.parseInt(prop.getProperty("count")) - 1, Control.CRITICAL);

        /* Set context's request controls */
        ctx.setRequestControls(new Control[]{sctl, vctl});
        return ctx;
    }

    static void printControls(Control[] controls) {

        if (controls == null) {
            System.out.println("No response controls");
            return;
        }

        for (int j = 0; j < controls.length; j++) {
            if (controls[j] instanceof SortResponseControl) {
                SortResponseControl src = (SortResponseControl) controls[j];
                if (src.isSorted()) {
                    System.out.println("Sorted-Search completed successfully");
                } else {
                    System.out.println(
                            "Sorted-Search did not complete successfully: error (" +
                                    src.getResultCode() + ") on attribute '" +
                                    src.getAttributeID() + "'");
                }
            } else if (controls[j] instanceof VirtualListViewResponseControl) {
                VirtualListViewResponseControl vlv =
                        (VirtualListViewResponseControl) controls[j];
                if (vlv.getResultCode() == 0) {
                    System.out.println("Sorted-View completed successfully");
                    System.out.println("TargetOffset: " + vlv.getTargetOffset());
                    System.out.println("ListSize: " + vlv.getListSize());
                } else {
                    System.out.println("Sorted-View did not complete successfully: "
                            + vlv.getResultCode());
                }
            } else {
                System.out.println("Received control: " + controls[j].getID());
            }
        }
    }

    /**
     * Is VLV Control supported?
     * <p>
     * Query the rootDSE object to find out if VLV Control
     * is supported.
     */
    static boolean isVLVControlSupported(LdapContext ctx)
            throws NamingException {

        SearchControls ctl = new SearchControls();
        ctl.setReturningAttributes(new String[]{"supportedControl"});
        ctl.setSearchScope(SearchControls.OBJECT_SCOPE);

        /* search for the rootDSE object */
        NamingEnumeration results = ctx.search("", "(objectClass=*)", ctl);

        while (results.hasMore()) {
            SearchResult entry = (SearchResult) results.next();
            NamingEnumeration attrs = entry.getAttributes().getAll();
            while (attrs.hasMore()) {
                Attribute attr = (Attribute) attrs.next();
                NamingEnumeration vals = attr.getAll();
                while (vals.hasMore()) {
                    String value = (String) vals.next();
                    if (value.equals(VLV_CONTROL_OID))
                        return true;
                }
            }
        }
        return false;
    }

}