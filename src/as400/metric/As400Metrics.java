package as400.metric;
import as400.*;
import as400.perfstat.*;
import as400.thread.ZabbixThread;
import com.ibm.as400.access.*;
import com.ibm.as400.data.*;
import java.io.*;
import java.util.Enumeration;
import java.beans.PropertyVetoException;

public class As400Metrics {

    //static class variables
    
    public As400Metrics() {
        //system = new AS400(as400ServerHost, Config.getUser(), asPassword);
    }//constructor As400Metrics()

    public static void init() {

        //initialization of anonymous classes for generic agent metrics
        try {
            new ZbxMetric("system.hostname", Util.CF_AS400COMM) {
                public DataObject process(AgentRequest req) throws ZbxException, IOException {
                    if (null == req)
                        throw new ZbxException("Bad request");
                    AS400 system = ((ZabbixThread)Thread.currentThread()).getAs400();
                    String s;
                    try {
                        s = new SystemStatus(system).getSystemName().toLowerCase();
                    } catch (AS400SecurityException|ErrorCompletingRequestException|ObjectDoesNotExistException|InterruptedException ex) {
                        Util.log(Util.LOG_WARNING," As400Metric.process() error: %s", ex);
                        throw new ZbxException(ex.toString());
                    }//try-catch
                    Util.log(Util.LOG_DEBUG,"As400Metric.process() is OK for %s: '%s'",req.getKeyName(), s);
                    return new DataObject(req.getUnparsedKey(), s);
                }//process()
            };//new anonymous class
        } catch (ZbxException ex) {
            Util.log(Util.LOG_ERROR,"%s",ex);
        }//try-catch

        try {
            new ZbxMetric("system.uname", Util.CF_AS400COMM) {
                public DataObject process(AgentRequest req) throws ZbxException, IOException {
                    if (null == req)
                        throw new ZbxException("Bad request");
                    AS400 system = ((ZabbixThread)Thread.currentThread()).getAs400();
                    String s;
                    try {
                        s = String.format("IBM OS/400 %s V%dR%dM%d, %s %s (v%s)",
                            new SystemStatus(system).getSystemName(), system.getVersion(),
                            system.getRelease(), system.getModification(),
                            System.getProperty("java.vendor",  "unknown"),
                            System.getProperty("java.vm.name", "unknown"),
                            System.getProperty("java.version", "unknown"));
                    } catch (AS400SecurityException|ErrorCompletingRequestException|ObjectDoesNotExistException|InterruptedException ex) {
                        Util.log(Util.LOG_WARNING," As400Metric.process() error: %s", ex);
                        throw new ZbxException(ex.toString());
                    }//try-catch
                    Util.log(Util.LOG_DEBUG,"As400Metric.process() is OK for %s: '%s'",req.getKeyName(), s);
                    return new DataObject(req.getUnparsedKey(), s);
                }//process()
            };//new anonymous class
        } catch (ZbxException ex) {
            Util.log(Util.LOG_ERROR,"%s",ex);
        }//try-catch

        try {
            new ZbxMetric("system.localtime", Util.CF_AS400COMM | Util.CF_HAVEPARAMS) {
                public DataObject process(AgentRequest req) throws ZbxException, IOException {
                    Util.log(Util.LOG_DEBUG," As400Metric.process() started for %s",req.getKeyName());
                    AS400 system = ((ZabbixThread)Thread.currentThread()).getAs400();
                    String curPar = null;
                    int N = req.getNparam();
                    try {
                        if ( N<1 || "".equals(curPar = req.getParam(0)) || "utc".equals(curPar) ) {
                            SystemStatus ss = new SystemStatus(system);
                            ss.refreshCache();
                            long ret = ss.getDateAndTimeStatusGathered().getTime() / 1000;
                            Util.log(Util.LOG_DEBUG,"  As400Metric.process(): UTC time = %d, elapsed time = %d",
                                ret, ss.getElapsedTime());
                            return new DataObject(req.getUnparsedKey(), new Long(ret));
                        } else if ( "local".equals(curPar) ) {
                            String qdatetime  = (String) new SystemValue(system, "QDATETIME" ).getValue();
                            String qutcoffset = (String) new SystemValue(system, "QUTCOFFSET").getValue();
                            String ret = String.format("%s-%s-%s,%s:%s:%s.%s,%s:%s",
                                qdatetime.substring(0,4),   qdatetime.substring(4,6),   qdatetime.substring(6,8),
                                qdatetime.substring(8,10),  qdatetime.substring(10,12), qdatetime.substring(12,14),
                                qdatetime.substring(14,17), qutcoffset.substring(0,3),  qutcoffset.substring(3) );
                            return new DataObject(req.getUnparsedKey(), ret);
                        } else {
                            throw new ZbxException("Invalid parameter: '" + curPar +"'");
                        }//if-else
                    } catch (AS400SecurityException|ErrorCompletingRequestException|ObjectDoesNotExistException|InterruptedException|RequestNotSupportedException ex) {
                        Util.log(Util.LOG_WARNING," As400Metric.process() error: %s", ex);
                        throw new ZbxException(ex.toString());
                    } finally {
                        Util.log(Util.LOG_DEBUG,"As400Metric.process() is OK for %s",req.getKeyName());
                    }//try-catch
                }//process()
            };//new anonymous class
        } catch (ZbxException ex) {
            Util.log(Util.LOG_ERROR,"%s",ex);
        }//try-catch

        try {
            new ZbxMetric("system.cpu.num", Util.CF_AS400COMM | Util.CF_HAVEPARAMS) {
                public DataObject process(AgentRequest req) throws ZbxException, IOException {
                    Util.log(Util.LOG_DEBUG," As400Metric.process() started for %s",req.getKeyName());
                    AS400 system = ((ZabbixThread)Thread.currentThread()).getAs400();
                    String curPar = null;
                    int N = req.getNparam();
                    try {
                        if ( N<1 || "".equals(curPar = req.getParam(0)) || "online".equals(curPar) ||
                            "max".equals(curPar) ) {
                            SystemStatus ss = new SystemStatus(system);
                            ss.refreshCache();
                            int ret = ss.getNumberOfProcessors();
                            return new DataObject(req.getUnparsedKey(), new Integer(ret));
                        } else {
                            throw new ZbxException("Invalid parameter: '" + curPar +"'");
                        }//if-else
                    } catch (AS400SecurityException|ErrorCompletingRequestException|ObjectDoesNotExistException|InterruptedException ex) {
                        Util.log(Util.LOG_WARNING," As400Metric.process() error: %s", ex);
                        throw new ZbxException(ex.toString());
                    } finally {
                        Util.log(Util.LOG_DEBUG,"As400Metric.process() is OK for %s",req.getKeyName());
                    }//try-catch
                }//process()
            };//new anonymous class
        } catch (ZbxException ex) {
            Util.log(Util.LOG_ERROR,"%s",ex);
        }//try-catch

        try {
            new ZbxMetric("as400.cpu.capacity", Util.CF_AS400COMM) {
                public DataObject process(AgentRequest req) throws ZbxException, IOException {
                    Util.log(Util.LOG_DEBUG," As400Metric.process() started for %s",req.getKeyName());
                    AS400 system = ((ZabbixThread)Thread.currentThread()).getAs400();
                    String curPar = null;
                    try {
                        SystemStatus ss = new SystemStatus(system);
                        ss.refreshCache();
                        float ret = ss.getCurrentProcessingCapacity();
                        return new DataObject(req.getUnparsedKey(), new Float(ret));
                    } catch (AS400SecurityException|ErrorCompletingRequestException|ObjectDoesNotExistException|InterruptedException ex) {
                        Util.log(Util.LOG_WARNING," As400Metric.process() error: %s", ex);
                        throw new ZbxException(ex.toString());
                    } finally {
                        Util.log(Util.LOG_DEBUG,"As400Metric.process() is OK for %s",req.getKeyName());
                    }//try-catch
                }//process()
            };//new anonymous class
        } catch (ZbxException ex) {
            Util.log(Util.LOG_ERROR,"%s",ex);
        }//try-catch

        try {
            new ZbxMetric("system.users.num", Util.CF_AS400COMM) {
                public DataObject process(AgentRequest req) throws ZbxException, IOException {
                    Util.log(Util.LOG_DEBUG," As400Metric.process() started for %s",req.getKeyName());
                    AS400 system = ((ZabbixThread)Thread.currentThread()).getAs400();
                    try {
                        SystemStatus ss = new SystemStatus(system);
                        ss.refreshCache();
                        int ret = ss.getUsersCurrentSignedOn();
                        return new DataObject(req.getUnparsedKey(), new Integer(ret));
                    } catch (AS400SecurityException|ErrorCompletingRequestException|ObjectDoesNotExistException|InterruptedException ex) {
                        Util.log(Util.LOG_WARNING," As400Metric.process() error: %s", ex);
                        throw new ZbxException(ex.toString());
                    } finally {
                        Util.log(Util.LOG_DEBUG,"As400Metric.process() is OK for %s",req.getKeyName());
                    }//try-catch
                }//process()
            };//new anonymous class
        } catch (ZbxException ex) {
            Util.log(Util.LOG_ERROR,"%s",ex);
        }//try-catch

        try {
            //proc.num[<name>,<user>,<state>,<cmdline>]
            new ZbxMetric("proc.num", Util.CF_AS400COMM | Util.CF_HAVEPARAMS) {
                public DataObject process(AgentRequest req) throws ZbxException, IOException {
                    Util.log(Util.LOG_DEBUG," As400Metric.process() started for %s",req.getKeyName());
                    int N = req.getNparam(), ret = 0;
                    if (N > 4)
                        throw new ZbxException("Bad request: maximum 4 parameters supported");
                    AS400 system = ((ZabbixThread)Thread.currentThread()).getAs400();
                    String curPar = null;
                    JobList jl = new JobList(system);
                    try {
                        jl.clearJobSelectionCriteria();
                        jl.clearJobAttributesToRetrieve();
                        //1-st parameter: <name>
                        if ( N<1 || "".equals(curPar = req.getParam(0)) )
                            curPar = JobList.SELECTION_JOB_NAME_ALL;
                        jl.addJobSelectionCriteria(JobList.SELECTION_JOB_NAME, curPar);
                        //2-nd parameter: <user>
                        if ( N<2 || "".equals(curPar = req.getParam(1)) )
                            curPar = JobList.SELECTION_USER_NAME_ALL;
                        jl.addJobSelectionCriteria(JobList.SELECTION_USER_NAME, curPar);
                        //3-rd parameter: <state>
                        if ( N<3 || "".equals(curPar = req.getParam(2).toUpperCase()) || "ALL".equals(curPar) ) {
                            jl.addJobSelectionCriteria(JobList.SELECTION_PRIMARY_JOB_STATUS_ACTIVE, true);
                            jl.addJobSelectionCriteria(JobList.SELECTION_PRIMARY_JOB_STATUS_JOBQ  , true);
                            jl.addJobSelectionCriteria(JobList.SELECTION_PRIMARY_JOB_STATUS_OUTQ  , true);
                        } else {//state is defined
                            switch (curPar) {
                            case "RUN":
                                jl.addJobSelectionCriteria(JobList.SELECTION_PRIMARY_JOB_STATUS_ACTIVE, true );
                                jl.addJobSelectionCriteria(JobList.SELECTION_PRIMARY_JOB_STATUS_JOBQ  , true );
                                jl.addJobSelectionCriteria(JobList.SELECTION_PRIMARY_JOB_STATUS_OUTQ  , false);
                                break;
                            case Job.JOB_STATUS_ACTIVE:
                                jl.addJobSelectionCriteria(JobList.SELECTION_PRIMARY_JOB_STATUS_ACTIVE, true );
                                jl.addJobSelectionCriteria(JobList.SELECTION_PRIMARY_JOB_STATUS_JOBQ  , false);
                                jl.addJobSelectionCriteria(JobList.SELECTION_PRIMARY_JOB_STATUS_OUTQ  , false);
                                break;
                            case Job.JOB_STATUS_JOBQ:
                                jl.addJobSelectionCriteria(JobList.SELECTION_PRIMARY_JOB_STATUS_ACTIVE, false);
                                jl.addJobSelectionCriteria(JobList.SELECTION_PRIMARY_JOB_STATUS_JOBQ  , true );
                                jl.addJobSelectionCriteria(JobList.SELECTION_PRIMARY_JOB_STATUS_OUTQ  , false);
                                break;
                            case Job.JOB_STATUS_OUTQ:
                                jl.addJobSelectionCriteria(JobList.SELECTION_PRIMARY_JOB_STATUS_ACTIVE, false);
                                jl.addJobSelectionCriteria(JobList.SELECTION_PRIMARY_JOB_STATUS_JOBQ  , false);
                                jl.addJobSelectionCriteria(JobList.SELECTION_PRIMARY_JOB_STATUS_OUTQ  , true );
                                break;
                            default:
                                jl.addJobSelectionCriteria(JobList.SELECTION_PRIMARY_JOB_STATUS_ACTIVE, true );
                                jl.addJobSelectionCriteria(JobList.SELECTION_PRIMARY_JOB_STATUS_JOBQ  , false);
                                jl.addJobSelectionCriteria(JobList.SELECTION_PRIMARY_JOB_STATUS_OUTQ  , false);
                                jl.addJobSelectionCriteria(JobList.SELECTION_ACTIVE_JOB_STATUS,         curPar);
                            }//switch-case
                        }//state
                        //4-th parameter: <subsystem>
                        if ( N<4 || "".equals(curPar = req.getParam(3)) ) {
                            //implicit call of load() method from jl.getLength() does not throw any exceptions upon communication errors,
                            //therefore we performs this call explicitly
                            jl.load();
                            ret = jl.getLength();
                        } else {
                            ZbxRegexp regex = new ZbxRegexp(curPar, false);
                            jl.addJobAttributeToRetrieve(Job.SUBSYSTEM);
                            jl.load();
                            Enumeration jobs = jl.getJobs();
                            while (jobs.hasMoreElements()) {
                                Job job = null;
                                String subsystem = null;
                                //in rare cases some job is disappeared during the job list processing, just ignore such job
                                try {
                                    job = (Job)jobs.nextElement();
                                    subsystem = job.getSubsystem();
                                } catch (ErrorCompletingRequestException|ObjectDoesNotExistException ex) { }
                                if (null != subsystem) {
                                    int i = subsystem.lastIndexOf('/') + 1;
                                    int j = subsystem.length() - (subsystem.endsWith(".SBSD") ? 5 : 0);
                                    subsystem = subsystem.substring(i, j);
                                    if ( regex.matches(subsystem) ) {
                                        Util.log(Util.LOG_DEBUG,"   processed subsystem '%s' matched with '%s'", subsystem, regex);
                                        ret++;
                                    }//if(matches)
                                }//if(subsystem!=null)
                            }//while
                        }//if (subsystem defined)
                    } catch (PropertyVetoException|InterruptedException|AS400SecurityException|ErrorCompletingRequestException|ObjectDoesNotExistException ex) {
                        Util.log(Util.LOG_WARNING," As400Metric.process() error: %s", ex);
                        throw new ZbxException(ex.toString());
                    } finally {
                        try { jl.close(); } catch (InterruptedException|IOException|AS400SecurityException|ErrorCompletingRequestException|ObjectDoesNotExistException ex) { ; }
                    }//try-catch-finally
                    return new DataObject(req.getUnparsedKey(), new Integer(ret));
                }//process()
            };//new anonymous class
        } catch (ZbxException ex) {
            Util.log(Util.LOG_ERROR,"%s",ex);
        }//try-catch

        try {
            new ZbxMetric("as400.subsystem", Util.CF_AS400COMM | Util.CF_HAVEPARAMS) {
                public DataObject process(AgentRequest req) throws ZbxException, IOException {
                    String s, subsystem, library;
                    if (2 != req.getNparam() || "".equals(subsystem = req.getParam(0)) || "".equals(library = req.getParam(1)))
                        throw new ZbxException("Bad request: 2 parameters needed: <subsystem> and <library>");
                    Util.log(Util.LOG_DEBUG," As400Metric.process() started for %s",req.getKeyName());
                    AS400 system = ((ZabbixThread)Thread.currentThread()).getAs400();
                    try {
                        Subsystem sbs = new Subsystem(system, library, subsystem);
                        if (sbs.exists()) {
                            sbs.refresh();
                            s = sbs.getStatus();
                        } else {
                            throw new ZbxException("There is no subsystem with name='"+subsystem+"' in library '"+library+"'");
                        }//if (subsystem exists)
                    } catch (InterruptedException|AS400SecurityException|ErrorCompletingRequestException|ObjectDoesNotExistException ex) {
                        Util.log(Util.LOG_WARNING," As400Metric.process() error: %s", ex);
                        throw new ZbxException(ex.toString());
                    }//try-catch
                    //Possible values are: *ACTIVE, *ENDING, *INACTIVE, *RESTRICTED, and *STARTING.
                    return new DataObject(req.getUnparsedKey(), s);
                }//process()
            };//new anonymous class
        } catch (ZbxException ex) {
            Util.log(Util.LOG_ERROR,"%s",ex);
        }//try-catch
/*
        //Unfortunately, this part still does not work successfully, it is experimental only
        try {
            new ZbxMetric("system.run", Util.CF_AS400COMM | Util.CF_HAVEPARAMS) {
                public DataObject process(AgentRequest req) throws ZbxException, IOException {
                    String cmd, library;
                    StringBuffer s = new StringBuffer();
                    if (1 > req.getNparam() || "".equals(cmd = req.getParam(0)))
                        throw new ZbxException("Bad request: command needed");
                    Util.log(Util.LOG_DEBUG," As400Metric.process() started for %s",req.getKeyName());
                    AS400 system = ((ZabbixThread)Thread.currentThread()).getAs400();
                    try {
                        CommandCall command = new CommandCall(system);
                        boolean res = command.run(cmd);
                        AS400Message[] messagelist = command.getMessageList();
                        for (int i = 0; i < messagelist.length; ++i) {
                            s.append(messagelist[i].getID());
                            s.append(" - ");
                            s.append(messagelist[i].getText());
                            s.append('\n');
                        }//for
                        if ( !res ) {
                            throw new ZbxException("Error running CL command '"+cmd+"': "+s.toString());
                        }//if (not success)
                        JobLog joblog = command.getServerJob().getJobLog();
                        joblog.load();
                        for (Enumeration e = joblog.getMessages(); e.hasMoreElements(); ) {
                            QueuedMessage msg = (QueuedMessage)e.nextElement();
                            s.append("  Type: ");
                            s.append(msg.getType());
                            s.append(", ID: ");
                            s.append(msg.getID());
                            s.append('\n');
                            s.append(msg.getText());
                            s.append('\n');
                        }//for
                        joblog.close();
                    } catch (InterruptedException|PropertyVetoException|AS400SecurityException|ErrorCompletingRequestException|ObjectDoesNotExistException ex) {
                        Util.log(Util.LOG_WARNING," As400Metric.process() error: %s", ex);
                        throw new ZbxException(ex.toString());
                    }//try-catch
                    return new DataObject(req.getUnparsedKey(), s.toString());
                }//process()
            };//new anonymous class
        } catch (ZbxException ex) {
            Util.log(Util.LOG_ERROR,"%s",ex);
        }//try-catch
*/
        try {
            new ZbxMetric("as400.outputqueue.size", Util.CF_AS400COMM | Util.CF_HAVEPARAMS) {
                public DataObject process(AgentRequest req) throws ZbxException, IOException {
                    String oqueue, library;
                    int ret;
                    if (2 != req.getNparam() || "".equals(oqueue = req.getParam(0)) || "".equals(library = req.getParam(1)))
                        throw new ZbxException("Bad request: 2 parameters needed: <output queue> and <library>");
                    Util.log(Util.LOG_DEBUG," As400Metric.process() started for %s",req.getKeyName());
                    AS400 system = ((ZabbixThread)Thread.currentThread()).getAs400();
                    SpooledFileList sfl = new SpooledFileList(system);
                    try {
                        sfl.setUserFilter("*ALL");
                        sfl.setQueueFilter("/QSYS.LIB/" + library + ".LIB/" + oqueue + ".OUTQ");
                        sfl.openSynchronously();
                        ret = sfl.size();
                    } catch (PropertyVetoException|InterruptedException|AS400SecurityException|ErrorCompletingRequestException|RequestNotSupportedException ex) {
                        Util.log(Util.LOG_WARNING," As400Metric.process() error: %s", ex);
                        throw new ZbxException(ex.toString());
                    } finally {
                        sfl.close();
                    }//try-catch
                    return new DataObject(req.getUnparsedKey(), new Integer(ret));
                }//process()
            };//new anonymous class
        } catch (ZbxException ex) {
            Util.log(Util.LOG_ERROR,"%s",ex);
        }//try-catch

        try {
            new ZbxMetric("as400.services", Util.CF_AS400COMM | Util.CF_HAVEPARAMS) {
                public DataObject process(AgentRequest req) throws ZbxException {
                    Util.log(Util.LOG_DEBUG," As400Metric.process() started for %s",req.getKeyName());
                    String s_services = null;
                    int services = 0x00FF, ret = 0;
                    if (0 < req.getNparam() && !"".equals(s_services = req.getParam(0)))
                        try {
                            services = Integer.parseInt(s_services); 
                            if (0 >= services || 255 < services)
                                throw new ZbxException("Invalid parameter '" + s_services + "': number must be between 1 and 255");
                        } catch (NumberFormatException ex) {
                            switch (s_services.toUpperCase()) {
                            case "FILE":
                                services = AS400.FILE;
                                break;
                            case "PRINT":
                                services = AS400.PRINT;
                                break;
                            case "COMMAND":
                                services = AS400.COMMAND;
                                break;
                            case "DATAQUEUE":
                                services = AS400.DATAQUEUE;
                                break;
                            case "DATABASE":
                                services = AS400.DATABASE;
                                break;
                            case "RECORDACCESS":
                                services = AS400.RECORDACCESS;
                                break;
                            case "CENTRAL":
                                services = AS400.CENTRAL;
                                break;
                            case "SIGNON":
                                services = AS400.SIGNON;
                                break;
                            default:
                                throw new ZbxException("Invalid parameter in '" + req.getUnparsedKey() + "': '" + s_services + "'");
                            }//switch-case
                            services = 0x01 << services;
                        }//try-catch
                    AS400 system = ((ZabbixThread)Thread.currentThread()).getAs400();
                    for (int i = 0, service = 1; i < 8; i++, service <<= 1) {
                        if (0 == (services & service))
                            continue;
                        try  {
                            if (!system.isConnected(i))
                                system.connectService(i);
                        } catch (IOException|AS400SecurityException ex) { ; }
                        Util.log(Util.LOG_DEBUG, " checking connection for a service %d (%d)", i, service);
                        if (!system.isConnectionAlive(i))
                            ret |= service;
                    }//for
                    Util.log(Util.LOG_DEBUG," As400Metric.process() ended for %s",req.getKeyName());
                    return new DataObject(req.getUnparsedKey(), new Integer(ret));
                }//process()
            };//new anonymous class
        } catch (ZbxException ex) {
            Util.log(Util.LOG_ERROR,"%s",ex);
        }//try-catch

        try {
            new ZbxMetric("vfs.fs.discovery", Util.CF_AS400COMM) {
                public DataObject process(AgentRequest req) throws ZbxException, IOException {
                    Util.log(Util.LOG_DEBUG," As400Metric.process() started for %s", req.getKeyName());
                    try {
                        return new DataObject(req.getUnparsedKey(), QYASPOL.process_asp_discovery());
                    } finally {
                        Util.log(Util.LOG_DEBUG," As400Metric.process() ended %s", req.getKeyName());
                    }
                }//process()
            };//new anonymous class
        } catch (ZbxException ex) {
            Util.log(Util.LOG_ERROR,"%s",ex);
        }//try-catch

        try {
            new ZbxMetric("vfs.fs.size", Util.CF_AS400COMM | Util.CF_HAVEPARAMS) {
                public DataObject process(AgentRequest req) throws ZbxException, IOException {
                    String fs = null, mode = null;
                    if (1 > req.getNparam() || "".equals(fs = req.getParam(0)))
                        throw new ZbxException("Bad request: parameters FS needed");
                    if (2 > req.getNparam() || "".equals(mode = req.getParam(1)))
                        mode = "total";
                    Util.log(Util.LOG_DEBUG," As400Metric.process() started for %s", req.getKeyName());
                    try {
                        switch (mode) {
                        case "total":
                        case "free":
                        case "used":
                        case "pfree":
                        case "pused":
                            return new DataObject(req.getUnparsedKey(),
                                        QYASPOL.process_asp(fs, mode));
                        default:
                            throw new ZbxException("Bad request: invalid parameter '" + mode + "'");
                        }//switch-case
                    } finally {
                        Util.log(Util.LOG_DEBUG," As400Metric.process() ended %s", req.getKeyName());
                    }
                }//process()
            };//new anonymous class
        } catch (ZbxException ex) {
            Util.log(Util.LOG_ERROR,"%s",ex);
        }//try-catch

        try {
            new ZbxMetric("vfs.fs.state", Util.CF_AS400COMM | Util.CF_HAVEPARAMS) {
                public DataObject process(AgentRequest req) throws ZbxException, IOException {
                    String fs = null;
                    if (1 > req.getNparam() || "".equals(fs = req.getParam(0)))
                        throw new ZbxException("Bad request: parameters FS needed");
                    Util.log(Util.LOG_DEBUG," As400Metric.process() started for %s", req.getKeyName());
                    try {
                            return new DataObject(req.getUnparsedKey(),
                                        QYASPOL.process_asp(fs, "state"));
                    } finally {
                        Util.log(Util.LOG_DEBUG," As400Metric.process() ended %s", req.getKeyName());
                    }
                }//process()
            };//new anonymous class
        } catch (ZbxException ex) {
            Util.log(Util.LOG_ERROR,"%s",ex);
        }//try-catch

        try {
            new ZbxMetric("as400.disk.discovery", Util.CF_AS400COMM) {
                public DataObject process(AgentRequest req) throws ZbxException, IOException {
                    Util.log(Util.LOG_DEBUG," As400Metric.process() started for %s", req.getKeyName());
                    try {
                        return new DataObject(req.getUnparsedKey(), QYASPOL.process_dsk_discovery());
                    } finally {
                        Util.log(Util.LOG_DEBUG," As400Metric.process() ended %s", req.getKeyName());
                    }
                }//process()
            };//new anonymous class
        } catch (ZbxException ex) {
            Util.log(Util.LOG_ERROR,"%s",ex);
        }//try-catch

        try {
            new ZbxMetric("as400.disk.size", Util.CF_AS400COMM | Util.CF_HAVEPARAMS) {
                public DataObject process(AgentRequest req) throws ZbxException, IOException {
                    String fs = null, mode = null;
                    if (1 > req.getNparam() || "".equals(fs = req.getParam(0)))
                        throw new ZbxException("Bad request: parameters DISK needed");
                    if (2 > req.getNparam() || "".equals(mode = req.getParam(1)))
                        mode = "total";
                    Util.log(Util.LOG_DEBUG," As400Metric.process() started for %s", req.getKeyName());
                    try {
                        switch (mode) {
                        case "total":
                        case "free":
                        case "used":
                        case "pfree":
                        case "pused":
                            return new DataObject(req.getUnparsedKey(),
                                        QYASPOL.process_dsk(fs, mode));
                        default:
                            throw new ZbxException("Bad request: invalid parameter '" + mode + "'");
                        }//switch-case
                    } finally {
                        Util.log(Util.LOG_DEBUG," As400Metric.process() ended %s", req.getKeyName());
                    }
                }//process()
            };//new anonymous class
        } catch (ZbxException ex) {
            Util.log(Util.LOG_ERROR,"%s",ex);
        }//try-catch

        try {
            new ZbxMetric("as400.disk.state", Util.CF_AS400COMM | Util.CF_HAVEPARAMS) {
                public DataObject process(AgentRequest req) throws ZbxException, IOException {
                    String fs = null;
                    if (1 > req.getNparam() || "".equals(fs = req.getParam(0)))
                        throw new ZbxException("Bad request: parameters DISK needed");
                    Util.log(Util.LOG_DEBUG," As400Metric.process() started for %s", req.getKeyName());
                    try {
                            return new DataObject(req.getUnparsedKey(),
                                        QYASPOL.process_dsk(fs, "state"));
                    } finally {
                        Util.log(Util.LOG_DEBUG," As400Metric.process() ended %s", req.getKeyName());
                    }
                }//process()
            };//new anonymous class
        } catch (ZbxException ex) {
            Util.log(Util.LOG_ERROR,"%s",ex);
        }//try-catch

        try {
            new ZbxMetric("as400.disk.asp", Util.CF_AS400COMM | Util.CF_HAVEPARAMS) {
                public DataObject process(AgentRequest req) throws ZbxException, IOException {
                    String fs = null;
                    if (1 > req.getNparam() || "".equals(fs = req.getParam(0)))
                        throw new ZbxException("Bad request: parameters DISK needed");
                    Util.log(Util.LOG_DEBUG," As400Metric.process() started for %s", req.getKeyName());
                    try {
                            return new DataObject(req.getUnparsedKey(),
                                        QYASPOL.process_dsk(fs, "asp"));
                    } finally {
                        Util.log(Util.LOG_DEBUG," As400Metric.process() ended %s", req.getKeyName());
                    }
                }//process()
            };//new anonymous class
        } catch (ZbxException ex) {
            Util.log(Util.LOG_ERROR,"%s",ex);
        }//try-catch

        try {
            new ZbxMetric("as400.systemPool.discovery", Util.CF_AS400COMM) {
                public DataObject process(AgentRequest req) throws ZbxException, IOException {
                    Util.log(Util.LOG_DEBUG," As400Metric.process() started for %s", req.getKeyName());
                    try {
                        return new DataObject(req.getUnparsedKey(), SystemPoolMetric.process_systemPool_discovery());
                    } finally {
                        Util.log(Util.LOG_DEBUG," As400Metric.process() ended %s", req.getKeyName());
                    }
                }//process()
            };//new anonymous class
        } catch (ZbxException ex) {
            Util.log(Util.LOG_ERROR,"%s",ex);
        }//try-catch

        try {
            new ZbxMetric("as400.systemPool.state", Util.CF_AS400COMM | Util.CF_HAVEPARAMS) {
                public DataObject process(AgentRequest req) throws ZbxException, IOException {
                    String name = null, mode = null;
                    if (1 > req.getNparam() || "".equals(name = req.getParam(0)))
                        throw new ZbxException("Bad request: parameters NAME needed");
                    if (2 > req.getNparam() || "".equals(mode = req.getParam(1)))
                        mode = "size";
                    Util.log(Util.LOG_DEBUG," As400Metric.process() started for %s", req.getKeyName());
                    try {
                        switch (mode) {
                        case "size":
                        case "identifier":
                        case "description":
                        case "databaseFaults":
                        case "nonDatabaseFaults":
                        case "totalFaults":
                            return new DataObject(req.getUnparsedKey(),
                                        SystemPoolMetric.process_systemPool(name, mode));
                        default:
                            throw new ZbxException("Bad request: invalid parameter '" + mode + "'");
                        }//switch-case
                    } finally {
                        Util.log(Util.LOG_DEBUG," As400Metric.process() ended %s", req.getKeyName());
                    }
                }//process()
            };//new anonymous class
        } catch (ZbxException ex) {
            Util.log(Util.LOG_ERROR,"%s",ex);
        }//try-catch
/*
        //This part tried to use QGYOLJOB AS/400 API to collect native CPU usage statistics
        //(for elapsed time); however it looks that it's impossible from Java as it starts
        //a new short-term monitoring job every time. In result, the elapsed time and all
        //statistics for an "elapsed time" always returns zero's.
        //We use just a total usage time for each job and produce own calculations in Procstat class.
        try {
            new ZbxMetric("as400.job.discovery", Util.CF_AS400COMM) {
                public DataObject process(AgentRequest req) throws ZbxException {
                    Util.log(Util.LOG_DEBUG," As400Metric.process() started for %s", req.getKeyName());
                    try {
                        return new DataObject(req.getUnparsedKey(), qgyoljob.process_job_discovery());
                    } finally {
                        Util.log(Util.LOG_DEBUG," As400Metric.process() ended %s", req.getKeyName());
                    }
                }//process()
            };//new anonymous class
        } catch (ZbxException ex) {
            Util.log(Util.LOG_ERROR,"%s",ex);
        }//try-catch
*/
        try {
            new ZbxMetric("proc.cpu.util.discovery", Util.CF_HAVEPARAMS) {
                public DataObject process(AgentRequest req) throws ZbxException {
                    Util.log(Util.LOG_DEBUG," As400Metric.process() started for %s", req.getKeyName());
                    int seconds = 0;
                    String tmp;
                    if (1 > req.getNparam() || "".equals(tmp = req.getParam(0)))
                        throw new ZbxException("Bad request: the first parameter needed");
                    try {
                        seconds = Integer.parseInt(tmp);
                        if (1 >= seconds)
                            throw new ZbxException("");
                    } catch (NumberFormatException|ZbxException ex) {
                            throw new ZbxException("Invalid parameter '" + tmp + "': must be a number more than 1");
                    }//try-catch
                    try {
                        return new DataObject(req.getUnparsedKey(), Procstat.jobDiscovery(seconds));
                    } finally {
                        Util.log(Util.LOG_DEBUG," As400Metric.process() ended %s", req.getKeyName());
                    }
                }//process()
            };//new anonymous class
        } catch (ZbxException ex) {
            Util.log(Util.LOG_ERROR,"%s",ex);
        }//try-catch

        try {
            new ZbxMetric("proc.cpu.util", Util.CF_HAVEPARAMS) {
                public DataObject process(AgentRequest req) throws ZbxException {
                    Util.log(Util.LOG_DEBUG," As400Metric.process() started for %s", req.getKeyName());
                    int N = req.getNparam(), mode = 0;
                    String jobname, usrname, jobnum, subsystem, tmp;
                    //1-st parameter: <name>
                    jobname = N<1 ? "" : req.getParam(0);
                    //2-st parameter: <user>
                    usrname = N<2 ? "" : req.getParam(1);
                    //3-st parameter: <type> (not used)
                    tmp = N<3 ? "" : req.getParam(2);
                    switch (tmp) {
                        case "":
                        case "total":
                        case "user" :
                        case "system":
                            break;
                        default:
                            throw new ZbxException("Invalid <type> parameter: '" + tmp + "'");
                    }//switch-case
                    //4-st parameter: <subsystem> (RE)
                    subsystem =  N<4 ? "" : req.getParam(3);
                    //5-st parameter: <mode>
                    tmp = N<5 ? "" : req.getParam(4);
                    switch (tmp) {
                        case "":
                        case "avg1":
                            mode = 1; break;
                        case "avg5" :
                            mode = 5; break;
                        case "avg15":
                            mode = 15; break;
                        default:
                            throw new ZbxException("Invalid <mode> parameter: '" + tmp + "'");
                    }//switch-case
                    //6-st parameter: <jobnum>
                    jobnum = N<6 ? "" : req.getParam(5);

                    try {
                        return new DataObject(req.getUnparsedKey(), Procstat.getPercentage(mode,
                                              jobnum, usrname, jobname, subsystem));
                    } finally {
                        Util.log(Util.LOG_DEBUG," As400Metric.process() ended %s", req.getKeyName());
                    }
                }//process()
            };//new anonymous class
        } catch (ZbxException ex) {
            Util.log(Util.LOG_ERROR,"%s",ex);
        }//try-catch

    }//init()

}//class As400Metrics
