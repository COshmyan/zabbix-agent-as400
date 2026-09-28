package as400.thread;
import as400.*;
import as400.cache.ZbxCacheControllerThread;
import as400.metric.*;
import com.ibm.as400.access.*;

public class ZabbixAgent extends ZabbixThread {

    //static class variables
    private static String configFile = "zabbix_agentd.conf";

    public ZabbixAgent() {
        super();
        setName("ZabbixAgent config");
    }//constructor ZabbixAgent()

    public static DataObject process(AgentRequest req) throws ZbxException {
        Util.log(Util.LOG_DEBUG, "in ZabbixAgent.process(): key_name='%s', full key='%s'",
                req.getKeyName(), req.getUnparsedKey());
        ZbxMetric command = Config.commands.get(req.getKeyName());
        if (null == command)
            throw new ZbxException("Unsupported item key name: " + req.getKeyName());
        Util.log(Util.LOG_DEBUG, "end of ZabbixAgent.process()");
        return command.process(req);
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

        try {
            String []ServerActive = Config.getServerActive();
            if (null != ServerActive) {
                for (int i = 0; i < ServerActive.length; i++) {
                    t = new ActiveCheck(ServerActive[i]);
                    t.start();
                }//for(ServerActive)
            }//if (ServerActive)
            PassiveCheck.init();
            cc.interrupt();
            try { cc.join(1000); } catch (InterruptedException ex) { ; }
        } catch (Exception ex) {
	        Util.log(Util.LOG_DEBUG, "Exception: %s", ex);
        } finally {
            Util.log(Util.LOG_WARNING, "Zabbix Agent stopped. v%s", GenericMetrics.VERSION);
        }
    }//main()

    public void run() {

        if (!Config.parseConfig(configFile)) {
            System.err.printf("Could not process config file '%s', exiting\n", configFile);
            System.exit(1);
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
        GenericMetrics.init();
        As400Metrics.init();

        ((ZabbixThread)Thread.currentThread()).system = new com.ibm.as400.access.AS400(Config.getAs400ServerHost());

        if (!Config.setDefaultsAndValidate(configFile)) {
            System.err.printf("Could not validate config file '%s', exiting\n", configFile);
            System.exit(1);
        }

        if (system.isConnected())
            system.disconnectAllServices();

        Util.log(Util.LOG_DEBUG, "%s #%d stopped [%s]", "agent", server_num, Thread.currentThread().getName());
    }//run()

}//class ZabbixAgent