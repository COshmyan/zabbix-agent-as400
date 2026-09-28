package as400.metric;
import as400.*;
import as400.cache.*;
import as400.thread.*;
import com.ibm.as400.access.*;
import com.ibm.as400.data.*;
//import java.io.*;
import java.util.*;
//import java.beans.PropertyVetoException;

class QYASPOL {

    //inner classes
    static class Entry {
        int capacity;
        int available;
    }//inner class Entry

    static class AspEntry extends Entry implements ZbxCacheEntry {
        String type;

        public void appendToStringBuilder(StringBuilder buf) {
            buf.append(",\"{#FSTYPE}\":\"");
            buf.append(this.type);
            buf.append("\"");
        }//appendToStringBuilder
    }//inner class AspEntry

    static class DskEntry extends Entry implements ZbxCacheEntry {
        int unitno;
        int status;
        String type;
        String model;
        String name;

        public void appendToStringBuilder(StringBuilder buf) {
            buf.append(    ",\"{#DSK_ID}\":\"");
            buf.append(this.unitno);
            buf.append("\",\"{#DSK_TYPE}\":\"");
            buf.append(this.type);
            buf.append("\",\"{#DSK_MODEL}\":\"");
            buf.append(this.model);
            buf.append("\",\"{#DSK_NAME}\":\"");
            buf.append(this.name);
            buf.append("\"");
        }//appendToStringBuilder
    }//inner class DskEntry

    static class aspCacheFiller implements ZbxCacheFiller {
        public void fill() throws ZbxException {
            Util.log(Util.LOG_DEBUG, " QYASPOL.aspCacheFiller.fill() started");

            try {
                AS400 system = ((ZabbixThread)Thread.currentThread()).getAs400();

                Util.log(Util.LOG_DEBUG, "  Constructing the ProgramCallDocument");
                ProgramCallDocument pcml = new ProgramCallDocument(system, "as400.pcml.qyaspol");
                Util.log(Util.LOG_DEBUG, "  Call...");
                boolean rc = pcml.callProgram("qyaspol-yasp0200");
                if (rc) {
                    int rcdsReturned = pcml.getIntValue("qyaspol-yasp0200.listInfo.rcdsReturned");
                    Util.log(Util.LOG_DEBUG, "  OK. Records: %d", rcdsReturned);
                    String value = (String)pcml.getValue("qyaspol-yasp0200.listInfo.infoComplete");
                    //should be "C" for "Complete and accurate information"
                    if (!"C".equals(value))
                        Util.log(Util.LOG_ERROR, "  Error during qyaspol-yasp0200: complete indicator is '%s'", value);
                    int[] indices = new int[1];
                    for (indices[0] = 0; indices[0] < rcdsReturned; indices[0]++) {
                        AspEntry e = new AspEntry();
                        int num = pcml.getIntValue("qyaspol-yasp0200.receiver.aspCapacityTotal", indices);
                        Util.log(Util.LOG_DEBUG, "   aspCapacityTotal: %d", num);
                        e.capacity = num;
                        num = pcml.getIntValue("qyaspol-yasp0200.receiver.aspCapacityAvailableTotal", indices);
                        Util.log(Util.LOG_DEBUG, "   aspCapacityAvailableTotal: %d", num);
                        e.available = num;
                        value = (String)pcml.getValue("qyaspol-yasp0200.receiver.aspType", indices);
                        Util.log(Util.LOG_DEBUG, "   aspType: '%s'", value);
                        e.type = value;
                        num = pcml.getIntValue("qyaspol-yasp0200.receiver.aspNum", indices);
                        Util.log(Util.LOG_DEBUG, "   aspNum: %d", num);
                        aspTable.putEntry(Integer.toString(num), e);
                    }//for
                } else {
                    Util.log(Util.LOG_WARNING, "  Fail, messages are:");
                    AS400Message[] msgs = pcml.getMessageList("qyaspol-yasp0200");
                    for (int i = 0; i < msgs.length; i++) {
                        Util.log(Util.LOG_WARNING, "   %s - %s", msgs[i].getID(), msgs[i].getText());
                    }//for
                    throw new ZbxException(0 < msgs.length ? msgs[0].getID() + " " + msgs[0].getText()
                                            : "Unknown error during qyaspol-yasp0200");
                }//if (rc)
            } catch (PcmlException ex) {
                Util.log(Util.LOG_WARNING,"  QYASPOL.aspCacheFiller.fill() error: %s", ex);
                throw new ZbxException(ex.getMessage());
            } finally{
                Util.log(Util.LOG_DEBUG, " QYASPOL.aspCacheFiller.fill() ended");
            }//try-catch-finally

        }//fill()
    }//inner class aspCacheFiller

    static class dskCacheFiller implements ZbxCacheFiller {

        public void fill() throws ZbxException {
            Util.log(Util.LOG_DEBUG, " QYASPOL.dskCacheFiller.fill() started");

            try {
                AS400 system = ((ZabbixThread)Thread.currentThread()).getAs400();

                Util.log(Util.LOG_DEBUG, "  Constructing the ProgramCallDocument");
                ProgramCallDocument pcml = new ProgramCallDocument(system, "as400.pcml.qyaspol");
                Util.log(Util.LOG_DEBUG, "  Call...");
                boolean rc = pcml.callProgram("qyaspol-yasp0300");
                if (rc) {
                    int rcdsReturned = pcml.getIntValue("qyaspol-yasp0300.listInfo.rcdsReturned");
                    Util.log(Util.LOG_DEBUG, "  OK. Records: %d", rcdsReturned);
                    String value = (String)pcml.getValue("qyaspol-yasp0300.listInfo.infoComplete");
                    //should be "C" for "Complete and accurate information"
                    if (!"C".equals(value))
                        Util.log(Util.LOG_ERROR, "  Error during qyaspol-yasp0300: complete indicator is '%s'", value);
                    int[] indices = new int[1];

                    for (indices[0] = 0; indices[0] < rcdsReturned; indices[0]++) {
                        DskEntry e = new DskEntry();
                        int num = pcml.getIntValue("qyaspol-yasp0300.receiver.diskUnitNo", indices);
                        Util.log(Util.LOG_DEBUG, "   diskUnitNo: %d", num);
                        e.unitno = num;
                        num = pcml.getIntValue("qyaspol-yasp0300.receiver.diskCapacity", indices);
                        Util.log(Util.LOG_DEBUG, "   diskCapacity: %d", num);
                        e.capacity = num;
                        num = pcml.getIntValue("qyaspol-yasp0300.receiver.diskStorageAvailable", indices);
                        Util.log(Util.LOG_DEBUG, "   diskStorageAvailable: %d", num);
                        e.available = num;
                        num = pcml.getIntValue("qyaspol-yasp0300.receiver.unitControl", indices);
                        Util.log(Util.LOG_DEBUG, "   unitControl: %d", num);
                        e.status = num;
                        value = (String)pcml.getValue("qyaspol-yasp0300.receiver.diskType", indices);
                        Util.log(Util.LOG_DEBUG, "   diskType: '%s'", value);
                        e.type = value;
                        value = (String)pcml.getValue("qyaspol-yasp0300.receiver.diskModel", indices);
                        Util.log(Util.LOG_DEBUG, "   diskModel: '%s'", value);
                        e.model = value;
                        value = (String)pcml.getValue("qyaspol-yasp0300.receiver.resName", indices);
                        Util.log(Util.LOG_DEBUG, "   resName: '%s'", value);
                        e.name = value;
                        value = (String)pcml.getValue("qyaspol-yasp0300.receiver.diskSerial", indices);
                        Util.log(Util.LOG_DEBUG, "   diskSerial: '%s'", value);
                        dskTable.putEntry(value, e);
                    }//for
                } else {
                    Util.log(Util.LOG_WARNING, "  Fail, messages are:");
                    AS400Message[] msgs = pcml.getMessageList("qyaspol-yasp0300");
                    for (int i = 0; i < msgs.length; i++) {
                        Util.log(Util.LOG_WARNING, "   %s - %s", msgs[i].getID(), msgs[i].getText());
                    }//for
                    throw new ZbxException(0 < msgs.length ? msgs[0].getID() + " " + msgs[0].getText()
                                            : "Unknown error during qyaspol-yasp0300");
                }//if (rc)
            } catch (PcmlException ex) {
                Util.log(Util.LOG_WARNING,"  QYASPOL.dskCacheFiller.fill() error: %s", ex);
                throw new ZbxException(ex.getMessage());
            } finally{
                Util.log(Util.LOG_DEBUG, " QYASPOL.dskCacheFiller.fill() ended");
            }//try-catch-finally

        }//fill()
    }//inner class dskCacheFiller

    //static class variables
    private static final long TIMEOUT_MS = 5000l;//5 seconds
    private static ZbxCache aspTable = new ZbxCache(new aspCacheFiller(), "{#FSNAME}", "aspTable", TIMEOUT_MS);
    private static ZbxCache dskTable = new ZbxCache(new dskCacheFiller(), "{#DSK_SN}", "dskTable", TIMEOUT_MS);

    static String process_asp_discovery() throws ZbxException {
        return aspTable.discovery();
    }//process_asp_discovery()

    static Object process_asp(String id, String mode) throws ZbxException {
        AspEntry ae = (AspEntry)aspTable.getEntry(id);

        if (null == ae)
            throw new ZbxException("There is no such ASP: '" + id + "'");

        switch (mode) {
        case "total":
            return new Long(ae.capacity * 1000000l);
        case "free":
            return new Long(ae.available * 1000000l);
        case "used":
            return new Long((ae.capacity - ae.available) * 1000000l);
        case "pfree":
            return new Float(100.0 * ae.available / ae.capacity);
        case "pused":
            return new Float(100.0 * (ae.capacity - ae.available)/ ae.capacity);
        default:
            throw new ZbxException("Invalid parameter '" + mode + "'");
        }//switch-case
    }//process_asp_total()

    static String process_dsk_discovery() throws ZbxException {
        return dskTable.discovery();
    }//process_dsk_discovery()

    static Object process_dsk(String id, String mode) throws ZbxException {
        DskEntry de = (DskEntry)dskTable.getEntry(id);

        if (null == de)
            throw new ZbxException("There is no such disk: '" + id + "'");

        switch (mode) {
        case "total":
            return new Long(de.capacity * 1000000l);
        case "free":
            return new Long(de.available * 1000000l);
        case "used":
            return new Long((de.capacity - de.available) * 1000000l);
        case "pfree":
            return new Float(100.0 * de.available / de.capacity);
        case "pused":
            return new Float(100.0 * (de.capacity - de.available)/ de.capacity);
        case "state":
            return new Long(de.status & 0x00000000FFFFFFFF);
        default:
            throw new ZbxException("Invalid parameter '" + mode + "'");
        }//switch-case
    }//process_dsk()

}//class QYASPOL
