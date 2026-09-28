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
            rt.join(req.getTimeout());
            if (rt.completed) {
                if (null != rt.ret)
                    return rt.ret;
                else
                    throw rt.e;
            } else {
                rt.interrupt();
                DataObject ret = new DataObject(req.getUnparsedKey(), "Timeout " + req.getTimeout()/1000 + " seconds expired.");
                ret.setStateNotsupported(true);
                return ret;
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
