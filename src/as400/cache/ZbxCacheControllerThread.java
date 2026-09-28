package as400.cache;
import as400.*;
import as400.thread.ZabbixThread;
import java.util.Iterator;

public class ZbxCacheControllerThread extends ZabbixThread {

    public ZbxCacheControllerThread() {
        super();
        this.system = null; //it is not used in this thread
        setName("Cache Controller thread");
    }//constructor ZbxCacheControllerThread()

    public void run() {
        Util.log(Util.LOG_DEBUG, "%s #%d started [%s]", "agent", server_num, Thread.currentThread().getName());
        while (Config.running) {

            Iterator<ZbxCache> it = ZbxCache.getCacheList();
            while (it.hasNext()) {
                ZbxCache zc = it.next();
                synchronized (zc) {
                    if (zc.isClearNeeded())
                        zc.clear();
                }//sync (zc)
            }//while(it)

            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                Config.running = false;
            }//try-catch

        }//while(main loop)
        Util.log(Util.LOG_DEBUG, "%s #%d stopped [%s]", "agent", server_num, Thread.currentThread().getName());
    }//run()

}//class ZbxCacheControllerThread
