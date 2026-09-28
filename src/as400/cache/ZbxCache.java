package as400.cache;
import as400.*;
import java.util.*;
import java.io.IOException;

public class ZbxCache {

    //static class variables
    private static ArrayList<ZbxCache> cacheList = new ArrayList<ZbxCache>();

    //class variables
    private Hashtable<String,ZbxCacheEntry> ht;
    private ZbxCacheFiller cache_filler;
    String key_macro, name;
    private long timeout, expired;

    public ZbxCache(ZbxCacheFiller cache_filler, String key_macro, String name, long timeout) {
        this.ht = new Hashtable<String,ZbxCacheEntry>();
        this.cache_filler = cache_filler;
        this.key_macro = key_macro;
        this.name = name;
        this.timeout = timeout;
        this.expired = 0l;
        synchronized (cacheList) {
            cacheList.add(this);
        }//sync(cacheList)
    }//constructor ZbxCache()

    static Iterator<ZbxCache> getCacheList() {
        synchronized (cacheList) {
            return cacheList.iterator();
        }//sync
    }//getCacheList

    boolean isClearNeeded() {
        if (0l < this.expired && this.expired < System.currentTimeMillis())
            return true;
        else
            return false;
    }//isClearNeeded

    private synchronized void checkCache() throws ZbxException, IOException {
        if (this.expired < System.currentTimeMillis()) {
            Util.log(Util.LOG_DEBUG, " checkCache(): refreshing cache for %s", name);
            clear();
            this.cache_filler.fill();
            this.expired = System.currentTimeMillis() + this.timeout;
        }//if
    }//checkCache()

    public synchronized ZbxCacheEntry getEntry(String key) throws ZbxException, IOException {
        checkCache();
        return this.ht.get(key);
    }//getEntry()

    public synchronized ZbxCacheEntry getRawEntry(String key) {
        return this.ht.get(key);
    }//getEntry()

    public synchronized void clear() {
        this.ht.clear();
        this.expired = 0l;
        Util.log(Util.LOG_DEBUG, " ZbxCache.clear(): cache %s cleared", this.name);
    }//clear()

    public synchronized void putEntry(String key, ZbxCacheEntry entry) {
        this.ht.put(key, entry);
    }//putEntry()

    public synchronized String discovery() throws ZbxException, IOException {

        checkCache();

        StringBuilder buf = new StringBuilder();
        buf.append("{\"data\":[");
        boolean first = true;
        for (Enumeration<String> e = ht.keys(); e.hasMoreElements(); ) {
            String id = e.nextElement();
            ZbxCacheEntry entry = ht.get(id);
            if (first) 
                first = false;
            else
                buf.append(",");
            buf.append("\n {\"");
            buf.append(key_macro);
            buf.append("\":\"");
            buf.append(id);
            buf.append("\"");
            entry.appendToStringBuilder(buf);
            buf.append("}");
        }//for
        buf.append("\n]}\n");
        return buf.toString();

    }//discovery()

}//class ZbxCache
