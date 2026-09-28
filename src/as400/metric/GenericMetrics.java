package as400.metric;
import as400.*;

public class GenericMetrics {

    //constants
    public static final String VERSION = "0.8.0-beta3";
    public static final String VARIANT = "1";

    static class ActiveOnlyMetric extends ZbxMetric {

        ActiveOnlyMetric(String key_name, int flags) throws ZbxException {
            super(key_name, flags);
        }//constructor ActiveOnlyMetric()

        public DataObject process(AgentRequest req) throws ZbxException {
            throw new ZbxException("Key " + this.key_name + " could be used in active mode only.");
        }//process()

    }//class ActiveOnlyMetric

    public static void init() {

        //initialization of stubs for active-mode-only metrics
        try {
            new ActiveOnlyMetric("log", 0);
        } catch (ZbxException ex) {
            Util.log(Util.LOG_ERROR,"%s",ex);
        }//try-catch

        try {
            new ActiveOnlyMetric("logrt", 0);
        } catch (ZbxException ex) {
            Util.log(Util.LOG_ERROR,"%s",ex);
        }//try-catch

        try {
            new ActiveOnlyMetric("eventlog", 0);
        } catch (ZbxException ex) {
            Util.log(Util.LOG_ERROR,"%s",ex);
        }//try-catch

        //initialization of anonymous classes for generic agent metrics
        try {
            new ZbxMetric("agent.exit", 0) {
                public DataObject process(AgentRequest req) throws ZbxException {
                    if (null == req)
                        throw new ZbxException("Bad request");
                    Util.log(Util.LOG_DEBUG,"GenericMetric.process() is OK for %s",req.getKeyName());
                    Config.running = false;
                    return new DataObject(req.getUnparsedKey(), "OK");
                }//process()
            };//new anonymous class
        } catch (ZbxException ex) {
            Util.log(Util.LOG_ERROR,"%s",ex);
        }//try-catch

        try {
            new ZbxMetric("agent.hostname", 0) {
                public DataObject process(AgentRequest req) throws ZbxException {
                    if (null == req)
                        throw new ZbxException("Bad request");
                    Util.log(Util.LOG_DEBUG,"GenericMetric.process() is OK for %s",req.getKeyName());
                    return new DataObject(req.getUnparsedKey(), Config.getHostname());
                }//process()
            };//new anonymous class
        } catch (ZbxException ex) {
            Util.log(Util.LOG_ERROR,"%s",ex);
        }//try-catch

        try {
            new ZbxMetric("agent.hostmetadata", 0) {
                public DataObject process(AgentRequest req) throws ZbxException {
                    if (null == req)
                        throw new ZbxException("Bad request");
                    Util.log(Util.LOG_DEBUG,"GenericMetric.process() is OK for %s",req.getKeyName());
                    String str = Config.getHostMetadata();
                    return new DataObject(req.getUnparsedKey(), (null == str) ? "" : str);
                }//process()
            };//new anonymous class
        } catch (ZbxException ex) {
            Util.log(Util.LOG_ERROR,"%s",ex);
        }//try-catch

        try {
            new ZbxMetric("agent.ping", 0) {
                public DataObject process(AgentRequest req) throws ZbxException {
                    if (null == req)
                        throw new ZbxException("Bad request");
                    Util.log(Util.LOG_DEBUG,"GenericMetric.process() is OK for %s",req.getKeyName());
                    return new DataObject(req.getUnparsedKey(), new Integer(1));
                }//process()
            };//new anonymous class
        } catch (ZbxException ex) {
            Util.log(Util.LOG_ERROR,"%s",ex);
        }//try-catch

        try {
            new ZbxMetric("agent.version", 0) {
                public DataObject process(AgentRequest req) throws ZbxException {
                    if (null == req)
                        throw new ZbxException("Bad request");
                    Util.log(Util.LOG_DEBUG,"GenericMetric.process() is OK for %s",req.getKeyName());
                    return new DataObject(req.getUnparsedKey(), VERSION);
                }//process()
            };//new anonymous class
        } catch (ZbxException ex) {
            Util.log(Util.LOG_ERROR,"%s",ex);
        }//try-catch

        try {
            new ZbxMetric("agent.variant", 0) {
                public DataObject process(AgentRequest req) throws ZbxException {
                    if (null == req)
                        throw new ZbxException("Bad request");
                    Util.log(Util.LOG_DEBUG,"GenericMetric.process() is OK for %s",req.getKeyName());
                    return new DataObject(req.getUnparsedKey(), VARIANT);
                }//process()
            };//new anonymous class
        } catch (ZbxException ex) {
            Util.log(Util.LOG_ERROR,"%s",ex);
        }//try-catch

        try {
            new ZbxMetric("agent.debug.active.thread", 0) {
                public DataObject process(AgentRequest req) throws ZbxException {
                    if (null == req)
                        throw new ZbxException("Bad request");
                    StringBuilder buf = new StringBuilder();
                    as400.thread.ActiveCheck []activeChecks = Config.getActiveChecks();
                    for (int i = 0; i < activeChecks.length; i++) {
                        as400.thread.ActiveCheck t = activeChecks[i];
                        buf.append(t.toString());
                        buf.append("\nisAlive=");
                        buf.append(t.isAlive());
                        buf.append("\nState=");
                        buf.append(t.getState().toString());
                        buf.append("\nActiveCount=");
                        buf.append(Thread.activeCount());
                        buf.append("\nStackTrace:\n");
                        StackTraceElement []st = t.getStackTrace();
                        for (int j = 0; j < st.length; j++) {
                            buf.append(st[j].toString());
                            buf.append("\n");
                        }
                        buf.append("----\n");
                    }//for(ServerActive)
                    Runtime rt = Runtime.getRuntime();
                    buf.append("Memory (max/total/free): ");
                    buf.append(rt.maxMemory());
                    buf.append('/');
                    buf.append(rt.totalMemory());
                    buf.append('/');
                    buf.append(rt.freeMemory());
                    buf.append("\n----\n");
                    Util.log(Util.LOG_DEBUG,"GenericMetric.process() is OK for %s",req.getKeyName());
                    return new DataObject(req.getUnparsedKey(), buf.toString());
                }//process()
            };//new anonymous class
        } catch (ZbxException ex) {
            Util.log(Util.LOG_ERROR,"%s",ex);
        }//try-catch
/*
        try {
            new ZbxMetric("agent.echo", Util.CF_HAVEPARAMS) {
                public DataObject process(AgentRequest req) throws ZbxException {
                    if (null == req)
                        throw new ZbxException("Bad request");
                    int N = req.getNparam();
                    //1-st parameter: <type>
                    String param1 = N<1 ? "" : req.getParam(0);
                    DataObject rez = new DataObject(req.getUnparsedKey(), param1);
                    if ("".equals(param1)) {
                        rez.setValue("There is no parameter, not supported result");
                        rez.setStateNotsupported(true);
                    }

                    Util.log(Util.LOG_DEBUG,"GenericMetric.process() is OK, notSupported=%b for %s",rez.getStateNotsupported(),req.getKeyName());
                    return rez;
                }//process()
            };//new anonymous class
        } catch (ZbxException ex) {
            Util.log(Util.LOG_ERROR,"%s",ex);
        }//try-catch
*/
    }//init()

}//class GenericMetrics
