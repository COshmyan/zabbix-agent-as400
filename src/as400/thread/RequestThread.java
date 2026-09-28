package as400.thread;
import as400.*;
import as400.metric.*;
import java.io.IOException;
import com.ibm.as400.access.AS400;

public class RequestThread extends Thread implements As400Thread {

    //class variables
    private ZbxMetric command;
    private AgentRequest req;
    private DataObject ret;
    private boolean completed;
    private Thread parent_thread;
    private Exception e;

    public RequestThread(ZbxMetric command, AgentRequest req) {
        super();
        this.command = command;
        this.req = req;
        this.ret = null;
        this.completed = false;
        this.parent_thread = Thread.currentThread();
    }//constructor ZabbixAgent()


    public void run() {
        try {
            this.ret = command.process(req);
        } catch (ZbxException|IOException ex) {
            this.e = ex;
        }
        this.completed = true;
    }//run()

    public static DataObject getResult(ZbxMetric command, AgentRequest req) throws Exception {
        RequestThread rt = new RequestThread(command, req);
        try {
            rt.start();
            rt.join(req.getTimeout_ms());
            if (rt.completed) {
                if (null != rt.ret)
                    return rt.ret;
                else
                    throw rt.e;
            } else {
                Config.incTimeouts();
                rt.interrupt();
                Thread.sleep(500);//give the child thread a chance to die gracefully
                ZabbixThread zt = (ZabbixThread)Thread.currentThread();
                Util.log(Util.LOG_WARNING, "getResult(): before disconnectAllServices()");
                zt.getAs400().disconnectAllServices();  //disconnect all services from current connection
                Util.log(Util.LOG_WARNING, "getResult(): after disconnectAllServices()");
/*
 We tried to throw away the old AS400 object and create a new one to be sure.
 However, it has a very bad side effect: memory leak.
 The JTOpen library stores hard references to all AS400 objects created (for efficiency) to reuse them,
 even if we do not want it. In result, these objects are not released by JVM Garbage Collector.
 In addition, each AS400 object contains its own structures needed to support connections and results
 of requests until they are readed by application (even if we do not need it anymore).
 So: DO NOT RECREATE AS400 objects, reuse existing ones!
 Just close all connections (they are re-opened if necessary).
*/
//                zt.initAs400();                         //and reinitialize the AS400 object for safety
                throw new ZbxException("Timeout " + req.getTimeout_ms()/1000 + " seconds expired.");
            }
        } catch (Exception ex) {
            throw new ZbxException(ex.toString());
        }
    }//getResult()

    public AS400 getAs400()             { return ((As400Thread)this.parent_thread).getAs400();         }

    public boolean isAs400CommError()   { return ((As400Thread)this.parent_thread).isAs400CommError(); }

    public void setAs400CommError(boolean value) {
        ((As400Thread)this.parent_thread).setAs400CommError(value);
    }

    public Thread getParentThread()     { return this.parent_thread; }
}//class RequestThread
