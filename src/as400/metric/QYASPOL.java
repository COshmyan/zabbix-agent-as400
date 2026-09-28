package as400.metric;
import as400.*;
import as400.cache.*;
import as400.thread.*;
import com.ibm.as400.access.*;
import com.ibm.as400.data.*;
import java.io.*;
import java.util.*;
//import java.beans.PropertyVetoException;

class QYASPOL {

    //inner classes
    static class Entry {
        int status;
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
        int unitNo;
        int aspNum;
        String type;
        String model;
        String name;

        public void appendToStringBuilder(StringBuilder buf) {
            buf.append(    ",\"{#DSK_ID}\":\"");
            buf.append(this.unitNo);
            buf.append("\",\"{#DSK_TYPE}\":\"");
            buf.append(this.type);
            buf.append("\",\"{#DSK_MODEL}\":\"");
            buf.append(this.model);
            buf.append("\",\"{#DSK_NAME}\":\"");
            buf.append(this.name);
            buf.append("\",\"{#DSK_ASP}\":\"");
            buf.append(this.aspNum);
            buf.append("\"");
        }//appendToStringBuilder
    }//inner class DskEntry

    static class aspCacheFiller implements ZbxCacheFiller {
        public void fill() throws ZbxException, IOException {
            Util.log(Util.LOG_DEBUG, " QYASPOL.aspCacheFiller.fill() started");

            AS400 system = ((ZabbixThread)Thread.currentThread()).getAs400();
            ProgramCallDocument pcml = null;
            byte [] reqHandle = null;
            try {
                Util.log(Util.LOG_DEBUG, "  Constructing the ProgramCallDocument");
/*
                try {
                    Trace.setFileName("C:\\workfiles\\as400\\debug_pcml_qyaspol.txt");
                } catch (IOException ex) {
                    Util.log(Util.LOG_ERROR, " Error " + ex);
                }//try-catch
                Trace.setTraceOn(true);
                Trace.setTracePCMLOn(true);
*/
                pcml = new ProgramCallDocument(system, "as400.pcml.qyaspol");
                Util.log(Util.LOG_DEBUG, "  Call...");

                if (pcml.callProgram("qyaspol-yasp0200")) {
                    reqHandle = (byte[])pcml.getValue("qyaspol-yasp0200.listInfo.reqHandle");
                    int rcdsReturned = pcml.getIntValue("qyaspol-yasp0200.listInfo.rcdsReturned");
                    Util.log(Util.LOG_DEBUG, "  OK. Records: %d", rcdsReturned);
                    String value = (String)pcml.getValue("qyaspol-yasp0200.listInfo.infoComplete");
                    //should be "C" for "Complete and accurate information"
                    if (!"C".equals(value))
                        Util.log(Util.LOG_ERROR, "  Error during qyaspol-yasp0200: complete indicator is '%s'", value);
                    int[] indices = new int[1];
                    for (indices[0] = 0; indices[0] < rcdsReturned; indices[0]++) {
                        AspEntry e = new AspEntry();
                        e.status = 0;
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

                    if (null != reqHandle) {
                        try {
                            pcml.setValue("qyaspol-qgyclst.reqHandle", reqHandle);
                            boolean ret = pcml.callProgram("qyaspol-qgyclst");
                            Util.log(Util.LOG_DEBUG,"   QYASPOL.aspCacheFiller.fill() closing list for qyaspol-yasp0200: %s", (ret ? "success" : "fail"));
                        } catch (PcmlException ex1) {
                            Util.log(Util.LOG_WARNING,"   QYASPOL.aspCacheFiller.fill() error during closing list for qyaspol-yasp0200: %s", ex1);
                        }//try-catch
                    }//if (reqHandle)

                    if (pcml.callProgram("qyaspol-yasp0100")) {
                        reqHandle = (byte[])pcml.getValue("qyaspol-yasp0100.listInfo.reqHandle");
                        rcdsReturned = pcml.getIntValue("qyaspol-yasp0100.listInfo.rcdsReturned");
                        Util.log(Util.LOG_DEBUG, "  qyaspol-yasp0100 OK. Records: %d", rcdsReturned);
                        value = (String)pcml.getValue("qyaspol-yasp0100.listInfo.infoComplete");
                        //should be "C" for "Complete and accurate information"
                        if (!"C".equals(value))
                            Util.log(Util.LOG_ERROR, "  Error during qyaspol-yasp0100: complete indicator is '%s'", value);
                        for (indices[0] = 0; indices[0] < rcdsReturned; indices[0]++) {
                            int status = pcml.getIntValue("qyaspol-yasp0100.receiver.aspStatus", indices);
                            Util.log(Util.LOG_DEBUG, "   aspStatus: %d", status);
                            int num = pcml.getIntValue("qyaspol-yasp0100.receiver.aspNum", indices);
                            Util.log(Util.LOG_DEBUG, "   aspNum: %d", num);
                            AspEntry e = (AspEntry)aspTable.getRawEntry(Integer.toString(num));
                            if (null != e) {
                                e.status = status;
                                Util.log(Util.LOG_DEBUG, "   aspEntry updated for aspNum=%d", num);
                            } else {
                                Util.log(Util.LOG_DEBUG, "   aspEntry not found for aspNum=%d", num);
                            }//if (e not found)
                        }//for
                    } else {
                        Util.log(Util.LOG_WARNING, "  Fail, messages are:");
                        AS400Message[] msgs = pcml.getMessageList("qyaspol-yasp0100");
                        for (int i = 0; i < msgs.length; i++) {
                            Util.log(Util.LOG_WARNING, "   %s - %s", msgs[i].getID(), msgs[i].getText());
                        }//for
                        throw new ZbxException(0 < msgs.length ? msgs[0].getID() + " " + msgs[0].getText()
                                                : "Unknown error during qyaspol-yasp0100");
                    }//if (pcml.callProgram() returned success)

                } else {
                    Util.log(Util.LOG_WARNING, "  Fail, messages are:");
                    AS400Message[] msgs = pcml.getMessageList("qyaspol-yasp0200");
                    for (int i = 0; i < msgs.length; i++) {
                        Util.log(Util.LOG_WARNING, "   %s - %s", msgs[i].getID(), msgs[i].getText());
                    }//for
                    throw new ZbxException(0 < msgs.length ? msgs[0].getID() + " " + msgs[0].getText()
                                            : "Unknown error during qyaspol-yasp0200");
                }//if (pcml.callProgram() returned success)

            } catch (PcmlException ex) {
                Exception ex1 = ex.getException();
                if (null != ex1 && ex1 instanceof IOException) {
/*
                    if (!((ZabbixThread)Thread.currentThread()).isAs400CommError()) {
                        Util.log(Util.LOG_WARNING,"  QYASPOL.aspCacheFiller.fill() communication to AS/400 error: %s", ex);
                    }//if(it is the first communication error)
*/
                    throw (IOException)ex1;
                } else {
                    Util.log(Util.LOG_WARNING,"  QYASPOL.aspCacheFiller.fill() error: %s", ex);
                }//if(communication error)
                throw new ZbxException(ex.getMessage());
            } finally {
                if (null != reqHandle) {
                    try {
                        pcml.setValue("qyaspol-qgyclst.reqHandle", reqHandle);
                        boolean ret = pcml.callProgram("qyaspol-qgyclst");
                        Util.log(Util.LOG_DEBUG,"   QYASPOL.aspCacheFiller.fill() closing list: %s", (ret ? "success" : "fail"));
                    } catch (PcmlException ex1) {
                        Util.log(Util.LOG_WARNING,"   QYASPOL.aspCacheFiller.fill() error during closing list: %s", ex1);
                    }//try-catch
                }//if (reqHandle)
//                Trace.setTraceOn(false);
                Util.log(Util.LOG_DEBUG, " QYASPOL.aspCacheFiller.fill() ended");
            }//try-catch-finally

        }//fill()
    }//inner class aspCacheFiller

    static class dskCacheFiller implements ZbxCacheFiller {

        public void fill() throws ZbxException, IOException {
            Util.log(Util.LOG_DEBUG, " QYASPOL.dskCacheFiller.fill() started");

            AS400 system = ((ZabbixThread)Thread.currentThread()).getAs400();
            ProgramCallDocument pcml = null;
            byte [] reqHandle = null;
            try {
                Util.log(Util.LOG_DEBUG, "  Constructing the ProgramCallDocument");
/*
                try {
                    Trace.setFileName("C:\\workfiles\\as400\\debug_pcml_qyaspol.txt");
                } catch (IOException ex) {
                    Util.log(Util.LOG_ERROR, " Error " + ex);
                }//try-catch
                Trace.setTraceOn(true);
                Trace.setTracePCMLOn(true);
*/
                pcml = new ProgramCallDocument(system, "as400.pcml.qyaspol");
                Util.log(Util.LOG_DEBUG, "  Call...");
                boolean rc = pcml.callProgram("qyaspol-yasp0300");
                if (rc) {
                    reqHandle = (byte[])pcml.getValue("qyaspol-yasp0300.listInfo.reqHandle");
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
                        e.unitNo = num;
                        num = pcml.getIntValue("qyaspol-yasp0300.receiver.aspNum", indices);
                        Util.log(Util.LOG_DEBUG, "   aspNum: %d", num);
                        e.aspNum = num;
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
                Exception ex1 = ex.getException();
                if (null != ex1 && ex1 instanceof IOException) {
/*
                    if (!((ZabbixThread)Thread.currentThread()).isAs400CommError()) {
                        Util.log(Util.LOG_WARNING,"  QYASPOL.dskCacheFiller.fill() communication to AS/400 error: %s", ex);
                    }//if(it is the first communication error)
*/
                    throw (IOException)ex1;
                } else {
                    Util.log(Util.LOG_WARNING,"  QYASPOL.dskCacheFiller.fill() error: %s", ex);
                }//if(communication error)
                throw new ZbxException(ex.getMessage());
            } finally{
                if (null != reqHandle) {
                    try {
                        pcml.setValue("qyaspol-qgyclst.reqHandle", reqHandle);
                        boolean ret = pcml.callProgram("qyaspol-qgyclst");
                        Util.log(Util.LOG_DEBUG,"   QYASPOL.dskCacheFiller.fill() closing list: %s", (ret ? "success" : "fail"));
                    } catch (PcmlException ex1) {
                        Util.log(Util.LOG_WARNING,"   QYASPOL.dskCacheFiller.fill() error during closing list: %s", ex1);
                    }//try-catch
                }//if (reqHandle)
//                Trace.setTraceOn(false);
                Util.log(Util.LOG_DEBUG, " QYASPOL.dskCacheFiller.fill() ended");
            }//try-catch-finally

        }//fill()
    }//inner class dskCacheFiller

    //static class variables
    private static final long TIMEOUT_MS = 5000l;//5 seconds
    private static ZbxCache aspTable = new ZbxCache(new aspCacheFiller(), "{#FSNAME}", "aspTable", TIMEOUT_MS);
    private static ZbxCache dskTable = new ZbxCache(new dskCacheFiller(), "{#DSK_SN}", "dskTable", TIMEOUT_MS);

    static String process_asp_discovery() throws ZbxException, IOException {
        return aspTable.discovery();
    }//process_asp_discovery()

    static Object process_asp(String id, String mode) throws ZbxException, IOException {
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
            return new Float(100.0 *
                (0 == ae.capacity ? 1 : (float)ae.available / ae.capacity)
            );
        case "pused":
            return new Float(100.0 *
                (0 == ae.capacity ? 0 : (float)(ae.capacity - ae.available) / ae.capacity)
            );
        case "state":
            return new Long(ae.status & 0x00000000FFFFFFFF);
        default:
            throw new ZbxException("Invalid parameter '" + mode + "'");
        }//switch-case
    }//process_asp_total()

    static String process_dsk_discovery() throws ZbxException, IOException {
        return dskTable.discovery();
    }//process_dsk_discovery()

    static Object process_dsk(String id, String mode) throws ZbxException, IOException {
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
            return new Float(100.0 *
                (0 == de.capacity ? 1 : (float)de.available / de.capacity)
            );
        case "pused":
            return new Float(100.0 * 
                (0 == de.capacity ? 0 : (float)(de.capacity - de.available) / de.capacity)
            );
        case "state":
            long asp_state; //1==varyoff, 2==varyon
            if (0 == de.aspNum || 1 == (asp_state = ((Long)process_asp(Integer.toString(de.aspNum), "state")).longValue()) || 2 == asp_state) {
//            if (0 == de.aspNum || 1 == ((Long)process_asp(Integer.toString(de.aspNum), "state")).longValue()) {
                return new Long(4294967295l);
            } else {
                return new Long(de.status & 0x00000000FFFFFFFF);
            }//if(asp not available)
        case "asp":
            return new Long(de.aspNum);
        default:
            throw new ZbxException("Invalid parameter '" + mode + "'");
        }//switch-case
    }//process_dsk()

}//class QYASPOL
