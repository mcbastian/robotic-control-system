package ngat.rcs;

import java.io.File;
import java.net.InetAddress;
import java.rmi.Naming;
import java.rmi.Remote;

/** Optional relocation of the controller's historical local resources.
 * Defaults retain deployment behavior. Lab launcher supplies all three properties.
 */
public final class RcsEnvironment {
    private RcsEnvironment() {}
    public static File file(String path) {
        String root = System.getProperty("rcs.config.root");
        if (root != null && path.startsWith("/occ/rcs/"))
            return new File(root, path.substring("/occ/rcs/".length()));
        String data = System.getProperty("rcs.data.root");
        if (data != null && path.startsWith("/occ/data/"))
            return new File(data, path.substring("/occ/data/".length()));
        return new File(path);
    }
    public static String rmi(String name) {
        String base = System.getProperty("rcs.rmi.base");
        if (base == null) return name;
        if (name.startsWith("rmi://localhost/") || name.startsWith("rmi://localhost:1099/"))
            name = name.substring(name.lastIndexOf('/')+1);
        if (name.contains(":") || name.contains("/"))
            throw new IllegalArgumentException("Non-local RMI resource in relocated controller: " + name);
        return base + "/" + name;
    }
    public static void rebind(String name, Remote object) throws java.rmi.RemoteException, java.net.MalformedURLException { Naming.rebind(rmi(name), object); }
    public static Remote lookup(String name) throws java.rmi.RemoteException, java.net.MalformedURLException, java.rmi.NotBoundException { return Naming.lookup(rmi(name)); }
    public static InetAddress bindAddress() throws java.net.UnknownHostException {
        String host = System.getProperty("ngat.net.bindAddress");
        return host == null ? null : InetAddress.getByName(host);
    }
}
