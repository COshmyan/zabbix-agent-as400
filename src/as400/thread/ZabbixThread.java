package as400.thread;
import as400.*;
import com.ibm.as400.access.AS400;
import com.ibm.as400.access.SocketProperties;
import java.beans.PropertyVetoException;

public abstract class ZabbixThread extends Thread implements Thread.UncaughtExceptionHandler, As400Thread {

    //class variables
    protected AS400  system = null;
    protected int    server_num;
    protected boolean as400_comm_error = false;
//    private volatile boolean as400Result;
    //static fields
    private static int server_count = -1;

    protected ZabbixThread() {
        this.server_num = server_count++;
        if (Config.isConfigured()) {
            try {
                initAs400();
                system.setUserId(Config.getUser());
                system.setPassword(Config.getAs400Password());
            } catch (PropertyVetoException ex) {
                Util.log(Util.LOG_CRITICAL, ex, "Could not set some value for AS400 object");
                Util.flushLogAndExit();
            }//try-catch
        }//if(configured)
        setUncaughtExceptionHandler(this);
    }//constructor ZabbixThread()

    protected void initAs400() throws PropertyVetoException {
        SocketProperties sp = new SocketProperties();
//This setting was commented out because it caused to disconnection after the specified timeout
//even for a normal, but inactive at the moment connections.
//I.e. it is incompatible with the permanent connections to AS/400 systems.
//        sp.setSoTimeout   (Config.getTimeout_ms()  );
//This setting, in theory, could be useful; however, the timeouts are OS-dependent and unreliable
//        sp.setKeepAlive(true);
        sp.setLoginTimeout(Config.getTimeout_ms()/2);
        this.system = new AS400(Config.getAs400ServerHost());
//        this.system = new AS400(Config.getAs400ServerHost(),
//                                Config.getUser(), Config.getAs400Password());
        this.system.setSocketProperties(sp);
        this.system.setGuiAvailable(false);//throws PropertyVetoException
    }//initAs400

    public AS400 getAs400() {
        return this.system;
    }//getAs400()

    public boolean isAs400CommError() {
        return this.as400_comm_error;
    }//isAs400CommError()

    public void setAs400CommError(boolean value) {
        this.as400_comm_error = value;
    }//setAs400CommError
/*
    public void setAs400Result(boolean value) {
        this.as400Result = value;
    }//setAs400Result

    public boolean getAs400Result() {
        return this.as400Result;
    }//getAs400()
*/
    //implements the UncaughtExceptionHandler interface
    public void uncaughtException(Thread t, Throwable ex) {
        Util.log(Util.LOG_CRITICAL, ex, "UncaughtException in the thread %s [%d]", t.getName(), server_num);
        Util.flushLogAndExit();
    }//uncaughtException()
}//class ZabbixThread
