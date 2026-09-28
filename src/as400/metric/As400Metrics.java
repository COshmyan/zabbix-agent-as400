package as400.metric;
import as400.*;
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
            new ZbxMetric("system.hostname", 0) {
                public DataObject process(AgentRequest req) throws ZbxException {
                    if (null == req)
                        throw new ZbxException("Bad request");
                    AS400 system = ((ZabbixThread)Thread.currentThread()).getAs400();
                    String s;
                    try {
                        s = new SystemStatus(system).getSystemName().toLowerCase();
                    } catch (AS400SecurityException|ErrorCompletingRequestException|ObjectDoesNotExistException|IOException|InterruptedException ex) {
                        Util.log(Util.LOG_WARNING," As400Metric.process() error: %s", ex);
                        throw new ZbxException(ex.getMessage());
                    }//try-catch
                    try {
                        if (system.isConnected())
                            system.disconnectAllServices();
                    } catch (Exception ex) {
                        Util.log(Util.LOG_WARNING, " Error in process() during closing AS/400: %s",  ex);
                    }//try-catch

                    Util.log(Util.LOG_DEBUG,"As400Metric.process() is OK for %s: '%s'",req.getKeyName(), s);
                    return new DataObject(req.getUnparsedKey(), s);
                }//process()
            };//new anonymous class
        } catch (ZbxException ex) {
            Util.log(Util.LOG_ERROR,"%s",ex);
        }//try-catch

        try {
            new ZbxMetric("system.uname", 0) {
                public DataObject process(AgentRequest req) throws ZbxException {
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
                    } catch (AS400SecurityException|ErrorCompletingRequestException|ObjectDoesNotExistException|IOException|InterruptedException ex) {
                        Util.log(Util.LOG_WARNING," As400Metric.process() error: %s", ex);
                        throw new ZbxException(ex.getMessage());
                    }//try-catch
                    Util.log(Util.LOG_DEBUG,"As400Metric.process() is OK for %s: '%s'",req.getKeyName(), s);
                    return new DataObject(req.getUnparsedKey(), s);
                }//process()
            };//new anonymous class
        } catch (ZbxException ex) {
            Util.log(Util.LOG_ERROR,"%s",ex);
        }//try-catch

        try {
            new ZbxMetric("system.localtime", Util.CF_HAVEPARAMS) {
                public DataObject process(AgentRequest req) throws ZbxException {
                    Util.log(Util.LOG_DEBUG," As400Metric.process() started for %s",req.getKeyName());
                    AS400 system = ((ZabbixThread)Thread.currentThread()).getAs400();
                    SystemStatus ss = new SystemStatus(system);
                    String curPar = null;
                    int N = req.getNparam();
                    try {
                        if ( N<1 || "".equals(curPar = req.getParam(0)) || "utc".equals(curPar) ) {
                            long ret = ss.getDateAndTimeStatusGathered().getTime() / 1000 +
                                       ss.getElapsedTime();
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
                    } catch (AS400SecurityException|ErrorCompletingRequestException|ObjectDoesNotExistException|IOException|InterruptedException|RequestNotSupportedException ex) {
                        Util.log(Util.LOG_WARNING," As400Metric.process() error: %s", ex);
                        throw new ZbxException(ex.getMessage());
                    } finally {
                        Util.log(Util.LOG_DEBUG,"As400Metric.process() is OK for %s",req.getKeyName());
                    }//try-catch
                }//process()
            };//new anonymous class
        } catch (ZbxException ex) {
            Util.log(Util.LOG_ERROR,"%s",ex);
        }//try-catch

        try {
            new ZbxMetric("system.cpu.num", Util.CF_HAVEPARAMS) {
                public DataObject process(AgentRequest req) throws ZbxException {
                    Util.log(Util.LOG_DEBUG," As400Metric.process() started for %s",req.getKeyName());
                    AS400 system = ((ZabbixThread)Thread.currentThread()).getAs400();
                    SystemStatus ss = new SystemStatus(system);
                    String curPar = null;
                    int N = req.getNparam();
                    try {
                        if ( N<1 || "".equals(curPar = req.getParam(0)) || "online".equals(curPar) ||
                            "max".equals(curPar) ) {
                            int ret = ss.getNumberOfProcessors();
                            return new DataObject(req.getUnparsedKey(), new Integer(ret));
                        } else {
                            throw new ZbxException("Invalid parameter: '" + curPar +"'");
                        }//if-else
                    } catch (AS400SecurityException|ErrorCompletingRequestException|ObjectDoesNotExistException|IOException|InterruptedException ex) {
                        Util.log(Util.LOG_WARNING," As400Metric.process() error: %s", ex);
                        throw new ZbxException(ex.getMessage());
                    } finally {
                        Util.log(Util.LOG_DEBUG,"As400Metric.process() is OK for %s",req.getKeyName());
                    }//try-catch
                }//process()
            };//new anonymous class
        } catch (ZbxException ex) {
            Util.log(Util.LOG_ERROR,"%s",ex);
        }//try-catch

        try {
            new ZbxMetric("system.users.num", 0) {
                public DataObject process(AgentRequest req) throws ZbxException {
                    Util.log(Util.LOG_DEBUG," As400Metric.process() started for %s",req.getKeyName());
                    AS400 system = ((ZabbixThread)Thread.currentThread()).getAs400();
                    try {
                        int ret = new SystemStatus(system).getUsersCurrentSignedOn();
                        return new DataObject(req.getUnparsedKey(), new Integer(ret));
                    } catch (AS400SecurityException|ErrorCompletingRequestException|ObjectDoesNotExistException|IOException|InterruptedException ex) {
                        Util.log(Util.LOG_WARNING," As400Metric.process() error: %s", ex);
                        throw new ZbxException(ex.getMessage());
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
            new ZbxMetric("proc.num", Util.CF_HAVEPARAMS) {
                public DataObject process(AgentRequest req) throws ZbxException {
                    int N = req.getNparam(), ret = 0;
                    if (N > 4)
                        throw new ZbxException("Bad request: maximum 4 parameters supported");
                    AS400 system = ((ZabbixThread)Thread.currentThread()).getAs400();
                    Util.log(Util.LOG_DEBUG," As400Metric.process() started for %s",req.getKeyName());
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
                            //curPar = null;
                            ret = jl.getLength();
                        } else {
                            ZbxRegexp regex = new ZbxRegexp(curPar, false);
                            jl.addJobAttributeToRetrieve(Job.SUBSYSTEM);
                            Enumeration jobs = jl.getJobs();
                            while (jobs.hasMoreElements()) {
                                Job job = (Job)jobs.nextElement();
                                String subsystem = job.getSubsystem();
                                int i = subsystem.lastIndexOf('/') + 1;
                                int j = subsystem.length() - (subsystem.endsWith(".SBSD") ? 5 : 0);
                                subsystem = subsystem.substring(i, j);
                                if ( regex.matches(subsystem) ) {
                                    Util.log(Util.LOG_DEBUG,"   processed subsystem '%s' matched with '%s'", subsystem, regex);
                                    ret++;
                                }
                            }//while
                        }//if (subsystem defined)
                    } catch (PropertyVetoException|InterruptedException|IOException|AS400SecurityException|ErrorCompletingRequestException|ObjectDoesNotExistException ex) {
                        Util.log(Util.LOG_WARNING," As400Metric.process() error: %s", ex);
                        throw new ZbxException(ex.getMessage());
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
            new ZbxMetric("as400.subsystem", Util.CF_HAVEPARAMS) {
                public DataObject process(AgentRequest req) throws ZbxException {
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
                    } catch (InterruptedException|IOException|AS400SecurityException|ErrorCompletingRequestException|ObjectDoesNotExistException ex) {
                        Util.log(Util.LOG_WARNING," As400Metric.process() error: %s", ex);
                        throw new ZbxException(ex.getMessage());
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
            new ZbxMetric("system.run", Util.CF_HAVEPARAMS) {
                public DataObject process(AgentRequest req) throws ZbxException {
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
                    } catch (InterruptedException|IOException|PropertyVetoException|AS400SecurityException|ErrorCompletingRequestException|ObjectDoesNotExistException ex) {
                        Util.log(Util.LOG_WARNING," As400Metric.process() error: %s", ex);
                        throw new ZbxException(ex.getMessage());
                    }//try-catch
                    return new DataObject(req.getUnparsedKey(), s.toString());
                }//process()
            };//new anonymous class
        } catch (ZbxException ex) {
            Util.log(Util.LOG_ERROR,"%s",ex);
        }//try-catch
*/
        try {
            new ZbxMetric("as400.outputqueue.size", Util.CF_HAVEPARAMS) {
                public DataObject process(AgentRequest req) throws ZbxException {
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
                    } catch (PropertyVetoException|InterruptedException|IOException|AS400SecurityException|ErrorCompletingRequestException|RequestNotSupportedException ex) {
                        Util.log(Util.LOG_WARNING," As400Metric.process() error: %s", ex);
                        throw new ZbxException(ex.getMessage());
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
            new ZbxMetric("as400.services", Util.CF_HAVEPARAMS) {
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
/*
        try {
            new ZbxMetric("as400.ASP1", Util.CF_HAVEPARAMS) {
                public DataObject process(AgentRequest req) throws ZbxException {
                    String param = null;
                    if (0 == req.getNparam() || "".equals(param = req.getParam(0)))
                        param = "total";
                    AS400 system = ((ZabbixThread)Thread.currentThread()).getAs400();

                    try {
                        switch (param) {
                        case "total":
                            return new DataObject(req.getUnparsedKey(), new Long(new SystemStatus(system).getSystemASP() * 1000000l));
                        case "pused":
                            return new DataObject(req.getUnparsedKey(), new Float(new SystemStatus(system).getPercentSystemASPUsed()));
                        default:
                            throw new ZbxException("Invalid parameter '" + param + "'");
                        }//switch-case
                    } catch (AS400SecurityException|ErrorCompletingRequestException|ObjectDoesNotExistException|IOException|InterruptedException ex) {
                        Util.log(Util.LOG_WARNING," As400Metric.process() error: %s", ex);
                        throw new ZbxException(ex.getMessage());
                    }//try-catch-finally

                }//process()
            };//new anonymous class
        } catch (ZbxException ex) {
            Util.log(Util.LOG_ERROR,"%s",ex);
        }//try-catch
*/
        try {
            new ZbxMetric("vfs.fs.discovery", 0) {
                public DataObject process(AgentRequest req) throws ZbxException {
//                    AS400 system = ((ZabbixThread)Thread.currentThread()).getAs400();
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
            new ZbxMetric("vfs.fs.size", Util.CF_HAVEPARAMS) {
                public DataObject process(AgentRequest req) throws ZbxException {
                    String fs = null, mode = null;
                    if (1 > req.getNparam() || "".equals(fs = req.getParam(0)))
                        throw new ZbxException("Bad request: parameters FS needed");
                    if (2 > req.getNparam() || "".equals(mode = req.getParam(1)))
                        mode = "total";
//                    AS400 system = ((ZabbixThread)Thread.currentThread()).getAs400();
                    Util.log(Util.LOG_DEBUG," As400Metric.process() started for %s", req.getKeyName());
                    try {
                        return new DataObject(req.getUnparsedKey(),
                            QYASPOL.process_asp(fs, mode));
                    } finally {
                        Util.log(Util.LOG_DEBUG," As400Metric.process() ended %s", req.getKeyName());
                    }
                }//process()
            };//new anonymous class
        } catch (ZbxException ex) {
            Util.log(Util.LOG_ERROR,"%s",ex);
        }//try-catch

        try {
            new ZbxMetric("as400.disk.discovery", 0) {
                public DataObject process(AgentRequest req) throws ZbxException {
//                    AS400 system = ((ZabbixThread)Thread.currentThread()).getAs400();
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
            new ZbxMetric("as400.disk.size", Util.CF_HAVEPARAMS) {
                public DataObject process(AgentRequest req) throws ZbxException {
                    String fs = null, mode = null;
                    if (1 > req.getNparam() || "".equals(fs = req.getParam(0)))
                        throw new ZbxException("Bad request: parameters FS needed");
                    if (2 > req.getNparam() || "".equals(mode = req.getParam(1)))
                        mode = "total";
                    Util.log(Util.LOG_DEBUG," As400Metric.process() started for %s", req.getKeyName());
//                    AS400 system = ((ZabbixThread)Thread.currentThread()).getAs400();
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
            new ZbxMetric("as400.disk.state", Util.CF_HAVEPARAMS) {
                public DataObject process(AgentRequest req) throws ZbxException {
                    String fs = null;
                    if (1 > req.getNparam() || "".equals(fs = req.getParam(0)))
                        throw new ZbxException("Bad request: parameters FS needed");
                    Util.log(Util.LOG_DEBUG," As400Metric.process() started for %s", req.getKeyName());
//                    AS400 system = ((ZabbixThread)Thread.currentThread()).getAs400();
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

    }//init()

}//class As400Metrics
