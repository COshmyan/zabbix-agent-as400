package as400.metric;
import as400.*;
import as400.cache.*;
import as400.thread.*;
import com.ibm.as400.access.*;
import com.ibm.as400.data.*;
import java.io.*;
import java.util.*;
//import java.beans.PropertyVetoException;

class qgyoljob {

    static class JobEntry implements ZbxCacheEntry {
        String jobFullname;
        long cpuTimeTotal;
        long cpuUsedPercentage;
        long cpuUsedElapsed;

        public void appendToStringBuilder(StringBuilder buf) {
/*
            buf.append(",\"{#FSTYPE}\":\"");
            buf.append(this.type);
            buf.append("\"");
*/
        }//appendToStringBuilder
    }//inner class JobEntry

    static class JobCacheFiller implements ZbxCacheFiller {
        public void fill() throws ZbxException {
            Util.log(Util.LOG_DEBUG, " qgyoljob.aspCacheFiller.fill() started");

            try {
                AS400 system = ((ZabbixThread)Thread.currentThread()).getAs400();

                Util.log(Util.LOG_DEBUG, "  Constructing the ProgramCallDocument");
                try {
                    Trace.setFileName("C:\\workfiles\\as400\\debug_pcml.txt");
                } catch (IOException ex) {
                    Util.log(Util.LOG_ERROR, " Error " + ex);
                }//try-catch
                Trace.setTraceOn(true);
                Trace.setTracePCMLOn(true);
                ProgramCallDocument pcml = new ProgramCallDocument(system, "as400.pcml.qgyoljob");
                Util.log(Util.LOG_DEBUG, "  Setting values...");
                int[] indices = new int[1];
/**/
                indices[0] = 0;
                pcml.setIntValue("qgyoljob-oljb0300.fieldstoreturn", indices, 312);
                indices[0] = 1;
                pcml.setIntValue("qgyoljob-oljb0300.fieldstoreturn", indices, 314);
                indices[0] = 2;
                pcml.setIntValue("qgyoljob-oljb0300.fieldstoreturn", indices, 315);
/**/
                Util.log(Util.LOG_DEBUG, "  Call...");
                boolean rc = pcml.callProgram("qgyoljob-oljb0300");
                if (rc) {
                    int rcdsReturned = pcml.getIntValue("qgyoljob-oljb0300.listInfo.rcdsReturned");
                    Util.log(Util.LOG_DEBUG, "  OK. Records: %d", rcdsReturned);
                    String value = (String)pcml.getValue("qgyoljob-oljb0300.listInfo.infoComplete");
                    //should be "C" for "Complete and accurate information"
                    if (!"C".equals(value))
                        Util.log(Util.LOG_ERROR, "  Error during qgyoljob-oljb0300: complete indicator is '%s'", value);
                    for (indices[0] = 0; indices[0] < rcdsReturned; indices[0]++) {
                        JobEntry e = new JobEntry();
                        String jobFullname = (String)pcml.getValue("qgyoljob-oljb0300.receiver.jobNumber", indices) + "/" +
                                             (String)pcml.getValue("qgyoljob-oljb0300.receiver.userName" , indices) + "/" +
                                             (String)pcml.getValue("qgyoljob-oljb0300.receiver.jobName"  , indices);
                        e.jobFullname = jobFullname;
                        e.cpuTimeTotal      = ((Long)pcml.getValue("qgyoljob-oljb0300.receiver.cpuTimeTotal"  , indices)).longValue();
                        e.cpuUsedElapsed    = ((Long)pcml.getValue("qgyoljob-oljb0300.receiver.cpuUsedElapsed", indices)).longValue();
                        e.cpuUsedPercentage =  pcml.getIntValue("qgyoljob-oljb0300.receiver.cpuUsedPercentage", indices);
                        jobTable.putEntry(jobFullname, e);
                        Util.log(Util.LOG_DEBUG, "   Job added to list: '%28s' cpuTimeTotal=%10d, cpuUsedElapsed=%10d, cpuUsedPercentage=%3d",
                                jobFullname, e.cpuTimeTotal, e.cpuUsedElapsed, e.cpuUsedPercentage);
                    }//for
                } else {
                    Util.log(Util.LOG_WARNING, "  Fail, messages are:");
                    AS400Message[] msgs = pcml.getMessageList("qgyoljob-oljb0300");
                    for (int i = 0; i < msgs.length; i++) {
                        Util.log(Util.LOG_WARNING, "   %s - %s", msgs[i].getID(), msgs[i].getText());
                    }//for
                    throw new ZbxException(0 < msgs.length ? msgs[0].getID() + " " + msgs[0].getText()
                                            : "Unknown error during qgyoljob-oljb0300");
                }//if (rc)
            } catch (PcmlException ex) {
                Util.log(Util.LOG_WARNING,"  qgyoljob.aspCacheFiller.fill() error: %s", ex);
                ex.printStackTrace();
                throw new ZbxException(ex.getMessage());
            } finally{
                Trace.setTraceOn(false);
                Util.log(Util.LOG_DEBUG, " qgyoljob.aspCacheFiller.fill() ended");
            }//try-catch-finally

        }//fill()
    }//inner class ZbxCacheFiller

    //static class variables
    private static final long TIMEOUT_MS = 5000l;//5 seconds
    private static ZbxCache jobTable = new ZbxCache(new JobCacheFiller(), "{#JOB}", "jobTable", TIMEOUT_MS);

    static String process_job_discovery() throws ZbxException, IOException {
        return jobTable.discovery();
    }//process_asp_discovery()


}//class qgyoljob
