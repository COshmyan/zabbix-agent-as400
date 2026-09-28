package as400.metric;
import as400.*;
import as400.perfstat.*;
import as400.thread.As400Thread;
import com.ibm.as400.access.*;
import com.ibm.as400.data.*;
import java.io.*;
import java.util.Enumeration;
import java.beans.PropertyVetoException;

public class As400Metrics {

    static class As400Result extends Thread {
        AS400 system;
        int i;
        boolean result;

        public As400Result(AS400 system, int i) {
            this.system = system;
            this.i = i;
            this.result = false;
        }//Constructor

        //run method override
        public void run() {
            Util.log(Util.LOG_DEBUG, "   Child thread is started for service %d", this.i);
            this.result = this.system.isConnectionAlive(this.i);
            Util.log(Util.LOG_DEBUG, "   Child thread is finished, result is: %b", this.result);
        }//run()

    }//internal static class

    //class static final variables
    static final int WAIT   = 1;
    static final int NOWAIT = 0;

    public As400Metrics() {
        //system = new AS400(as400ServerHost, Config.getUser(), asPassword);
    }//constructor As400Metrics()

    public static void init() {

        //initialization of anonymous classes for generic agent metrics
        try {
            new ZbxMetric("system.hostname", Util.CF_AS400COMM | Util.CF_HAVEPARAMS) {
                public DataObject process(AgentRequest req) throws ZbxException, IOException {
                    if (null == req)
                        throw new ZbxException("Bad request");

                    AS400 system = ((As400Thread)Thread.currentThread()).getAs400();
                    String tmp, type, s;
                    int N = req.getNparam();
                    //1-st parameter: <type>
                    type = N<1 ? "" : req.getParam(0);
                    switch (type) {
                        case "":
                            type = "host";
                            break;
                        case "host":
                        case "shorthost":
                        case "fqdn":
                            break;
                        default:
                            throw new ZbxException("Invalid <type> parameter: '" + type + "'");
                    }//switch-case
                    //2-st parameter: <conversion>
                    tmp = N<2 ? "" : req.getParam(1);
                    switch (tmp) {
                        case "":
                        case "none":
                        case "lower":
                            break;
                        default:
                            throw new ZbxException("Invalid second parameter: '" + tmp + "'");
                    }//switch-case

                    try {
                        s = new SystemStatus(system).getSystemName();
                    } catch (AS400SecurityException|ErrorCompletingRequestException|ObjectDoesNotExistException|InterruptedException ex) {
                        Util.log(Util.LOG_WARNING," As400Metric.process(%s) error: %s", req.getUnparsedKey(), ex);
                        throw new ZbxException(ex.toString());
                    }//try-catch

                    switch (type) {
                        case "shorthost":
                            N = s.indexOf('.');
                            if (0 < N)
                                s = s.substring(0, N);
                            break;
                        case "fqdn":
                            try {
                                java.net.InetAddress ia = java.net.InetAddress.getByName(s);
                                s = ia.getCanonicalHostName();
                            } catch (java.net.UnknownHostException ex) { ; }
                            break;
                        default:
                            break;
                    }//switch-case

                    if ("lower".equals(tmp))
                        s = s.toLowerCase();
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
                    AS400 system = ((As400Thread)Thread.currentThread()).getAs400();
                    String s;
                    try {
                        s = String.format("IBM OS/400 %s V%dR%dM%d, %s %s (v%s)",
                            new SystemStatus(system).getSystemName(), system.getVersion(),
                            system.getRelease(), system.getModification(),
                            System.getProperty("java.vendor",  "unknown"),
                            System.getProperty("java.vm.name", "unknown"),
                            System.getProperty("java.version", "unknown"));
                    } catch (AS400SecurityException|ErrorCompletingRequestException|ObjectDoesNotExistException|InterruptedException ex) {
                        Util.log(Util.LOG_WARNING," As400Metric.process(%s) error: %s", req.getUnparsedKey(), ex);
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
                    AS400 system = ((As400Thread)Thread.currentThread()).getAs400();
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
                        Util.log(Util.LOG_WARNING," As400Metric.process(%s) error: %s", req.getUnparsedKey(), ex);
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
            new ZbxMetric("system.uptime", Util.CF_AS400COMM) {
                public DataObject process(AgentRequest req) throws ZbxException, IOException {
                    if (null == req)
                        throw new ZbxException("Bad request");
                    AS400 system = ((As400Thread)Thread.currentThread()).getAs400();
                    Long res;
                    try {
                        Job job = new Job(system, "SCPF", "QSYS", "000000");
                        java.util.Date date = job.getJobEnterSystemDate();
                        res = (System.currentTimeMillis() - date.getTime()) / 1000;
                    } catch (AS400SecurityException|ErrorCompletingRequestException|ObjectDoesNotExistException|InterruptedException ex) {
                        Util.log(Util.LOG_WARNING," As400Metric.process(%s) error: %s", req.getUnparsedKey(), ex);
                        throw new ZbxException(ex.toString());
                    }//try-catch
                    Util.log(Util.LOG_DEBUG,"As400Metric.process() is OK for %s: '%d'",req.getKeyName(), res);
                    return new DataObject(req.getUnparsedKey(), res);
                }//process()
            };//new anonymous class
        } catch (ZbxException ex) {
            Util.log(Util.LOG_ERROR,"%s",ex);
        }//try-catch

        try {
            new ZbxMetric("system.cpu.num", Util.CF_AS400COMM | Util.CF_HAVEPARAMS) {
                public DataObject process(AgentRequest req) throws ZbxException, IOException {
                    Util.log(Util.LOG_DEBUG," As400Metric.process() started for %s",req.getKeyName());
                    AS400 system = ((As400Thread)Thread.currentThread()).getAs400();
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
                        Util.log(Util.LOG_WARNING," As400Metric.process(%s) error: %s", req.getUnparsedKey(), ex);
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
                    AS400 system = ((As400Thread)Thread.currentThread()).getAs400();
                    String curPar = null;
                    try {
                        SystemStatus ss = new SystemStatus(system);
                        ss.refreshCache();
                        float ret = ss.getCurrentProcessingCapacity();
                        return new DataObject(req.getUnparsedKey(), new Float(ret));
                    } catch (AS400SecurityException|ErrorCompletingRequestException|ObjectDoesNotExistException|InterruptedException ex) {
                        Util.log(Util.LOG_WARNING," As400Metric.process(%s) error: %s", req.getUnparsedKey(), ex);
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
                    AS400 system = ((As400Thread)Thread.currentThread()).getAs400();
                    try {
                        SystemStatus ss = new SystemStatus(system);
                        ss.refreshCache();
                        int ret = ss.getUsersCurrentSignedOn();
                        return new DataObject(req.getUnparsedKey(), new Integer(ret));
                    } catch (AS400SecurityException|ErrorCompletingRequestException|ObjectDoesNotExistException|InterruptedException ex) {
                        Util.log(Util.LOG_WARNING," As400Metric.process(%s) error: %s", req.getUnparsedKey(), ex);
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
                    AS400 system = ((As400Thread)Thread.currentThread()).getAs400();
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
                            @SuppressWarnings("unchecked") Enumeration<Job> jobs = (Enumeration<Job>)jl.getJobs();
                            while (jobs.hasMoreElements()) {
                                Job job = null;
                                String subsystem = null;
                                //in rare cases some job is disappeared during the job list processing, just ignore such job
                                try {
                                    job = jobs.nextElement();
                                    subsystem = job.getSubsystem();
                                } catch (ErrorCompletingRequestException|ObjectDoesNotExistException|java.util.NoSuchElementException ex) { }
                                if (null != subsystem) {
                                    int i = subsystem.lastIndexOf('/') + 1;
                                    int j = subsystem.length() - (subsystem.endsWith(".SBSD") ? 5 : 0);
                                    subsystem = subsystem.substring(i, j);
                                    if ( regex.matches(subsystem) ) {
                                        Util.log(Util.LOG_TRACE1,"   processed subsystem '%s' matched with '%s'", subsystem, regex);
                                        ret++;
                                    }//if(matches)
                                }//if(subsystem!=null)
                            }//while
                        }//if (subsystem defined)
                    } catch (PropertyVetoException|InterruptedException|AS400SecurityException|ErrorCompletingRequestException|ObjectDoesNotExistException ex) {
                        Util.log(Util.LOG_WARNING," As400Metric.process(%s) error: %s", req.getUnparsedKey(), ex);
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
                    AS400 system = ((As400Thread)Thread.currentThread()).getAs400();
                    try {
                        Subsystem sbs = new Subsystem(system, library, subsystem);
                        if (sbs.exists()) {
                            sbs.refresh();
                            s = sbs.getStatus();
                        } else {
                            throw new ZbxException("There is no subsystem with name='"+subsystem+"' in library '"+library+"'");
                        }//if (subsystem exists)
                    } catch (InterruptedException|AS400SecurityException|ErrorCompletingRequestException|ObjectDoesNotExistException ex) {
                        Util.log(Util.LOG_WARNING," As400Metric.process(%s) error: %s", req.getUnparsedKey(), ex);
                        throw new ZbxException(ex.toString());
                    }//try-catch
                    //Possible values are: *ACTIVE, *ENDING, *INACTIVE, *RESTRICTED, and *STARTING.
                    return new DataObject(req.getUnparsedKey(), s);
                }//process()
            };//new anonymous class
        } catch (ZbxException ex) {
            Util.log(Util.LOG_ERROR,"%s",ex);
        }//try-catch

        try {
            new ZbxMetric("as400.outputqueue.size", Util.CF_AS400COMM | Util.CF_HAVEPARAMS) {
                public DataObject process(AgentRequest req) throws ZbxException, IOException {
                    String oqueue, library;
                    int ret;
                    if (2 != req.getNparam() || "".equals(oqueue = req.getParam(0)) || "".equals(library = req.getParam(1)))
                        throw new ZbxException("Bad request: 2 parameters needed: <output queue> and <library>");
                    Util.log(Util.LOG_DEBUG," As400Metric.process() started for %s",req.getKeyName());
                    AS400 system = ((As400Thread)Thread.currentThread()).getAs400();
                    SpooledFileList sfl = new SpooledFileList(system);
                    try {
                        sfl.setUserFilter("*ALL");
                        sfl.setQueueFilter("/QSYS.LIB/" + library + ".LIB/" + oqueue + ".OUTQ");
                        sfl.openSynchronously();
                        ret = sfl.size();
                    } catch (PropertyVetoException|InterruptedException|AS400SecurityException|ErrorCompletingRequestException|RequestNotSupportedException ex) {
                        Util.log(Util.LOG_WARNING," As400Metric.process(%s) error: %s", req.getUnparsedKey(), ex);
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
                    final AS400 system = ((As400Thread)Thread.currentThread()).getAs400();
                    long timeout = req.getTimeout_ms() / Integer.bitCount(services);
                    Util.log(Util.LOG_DEBUG, " timeout for a single service is: %d ms", timeout);
                    for (int i = 0, service = 1; i <= 7; i++, service <<= 1) {
                        Util.log(Util.LOG_DEBUG, " checking connection for a service %d (%d), mask=%d", i, service, services);
                        if (0 == (services & service))
                            continue;
                        try  {
                            if (!system.isConnected(i))
                                system.connectService(i);
                        } catch (IOException|AS400SecurityException ex) {
                            Util.log(Util.LOG_ERROR, "Error in As400Metric.process(%s): %s", req.getUnparsedKey(), ex);
                        }//try-catch
                        try {
                            final As400Result as400Result = new As400Result(system, i);
                            as400Result.start();
                            as400Result.join(timeout);
                            if (as400Result.isAlive()) {
                                Util.log(Util.LOG_ERROR,
                                    "  Child thread for checking \"as400System.isConnectionAlive(%d)\" was hung. Interrupting...", i);
                                as400Result.interrupt();
                                Util.log(Util.LOG_ERROR, "  Child thread was interrupted");
                                Thread.sleep(500);//give the child thread a chance to die gracefully
                                system.disconnectService(i);
                                Util.log(Util.LOG_ERROR, "  Child thread was disconnected from service %d", i);
                            }//if(isAlive)
                            if (!as400Result.result)
                                ret |= service;
                        } catch (Throwable ex) {
                            Util.log(Util.LOG_ERROR, "Error in As400Metric.process(%s): %s", req.getUnparsedKey(), ex);
                        }//try-catch
                        Util.log(Util.LOG_DEBUG, "  result is: %d", ret);
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
            new ZbxMetric("vfs.fs.get", Util.CF_AS400COMM) {
                public DataObject process(AgentRequest req) throws ZbxException, IOException {
                    Util.log(Util.LOG_DEBUG," As400Metric.process() started for %s", req.getKeyName());
                    try {
                        return new DataObject(req.getUnparsedKey(), QYASPOL.process_asp_get());
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
            new ZbxMetric("as400.disk.get", Util.CF_AS400COMM) {
                public DataObject process(AgentRequest req) throws ZbxException, IOException {
                    Util.log(Util.LOG_DEBUG," As400Metric.process() started for %s", req.getKeyName());
                    try {
                        return new DataObject(req.getUnparsedKey(), QYASPOL.process_dsk_get());
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

        try {
            new ZbxMetric("as400.systemPool.get", Util.CF_AS400COMM) {
                public DataObject process(AgentRequest req) throws ZbxException, IOException {
                    Util.log(Util.LOG_DEBUG," As400Metric.process() started for %s", req.getKeyName());
                    try {
                        return new DataObject(req.getUnparsedKey(), SystemPoolMetric.process_systemPool_get());
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
                    long ms = 0l;
                    String tmp;
                    if (1 > req.getNparam() || "".equals(tmp = req.getParam(0)))
                        throw new ZbxException("Bad request: the first parameter needed");
                    try {
                        ms = Interval.time2long(tmp);
                        if (1000l >= ms)
                            throw new ZbxException("");
                    } catch (ZbxException ex) {
                            throw new ZbxException("Invalid parameter '" + tmp + "': must be a period more than 1 second");
                    }//try-catch
                    try {
                        return new DataObject(req.getUnparsedKey(), Procstat.jobDiscovery(ms));
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
                                              jobnum, usrname, jobname, subsystem, false));
                    } finally {
                        Util.log(Util.LOG_DEBUG," As400Metric.process() ended %s", req.getKeyName());
                    }
                }//process()
            };//new anonymous class
        } catch (ZbxException ex) {
            Util.log(Util.LOG_ERROR,"%s",ex);
        }//try-catch

        try {
            new ZbxMetric("proc.cpu.util.get", Util.CF_HAVEPARAMS) {
                public DataObject process(AgentRequest req) throws ZbxException {
                    Util.log(Util.LOG_DEBUG," As400Metric.process() started for %s", req.getKeyName());
                    int N = req.getNparam(), /* seconds = 0,*/ mode = 0;
                    long ms = 0l;
                    String tmp;
                    //1-st parameter: <seconds>
                    if (1 > N || "".equals(tmp = req.getParam(0)))
                        throw new ZbxException("Bad request: the first parameter needed");
                    try {
                        ms = Interval.time2long(tmp);
                        if (1000l >= ms)
                            throw new ZbxException("");
                    } catch (NumberFormatException|ZbxException ex) {
                            throw new ZbxException("Invalid parameter '" + tmp + "': must be a period more than 1 second");
                    }//try-catch
                    //2-nd parameter: <mode>
                    tmp = 2 > N ? "" : req.getParam(1);
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
                    try {
                        return new DataObject(req.getUnparsedKey(), Procstat.jobGet(ms, mode));
                    } finally {
                        Util.log(Util.LOG_DEBUG," As400Metric.process() ended %s", req.getKeyName());
                    }
                }//process()
            };//new anonymous class
        } catch (ZbxException ex) {
            Util.log(Util.LOG_ERROR,"%s",ex);
        }//try-catch

        try {
            new ZbxMetric("as400.sql.select", Util.CF_AS400COMM | Util.CF_HAVEPARAMS) {
                public DataObject process(AgentRequest req) throws ZbxException, IOException {
                    if (null == req)
                        throw new ZbxException("Bad request");
                    AS400 system = ((As400Thread)Thread.currentThread()).getAs400();
                    String sql, res=null;
                    //1-st parameter: <SQL request>
                    if (1 > req.getNparam())
                        throw new ZbxException("Invalid request: '" + req.getUnparsedKey() +"'");
                    sql = req.getParam(0);
                    Util.log(Util.LOG_DEBUG," SQL request is: '%s'", sql);

                    try {
                        //IBM Toolbox JDBC driver
                        AS400JDBCDriver driver = new AS400JDBCDriver();
                        java.util.Properties prop = new java.util.Properties();
                        prop.setProperty("access",            "read only");
                        prop.setProperty("date format",       "iso"      );
                        prop.setProperty("time format",       "iso"      );
                        prop.setProperty("decimal separator", "."        );
                        try (
                            java.sql.Connection conn = driver.connect(system, prop, "defaultSchema");
                            java.sql.Statement st = conn.createStatement();
                        ) {
                            st.setQueryTimeout((int)(req.getTimeout_ms()/1000));//in seconds, not milliseconds
                            if (st.execute(sql)) {
                                try (java.sql.ResultSet rs = st.getResultSet()) {
                                    if (null != rs && rs.next())
                                        res = rs.getString(1); //get a single value only
                                }//try-catch
                            }//if
                        }//try-catch
                    } catch (NullPointerException|java.sql.SQLException ex) {
                        Util.log(Util.LOG_WARNING," As400Metric.process(%s) error: %s", req.getUnparsedKey(), ex);
                        throw new ZbxException(ex.toString());
                    }//try-catch
                    if (null == res)
                        res = "<NULL>";

                    Util.log(Util.LOG_DEBUG,"As400Metric.process() is OK for %s: '%s'",req.getKeyName(), res);
                    return new DataObject(req.getUnparsedKey(), res);
                }//process()
            };//new anonymous class
        } catch (ZbxException ex) {
            Util.log(Util.LOG_ERROR,"%s",ex);
        }//try-catch

        try {
            new ZbxMetric("as400.sql.get", Util.CF_AS400COMM | Util.CF_HAVEPARAMS) {
                public DataObject process(AgentRequest req) throws ZbxException, IOException {
                    if (null == req)
                        throw new ZbxException("Bad request");
                    AS400 system = ((As400Thread)Thread.currentThread()).getAs400();
                    String sql, res;
                    StringBuilder buf = new StringBuilder(""), cur_line = new StringBuilder();
                    //1-st parameter: <SQL request>
                    if (1 > req.getNparam())
                        throw new ZbxException("Invalid request: '" + req.getUnparsedKey() +"'");
                    sql = req.getParam(0);
                    Util.log(Util.LOG_DEBUG," SQL request is: '%s'", sql);

                    try {
                        //IBM Toolbox JDBC driver
                        AS400JDBCDriver driver = new AS400JDBCDriver();
                        java.util.Properties prop = new java.util.Properties();
                        prop.setProperty("access",            "read only");
                        prop.setProperty("date format",       "iso"      );
                        prop.setProperty("time format",       "iso"      );
                        prop.setProperty("decimal separator", "."        );
                        try (
                            java.sql.Connection conn = driver.connect(system, prop, "defaultSchema");
                            java.sql.Statement st = conn.createStatement();
                        ) {
                            st.setQueryTimeout((int)(req.getTimeout_ms()/1000));//in seconds, not in milliseconds
                            if (st.execute(sql)) {
                                try (java.sql.ResultSet rs = st.getResultSet()) {
                                    if (null != rs) {
                                        java.sql.ResultSetMetaData rsmd = rs.getMetaData();
                                        while (rs.next()) {//loop by rows
                                            cur_line.setLength(0);
                                            //loop by fields in a single row
                                            for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                                                String cur_value = rs.getString(i);
                                                if (null == cur_value)
                                                    cur_value = "";

                                                cur_line.append("\n  ");
                                                cur_line.append(org.json.simple.JSONValue.toJSONString(rsmd.getColumnLabel(i)));
                                                cur_line.append(": ");
                                                cur_line.append(org.json.simple.JSONValue.toJSONString(cur_value));
                                                cur_line.append(",");
                                            }//for(line)
                                            if (0 < cur_line.length()) //remove the final comma
                                                cur_line.setLength(cur_line.length() - 1);
                                            buf.append("\n {").append(cur_line).append("\n },");
                                        }//while(rs)
                                    }//if(rs!=null)
                                }//try-catch, autoclose rs
                            }//if
                        }//try-catch, autoclose st an conn
                    } catch (NullPointerException|java.sql.SQLException ex) {
                        Util.log(Util.LOG_WARNING," As400Metric.process(%s) error: %s", req.getUnparsedKey(), ex);
                        throw new ZbxException(ex.toString());
                    }//try-catch
                    if (0 < buf.length())
                        buf.setLength(buf.length() - 1); //remove the final comma
                    res = buf.insert(0, "[").append("\n]\n").toString();

                    Util.log(Util.LOG_DEBUG,"As400Metric.process() is OK for %s: '%s'", req.getKeyName(), res);
                    return new DataObject(req.getUnparsedKey(), res);
                }//process()
            };//new anonymous class
        } catch (ZbxException ex) {
            Util.log(Util.LOG_ERROR,"%s",ex);
        }//try-catch

        /*
         * The only analog for "system.run" metric in as400 that we could find is the QSH call.
         * The QShell has concept of "stdout" and "stderr", but the only way to get them is to
         * redirect these streams to (temporary) file and then to read this file.
         * Therefore, we are calling the QSH, forming its command line to redirect both stdout and stderr,
         * and then read it.
         * Using "system" command, it's possible to call from QShell some CL commands, too.
         */
        try {
            new ZbxMetric("system.run", Util.CF_AS400COMM | Util.CF_HAVEPARAMS) {
                public DataObject process(AgentRequest req) throws ZbxException, IOException {
                    int N = req.getNparam(), mode;
                    String cmd, res, tmp;
                    //1-st parameter: <command>
                    cmd = N<1 ? "" : req.getParam(0);
                    //2-st parameter: <mode>
                    tmp = N<2 ? "" : req.getParam(1);
                    switch (tmp) {
                        case "":
                        case "wait":
                            mode = WAIT;
                            break;
                        case "nowait" :
                            mode = NOWAIT;
                            break;
                        default:
                            throw new ZbxException("Invalid <mode> parameter: '" + tmp + "'");
                    }//switch-case

                    if ("".equals(cmd))
                        throw new ZbxException("Bad request: command needed");
                    if (2 < N)
                        throw new ZbxException("Bad request: too many parameters");
                    Util.log(Util.LOG_DEBUG," As400Metric.process() started for %s",req.getKeyName());
                    res = executeStr(cmd, mode);
                    Util.log(Util.LOG_DEBUG, " As400Metric.process() ended OK for metric '%s'", req.getUnparsedKey());
                    return new DataObject(req.getUnparsedKey(), res);
                }//process()
            };//new anonymous class
        } catch (ZbxException ex) {
            Util.log(Util.LOG_ERROR,"%s",ex);
        }//try-catch

    }//init()

    public static String executeStr(String cmd, int mode) throws ZbxException, IOException {
        String output;
        StringBuilder buf = new StringBuilder();
        int i = 0, j, ccsid;
        boolean res = false;
        IFSFile output_file = null;
        Util.log(Util.LOG_DEBUG, "  executeStr() started for \"%s\"", cmd);

        //we can not ensure absolute uniqueness of this filename; but we can, at least, to lower its probability
        output = String.format("/tmp/qsh-output-%X.txt", Util.currentTimeMillis());
        buf.append("QSH CMD('{ ");
        //loop to double all apostrophes (if any) in cmd, as it is necessary for correct interpretation by QShell
        while ( (j = cmd.indexOf('\'', i)) >= 0) {
            buf.append(cmd.substring(i, ++j));
            buf.append('\'');
            i = j;
        }//while
        buf.append(cmd.substring(i));
        buf.append("; } >");
        buf.append(output);
        buf.append(" 2>&1')");
        cmd = buf.toString();
        buf.setLength(0);
        j = Config.getLogRemoteCommands() ? Util.LOG_WARNING : Util.LOG_DEBUG;
        Util.log(j, "  executing the CommandCall: \"%s\"", cmd);

        AS400 system = ((As400Thread)Thread.currentThread()).getAs400();
        output_file = new IFSFile(system, output);
        try {
            CommandCall command = new CommandCall(system);
            res = command.run(cmd);
            if ( !res ) {
                AS400Message[] messagelist = command.getMessageList();
                for (i = 0; i < messagelist.length; ++i) {
                    buf.append(messagelist[i].getID());
                    buf.append(" - ");
                    buf.append(messagelist[i].getText());
                    buf.append('\n');
                }//for
                throw new ZbxException("Error running CL command '" + cmd + "': " + buf.toString());
            }//if (not success)
            Util.log(Util.LOG_DEBUG, "  CommandCall executed OK");

            ccsid = output_file.getCCSID();
            Util.log(Util.LOG_DEBUG, "  '%s' does exist: %b, is file: %b, CCSID=%d", output, output_file.exists(), output_file.isFile(), output_file.getCCSID());
            if (!output_file.exists() || !output_file.isFile()) {
                JobLog joblog = command.getServerJob().getJobLog();
                joblog.load();
                for (Enumeration e = joblog.getMessages(); e.hasMoreElements(); ) {
                    QueuedMessage msg = (QueuedMessage)e.nextElement();
                    buf.append(msg.getID());
                    buf.append(": ");
                    buf.append(msg.getText());
                    buf.append('\n');
                }//for
                joblog.close();
                    throw new ZbxException("There is no output file; probably, CommandCall was unsuccessful. JobLog is:\n" + buf.toString());
            }
            if (0 > ccsid || 65535 == ccsid)
                throw new java.io.UnsupportedEncodingException("Text encoding of output is undefined, CCSID=" + ccsid);

            //reading from the output file
            try (
                IFSFileInputStream in = new IFSFileInputStream(system, output);
                BufferedReader br = new BufferedReader(new ConvTableReader(in, ccsid));
            ) {
                String line;
                while ((line = br.readLine()) != null) {
                    buf.append(line).append('\n');
                }//while
            }//autoclose IFSFileInputStream & BufferedReader
            Util.log(Util.LOG_DEBUG, " executeStr() ended OK for command '%s', result is:\n%s", cmd, buf.toString());
        } catch (InterruptedException|PropertyVetoException|AS400SecurityException|ErrorCompletingRequestException|ObjectDoesNotExistException ex) {
            if ( ! (ex instanceof InterruptedException) )
                Util.log(Util.LOG_WARNING,"  executeStr(%s) error: %s", cmd, ex);
            throw new ZbxException(ex.toString());
        } finally {
            if (null != output_file && output_file.exists() && output_file.isFile()) {
                try {
                    res = output_file.delete();
                    Util.log(Util.LOG_DEBUG, "  output file '%s' deleted: %b", output, res);
                } catch (IOException ex) {
                    Util.log(Util.LOG_WARNING, "  Error during deleting output file '%s': %s", output, ex.toString());
                }//try-catch(IOException)
            }//if
        }//try-catch-finally

        //trim whitespaces from the end
        for (i = buf.length() - 1; i >= 0; i--) {
            char c = buf.charAt(i);
            if (Character.isWhitespace(c))
                buf.setLength(i);
            else
                break;
        }
        return buf.toString();
    }//executeStr()

}//class As400Metrics
