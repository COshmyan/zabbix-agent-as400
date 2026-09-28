package as400;
import as400.thread.ActiveCheck;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2016</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class DataObject {

    //Constants
    //Severity levels:
    public static final int ITEM_LOGTYPE_INFORMATION    = 1;
    public static final int ITEM_LOGTYPE_WARNING        = 2;
    public static final int ITEM_LOGTYPE_ERROR          = 4;
    public static final int ITEM_LOGTYPE_FAILURE_AUDIT  = 7;
    public static final int ITEM_LOGTYPE_SUCCESS_AUDIT  = 8;
    public static final int ITEM_LOGTYPE_CRITICAL       = 9;
    public static final int ITEM_LOGTYPE_VERBOSE        = 10;

    //Obligatory fields
    long clock_ms;       //unsigned int
//    long ns;          //unsigned int
    String host;      //(128)
    String key;       //(255)
    Object value;     //
    //Optional fields
    boolean  state_notsupported;       //really - boolean
    long lastlogsize; //unsigned long
    long severity;    //unsigned int
    long eventid;     //unsigned int
    long mtime;       //(in seconds)
    long timestamp;   //unsigned int (in seconds)
    String source;    //(64)
    byte flags;       //

    public DataObject(String key, Object value) {
        this.clock_ms    = Util.currentTimeMillis();
//        this.ns          = 0l;
        this.host        = Config.getHostname();
        this.key         = key;
        this.value       = value;
        this.state_notsupported = false;
        this.lastlogsize = 0l;
        this.severity    = 0l;
        this.eventid     = 0l;
        this.mtime       = 0l;
        this.timestamp   = 0l;
        this.source      = null;
    }//constructor

    public long getClock_ms() {
        return clock_ms;
    }

    public void setClock_ms(long clock_ms) {
        this.clock_ms = clock_ms;
    }
/*
    public long getNs() {
        return ns;
    }

    public void setNs(long ns) {
        this.ns = ns;
    }
*/
    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public boolean getStateNotsupported() {
        return state_notsupported;
    }

    public void setStateNotsupported(boolean state_notsupported) {
        this.state_notsupported = state_notsupported;
    }

    public long getLastlogsize() {
        return lastlogsize;
    }

    public void setLastlogsize(long lastlogsize) {
        this.lastlogsize = lastlogsize;
    }

    public long getSeverity() {
        return severity;
    }

    public void setSeverity(long severity) {
        this.severity = severity;
    }

    public long getEventId() {
        return eventid;
    }

    public void setEventId(long eventid) {
        this.eventid = eventid;
    }

    public long getMtime() {
        return mtime;
    }

    public void setMtime(long mtime) {
        this.mtime = mtime;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public byte getFlags() {
        return flags;
    }

    public boolean getFlag(byte flag) {
        return 0 != (flag & flags);
    }

    public void setFlags(byte flags) {
        this.flags = flags;
    }

    public void setFlag(byte flag) {
        this.flags |= flag;
    }

    public void clearFlag(byte flag) {
        this.flags &= (~flag);
    }

    public String toString(int id){
        StringBuffer sb = new StringBuffer();
        sb.append(" {\"host\":\"");
        sb.append(org.json.simple.JSONValue.escape(host));
        sb.append("\",\n  \"key\":\"");
        sb.append(org.json.simple.JSONValue.escape(key));
        sb.append("\"");
        if (null != value)
            sb.append(",\n  \"value\":").append(org.json.simple.JSONValue.toJSONString(value));
        sb.append(",\n  \"id\":");
        sb.append(id);
        sb.append(",\n  \"clock\":");
        sb.append(clock_ms / 1000);             //ms -> seconds
        sb.append(",\n  \"ns\":");
        sb.append( (clock_ms % 1000) * 1000000);//ms -> ns
        if (state_notsupported)
            sb.append(",\n  \"state\":1");
        if (0  != (ActiveCheck.ZBX_METRIC_FLAG_LOG & flags))
            sb.append(",\n  \"lastlogsize\":").append(lastlogsize);
//        if (0  != (ActiveCheck.ZBX_METRIC_FLAG_LOG_LOGRT & flags))
        if (0  != (ActiveCheck.ZBX_METRIC_FLAG_LOG & flags))
            sb.append(",\n  \"mtime\":").append(mtime);
        if (0l != severity)
            sb.append(",\n  \"severity\":").append(severity);
        if (0l != eventid)
            sb.append(",\n  \"eventid\":").append(eventid);
        if (0l != timestamp)
            sb.append(",\n  \"timestamp\":").append(timestamp);
        if (null!=source)
            sb.append(",\n  \"source\":\"").append(org.json.simple.JSONValue.escape(source)).append('\"');
        sb.append("}");
        return sb.toString();
    }//toString()

}//class DataObject
