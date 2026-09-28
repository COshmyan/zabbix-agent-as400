package as400.thread;
import as400.*;
import as400.cache.ZbxCacheControllerThread;
import as400.metric.*;
import com.ibm.as400.access.*;
import java.io.IOException;

public class ZabbixAgent extends ZabbixThread {

    //static class variables
    private static String configFile = "zabbix_agentd.conf";

    public ZabbixAgent() {
        super();
        setName("ZabbixAgent config");
    }//constructor ZabbixAgent()

    public static DataObject process(AgentRequest req) throws ZbxException {
        DataObject ret;
        Util.log(Util.LOG_DEBUG, "in ZabbixAgent.process(): key_name='%s', full key='%s'",
                req.getKeyName(), req.getUnparsedKey());
        ZbxMetric command = Config.commands.get(req.getKeyName());
        if (null == command)
            throw new ZbxException("Unsupported item key name: " + req.getKeyName());
        try {
            ret = command.process(req);
        } catch (IOException ex) {
            //try to reconnect to AS/400
            if (!((ZabbixThread)Thread.currentThread()).isAs400CommError()) {
                Util.log(Util.LOG_WARNING, " ZabbixAgent.process(): '%s' communication error: %s, trying to reconnect...",
                        req.getUnparsedKey(), ex);
            }//if(it is the first communication error)
            AS400 system = ((ZabbixThread)Thread.currentThread()).getAs400();
            if (system.isConnected())
                system.disconnectAllServices();
            try {
                ret = command.process(req);
            } catch (IOException exi) {
                if (!((ZabbixThread)Thread.currentThread()).isAs400CommError()) {
                    Util.log(Util.LOG_WARNING,"  ZabbixAgent.process() '%s' communication error: %s", req.getUnparsedKey(), exi);
                }//if(it is the first communication error)
                throw new ZbxException(exi.toString());
            } finally {
                ((ZabbixThread)Thread.currentThread()).setAs400CommError(true);
            }//try-catch
        } catch (Throwable ex) {
            Util.log(Util.LOG_ERROR, "Error in ZabbixAgent.process(): %s", ex);
            throw ex;
        } finally {
            Util.log(Util.LOG_DEBUG, "end of ZabbixAgent.process()");
        }//try-catch-finally

        if (((ZabbixThread)Thread.currentThread()).isAs400CommError() && !(req.getKeyName().startsWith("agent."))) {
            Util.log(Util.LOG_WARNING," ZabbixAgent.process() '%s' communication to AS/400 is working again", req.getUnparsedKey());
            ((ZabbixThread)Thread.currentThread()).setAs400CommError(false);
        }//if
        return ret;
    }//process()

    public static void main(String[] args) throws Exception {
        //process parameters
        if (args.length>0) {
	        configFile = args[0];
        }

        ZabbixThread t = new ZabbixAgent();
        t.start();
        try { t.join(10000); } catch (InterruptedException ex) { ; }
        ZabbixThread cc = new ZbxCacheControllerThread();
        cc.start();
        ZabbixThread collector = new CollectorThread();
        collector.system = t.getAs400();    //reuse AS/400 connections from the completed configuration thread
        collector.start();

        try {
            String []ServerActive = Config.getServerActive();
            if (null != ServerActive) {
                for (int i = 0; i < ServerActive.length; i++) {
                    t = new ActiveCheck(ServerActive[i]);
                    t.start();
                }//for(ServerActive)
            }//if (ServerActive)
            PassiveCheck.init();
            collector.interrupt();
            try { collector.join(500); } catch (InterruptedException ex) { ; }
            cc.interrupt();
            try { cc.join(500); } catch (InterruptedException ex) { ; }
        } catch (Throwable ex) {
	        Util.log(Util.LOG_CRITICAL, "ERROR: %s\n\tStack Trace:", ex);
                ex.printStackTrace(Util.getPrintWriter());
        } finally {
            Config.running = false;
            Util.log(Util.LOG_WARNING, "Zabbix Agent stopped. v%s", GenericMetrics.VERSION);
        }
    }//main()

    public void run() {

        if (!Config.parseConfig(configFile)) {
            Util.log(Util.LOG_CRITICAL,"Could not process config file '%s', exiting\n", configFile);
            Util.flushLogAndExit();
        }

        Util.log(Util.LOG_WARNING, "Starting Zabbix Agent v%s", GenericMetrics.VERSION);
        Util.log(Util.LOG_WARNING, "using configuration file: %s", configFile);
        Util.log(Util.LOG_DEBUG, "%s #%d started [%s]", "agent", server_num, Thread.currentThread().getName());
        Util.log(Util.LOG_WARNING, "%s Java version \"%s\"",
                            System.getProperty("java.vendor",  "unknown"),
                            System.getProperty("java.version", "unknown"));
        Util.log(Util.LOG_WARNING, " %s (build %s)",
                            System.getProperty("java.runtime.name",    "unknown"),
                            System.getProperty("java.runtime.version", "unknown"));
        Util.log(Util.LOG_WARNING, " %s (build %s, %s)",
                            System.getProperty("java.vm.name",    "unknown"),
                            System.getProperty("java.vm.version", "unknown"),
                            System.getProperty("java.vm.info",    "unknown"));
        Util.log(Util.LOG_WARNING, " %s", Copyright.version);

        try {
            Class.forName("com.ibm.as400.access.AS400");
            Class.forName("org.json.simple.JSONValue");
        } catch (ClassNotFoundException ex) {
            String library = "<unknown>", message = ex.getMessage();
            if (null != message)
                if (message.contains("as400"))
                    library = "jt400.jar";
                else if (message.contains("json"))
                    library = "json-simple-<version>.jar";
            Util.log(Util.LOG_CRITICAL, "The library '%s' is not available, exiting:\n%s", library, ex);
            Util.flushLogAndExit();
        }//try-catch(ClassNotFoundException)

        GenericMetrics.init();
        As400Metrics.init();

        ((ZabbixThread)Thread.currentThread()).system = new com.ibm.as400.access.AS400(Config.getAs400ServerHost());

        if (!Config.setDefaultsAndValidate(configFile)) {
            Util.log(Util.LOG_CRITICAL,"Could not validate config file '%s', exiting\n", configFile);
            Util.flushLogAndExit();
        }

        try {
            Util.log(Util.LOG_WARNING, " Agent hostname: '%s', System info: %s", Config.getHostname(),
                    ZabbixAgent.process(new AgentRequest("system.uname")).getValue().toString());
        } catch (ZbxException ex) {
            Util.log(Util.LOG_CRITICAL, "Error obtaining 'system.uname' metric, exiting\n");
            Util.flushLogAndExit();
        }//try-catch

        if (system.isConnected())
            system.disconnectAllServices();

        Util.log(Util.LOG_DEBUG, "%s #%d stopped [%s]", "agent", server_num, Thread.currentThread().getName());
    }//run()

}//class ZabbixAgent