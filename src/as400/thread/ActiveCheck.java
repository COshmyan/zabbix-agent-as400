package as400.thread;
import as400.*;
import as400.comms.*;
import com.ibm.as400.access.*;
import java.beans.PropertyVetoException;
import java.io.IOException;
import java.util.*;
import org.json.simple.*;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class ActiveCheck extends ZabbixThread {

    //public constants
    public static final byte ZBX_METRIC_FLAG_PERSISTENT     =0x01;  //do not overwrite old values when adding to the buffer
    public static final byte ZBX_METRIC_FLAG_NEW            =0x02;  //new metric, just added
    public static final byte ZBX_METRIC_FLAG_LOG_LOG        =0x04;  //log[
    public static final byte ZBX_METRIC_FLAG_LOG_LOGRT      =0x08;  //logrt[
    public static final byte ZBX_METRIC_FLAG_LOG_EVENTLOG   =0x10;  //eventlog[
    public static final byte ZBX_METRIC_FLAG_LOG            =       //item for log file monitoring, one of the above
        (ZBX_METRIC_FLAG_LOG_LOG | ZBX_METRIC_FLAG_LOG_LOGRT | ZBX_METRIC_FLAG_LOG_EVENTLOG);

    //private constants
    //0x0000000100000000, it is more than max 4-bytes integer.
    //Util.long2key() processes only lower 4 bytes, so result will be equivalent 0l, i.e. MessageQueue.OLDEST
    private static final long NON_EXISTING_MESSAGE          = 4294967296l;
    private static final long MSEC_PER_HOUR                 = 3600000l; //milliseconds per hour
    private static final long RETRY_INTERVAL_MIN            = 2000l;
    private static final long RETRY_INTERVAL_MAX            = 60000l;

    static class ActiveCheckMetric {
        //object variables
        String  key;
        long    itemid;
        long    timeout_ms;
        long    lastlogsize;
        long    lastlogsize_sent;
//        long    refresh_ms; //-> Interval
        Interval interval;  //instead of "char *delay" in original Zabbix agent
        long    nextcheck_ms;
        long    mtime_ms;
        long    mtime_ms_sent;
        byte    flags;
        boolean state_unsupported;
        int     error_count;    //number of file reading errors in consecutive checks
        AgentRequest agent_request;
        
        private ActiveCheckMetric(String key, long itemid, long timeout_ms, Interval interval,
                                  long lastlogsize, long mtime_ms) {
            this.key                 = key;
            this.itemid              = itemid;
            this.timeout_ms          = timeout_ms;
            this.interval            = interval;
            this.lastlogsize         = lastlogsize;
            this.lastlogsize_sent    = lastlogsize;
            this.mtime_ms            = mtime_ms;
            this.mtime_ms_sent       = mtime_ms;
            this.nextcheck_ms    = 0l;
            this.state_unsupported   = false;
            this.error_count         = 0;
            this.flags               = initFlags(key);
            try {
                this.agent_request  = new AgentRequest(key);
            } catch (ZbxException ex) {
                this.agent_request = null;
                Util.log(Util.LOG_ERROR,"%s",ex);
            }
        }//constructor ActiveCheckMetric()
    }//internal class ActiveCheckMetric

    public static class ActiveBuffer {
        int  pcount      = 0;
        long lastsent_ms = 0l;
        long first_error = 0l;
        long id          = 0l;
        ArrayList<DataObject> items;
        //ArrayList: boolean add(E e), void clear(), int size(), get(int i), Object[] toArray()
    
        public ActiveBuffer() {
            items = new ArrayList<DataObject>(Config.getBufferSize());
        }//constructor ActiveBuffer()

        public ActiveBuffer(DataObject dobj) {
            this();
            add(dobj);
        }//constructor ActiveBuffer()

        public ArrayList<DataObject> getItems() {
		    return this.items;
	    }//getItems()

        public synchronized void add(DataObject dobj) {
		    if (null != dobj) {
                if (dobj.getFlag(ZBX_METRIC_FLAG_PERSISTENT))
                    pcount++;
		        items.add(dobj);
            }
	    }//add()

        DataObject getLast() {
            int i = items.size() - 1;
            if (0 > i)
                return null;
            else
                return items.get(i);
        }//getLast()

        public String toString() {
		    StringBuilder sb=new StringBuilder();
		
		    for (int i=0; i<items.size(); i++){
			    if (0<i)
    			    sb.append(",\n");
			    sb.append(items.get(i).toString(++id));
		    }
		    return sb.toString();
	    }//toString()

    }//internal class ActiveBuffer

    static class ActiveCommand {
        long id;
        String key;
        long timeout_ms;

        private ActiveCommand(long id, String key, long timeout_ms) {
            this.id         = id;
            this.key        = key;
            this.timeout_ms = timeout_ms;
        }//constructor ActiveCommand
    }//internal class ActiveCommand

    static class ActiveCommands {
        private ArrayList<ActiveCommand> active_commands;

        private ActiveCommands() {
            this.active_commands = new ArrayList<ActiveCommand>();
        }//constructor ActiveCommands

        private void addCommand(Hashtable<Long,Long> commands_hash, long command_id, String key, long timeout_ms) {
            Util.log(Util.LOG_DEBUG,"In addCommand(): id=%d, timeout=%d, '%s'", command_id, timeout_ms, key);
            if (!commands_hash.containsKey(command_id)) {
                commands_hash.put(command_id, System.currentTimeMillis() + MSEC_PER_HOUR);//pluss 1h
                this.active_commands.add(new ActiveCommand(command_id, key, timeout_ms));
                Util.log(Util.LOG_DEBUG," addCommand(): added");
            }//if
            Util.log(Util.LOG_DEBUG,"End of addCommand()");
        }//addCommand()

        private void clear() {
            this.active_commands.clear();
        }//clear()

        private boolean isEmpty() { return this.active_commands.isEmpty(); }

        private Iterator<ActiveCommand> iterator() {
            return this.active_commands.iterator();
        }//elements()

    }//internal class ActiveCommands

    static class CommandResult {
        long id;
        String value;
        boolean unsupported;

        private CommandResult(long id, String value, boolean unsupported) {
            this.id          = id;
            this.value       = value;
            this.unsupported = unsupported;
        }//constructor CommandResult

        private String toJSON() {
            StringBuilder buf = new StringBuilder();
            buf.append(" {\n  \"id\":");
            buf.append(this.id);
            buf.append(",\n  ");
            if (this.unsupported) {
                buf.append("\"error\":");
            } else {
                buf.append("\"value\":");
            }//if
            buf.append((null == this.value) ? "\"\"" : JSONValue.toJSONString(this.value));
            buf.append("\n }");
            return buf.toString();
        }//toJSON()

    }//internal class CommandResult

    static class CommandResults {
        private Hashtable<Long,CommandResult> command_results;

        private CommandResults() {
            this.command_results = new Hashtable<Long,CommandResult>();
        }//constructor CommandResults

        private String toJSON() {
            if (this.command_results.isEmpty())
                return "";
            StringBuilder buf = new StringBuilder();
            boolean first = true;
            buf.append(",\n\"commands\": [");
            for (Enumeration<CommandResult> e = this.command_results.elements(); e.hasMoreElements(); ) {
                if (first)
                    first = false;
                else
                    buf.append(',');
                buf.append('\n');
                buf.append(e.nextElement().toJSON());
            }//for
            buf.append("\n]");
            return buf.toString();
        }//toJSON()

        private boolean isEmpty() { return this.command_results.isEmpty(); }

        private void clear() {
            this.command_results.clear();
        }//clear()

        private void put(CommandResult cr) {
            this.command_results.put(cr.id, cr);
        }//put()

        private boolean containsKey(Long id) {
            return this.command_results.containsKey(id);
        }//containsKey();

        private CommandResult remove(Long id) {
            return this.command_results.remove(id);
        }//containsKey();

    }//internal class CommandResults

    //class ActiveCheck fields
    private ArrayList<ActiveCheckMetric> active_metrics;
    private Hashtable<String,ZbxRegexp> regexps;
    private ActiveBuffer buffer;
    private ActiveCommands active_commands;
    private CommandResults command_results;
    private Hashtable<Long,Long> commands_hash;
    private boolean lastRefreshActiveChecks, lastHeartbeat;
    private ZbxAddrList addrs;
    private String  orig_serverActive;
    private String  session;
    private long    config_revision_local = 0l;
    private boolean history_upload_disabled = false;

    public ActiveCheck(String server) throws ZbxException {
        super();
        setName("active checks");
        this.active_metrics = new ArrayList<ActiveCheckMetric>();
        this.regexps = new Hashtable<String,ZbxRegexp>();
        this.buffer = new ActiveBuffer();
        this.active_commands = new ActiveCommands();
        this.command_results = new CommandResults();
        this.commands_hash = new Hashtable<Long,Long>();
        this.lastRefreshActiveChecks = true;
        this.lastHeartbeat = true;
        this.addrs = new ZbxAddrList(server);
        this.orig_serverActive = server;
        this.session = Util.createToken(this.hashCode());
    }//constructor ActiveCheck()

    private static byte initFlags(String key) {
        byte flags = ZBX_METRIC_FLAG_NEW;
        if (key.startsWith("log["))
            flags |= ZBX_METRIC_FLAG_LOG_LOG;
        if (key.startsWith("logrt["))
            flags |= ZBX_METRIC_FLAG_LOG_LOGRT;
        if (key.startsWith("eventlog["))
            flags |= ZBX_METRIC_FLAG_LOG_EVENTLOG;
        return flags;
    }//initFlags()

    public ZbxRegexp getGlobalRegex(String name) {
        synchronized (this.regexps) {
            return this.regexps.get(name);
        }//sync
    }//getRegexps()

    private long getMinNextcheck() {
        long min = -1l;
        for (ActiveCheckMetric metric: active_metrics) {
            if (metric.nextcheck_ms<min || -1l == min)
                min = metric.nextcheck_ms;
        }//for
        return min;
    }//getMinNextcheck()

    private void cleanCommandHash() {
        long now = System.currentTimeMillis();
        Util.log(Util.LOG_DEBUG,"In cleanCommandHash() at: %d", now);

        for (Iterator<Long> it = this.commands_hash.keySet().iterator(); it.hasNext(); ) {
            Long id = it.next();
            if (this.commands_hash.get(id) > now)
                continue;
            if (this.command_results.containsKey(id))
                this.command_results.remove(id);
            it.remove();
            Util.log(Util.LOG_DEBUG," cleanCommandHash(): command with id=%d removed", id);
        }//for
        Util.log(Util.LOG_DEBUG,"End of cleanCommandHash()");
    }//cleanCommandHash()

    /*
     * Add or replace the active check metric
     * @param key - item's key
     * @param itemid - item's id (in Zabbix server's database, unique for every item)
     * @param timeout_ms - timeout configured for this check via Zabbix frontend
     * @interval - parsed interval (can also include custom intervals - flexible/scheduled)
     * @lastlogsize - last value of log file size stored in Zabbix database
     * @mtime_ms - last value of mtime of log file stored in Zabbix database
     */
    private void addCheck(String key, long itemid, long timeout_ms,
                            Interval interval, long lastlogsize, long mtime_ms) {

        Util.log(Util.LOG_DEBUG,"In addCheck() key:'%s', itemid:'%d', timeout_ms: '%d', refresh_ms:%d, lastlogsize:%d, mtime_ms:%d",
                key, itemid, timeout_ms, interval.getDefaultDelay_ms(), lastlogsize, mtime_ms);
        boolean not_found = true;
        ActiveCheckMetric metric = null;

        for (Iterator<ActiveCheckMetric> it=active_metrics.iterator(); it.hasNext(); ) {
            metric = it.next();
            //check if this metric already stored
            if (metric.itemid != itemid)
                continue;
            //found, refresh stored metric
            not_found = false;
            if (!metric.key.equals(key)) {
                metric.key          = key;
                metric.lastlogsize  = lastlogsize;
                metric.mtime_ms     = mtime_ms;
                metric.error_count  = 0;
                metric.flags        = initFlags(key);
                try {
                    metric.agent_request  = new AgentRequest(key);
                } catch (ZbxException ex) {
                    metric.agent_request = null;
                    Util.log(Util.LOG_ERROR,"%s",ex);
                }//try-catch
                Util.log(Util.LOG_DEBUG,"  Key updated");
            }//if(keys are equals)
            if (!metric.interval.getStr().equals(interval.getStr())) {
                metric.nextcheck_ms = 0l;
                metric.interval     = interval;
            }//if
            metric.timeout_ms = timeout_ms;
            Util.log(Util.LOG_DEBUG,"  Updated");
            break;  //found, so do not need to continue the loop
        }//for

        if (not_found) {
            metric = new ActiveCheckMetric(key, itemid, timeout_ms, interval, lastlogsize, mtime_ms);
            active_metrics.add(metric);
            Util.log(Util.LOG_DEBUG,"  Added");
        }//if(not found)

        //metric is the added or modified metric
        if (0l == metric.nextcheck_ms) {
            if (interval.hasScheduling())
                metric.nextcheck_ms = interval.getAgentItemNextcheck_ms(itemid, new GregorianCalendar());
        }//if

        Util.log(Util.LOG_DEBUG,"End of addCheck()");
    }//addCheck()

    /*
     * Parse list of active checks received from server
     * @param jsonObj - JSON received from server (can be null)
     * @return true on successful parsing, false otherwise (incorrect format of string)
     */
    private boolean parseListOfChecks(JSONObject jsonObj) {
        boolean ret = false;
        String name, expression, tmp, exp_delimiter;
        long config_revision = 0l, itemid, timeout_ms, lastlogsize, mtime_ms = 0l;
        Object tmp_number = null;
        Interval interval;
        int type, case_sensitive;
        ArrayList<Long> received_metrics = new ArrayList<Long>();

        Util.log(Util.LOG_DEBUG,"In parseListOfChecks(): [%s:%d]", this.addrs.getIp(), this.addrs.getPort());

        //parse XML element
        try {
            tmp = (String)jsonObj.get("response");
            if (!"success".equals(tmp)) {
                if (null == tmp || "".equals(tmp)) {
                    tmp = (String)jsonObj.get("error");
                    if (null == tmp || "".equals(tmp)) {
                        tmp = "cannot find tag: 'response'";
                    }//both "response" and "error" absent
                    Util.log(Util.LOG_ERROR," cannot parse list of active checks from [%s:%d]: %s", this.addrs.getIp(), this.addrs.getPort(), tmp);
                } else {//response present, but not "success"
                    tmp = (String)jsonObj.get("info");
                    Util.log(Util.LOG_WARNING," no active checks on server [%s:%d]%s", this.addrs.getIp(), this.addrs.getPort(),
                        (null == tmp) ? "" : (": " + tmp)
                    );
                }//if(response absent)
            } else {

                try {
                    tmp_number = (Number)jsonObj.get("config_revision");
                    if (null != tmp_number) {
                        config_revision = ((Number)tmp_number).longValue();
                    }//if(config_revision exists)
                } catch (ClassCastException ex) {
                        Util.log(Util.LOG_WARNING," \"%s\" is not a valid revision", tmp_number.toString());
                        return false;
                }//try-catch

                tmp = (String)jsonObj.get("upload");
                history_upload_disabled = "disabled".equals(tmp);

                JSONArray ja = (JSONArray)jsonObj.get("data");
                if (null == ja) {//config did not changed, return OK
                    if (0l == config_revision_local) {
                        Util.log(Util.LOG_ERROR," cannot parse list of active checks: %s", jsonObj.toString());
                        return false;
                    }
                    return true;
                }//if(ja==null)

                Util.log(Util.LOG_DEBUG,"  data parsed");

                if (config_revision_local > config_revision) {
                    active_commands.clear();
                    command_results.clear();
                    cleanCommandHash();
                }
                config_revision_local = config_revision;

                for (Object o: ja) {
                    JSONObject metric = (JSONObject)o;
                    name        =  (String)metric.get("key");
                    try {
                        itemid = ((Number)metric.get("itemid")).longValue();
                    } catch (ClassCastException|NullPointerException ex) {
                        Util.log(Util.LOG_WARNING, "Cannot retrieve value for tag \"itemid\" for \"%s\"; %s", name, ex.toString());
                        continue;
                    }//try-catch
                    try {
                        tmp_number = metric.get("timeout");
                        if (null != tmp_number && !"".equals(tmp=tmp_number.toString()))
                            timeout_ms = Interval.time2long(tmp);
                        else
                            timeout_ms = Config.getTimeout_ms();
                    } catch (ZbxException ex) {
                        Util.log(Util.LOG_WARNING, "Wrong format for \"timeout\" for \"%s\": \"%s\"; %s",
                                                 name, tmp, ex.toString());
                        timeout_ms = -1l;
                        continue;
                    }
                    tmp = metric.get("delay").toString();
                    interval = new Interval(tmp);
                    lastlogsize = ((Number)metric.get("lastlogsize")).longValue()       ;
                    try {
                        mtime_ms= ((Number)metric.get("mtime")      ).longValue() * 1000;
                    } catch (ClassCastException ex) {
                        mtime_ms=0l;
                    }//try-catch
                    addCheck(Config.zbxAliasGet(name), itemid, timeout_ms, interval, lastlogsize, mtime_ms);
                    received_metrics.add(new Long(itemid));
                }//for(all "data" elements)

                //remove what wasn't received
                for (java.util.Iterator<ActiveCheckMetric> it=active_metrics.iterator(); it.hasNext(); ) {
                    ActiveCheckMetric metric = it.next();
                    if (!received_metrics.contains(new Long(metric.itemid)))
                        it.remove();
                }//for
                received_metrics.clear();

                //processing of global RE
                synchronized (this.regexps) {
                    this.regexps.clear();
                    ja = (JSONArray)jsonObj.get("regexp");
                    if (null != ja) {
                        for (Object o: ja) {
                            JSONObject regexp = (JSONObject)o;
                            name           =  (String)regexp.get("name");
                            expression     =  (String)regexp.get("expression");
                            exp_delimiter  =  (String)regexp.get("exp_delimiter");
                            char delimiter = (null == exp_delimiter || "".equals(exp_delimiter)) ?
                                             ',' : exp_delimiter.charAt(0);
                            type           = ((Number)regexp.get("expression_type")).intValue();
                            case_sensitive = ((Number)regexp.get("case_sensitive" )).intValue();
                            try {
                                if (this.regexps.containsKey(name)) {
                                    this.regexps.get(name).addSubexp(type, expression,
                                                            delimiter, (0 != case_sensitive));
                                } else {
                                    this.regexps.put(name,
                                        new ZbxRegexp(name, type, expression, delimiter,
                                                        (0 != case_sensitive)));
                                }//if (contains already)
                            } catch (IllegalArgumentException ex) {
                                Util.log(Util.LOG_WARNING, "parseListOfChecks() error: invalid regexp" +
                                    " '%s' for global regular expression '%s'", expression, name);
                            }//try-catch
                        }//for(all "regexp" elements)
                    }//if (ja)
                }//sync(regexps)

                ret = true;
            }//if(not success)
        } catch (ClassCastException|NullPointerException ex) {
            Util.log(Util.LOG_ERROR," cannot parse list of active checks: %s", ex);
        } finally {
            Util.log(Util.LOG_DEBUG,"End of parseListOfChecks(): %b", ret);
        }//try-catch

        return ret;
    }//parseListOfChecks()

    /*
     * Parse list of active checks received from server
     * @param jsonObj - JSON received from server (can be null)
     * @return true on successful parsing, false otherwise (incorrect format of string)
     */
    private boolean parseListOfCommands(JSONObject jsonObj, long default_timeout_ms) {
        boolean ret = false;
        JSONArray ja = null;
        String cmd, tmp, wait;
        long timeout_ms, command_id;
        Object tmp_number = null;
        Util.log(Util.LOG_DEBUG,"In parseListOfCommands()");

        try {
            ja = (JSONArray)jsonObj.get("commands");
            if (null != ja) {
                for (Object o: ja) {
                    JSONObject obj = (JSONObject)o;
                    cmd            =  (String)obj.get("command");
                    if (null == cmd || "".equals(cmd)) {
                        Util.log(Util.LOG_WARNING, " cannot retrieve value of tag 'command'");
                        continue;
                    }//if

                    try {
                        tmp_number = obj.get("timeout");
                        if (null == tmp_number || "".equals(tmp=tmp_number.toString()))
                            timeout_ms = default_timeout_ms;
                        else
                            timeout_ms = ((Number)tmp_number).longValue() * 1000;//seconds -> milliseconds
                    } catch (ClassCastException ex) {
                        Util.log(Util.LOG_ERROR, " invalid format for \"timeout\": \"%s\"",
                                tmp_number.toString());
                        continue;
                    }//try-catch

                    try {
                        cmd = Util.zbxQuoteKeyParam(cmd, 0);
                    } catch (ZbxException ex) {
                        Util.log(Util.LOG_WARNING, " Invalid command \"%s\"", cmd);
                        continue;
                    }//try-catch

                    try {
                        tmp_number = obj.get("id");
                        command_id = ((Number)tmp_number).longValue();
                    } catch (ClassCastException|NullPointerException ex) {
                        Util.log(Util.LOG_WARNING, " cannot retrieve value of tag \"id\": \"%s\"",
                                tmp_number.toString());
                        continue;
                    }//try-catch

                    try {
                        tmp_number = obj.get("wait");
                        wait = (0 == ((Number)tmp_number).intValue()) ? "nowait" : "wait";
                    } catch (ClassCastException|NullPointerException ex) {
                        Util.log(Util.LOG_WARNING, " cannot retrieve value of tag \"wait\": \"%s\"",
                                tmp_number.toString());
                        continue;
                    }//try-catch

                    cmd = "system.run[" + cmd + "," + wait + "]";
                
                    this.active_commands.addCommand(this.commands_hash, command_id, cmd, timeout_ms);
                }//for
            }//if(ja!=null)
            ret = true;

        } catch (ClassCastException|NullPointerException ex) {
            Util.log(Util.LOG_ERROR," cannot parse list of active commands: %s", ex);
        } finally {
            Util.log(Util.LOG_DEBUG,"End of parseListOfCommands(): %b", ret);
        }//try-catch-finally

        return ret;
    }//parseListOfCommands()

    /*
     * Retrieve from Zabbix server list of active checks
     * @return true on successful parsing, false otherwise
     */
    private boolean refreshActiveChecks() {
        boolean ret = false;
        String err ="";
        JSONObject reply;

        Util.log(Util.LOG_DEBUG,"In refreshActiveChecks(): host:%s, port:%d", this.addrs.getIp(), this.addrs.getPort());

        String host_metadata = Config.getHostMetadata();
        if (null == host_metadata) {
            String host_metadata_item = Config.getHostMetadataItem();
            if (null != host_metadata_item) {
                try {
                    host_metadata = ZabbixAgent.zbxExecuteAgentCheck(new AgentRequest(host_metadata_item),
                        (Util.ZBX_PROCESS_LOCAL_COMMAND | Util.ZBX_PROCESS_WITH_ALIAS), 0).getValue().toString();
                } catch (ZbxException ex) {
                    Util.log(Util.LOG_WARNING, "Could not obtain value for parameter 'HostMetadataItem=%s': %s",
                            host_metadata_item, ex);
                }//try-catch
                if (null != host_metadata && Config.HOST_METADATA_LEN < host_metadata.getBytes(Util.getUtf8()).length) {
                    Util.log(Util.LOG_WARNING, "The returned value '%s' of \"%s\" item specified by "
                        + "\"HostMetadataItem\" configuration parameter is too long, using first %d characters",
                        host_metadata, host_metadata_item, Config.HOST_METADATA_LEN);
                    host_metadata = host_metadata.substring(0, Config.HOST_METADATA_LEN);
                }//if (HOST_METADATA_LEN)
            }//if(host_metadata_item)
        }//if(host_metadata)
        String host_interface = Config.getHostInterface();
        if (null == host_interface) {
            String host_interface_item = Config.getHostInterfaceItem();
            if (null != host_interface_item) {
                try {
                    host_interface = ZabbixAgent.zbxExecuteAgentCheck(new AgentRequest(host_interface_item),
                        (Util.ZBX_PROCESS_LOCAL_COMMAND | Util.ZBX_PROCESS_WITH_ALIAS), 0).getValue().toString();
                } catch (ZbxException ex) {
                    Util.log(Util.LOG_WARNING, "Could not obtain value for parameter 'HostInterfaceItem=%s': %s",
                            host_interface_item, ex);
                }//try-catch
                if (null != host_metadata && Config.HOST_INTERFACE_LEN < host_metadata.length()) {
                    Util.log(Util.LOG_WARNING, "The returned value '%s' of \"%s\" item specified by "
                        + "\"HostInterfaceItem\" configuration parameter is too long, using first %d characters",
                        host_interface, host_interface_item, Config.HOST_INTERFACE_LEN);
                    host_interface = host_interface.substring(0, Config.HOST_INTERFACE_LEN);
                }//if (HOST_INTERFACE_LEN)
            }//if(host_interface_item)
        }//if(host_interface)
        ZbxRequest req = new ZbxRequest(session, config_revision_local, host_metadata, host_interface);
        try {
            int level = (true != lastRefreshActiveChecks) ?  Util.LOG_DEBUG : Util.LOG_WARNING;
            reply = new ZbxSender(req, this.addrs).exchangeWithRedirect(level);
            if (null != reply) {
                ret = true;
            } else {
                Util.log(Util.LOG_WARNING," Received empty response from active check configuration update");
                ret = false;
            }//if
            ret = ret && parseListOfChecks(reply);
            ret = parseListOfCommands(reply, Config.getTimeout_ms()) && ret;
            if (!ret)
                this.addrs.failover();
        } catch (java.io.IOException ex) {
            config_revision_local = 0l;
            err = ex.toString();
        }//try-catch

        if (!ret) {
            history_upload_disabled = true;
            if (lastRefreshActiveChecks)
                Util.log(Util.LOG_WARNING," active check configuration update from [%s:%d] started to fail [%s]",
	        		this.addrs.getIp(), this.addrs.getPort(), err);
        }//if(ret==false)
        if (ret && !lastRefreshActiveChecks)
            Util.log(Util.LOG_WARNING," active check configuration update from [%s:%d] is working again",
    			this.addrs.getIp(), this.addrs.getPort());
        lastRefreshActiveChecks = ret;
        Util.log(Util.LOG_DEBUG,"End of refreshActiveChecks(): %b", ret);

        return ret;
    }//refreshActiveChecks()

    /*
     * Check whether JSON response is SUCCEED
     * @param jsonObj - JSON response from Zabbix trapper (can be null)
     * @return true if processed successfully, false otherwise
     */
    private boolean checkResponse(JSONObject jsonObj) {
        boolean ret = false;
        Util.log(Util.LOG_DEBUG,"In checkResponse()");

        //parse XML element
        try {
            String tmp = (String)jsonObj.get("response");
            if ("success".equals(tmp)) {
                ret = true;
                tmp = (String)jsonObj.get("info");
                Util.log(Util.LOG_DEBUG," info from server: [%s]", tmp);
                tmp = (String)jsonObj.get("upload");
                history_upload_disabled = "disabled".equals(tmp);
            }//if(response=="success")
        } catch (NullPointerException|ClassCastException ex) {
                Util.log(Util.LOG_ERROR," cannot parse reply from server/proxy: %s", ex.toString());
        }//try-catch

        Util.log(Util.LOG_DEBUG,"End of checkResponse(): %b", ret);
        return ret;
    }//checkResponse()

    /*
     * Send value stored in the buffer to Zabbix server
     * @return true on successful sending, false otherwise
     */
    private boolean sendBuffer() {
        boolean ret = true, tryToSend = false;
        int buffer_count = buffer.items.size();
        long now = System.currentTimeMillis();
        String err = null;
        JSONObject jsonObj = null;

        if (0 < buffer_count || !this.command_results.isEmpty()) {
            if (this.command_results.isEmpty() && Config.getBufferSize() / 2 > buffer.pcount && 
                    Config.getBufferSize() > buffer_count &&Config.getBufferSend() * 1000 > now - buffer.lastsent_ms) {
                Util.log(Util.LOG_DEBUG," sendBuffer() now:%d lastsent:%d now-lastsent:%d BufferSend:%d; will not send now",
                            now, buffer.lastsent_ms, now - buffer.lastsent_ms, Config.getBufferSend()*1000);
            } else {
                tryToSend = true;
                ActiveBuffer data = null;
                if (history_upload_disabled) {
                    Util.log(Util.LOG_WARNING," sendBuffer() cannot send buffer: server has paused history upload");
                } else {
                    if (0 < buffer.items.size())
                        data = buffer;
                }//if
                ZbxRequest req = new ZbxRequest(session, data, this.command_results.toJSON());
                try {
                    int level = (0l == buffer.first_error) ? Util.LOG_WARNING : Util.LOG_DEBUG;
                    jsonObj = new ZbxSender(req, this.addrs).exchangeWithRedirect(level);
                    ret = checkResponse(jsonObj);
                    if (!ret) {
                        this.addrs.failover();
                    }//checkResponse() failed
                } catch (java.io.IOException ex) {
                    err = ex.toString();
                    ret = false;
                }//try-catch
            }//if(was sent recently)
	    }//if(buffer empty)

        if (tryToSend) {
            if (ret) {
                buffer.items.clear();
                buffer.pcount = 0;
                buffer.lastsent_ms = now;
                if (!this.command_results.isEmpty())
                    this.command_results.clear();
                if (0l != buffer.first_error) {
                    Util.log(Util.LOG_WARNING, " active check data upload to [%s:%d] is working again",
                            this.addrs.getIp(), this.addrs.getPort());
                    buffer.first_error = 0l;
                }//if
            } else {
                history_upload_disabled = true;
                if (0l == buffer.first_error) {
                    Util.log(Util.LOG_WARNING, " active check data upload to [%s:%d] started to fail",
                            this.addrs.getIp(), this.addrs.getPort());
                    buffer.first_error = now;
                }//if
                Util.log(Util.LOG_DEBUG, " send value error: %s", err);
            }//if(success)
        }//if(tryToSend)
        return ret;
    }//sendBuffer()

    /*
     * Buffer new value or send the whole buffer to the server
     * @param host          - name of host in Zabbix database
     * @param dobj          - metric with results that should be added to buffer
     * @return true on successful parsing, false otherwise
     */
    private boolean processValue(String host, DataObject dobj) {
        boolean ret = false;
        int i = 0;
        int config_buffer_size = Config.getBufferSize();
        int buffer_count = buffer.items.size();
        DataObject obj;

        Util.log(Util.LOG_DEBUG,"In processValue(): host:'%s', key:'%s', itemid:%d, value:'%s'",
                 host, dobj.getKey(), dobj.getItemid(), dobj.getValue());

        synchronized (buffer) {
            if (0 < buffer_count) {
                obj = buffer.getLast();
                if ((dobj.getFlag(ZBX_METRIC_FLAG_PERSISTENT) && config_buffer_size / 2 <= buffer.pcount) ||
                        config_buffer_size <= buffer_count || obj.getItemid() != dobj.getItemid()) {
                    sendBuffer();
                }//if
            }//if(buffer !empty)

            if (dobj.getFlag(ZBX_METRIC_FLAG_PERSISTENT) && config_buffer_size / 2 <= buffer.pcount) {
                Util.log(Util.LOG_WARNING,"buffer is full, cannot store persistent value");
            } else {
                if (config_buffer_size > buffer.items.size()) {
                    //will add new element to buffer
                    Util.log(Util.LOG_DEBUG,"buffer: new element %d", buffer.items.size());
                } else {
                    //buffer is full
                    obj = null;
                    if ( ! dobj.getFlag(ZBX_METRIC_FLAG_PERSISTENT) ) {
                        //for not persistent: replace it if already exists
                        for (i = buffer.items.size() - 1; i >= 0; i--) {
                            obj = buffer.items.get(i);
                            if (obj.getItemid() == dobj.getItemid()) {
                                Util.log(Util.LOG_DEBUG,"replacing element [%d] itemid:'%d', key:'%s', '%s'", i, obj.getItemid(), obj.getKey(), obj.getValue());
                                break;
                            }//if found
                        }//for
                    }//if(non-persistent)

                    if (dobj.getFlag(ZBX_METRIC_FLAG_PERSISTENT) || 0 > i) {
                        //replace the first non-persistent value in buffer
                        for (i = 0; i < buffer.items.size(); i++) {
                            obj = buffer.items.get(i);
                            if ( ! obj.getFlag(ZBX_METRIC_FLAG_PERSISTENT) ) {
                                Util.log(Util.LOG_DEBUG,"remove element [%d] itemid:'%d', key:'%s' '%s'", i, obj.getItemid(), obj.getKey(), obj.getValue());
                                break;
                            }//if found
                        }//for
                    }//if(persistent or non-persistent but still not found)

                    if (null != obj) {
                        buffer.items.remove(i);
                    }//if
                    Util.log(Util.LOG_DEBUG,"buffer full: new element [%d]", buffer.items.size());
                }//if(buffer is not full)

                buffer.add(dobj);
                //If conditions are met then send buffer now.
                if ((dobj.getFlag(ZBX_METRIC_FLAG_PERSISTENT) && config_buffer_size / 2 <= buffer.pcount) ||
                        config_buffer_size <= buffer.items.size()) {
                    sendBuffer();
                }
                ret = true;

            }//if(persistent && buffer is half-full)
        }//sync
        Util.log(Util.LOG_DEBUG,"End of processValue(): %b", ret);
        return ret;
    }//processValue()

    private static boolean isNeededMetaUpdate(ActiveCheckMetric metric, boolean old_state_unsupported) {
        boolean ret = false;

        Util.log(Util.LOG_DEBUG,"In isNeededMetaUpdate(): key:%s", metric.key);
        if (0 != (ZBX_METRIC_FLAG_LOG & metric.flags)) {
            //meta information update is needed if:
            //- lastlogsize or mtime changed since we last sent within this check
            //- nothing was sent during this check and state changed from notsupported to normal
            //- nothing was sent during this check and it's a new metric
            if ( metric.lastlogsize_sent != metric.lastlogsize || metric.mtime_ms_sent != metric.mtime_ms ||
                   old_state_unsupported != metric.state_unsupported || (0 != (ZBX_METRIC_FLAG_NEW & metric.flags)) ) {
                Util.log(Util.LOG_DEBUG, 
                    " metric.lastlogsize_sent=%d, metric.lastlogsize=%d, metric.mtime_ms_sent=%d metric.mtime_ms=%d, old_state_unsupported=%b, metric.state_unsupported=%b, metric.flags=0x%x",
                        metric.lastlogsize_sent, metric.lastlogsize, metric.mtime_ms_sent, metric.mtime_ms, old_state_unsupported, metric.state_unsupported, metric.flags);
                ret = true;
            }//if
        }//if
        Util.log(Util.LOG_DEBUG,"End of isNeededMetaUpdate(): %b", ret);
        return ret;
    }//isNeededMetaUpdate()

    private void processLogCheck(ActiveCheckMetric metric) throws ZbxException {

        Util.log(Util.LOG_DEBUG,"In processLogCheck(): key:%s", metric.key);
        //!!not implemented yet
        Util.log(Util.LOG_DEBUG,"End of processLogCheck()");
        throw new ZbxException("Processing of log files is not implemented yet.");
    }//processLogCheck()

    static class As400queue {
        private MessageQueue mqueue = null;
        private HistoryLog hlog     = null;
    }//As400Queue (internal class)

    private void processEventLogCheck(ActiveCheckMetric metric) throws ZbxException {

        Util.log(Util.LOG_DEBUG,"In processEventLogCheck(): key:'%s', lastlogsize:%d (%08x), lastsent=%d, mtime_ms=%d, mtime_ms_sent=%d",
                metric.key, metric.lastlogsize, metric.lastlogsize, metric.lastlogsize_sent, metric.mtime_ms, metric.mtime_ms_sent);
        try {
            As400queue as400queue = new As400queue();
            boolean skipMode = true;
            String curPar, mqueueName;
            AgentRequest req = metric.agent_request;
            int keySeverity, maxLines, s_count = 0, p_count = 0, max_s_count, N = req.getNparam();
            ZbxRegexp regex_value, regex_source, regex_eventid, regex_user;

            if (null == req)
                throw new ZbxException("Invalid format of key: '" + metric.key + "'.");
            //parameters check; first parameter must be specified, all other - optional
            //param1: mqueue name. If not specified as fully qualified IFS path name, use default path
            if ( N < 1 || "".equals(mqueueName = req.getParam(0)) )
                mqueueName = null;
            if (null != mqueueName && '/' != mqueueName.charAt(0))
                mqueueName = "/QSYS.LIB/" + mqueueName + ".MSGQ";
            //param2: regexp for a value
            if ( N < 2 || "".equals(curPar = req.getParam(1)) )
                regex_value = null;
            else
                regex_value = new ZbxRegexp(curPar, true);
            //param3: integer severity
            if ( N < 3 || "".equals(curPar = req.getParam(2)) )
                keySeverity = 0;
            else
                try {
                    keySeverity = Integer.parseInt(curPar); 
                } catch (NumberFormatException ex) {
                    throw new ZbxException("Invalid 3-rd parameter (severity): "+ex.getMessage());
                }
            //param4: regexp for source (job name)
            if ( N < 4 || "".equals(curPar = req.getParam(3)) )
                regex_source = null;
            else
                regex_source = new ZbxRegexp(curPar, false);
            //param5: regexp for eventID
            if ( N < 5 || "".equals(curPar = req.getParam(4)) )
                regex_eventid = null;
            else
                regex_eventid = new ZbxRegexp(curPar, false);
            //param6: maxlines (default is defined in config. file)
            if ( N < 6 || "".equals(curPar = req.getParam(5)) )
                maxLines = Config.getMaxLinesPerSecond();
            else
                try {
                    maxLines = Integer.parseInt(curPar); 
                } catch (NumberFormatException ex) {
                    throw new ZbxException("Invalid 6-th parameter (maxlines): "+ex.getMessage());
                }
            max_s_count = (int)((maxLines * metric.interval.getDefaultDelay_ms())/1000);
            //param7: mode ("all" or "skip"), default is "all"
            if ( N < 7 || "".equals(curPar = req.getParam(6)) || "all".equals(curPar))
                skipMode = false;
            else if (!"skip".equals(curPar))
                throw new ZbxException("Invalid 7-th parameter (mode): "+curPar);
            //param8: regexp for current user
            if ( N < 8 || "".equals(curPar = req.getParam(7)) )
                regex_user = null;
            else
                regex_user = new ZbxRegexp(curPar, false);

            Util.log(Util.LOG_DEBUG," regex_value='%s', regex_source='%s', regex_eventid='%s', regex_user='%s'",
                    regex_value, regex_source, regex_eventid, regex_user);
            //processing
            try {
                boolean prefix_eventid = Config.as400EventIdAsMessagePrefix();
                boolean prefix_user    = Config.as400UserAsMessagePrefix();
                boolean prefix_job     = Config.as400JobAsMessagePrefix();
                Enumeration<QueuedMessage> mlist = null;

                if (null != mqueueName) {
                    mlist = getMessageQueue(mqueueName, keySeverity, skipMode, metric, as400queue);
                } else {
                    mlist = getHistoryLog(keySeverity, skipMode, metric, as400queue);
                }//if(MessageQueue or HistoryLog)
                while (mlist.hasMoreElements()) {
                    if (!Config.running)
                        break;
                    QueuedMessage msg = mlist.nextElement();
                    long   cur_mtime_ms = -1l; try { cur_mtime_ms = msg.getDate().getTimeInMillis(); } catch (NullPointerException ex) {}
                    //last_logsize: we use a hash from message key for MessageQueue and message timestamp for HistoryLog (as it does not contain message keys)
                    long cur_lastlogsize = (null != mqueueName) ? Util.key2long(msg.getKey()) : cur_mtime_ms;
                    if (cur_lastlogsize == metric.lastlogsize)
                        continue;   //already processed message
                    int    cur_severity = msg.getSeverity();
                    String cur_eventid  = msg.getID();
                    String cur_source   = msg.getFromJobName();
                    String cur_value    = msg.getText();
                    String cur_user     = msg.getCurrentUser();
                    int    cur_type     = msg.getType();
                    long   eventid      = 0l;
                    try { eventid = Long.parseLong(cur_eventid.replaceAll("[^0-9+-]*","")); } catch (NumberFormatException ex) { ; }
                    Util.log(Util.LOG_DEBUG," New message processed: Key=%08x (%d), severity=%d, Type=%d, User='%s', EventID='%s', JobName='%s', timestamp_ms=%d, Value='%s'",
                            cur_lastlogsize, cur_lastlogsize, cur_severity, cur_type, cur_user, cur_eventid, cur_source, cur_mtime_ms, cur_value);

                    //ignore some messages: with old or invalid timestamps and with type=='reply'
                    if (metric.mtime_ms > cur_mtime_ms) {
                        Util.log(Util.LOG_DEBUG,"  Message with old mtime (%d < %d), ignored", cur_mtime_ms, metric.mtime_ms);
                        continue;
                    }//if(cur_mtime_ms)
                    if (null != mqueueName) {
                        switch (cur_type) {
                        case AS400Message.REPLY_NOT_VALIDITY_CHECKED:
                        case AS400Message.REPLY_VALIDITY_CHECKED:
                        case AS400Message.REPLY_MESSAGE_DEFAULT_USED:
                        case AS400Message.REPLY_SYSTEM_DEFAULT_USED:
                        case AS400Message.REPLY_FROM_SYSTEM_REPLY_LIST:
                            Util.log(Util.LOG_DEBUG,"  Message type is 'reply' (%d), ignored", cur_type);
                            continue;
                        }//switch-case
                    }//if(messageQueue)

                    boolean b_regexp, b_source, b_eventid, b_user, matched, processed = false;
                    b_regexp  = (null == regex_value   || regex_value.matches  (cur_value)  );
                    b_source  = (null == regex_source  || regex_source.matches (cur_source) );
                    b_eventid = (null == regex_eventid || regex_eventid.matches(cur_eventid));
                    b_user    = (null == regex_user    || regex_user.matches  (cur_user)   );
                    matched = b_regexp && b_source && b_eventid && b_user;
                    Util.log(Util.LOG_DEBUG,"  b_regexp=%b, b_source=%b, b_eventid=%b, b_user=%b, matched=%b",
                            b_regexp, b_source, b_eventid, b_user, matched);
                    if (matched) {
                        if (prefix_eventid)
                            cur_value = cur_eventid + " " + cur_value;
                        if (prefix_user)
                            cur_value = ("".equals(cur_user) ? "<blank>" : cur_user) + " " + cur_value;
                        //prefix is: "NUMBER/USER/JOBNAME"
                        if (prefix_job)
                            cur_value = msg.getFromJobNumber() + "/" + msg.getUser() + "/" + cur_source + " " + cur_value;
                        DataObject dobj = new DataObject(metric.key, cur_value);
                        dobj.setItemid(metric.itemid);
                        dobj.setTimestamp(cur_mtime_ms / 1000);  //in seconds
                        dobj.setLastlogsize(cur_lastlogsize);
                        dobj.setSeverity((long)cur_severity);
                        dobj.setEventId(eventid);
                        dobj.setSource(cur_source);
                        dobj.setFlags((byte)(metric.flags | ZBX_METRIC_FLAG_PERSISTENT));
                        dobj.setMtime(cur_mtime_ms / 1000); //in seconds
                        processed = processValue(Config.getHostname(), dobj);
                        if (processed) {
                            s_count++;
                            metric.lastlogsize_sent = cur_lastlogsize;
                            metric.mtime_ms_sent    = cur_mtime_ms;
                        }//if (ret)
                    }//if (match)
                    p_count++;
                    if (!matched || processed) {
                        metric.lastlogsize = cur_lastlogsize;
                        metric.mtime_ms    = cur_mtime_ms;
                    } else {
                        //buffer is full, stop processing active checks till the buffer is cleared
                        break;
                    }//if (successfully added to buffer)
                    //do not flood Zabbix server if mqueue grows too fast
                    if ((s_count) >= max_s_count)
                        break;
                    //do not flood local system if mqueue grows too fast
                    if ((p_count) >= (max_s_count * 4))
                        break;
                }//while (processing message queue)
            } catch (IllegalPathNameException|AS400SecurityException|ErrorCompletingRequestException|ObjectDoesNotExistException|PropertyVetoException|IOException|InterruptedException ex) {
                Util.log(Util.LOG_ERROR," processEventLogCheck() error: %s",ex);
                throw new ZbxException("error: "+ex);
            } finally {
                if (null != as400queue.mqueue)
                    try { as400queue.mqueue.close(); } catch (Exception ex) { Util.log(Util.LOG_ERROR," processEventLogCheck() error: %s",ex); }
                if (null != as400queue.hlog)
                    try { as400queue.hlog.close(); } catch (Exception ex) { Util.log(Util.LOG_ERROR," processEventLogCheck() error: %s",ex); }
            }//try-catch
        } finally {
            Util.log(Util.LOG_DEBUG,"End of processEventLogCheck()");
        }//try-catch
    }//processEventLogCheck()

    @SuppressWarnings("unchecked")
    private Enumeration<QueuedMessage> getMessageQueue(String mqueueName, int keySeverity, boolean skipMode, ActiveCheckMetric metric, As400queue as400queue)
            throws IllegalPathNameException, AS400SecurityException, ErrorCompletingRequestException, ObjectDoesNotExistException, PropertyVetoException, IOException, InterruptedException, ZbxException {

        Util.log(Util.LOG_DEBUG,"In getMessageQueue(): mqueueName:'%s'", mqueueName);

        Enumeration<QueuedMessage> mlist = null;
        as400queue.mqueue = new MessageQueue(this.system, mqueueName);
        as400queue.mqueue.setListDirection(true);  //from oldest to newest
        as400queue.mqueue.setSeverity(keySeverity);
        if (0l == metric.lastlogsize)
            as400queue.mqueue.setUserStartingMessageKey(skipMode ? MessageQueue.NEWEST : MessageQueue.OLDEST);
        else
            as400queue.mqueue.setUserStartingMessageKey(Util.long2key(metric.lastlogsize));
        try {
            mlist = (Enumeration<QueuedMessage>)as400queue.mqueue.getMessages();
        } catch (AS400Exception ex) {
            //Exception if this key not found. In this case set it to the oldest one.
            Util.log(Util.LOG_WARNING," AS400Exception, message is: '%s'", ex);
            if ("CPF2410".equals(ex.getAS400Message().getID())) {
                Util.log(Util.LOG_WARNING," Key %d (%08x) not found for the message queue %s, starting from the oldest one",
                        metric.lastlogsize, metric.lastlogsize, mqueueName);
                metric.lastlogsize = NON_EXISTING_MESSAGE;
                as400queue.mqueue.close();
                as400queue.mqueue.setUserStartingMessageKey(MessageQueue.OLDEST);
                mlist = (Enumeration<QueuedMessage>)as400queue.mqueue.getMessages();
            } else {
                throw new ZbxException(ex.getAS400Message().getText());
            }//if
        } finally {
            Util.log(Util.LOG_DEBUG,"End of getMessageQueue(): mqueueName:'%s'", mqueueName);
        }//try-catch-finally
        return mlist;
    }//getMessageQueue()

    @SuppressWarnings("unchecked")
    private Enumeration<QueuedMessage> getHistoryLog(int keySeverity, boolean skipMode, ActiveCheckMetric metric, As400queue as400queue)
            throws IllegalPathNameException, AS400SecurityException, ErrorCompletingRequestException, ObjectDoesNotExistException, PropertyVetoException, IOException, InterruptedException, ZbxException {

        Util.log(Util.LOG_DEBUG,"In getHistoryLog()");

        as400queue.hlog = new HistoryLog(this.system);
        as400queue.hlog.setMessageSeverity(keySeverity);
        Date cur_date = new Date(Util.currentTimeMillis() - 5000); //5 seconds ago to be sure that writes to History Log with that timestamp has been completed
        as400queue.hlog.setEndingDate(cur_date);
        if (0l == metric.lastlogsize && skipMode) {
            metric.lastlogsize = cur_date.getTime();
            metric.mtime_ms = cur_date.getTime();
            return Collections.emptyEnumeration();
        }
        as400queue.hlog.setStartingDate(new Date(metric.lastlogsize));
        Util.log(Util.LOG_DEBUG,"End of getHistoryLog()");
        return (Enumeration<QueuedMessage>)as400queue.hlog.getMessages();
    }//getHistoryLog()

    private void processCommonCheck(ActiveCheckMetric metric) throws ZbxException {

        Util.log(Util.LOG_DEBUG,"In processCommonCheck(): key_name='%s'", metric.key);
        AgentRequest req = metric.agent_request;
        try {
            DataObject result;
            if (metric.timeout_ms <= 0l) {
                result = new DataObject(metric.key, "Unsupported timeout value.");
                result.setStateNotsupported(true);
            } else {
                if (null == req)
                    throw new ZbxException("Invalid format of key: '" + metric.key + "'.");
                result = ZabbixAgent.zbxExecuteAgentCheck(req, 0, metric.timeout_ms);
            }
            result.setItemid(metric.itemid);
            result.setFlag(metric.flags);
            processValue(Config.getHostname(), result);
        } catch (ZbxException ex) {
            if (isAs400CommError() && null != req && (Config.getZbxMetric(req.getKeyName()).getFlags() & Util.CF_AS400COMM) != 0)
                    Util.log(Util.LOG_DEBUG," ActiveCheck.processCommonCheck(): error %s when thread is in \"communication to AS/400 error\" state, check ignored", ex);
            else
                throw ex;
        } finally {
            Util.log(Util.LOG_DEBUG,"End of processCommonCheck()");
        }//try-catch
    }//processCommonCheck()

    private void processCommand(ActiveCommand cmd) {
        CommandResult cr;
        Util.log(Util.LOG_DEBUG,"In ActiveCommand():id=%d, '%s' timeout=%dms", cmd.id, cmd.key, cmd.timeout_ms);
        try {
            AgentRequest req = new AgentRequest(cmd.key);
            DataObject dobj = ZabbixAgent.zbxExecuteAgentCheck(req, 0, cmd.timeout_ms);
            Object result = dobj.getValue();
            if (null == result)
                result = "";
            cr = new CommandResult(cmd.id, result.toString(), dobj.getStateNotsupported());
        } catch (ZbxException ex) {
            cr = new CommandResult(cmd.id, ex.getMessage(), true);
        }//try-catch
        this.command_results.put(cr);
        Util.log(Util.LOG_DEBUG,"End of ActiveCommand()");
    }//processCommand()

    private void processActiveCommands() {
        Util.log(Util.LOG_DEBUG,"In processActiveCommands() server:'%s' port:%d", this.addrs.getIp(), this.addrs.getPort());
        for (Iterator<ActiveCommand> it = this.active_commands.iterator(); it.hasNext(); )
           processCommand(it.next());
        this.active_commands.clear();
        sendBuffer();
        Util.log(Util.LOG_DEBUG,"End of processActiveCommands()");
    }//processActiveCommands()

    private void processActiveChecks() {
        boolean ret;
        GregorianCalendar cal = new GregorianCalendar();
        long now = cal.getTimeInMillis();

        Util.log(Util.LOG_DEBUG,"In processActiveChecks() server:'%s' port:%d", this.addrs.getIp(), this.addrs.getPort());
        for (ActiveCheckMetric metric: active_metrics) {
            if (!Config.running)
                break;
            if (metric.nextcheck_ms > now)
                continue;
            metric.nextcheck_ms = metric.interval.getAgentItemNextcheck_ms(metric.itemid, cal);
            if (0l > metric.nextcheck_ms) {
                DataObject dobj = new DataObject(metric.key, metric.interval.getStr());
                dobj.setItemid(metric.itemid);
                dobj.setStateNotsupported(true);
                dobj.setMtime(metric.mtime_ms / 1000);  //in seconds
                dobj.setLastlogsize(metric.lastlogsize);
                dobj.setFlags(metric.flags);
                processValue(Config.getHostname(), dobj);
                metric.nextcheck_ms = Interval.ZBX_JAN_2038;
                metric.state_unsupported = true;
                metric.error_count = 0;
                continue;
            }//if

            metric.lastlogsize_sent = metric.lastlogsize;
            metric.mtime_ms_sent    = metric.mtime_ms;
            try {
                if (0 != ((ZBX_METRIC_FLAG_LOG_LOG | ZBX_METRIC_FLAG_LOG_LOGRT) & metric.flags))
                    processLogCheck(metric);
                else if (0 != (ZBX_METRIC_FLAG_LOG_EVENTLOG & metric.flags))
                    processEventLogCheck(metric);
                else
                    processCommonCheck(metric);

                //we are here, so the last call of process*Check(metric) was successful
                //therefore reset the error_count
                metric.error_count = 0;
                if (0 == metric.error_count) {
                    boolean old_state_unsupported = metric.state_unsupported;
                    if (metric.state_unsupported) {
                        //item became supported
                        metric.state_unsupported   = false;
                    }//if(became supported)

                    if (isNeededMetaUpdate(metric, old_state_unsupported)) {
                        //meta information update
                        DataObject dobj = new DataObject(metric.key, null);
                        dobj.setItemid(metric.itemid);
                        dobj.setStateNotsupported(metric.state_unsupported);
                        dobj.setMtime(metric.mtime_ms / 1000);  //in seconds
                        dobj.setLastlogsize(metric.lastlogsize);
                        dobj.setFlags(metric.flags);
                        processValue(Config.getHostname(), dobj);
                    }//if(NeededMetaUpdate)

                    //remove "new metric" flag
                    metric.flags &= ~ZBX_METRIC_FLAG_NEW;
                }//if(error_count==0)
            } catch (ZbxException ex) {
                if (0 < metric.error_count++) {
                    metric.state_unsupported   = true;
                    metric.error_count = 0;
                    Util.log(Util.LOG_WARNING,"active check \"%s\" is not supported: %s", metric.key,
                                ex.getMessage());
                    DataObject dobj = new DataObject(metric.key, ex.getMessage());
                    dobj.setItemid(metric.itemid);
                    dobj.setStateNotsupported(true);
                    dobj.setMtime(metric.mtime_ms / 1000);  //in seconds
                    dobj.setLastlogsize(metric.lastlogsize);
                    dobj.setFlags(metric.flags);
                    processValue(Config.getHostname(), dobj);
                }//if(it is not the first error)
            }//try-catch

            sendBuffer();
            cal = new GregorianCalendar();
            now = cal.getTimeInMillis();
            if (metric.nextcheck_ms <= now) {
                //reschedule metric if polling took it past is scheduled next poll
                metric.nextcheck_ms = metric.interval.getAgentItemNextcheck_ms(metric.itemid, cal);
                if (0l > metric.nextcheck_ms) {
                    DataObject dobj = new DataObject(metric.key, "Very unlikely period calculation error: " + metric.interval.getStr());
                    dobj.setItemid(metric.itemid);
                    dobj.setStateNotsupported(true);
                    dobj.setMtime(metric.mtime_ms / 1000);  //in seconds
                    dobj.setLastlogsize(metric.lastlogsize);
                    dobj.setFlags(metric.flags);
                    processValue(Config.getHostname(), dobj);
                    metric.nextcheck_ms = Interval.ZBX_JAN_2038;
                    metric.state_unsupported   = true;
                    metric.error_count = 0;
                }//if(error)
            }//if

        }//for
        Util.log(Util.LOG_DEBUG,"End of processActiveChecks()");
    }//processActiveChecks()

    /*
     * This function is used to update checking and sending schedules
     * if the system time was rolled back.
     */
    private void updateSchedule(long delta) {
        synchronized (this.active_metrics) {
            for (ActiveCheckMetric metric: active_metrics) {
                metric.nextcheck_ms += delta;
            }//for
        }//sync
        this.buffer.lastsent_ms += delta;
    }//updateSchedule()

    private void sendHeartbeatMsg() {
        ZbxRequest req = new ZbxRequest();

        Util.log(Util.LOG_DEBUG,"In sendHeartbeatMsg()");

        try {
            int level = (true != lastHeartbeat) ?  Util.LOG_DEBUG : Util.LOG_WARNING;
            JSONObject reply = new ZbxSender(req, this.addrs).exchangeWithRedirect(level);
            if (!lastHeartbeat)
                Util.log(Util.LOG_WARNING,"Successfully sent heartbeat message to [%s:%d]",
    	    		this.addrs.getIp(), this.addrs.getPort());
            lastHeartbeat = true; //checkResponse(reply); but can be null
        } catch (java.io.IOException ex) {
            if (lastHeartbeat)
                Util.log(Util.LOG_WARNING,"Unable to send heartbeat message to [%s]:%d [%s]",
	        		this.addrs.getIp(), this.addrs.getPort(), ex.toString());
            lastHeartbeat = false;
        }//try-catch

        Util.log(Util.LOG_DEBUG,"End of sendHeartbeatMsg()");
    }//sendHeartbeatMsg()

    public void run() {
        long nextcheck = 0l, nextrefresh = 0l, nextsend = 0l, heartbeat_nextcheck = 0l, now,
             delta, lastcheck = 0l, lash_cmd_hash_check = 0l, retry_after = 0l;

        Util.log(Util.LOG_INFO,"agent #%d (%s) started [%s #%d]", server_num, orig_serverActive,
                Thread.currentThread().getName(), server_num + 1);

        if (0l != Config.getHeartbeatFrequency())
            heartbeat_nextcheck = System.currentTimeMillis();

        try {
            while (Config.running) {

                if ((now = System.currentTimeMillis()) >= nextsend) {
                    sendBuffer();
                    nextsend = System.currentTimeMillis() + 1000l;
                }//if(nextsend)

                if (0l != heartbeat_nextcheck && now >= heartbeat_nextcheck) {
                    heartbeat_nextcheck = now + Config.getHeartbeatFrequency() * 1000;
                    sendHeartbeatMsg();
                }//if(heartbeat)

                if (now >= nextrefresh) {
                    if (refreshActiveChecks()) {//OK
                        nextrefresh = System.currentTimeMillis() + 
                                        Config.getRefreshActiveChecks() * 1000;
                        nextcheck = 0l;
                        if (0l != retry_after)
                            retry_after = 0l;
                    } else {//Fail
                        retry_after = (0l == retry_after) ? RETRY_INTERVAL_MIN : (retry_after * 2);
                        if (retry_after > RETRY_INTERVAL_MAX)
                            retry_after = RETRY_INTERVAL_MAX;
                        nextrefresh = System.currentTimeMillis() + retry_after;
                    }//if(refreshActiveChecks())
                } else if (history_upload_disabled && nextrefresh > now + RETRY_INTERVAL_MAX) {
                    retry_after = RETRY_INTERVAL_MIN;
                    nextrefresh = now + retry_after;
                }//if(nextrefresh)

                if (!this.active_commands.isEmpty())
                    processActiveCommands();

                if (now >= nextcheck && Config.getBufferSize() / 2 > buffer.pcount) {
                    processActiveChecks();
                    if (Config.getBufferSize() / 2 <= buffer.pcount) {
                        //failed to complete processing active checks
                        continue;
                    }//if
                    nextcheck = getMinNextcheck();
                    if (nextcheck <= 0l)
                        nextcheck = System.currentTimeMillis() + RETRY_INTERVAL_MAX;
                } else {
                    if (0l > (delta = now - lastcheck)) {
                        Util.log(Util.LOG_WARNING,
                            "The system time has been pushed back, adjusting active check schedule by %d ms", delta);
                        updateSchedule(delta);
                        nextcheck   += delta;
                        nextsend    += delta;
                        nextrefresh += delta;
                        if (0l != heartbeat_nextcheck)
                            heartbeat_nextcheck += delta;
                    }//if(needed adjust)
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ex) {
                        Config.running = false;
                    }//try-catch
                }//if(nextrefresh)
                lastcheck = now;

                if (now > (lash_cmd_hash_check + MSEC_PER_HOUR)) {//pluss 1 hour (in milliseconds)
                    cleanCommandHash();
                    lash_cmd_hash_check = now;
                }

            }//while(main loop)
        } catch (Throwable ex) {
            //There should not be, but if it's occured - it is critical: stacktrace and stop agent
            Util.log(Util.LOG_CRITICAL, ex, "Error in ActiveCheck.run():");
            Config.running = false;
        }//try-catch

        if (system.isConnected())
            system.disconnectAllServices();
        Util.log(Util.LOG_INFO,"agent #%d (%s) stopped [%s #%d]", server_num, orig_serverActive,
                Thread.currentThread().getName(), server_num + 1);
    }//run()

}//class ActiveCheck
