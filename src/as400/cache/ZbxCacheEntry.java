package as400.cache;

public interface ZbxCacheEntry {

    public void appendToDiscovery(StringBuilder buf);
    public void appendToGet(StringBuilder buf);

}//interface ZbxCacheEntry
