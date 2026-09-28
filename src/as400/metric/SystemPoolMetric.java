package as400.metric;
import as400.*;
import as400.cache.*;
import as400.thread.*;
import com.ibm.as400.access.*;
import com.ibm.as400.data.*;
import java.io.*;
import java.util.Enumeration;

class SystemPoolMetric {

    static class SystemPoolMetricEntry implements ZbxCacheEntry {

        //inner fields
        //String name;
        String description;
        int identifier;
        int size;//in kilobytes
        float databaseFaults;
        float nonDatabaseFaults;

        public void appendToStringBuilder(StringBuilder buf) {
            buf.append(",\"{#ID}\":\"");
            buf.append(this.identifier);
            buf.append("\",\"{#DESCR}\":\"");
            buf.append(this.description);
            buf.append("\"");
        }//appendToStringBuilder()
    }//inner class SystemPoolMetricEntry

    static class SystemPoolMetricCacheFiller implements ZbxCacheFiller {
        static private SystemPoolMetricEntry fill(SystemPool sp) throws ZbxException, IOException {
            try {
                synchronized (systemPoolTable) {
                    SystemPoolMetricEntry e = new SystemPoolMetricEntry();
                    String name              = sp.getName();
                        Util.log(Util.LOG_DEBUG, "   name: %s", name);
                    if (null == name)
                        return null;
                        //throw new ZbxException("There is no such ASP: '" + name + "'");
                    e.description       = sp.getDescription();
                        Util.log(Util.LOG_DEBUG, "   description: %s", e.description);
                    e.identifier        = sp.getIdentifier();
                        Util.log(Util.LOG_DEBUG, "   identifier: %s", e.identifier);
                    e.size              = sp.getSize();
                        Util.log(Util.LOG_DEBUG, "   size: %d", e.size);
                    e.databaseFaults    = sp.getDatabaseFaults();
                        Util.log(Util.LOG_DEBUG, "   databaseFaults: %f", e.databaseFaults);
                    e.nonDatabaseFaults = sp.getNonDatabaseFaults();
                        Util.log(Util.LOG_DEBUG, "   nonDatabaseFaults: %f", e.nonDatabaseFaults);
                    systemPoolTable.putEntry(name, e);
                    return e;
                }//sync
            } catch (AS400SecurityException|ErrorCompletingRequestException|ObjectDoesNotExistException|InterruptedException|ExtendedIllegalArgumentException ex) {
                throw new ZbxException(ex.getMessage());
            }//try-catch
        }//fill()

        public void fill() throws ZbxException, IOException {
            Util.log(Util.LOG_DEBUG, " SystemPoolMetricCacheFiller.fill() started");
            AS400 system = ((ZabbixThread)Thread.currentThread()).getAs400();
            SystemStatus ss = new SystemStatus(system);

            try {
                Enumeration spList = ss.getSystemPools();
                while (spList.hasMoreElements()) {
                    SystemPool sp = (SystemPool)spList.nextElement();
                    try {
                        if (null == fill(sp))
                            continue;
                    } catch (ZbxException ex) {
                        continue;
                    }//try-catch
/*
                    SystemPoolMetricEntry e = new SystemPoolMetricEntry();
                    String name              = sp.getName();
                        Util.log(Util.LOG_DEBUG, "   name: %s", name);
                    if (null == name)
                        continue;
                    e.description       = sp.getDescription();
                        Util.log(Util.LOG_DEBUG, "   description: %s", e.description);
                    e.identifier        = sp.getIdentifier();
                        Util.log(Util.LOG_DEBUG, "   identifier: %s", e.identifier);
                    e.size              = sp.getSize();
                        Util.log(Util.LOG_DEBUG, "   size: %d", e.size);
                    e.databaseFaults    = sp.getDatabaseFaults();
                        Util.log(Util.LOG_DEBUG, "   databaseFaults: %f", e.databaseFaults);
                    e.nonDatabaseFaults = sp.getNonDatabaseFaults();
                        Util.log(Util.LOG_DEBUG, "   nonDatabaseFaults: %f", e.nonDatabaseFaults);
                    systemPoolTable.putEntry(name, e);
*/
                }//while
            } catch (AS400SecurityException|ErrorCompletingRequestException|ObjectDoesNotExistException|InterruptedException ex) {
                throw new ZbxException(ex.getMessage());
            } finally{
                Util.log(Util.LOG_DEBUG, " SystemPoolMetricCacheFiller.fill() ended");
            }//try-catch-finally
        }//fill()
    }//SystemPoolMetricCacheFiller class

    //static class variables
    private static ZbxCache systemPoolTable = new ZbxCache(new SystemPoolMetricCacheFiller(),
                                                            "{#NAME}", "systemPoolTable", 5000l);

    static String process_systemPool_discovery() throws ZbxException, IOException {
        return systemPoolTable.discovery();
    }//process_systemPool_discovery()

    static Object process_systemPool(String name, String mode) throws ZbxException, IOException {
        SystemPoolMetricEntry spme = (SystemPoolMetricEntry)systemPoolTable.getEntry(name);
        if (null == spme) {
            Util.log(Util.LOG_WARNING, " process_systemPool(): there is no '%s' pool found", name);
            AS400 system = ((ZabbixThread)Thread.currentThread()).getAs400();
            SystemPool sp = new SystemPool(system, name);
            spme = SystemPoolMetricCacheFiller.fill(sp);
        }//if(not found)

        switch (mode) {
        case "size":
            return new Long(spme.size * 1024l);
        case "identifier":
            return new Integer(spme.identifier);
        case "description":
            return spme.description;
        case "databaseFaults":
            return new Float(spme.databaseFaults);
        case "nonDatabaseFaults":
            return new Float(spme.nonDatabaseFaults);
        case "totalFaults":
            return new Float(spme.databaseFaults + spme.nonDatabaseFaults);
        default:
            throw new ZbxException("Invalid parameter '" + mode + "'");
        }//switch-case
    }//process_systemPool()
}//class SystemPoolMetric
