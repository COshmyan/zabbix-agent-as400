package as400.thread;
import as400.*;
import com.ibm.as400.access.AS400;

public abstract class ZabbixThread extends Thread {

    //class variables
    protected AS400  system = null;
    protected int    server_num;
    //static fields
    private static int server_count = -1;

    protected ZabbixThread() {
        this.server_num = server_count++;
//        this.system = new com.ibm.as400.access.SecureAS400(Config.getAs400ServerHost(),
        if (Config.isConfigured())
            this.system = new AS400(Config.getAs400ServerHost(),
                                    Config.getUser(), Config.getAs400Password());
    }//constructor ZabbixThread()

    public AS400 getAs400() {
        return this.system;
    }//getAs400()

}//class ZabbixThread
