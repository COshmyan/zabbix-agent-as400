package as400.metric;
import as400.*;

public class GenericMetrics {

    //constants
    public static final String VERSION = "0.7.7";

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

    }//init()

}//class GenericMetrics
