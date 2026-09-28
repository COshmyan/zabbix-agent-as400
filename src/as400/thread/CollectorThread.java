//import com.ibm.as400.access.*;
package as400.thread;
import as400.*;
import as400.perfstat.*;
import java.util.*;
//import java.io.*;
//import java.net.*;

public class CollectorThread extends ZabbixThread {

    //constants

    public CollectorThread() {
        super();
        setName("collector");
    }//constructor Collector()

    public void run() {
        long interval = 60000 / Procstat.TIMES_PER_MIN; //in milliseconds
        long next_ts, delay, last_ts, cur_ts;

        Util.log(Util.LOG_INFO,"agent #%d started [%s]", server_num, Thread.currentThread().getName());

        while (Config.running) {

            cur_ts = System.currentTimeMillis();
            next_ts = cur_ts + interval;

            try {
                Procstat.updateJobinfoList();
            } catch (Exception ex) {
                //There should not be, but if it's occured - it is critical: stacktrace and stop agent
                Util.log(Util.LOG_CRITICAL, ex, "Exception in Procstat.updateJobinfoList():");
                Config.running = false;
                continue;
/*
            } finally {
                if (system.isConnected())
                    system.disconnectAllServices();
*/
            }//try-catch
            Procstat.cleanJobinfoList(cur_ts - 3600 * 1000); //1 hour ago
            Procstat.cleanQueryList  (cur_ts - 3600 * 1000 * 24); //1 day ago

            last_ts = System.currentTimeMillis();
            delay = next_ts - last_ts;
            if (500 > delay) {
                delay = 500;
            }
            //Util.log(Util.LOG_DEBUG," should going to sleep. last_ts=%d, next_ts=%d, delay=%d",
		    //    last_ts, next_ts, delay);

            try {
                Thread.sleep(delay);
            } catch (InterruptedException ex) {
                Config.running = false;
            }//try-catch

        }//while(main loop)

        if (system.isConnected())
            system.disconnectAllServices();
        Util.log(Util.LOG_INFO,"agent #%d stopped [%s]", server_num, Thread.currentThread().getName());
    }//run()

}//class CollectorThread()
