package as400.thread;
import as400.*;
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
//    public static final byte ZBX_METRIC_FLAG_NEW            =0x02;  //new metric, just added
    public static final byte ZBX_METRIC_FLAG_LOG_LOG        =0x04;  //log[
    public static final byte ZBX_METRIC_FLAG_LOG_LOGRT      =0x08;  //logrt[
    public static final byte ZBX_METRIC_FLAG_LOG_EVENTLOG   =0x10;  //eventlog[
    public static final byte ZBX_METRIC_FLAG_LOG            =       //item for log file monitoring, one of the above
        (ZBX_METRIC_FLAG_LOG_LOG | ZBX_METRIC_FLAG_LOG_LOGRT | ZBX_METRIC_FLAG_LOG_EVENTLOG);

    //private constants
    //0x0000000100000000, it is more than max 4-bytes integer.
    //Util.long2key() processes only lower 4 bytes, so result will be equivalent 0l, i.e. MessageQueue.OLDEST
    private static final long NON_EXISTING_MESSAGE          = 4294967296l;

    static class ActiveCheckMetric {
        //object variables
        String  key;
        String  key_orig;
        long    lastlogsize;
        long    lastlogsize_sent;
        long    refresh_ms;
        long    nextcheck_ms;
        long    mtime_ms;
        long    mtime_ms_sent;
        byte    flags;
        boolean state_unsupported;
        boolean refresh_unsupported;
        int     error_count;    //number of file reading errors in consecutive checks
        AgentRequest agent_request;
        
        private ActiveCheckMetric(String key, String key_orig, long refresh_ms,
                                  long lastlogsize, long mtime_ms) {
            this.key                 = key;
            this.key_orig            = key_orig;
            this.refresh_ms          = refresh_ms;
            this.lastlogsize         = lastlogsize;
            this.lastlogsize_sent    = lastlogsize;
            this.mtime_ms            = mtime_ms;
            this.mtime_ms_sent       = mtime_ms;
            this.state_unsupported   = false;
            this.refresh_unsupported = false;
//            this.flags               = ZBX_METRIC_FLAG_NEW;
            if (key.startsWith("log["))
                this.flags |= ZBX_METRIC_FLAG_LOG_LOG;
            if (key.startsWith("logrt["))
                this.flags |= ZBX_METRIC_FLAG_LOG_LOGRT;
            if (key.startsWith("eventlog["))
                this.flags |= ZBX_METRIC_FLAG_LOG_EVENTLOG;
            try {
                this.agent_request  = new AgentRequest(key);
            } catch (ZbxException ex) {
                Util.log(Util.LOG_ERROR,"%s",ex);
            }
        }//constructor ActiveCheckMetric()
    }//internal class ActiveCheckMetric

    public static class ActiveBuffer {
        int  pcount      = 0;
        long lastsent_ms = 0l;
        long first_error = 0l;
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

        public void add(DataObject item) {
		    if (null!=item) {
                if ( (item.getFlags() | ZBX_METRIC_FLAG_PERSISTENT) !=0)
                    pcount++;
		        items.add(item);
            }
	    }//add()

        public String toString() {
		    StringBuffer sb=new StringBuffer();
		
		    for(int i=0;i<items.size();i++){
			    if(0<i)
    			    sb.append(",\n");
			    sb.append(items.get(i).toString(i));
		    }
		    return sb.toString();
	    }//toString()

    }//internal class ActiveBuffer

    //class ActiveCheck fields
    private ArrayList<ActiveCheckMetric> active_metrics;
    private  Hashtable<String,ZbxRegexp> regexps;
    private ActiveBuffer buffer;
    private boolean lastRefreshActiveChecks;
    private String  serverActive;
    private int     serverActivePort;
    private String  orig_serverActive;

    public ActiveCheck(String server) {
        super();
        setName("active checks");
        this.active_metrics = new ArrayList<ActiveCheckMetric>();
        this.regexps = new Hashtable<String,ZbxRegexp>();
        this.buffer = new ActiveBuffer();
        this.lastRefreshActiveChecks = true;
        this.orig_serverActive = server;
        String []splitted = server.split(":");
        this.serverActive = splitted[0].trim();
        this.serverActivePort = 10051;
        if (1 < splitted.length)
            try {
                this.serverActivePort = Integer.parseInt(splitted[1].trim());
            } catch (NumberFormatException ex) {
                Util.log(Util.LOG_ERROR,"port number in config line '%s' invalid, using default %d",
                        server, this.serverActivePort);
            }//try-catch
    }//constructor ActiveCheck()

    public ZbxRegexp getGlobalRegex(String name) {
        synchronized (this.regexps) {
            return this.regexps.get(name);
        }//sync
    }//getRegexps()

    private static boolean isMetricReadyToProcess(ActiveCheckMetric metric) {
        if (metric.state_unsupported && !metric.refresh_unsupported)
            return false;
        return true;
    }//isMetricReadyToProcess

    private long getMinNextcheck() {
        long min=-1l;
        for (ActiveCheckMetric metric: active_metrics) {
            if (isMetricReadyToProcess(metric) && (metric.nextcheck_ms<min || -1l==min))
                min = metric.nextcheck_ms;
        }//for
        return min;
    }//getMinNextcheck()

    /*
     * Add or replace the active check metric
     * @param key
     */
    private void addCheck(String key, String key_orig,
                            long refresh_ms, long lastlogsize, long mtime_ms) {

        Util.log(Util.LOG_DEBUG,"in addCheck() key:'%s', key_orig:'%s', refresh_ms:%d, lastlogsize:%d, mtime_ms:%d",
                key, key_orig, refresh_ms, lastlogsize, mtime_ms);
        boolean not_found = true;

        for (ActiveCheckMetric metric: active_metrics) {
            //check if this metric already stored
            if (!metric.key_orig.equals(key_orig))
                continue;
            //found, refresh stored metric
            not_found = false;
            if (!metric.key.equals(key)) {
                metric.key          =key;
                metric.lastlogsize  =lastlogsize;
                metric.mtime_ms     =mtime_ms;
                metric.error_count  =0;
            }//if(keys are equals)
            if (metric.refresh_ms!=refresh_ms) {
                metric.nextcheck_ms =0l;
                metric.refresh_ms   =refresh_ms;
            }//if
            if (metric.state_unsupported)
                metric.refresh_unsupported  = true;
        }//for

        if (not_found) {
            active_metrics.add(new ActiveCheckMetric(key, key_orig,
                                refresh_ms, lastlogsize, mtime_ms));
            Util.log(Util.LOG_DEBUG,"  Added");
        }//if(not found)
        Util.log(Util.LOG_DEBUG,"end of addCheck()");
    }//addCheck()

    /*
     * Parse list of active checks received from server
     * @param str  - NULL terminated string received from server
     * @return true on successful parsing, false otherwise (incorrect format of string)
     */
    private boolean parseListOfChecks(String str) {
        boolean ret = false;
        String name, key_orig, expression, tmp, exp_delimiter;
        long lastlogsize, delay_ms, mtime_ms = 0l;
        int type, case_sensitive;
        ArrayList<String> received_metrics = new ArrayList<String>();

        Util.log(Util.LOG_DEBUG,"in parseListOfChecks(): '%s' [%s:%d]", str, serverActive, serverActivePort);

        //parse XML element
        JSONParser parser = new JSONParser();
        try {
            JSONObject jsonObj = (JSONObject)parser.parse(str);
            tmp = (String)jsonObj.get("response");
            if (!"success".equals(tmp)) {
                //clean = false;
                tmp = (String)jsonObj.get("info");
                Util.log(Util.LOG_WARNING," no active checks: %s", tmp);
            } else {
                JSONArray ja = (JSONArray)jsonObj.get("data");
                for (Object o: ja) {
                    JSONObject metric = (JSONObject)o;
                    name        =  (String)metric.get("key");
                    key_orig    =  (String)metric.get("key_orig");
                    if (null==key_orig)
                        key_orig= name;
                    delay_ms    = ((Number)metric.get("delay")      ).longValue() * 1000;
                    lastlogsize = ((Number)metric.get("lastlogsize")).longValue()       ;
                    try {
                        mtime_ms= ((Number)metric.get("mtime")      ).longValue() * 1000;
                    } catch (ClassCastException ex) {
                    }//try-catch
                    addCheck(name, key_orig, delay_ms, lastlogsize, mtime_ms);
                    received_metrics.add(key_orig);
                }//for(all "data" elements)

                //remove what wasn't received
                for (java.util.Iterator<ActiveCheckMetric> it=active_metrics.iterator(); it.hasNext(); ) {
                    ActiveCheckMetric metric = it.next();
                    if (!received_metrics.contains(metric.key_orig))
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
        } catch (ParseException|ClassCastException ex) {
                Util.log(Util.LOG_ERROR," cannot parse list of active checks: %s", ex);
        }//try-catch

        Util.log(Util.LOG_DEBUG,"end of parseListOfChecks(): %b", ret);
        return ret;
    }//parseListOfChecks()

    /*
     * Retrieve from Zabbix server list of active checks
     * @return true on successful parsingg, false otherwise
     */
    private boolean refreshActiveChecks() {
        boolean ret = false;
        String reply, err ="";

        Util.log(Util.LOG_DEBUG,"in refreshActiveChecks(): host:%s, port:%d", serverActive, serverActivePort);

        String host_metadata = Config.getHostMetadata();
        if (null == host_metadata) {
            String host_metadata_item = Config.getHostMetadataItem();
            if (null != host_metadata_item) {
                try {
                    host_metadata = ZabbixAgent.process(new AgentRequest(host_metadata_item)).getValue().toString();
                } catch (ZbxException ex) {
                    Util.log(Util.LOG_WARNING, "Could not obtain value for parameter 'HostMetadataItem=%s': %s",
                            host_metadata_item, ex);
                }//try-catch
                if (null != host_metadata && Config.HOST_METADATA_LEN < host_metadata.length()) {
                    Util.log(Util.LOG_WARNING, "The returned value '%s' of \"%s\" item specified by "
                        + "\"HostMetadataItem\" configuration parameter is too long, using first %d characters",
                        host_metadata, host_metadata_item, Config.HOST_METADATA_LEN);
                    host_metadata = host_metadata.substring(0, Config.HOST_METADATA_LEN);
                }//if (HOST_METADATA_LEN)
            }//if(host_metadata_item)
        }//if(host_metadata)
        ZbxRequest req = new ZbxRequest(host_metadata);
        try {
            reply = new ZbxSender(req, serverActive, serverActivePort).send();
            Util.log(Util.LOG_DEBUG," got [%s]", reply);
            ret = parseListOfChecks(reply);
        } catch (java.io.IOException ex) {
            err = ex.toString();
        }//try-catch

        if (ret && !lastRefreshActiveChecks)
            Util.log(Util.LOG_DEBUG," active check configuration update from [%s:%d] is working again",
			serverActive, serverActivePort);
        if (!ret && lastRefreshActiveChecks)
            Util.log(Util.LOG_DEBUG," active check configuration update from [%s:%d] started to fail (%s)",
			serverActive, serverActivePort, err);
        lastRefreshActiveChecks = ret;
        Util.log(Util.LOG_DEBUG,"end of refreshActiveChecks(): %b", ret);

        return ret;
    }//refreshActiveChecks()

    /*
     * Check whether JSON response is SUCCEED
     * @param resp - JSON response from Zabbix trapper
     * @return true if processed successfully, false otherwise
     */
    private static boolean checkResponse(String str) {
        boolean ret = false;
        Util.log(Util.LOG_DEBUG,"in checkResponse(): '%s'", str);

        //parse XML element
        JSONParser parser = new JSONParser();
        try {
            JSONObject jsonObj = (JSONObject)parser.parse(str);
            String tmp = (String)jsonObj.get("response");
            if ("success".equals(tmp)) {
                ret = true;
                tmp = (String)jsonObj.get("info");
                Util.log(Util.LOG_DEBUG," info from server: %s", str);
            }
        } catch (NullPointerException|ParseException|ClassCastException ex) {
                Util.log(Util.LOG_ERROR," cannot parse list of active checks: %s", ex);
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
        String str = null;

        if (0<buffer_count) {
            if (Config.getBufferSize() / 2 > buffer.pcount && Config.getBufferSize() > buffer_count &&
                            Config.getBufferSend() * 1000 > now - buffer.lastsent_ms) {
                Util.log(Util.LOG_DEBUG," sendBuffer() now:%d lastsent:%d now-lastsent:%d BufferSend:%d; will not send now",
                            now, buffer.lastsent_ms, now - buffer.lastsent_ms, Config.getBufferSend()*1000);
            } else {
                tryToSend = true;
                ZbxRequest req = new ZbxRequest(buffer);
                try {
                    str = new ZbxSender(req, serverActive, serverActivePort).send();
                    Util.log(Util.LOG_DEBUG," json BACK [%s]", str);
                    ret = checkResponse(str);
                } catch (java.io.IOException ex) {
                    str = ex.toString();
                    ret = false;
                }//try-catch
            }//if(was sent recently)
	      }//if(buffer empty)

        if (tryToSend) {
            if (ret) {
                buffer.items.clear();
                buffer.pcount = 0;
                buffer.lastsent_ms = now;
                if (0l != buffer.first_error) {
                    Util.log(Util.LOG_WARNING, " active check data upload to [%s:%d] is working again",
                            serverActive, serverActivePort);
                    buffer.first_error = 0l;
                }//if
            } else {
                if (0l == buffer.first_error) {
                    Util.log(Util.LOG_WARNING, " active check data upload to [%s:%d] started to fail",
                            serverActive, serverActivePort);
                    buffer.first_error = now;
                }//if
                Util.log(Util.LOG_DEBUG, " send value error: %s", str);
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

        Util.log(Util.LOG_DEBUG,"in processValue(): host:'%s', key:'%s' value:'%s'", host, dobj.getKey(), dobj.getValue());
        sendBuffer();

        if (dobj.getFlag(ZBX_METRIC_FLAG_PERSISTENT) && Config.getBufferSize() / 2 <= buffer.pcount) {
            Util.log(Util.LOG_WARNING,"buffer is full, cannot store persistent value");
        } else {
            synchronized (buffer) {
                if (Config.getBufferSize() > buffer.items.size()) {
                    Util.log(Util.LOG_DEBUG,"buffer: new element %d", buffer.items.size());
                    buffer.items.add(dobj);
                    if (dobj.getFlag(ZBX_METRIC_FLAG_PERSISTENT))
                        buffer.pcount++;
                    ret = true;
                } else {
                    //buffer is full
                    if ( ! dobj.getFlag(ZBX_METRIC_FLAG_PERSISTENT) ) {
                        //for not persistent: replace it if already exists
                        for (i = buffer.items.size() - 1; i >= 0; i--) {
                            DataObject obj = buffer.items.get(i);
                            if (obj.getHost().equals(host) && obj.getKey().equals(dobj.getKey())) {
                                obj = buffer.items.remove(i);
                                Util.log(Util.LOG_DEBUG,"replacing element [%d] Key:'%s:%s' '%s'", i, obj.getHost(), obj.getKey(), obj.getValue());
                                buffer.items.add(dobj);
                                ret = true;
                                break;
                            }//if found
                        }//for
                    }//if(non-persistent)
                    if (dobj.getFlag(ZBX_METRIC_FLAG_PERSISTENT) || 0 > i) {
                        //replace the first non-persistent value in buffer
                        for (i = 0; i < buffer.items.size(); i++) {
                            if ( ! buffer.items.get(i).getFlag(ZBX_METRIC_FLAG_PERSISTENT) ) {
                                DataObject obj = buffer.items.remove(i);
                                Util.log(Util.LOG_DEBUG,"remove element [%d] Key:'%s:%s' '%s'", i, obj.getHost(), obj.getKey(), obj.getValue());
                                buffer.items.add(dobj);
                                if (dobj.getFlag(ZBX_METRIC_FLAG_PERSISTENT))
                                    buffer.pcount++;
                                ret = true;
                                break;
                            }//if found
                        }//for
                    }//if(persistent or non-persistent but still not found)
                }//if(buffer is not full)
            }//sync
        }//if(buffer is more then half-full)

        Util.log(Util.LOG_DEBUG,"End of processValue(): %b", ret);
        return ret;
    }//processValue()

    private static boolean isNeededMetaUpdate(ActiveCheckMetric metric, boolean old_state_unsupported) {
        boolean ret = false;

        Util.log(Util.LOG_DEBUG,"in isNeededMetaUpdate(): key:%s", metric.key);
        if (0 != (ZBX_METRIC_FLAG_LOG & metric.flags)) {
            //meta information update is needed if:
            //- lastlogsize or mtime changed since we last sent within this check
            //- nothing was sent during this check and state changed from notsupported to normal
            if ( metric.lastlogsize_sent != metric.lastlogsize || metric.mtime_ms_sent != metric.mtime_ms ||
                                   old_state_unsupported != metric.state_unsupported) {
                Util.log(Util.LOG_DEBUG, " metric.lastlogsize_sent=%d, metric.lastlogsize=%d, metric.mtime_ms_sent=%d metric.mtime_ms=%d, old_state_unsupported=%b, metric.state_unsupported=%b",
                        metric.lastlogsize_sent, metric.lastlogsize, metric.mtime_ms_sent, metric.mtime_ms, old_state_unsupported, metric.state_unsupported);
                ret = true;
            }//if
        }//if
        Util.log(Util.LOG_DEBUG,"End of isNeededMetaUpdate(): %b", ret);
        return ret;
    }//idNeededMetaUpdate

    private void processLogCheck(ActiveCheckMetric metric) throws ZbxException {

        Util.log(Util.LOG_DEBUG,"in processLogCheck(): key:%s", metric.key);
        //!!not implemented yet
        Util.log(Util.LOG_DEBUG,"End of processLogCheck()");
        throw new ZbxException("Processing of log files is not implemented yet.");
    }//processLogCheck()

    private void processEventLogCheck(ActiveCheckMetric metric) throws ZbxException {

        Util.log(Util.LOG_DEBUG,"in processEventLogCheck(): key:'%s', lastlogsize:%d (%08x), lastsent=%d, mtime_ms=%d, mtime_ms_sent=%d",
                metric.key, metric.lastlogsize, metric.lastlogsize, metric.lastlogsize_sent, metric.mtime_ms, metric.mtime_ms_sent);
        try {
            MessageQueue mqueue = null;
            boolean skipMode = true;
            String curPar, mqueueName;
            AgentRequest req = metric.agent_request;
            int keySeverity, maxLines, s_count = 0, p_count = 0, max_s_count, N = req.getNparam();
            ZbxRegexp regex_value, regex_source, regex_eventid, regex_user;

            //parameters check; first parameter must be specified, all other - optional
            if (1 > N || N > 8)
                throw new ZbxException("Invalid number of parameters.");
            //param1: mqueue name. If not specified as fully qualified IFS path name, use default path
            if ("".equals(mqueueName = req.getParam(0)))
                throw new ZbxException("Invalid first parameter (message queue name).");
            if ('/' != mqueueName.charAt(0))
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
            max_s_count = (int)((maxLines * metric.refresh_ms)/1000);
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
                Enumeration mlist = null;
                mqueue = new MessageQueue(this.system, mqueueName);
                mqueue.setListDirection(true);  //from oldest to newest
                mqueue.setSeverity(keySeverity);
                if (0l == metric.lastlogsize)
                    mqueue.setUserStartingMessageKey(skipMode ? MessageQueue.NEWEST : MessageQueue.OLDEST);
                else
                    mqueue.setUserStartingMessageKey(Util.long2key(metric.lastlogsize));
                try {
                   mlist = mqueue.getMessages();
                } catch (AS400Exception ex) {
                    //Exception if this key not found. In this case set it to the oldest one.
                    Util.log(Util.LOG_WARNING," AS400Exception, message is: '%s'", ex);
                    if ("CPF2410".equals(ex.getAS400Message().getID())) {
                        Util.log(Util.LOG_WARNING," Key %d (%08x) not found for the message queue %s, starting from the oldest one",
                                metric.lastlogsize, metric.lastlogsize, mqueueName);
                        //Util.log(Util.LOG_WARNING,"   MessageQueue.OLDEST = %d, MessageQueue.NEWEST = %d", Util.key2long(MessageQueue.OLDEST), Util.key2long(MessageQueue.NEWEST));
                        metric.lastlogsize = NON_EXISTING_MESSAGE;
                        mqueue.setUserStartingMessageKey(MessageQueue.OLDEST);
                        mlist = mqueue.getMessages();
                    } else {
                        throw new ZbxException(ex.getAS400Message().getText());
                    }//if
                }//try-catch
                while (mlist.hasMoreElements()) {
                    if (!Config.running)
                        break;
                    QueuedMessage msg = (QueuedMessage)mlist.nextElement();
                    long cur_lastlogsize = Util.key2long(msg.getKey());
                    if (cur_lastlogsize == metric.lastlogsize)
                        continue;   //already processed message
                    int    cur_severity = msg.getSeverity();
                    String cur_eventid  = msg.getID();
                    String cur_source   = msg.getFromJobName();
                    String cur_value    = msg.getText();
                    String cur_user     = msg.getCurrentUser();
                    int    cur_type     = msg.getType();
                    long   cur_mtime_ms = -1l; try { cur_mtime_ms = msg.getDate().getTimeInMillis(); } catch (NullPointerException ex) {}
                    long   eventid      = 0l;
                    try { eventid = Long.parseLong(cur_eventid.replaceAll("[^0-9+-]*","")); } catch (NumberFormatException ex) { ; }
                    Util.log(Util.LOG_DEBUG," New message processed: Key=%08x (%d), severity=%d, Type=%d, User='%s', EventID='%s', JobName='%s', timestamp_ms=%d, Value='%s'",
                            cur_lastlogsize, cur_lastlogsize, cur_severity, cur_type, cur_user, cur_eventid, cur_source, cur_mtime_ms, cur_value);

                    //ignore some messages: with old or invalid timestamps and with type=='reply'
                    if (metric.mtime_ms > cur_mtime_ms) {
                        Util.log(Util.LOG_DEBUG,"  Message with old mtime (%d < %d), ignored", cur_mtime_ms, metric.mtime_ms);
                        continue;
                    }//if(cur_mtime_ms)
                    switch (cur_type) {
                    case AS400Message.REPLY_NOT_VALIDITY_CHECKED:
                    case AS400Message.REPLY_VALIDITY_CHECKED:
                    case AS400Message.REPLY_MESSAGE_DEFAULT_USED:
                    case AS400Message.REPLY_SYSTEM_DEFAULT_USED:
                    case AS400Message.REPLY_FROM_SYSTEM_REPLY_LIST:
                        Util.log(Util.LOG_DEBUG,"  Message type is 'reply' (%d), ignored", cur_type);
                        continue;
                    }//switch-case

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
                        DataObject dobj = new DataObject(metric.key_orig, cur_value);
                        dobj.setTimestamp(msg.getDate().getTimeInMillis() / 1000);  //in seconds
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
                    if (!matched | processed) {
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
                if (null != mqueue)
                    try { mqueue.close(); } catch (Exception ex) { Util.log(Util.LOG_ERROR," processEventLogCheck() error: %s",ex); }
            }//try-catch
        } finally {
            Util.log(Util.LOG_DEBUG,"End of processEventLogCheck()");
        }//try-catch
    }//processEventLogCheck()

    private void processCommonCheck(ActiveCheckMetric metric) throws ZbxException {

        Util.log(Util.LOG_DEBUG,"in processCommonCheck(): key_name='%s'", metric.key);
        try {
            DataObject result = ZabbixAgent.process(metric.agent_request);
            result.setFlag(metric.flags);
            result.setKey (metric.key_orig);
            processValue(Config.getHostname(), result);
        } catch (ZbxException ex) {
            if (isAs400CommError() && (Config.getZbxMetric(metric.agent_request.getKeyName()).getFlags() & Util.CF_AS400COMM) != 0)
                    Util.log(Util.LOG_DEBUG," ActiveCheck.processCommonCheck(): error %s when thread is in \"communitation to AS/400 error\" state, check ignored", ex);
            else
                throw ex;
        } finally {
            Util.log(Util.LOG_DEBUG,"End of processCommonCheck()");
        }//try-catch
    }//processCommonCheck()

    private void processActiveChecks() {
        boolean ret;
        long now = System.currentTimeMillis();

        Util.log(Util.LOG_DEBUG,"in processActiveChecks() server:'%s' port:%d", serverActive, serverActivePort);
        for (ActiveCheckMetric metric: active_metrics) {
            if (!Config.running)
                break;
            if (now < metric.nextcheck_ms)
                continue;
            if (!isMetricReadyToProcess(metric))
                continue;

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
                        metric.refresh_unsupported = false;
                    }//if(became supported)

                    if (isNeededMetaUpdate(metric, old_state_unsupported)) {
                        //meta information update
                        DataObject dobj = new DataObject(metric.key_orig, null);
                        dobj.setStateNotsupported(metric.state_unsupported);
                        dobj.setMtime(metric.mtime_ms / 1000);  //in seconds
                        dobj.setLastlogsize(metric.lastlogsize);
                        dobj.setFlags(metric.flags);
                        processValue(Config.getHostname(), dobj);
                    }//if(NeededMetaUpdate)

                    //remove "new metric" flag
                    //metric.flags &= ~ZBX_METRIC_FLAG_NEW;
                }//if(error_count==0)
            } catch (ZbxException ex) {
                if (0 < metric.error_count++) {
                    metric.state_unsupported   = true;
                    metric.refresh_unsupported = false;
                    metric.error_count = 0;
                    Util.log(Util.LOG_WARNING,"active check \"%s\" is not supported: %s", metric.key,
                                ex.getMessage());
                    DataObject dobj = new DataObject(metric.key_orig, ex.getMessage());
                    dobj.setStateNotsupported(true);
                    dobj.setMtime(metric.mtime_ms / 1000);  //in seconds
                    dobj.setLastlogsize(metric.lastlogsize);
                    dobj.setFlags(metric.flags);
                    processValue(Config.getHostname(), dobj);
                }//if(it is not the first error)
            }//try-catch

            metric.nextcheck_ms = System.currentTimeMillis() + metric.refresh_ms;
        }//for
        Util.log(Util.LOG_DEBUG,"End of processActiveChecks()");
    }//processActiveChecks()

    public void run() {
        long nextcheck = 0l, nextrefresh = 0l, nextsend = 0l;

        Util.log(Util.LOG_INFO,"agent #%d (%s) started [%s #%d]", server_num, orig_serverActive,
                Thread.currentThread().getName(), server_num + 1);

        try {
            while (Config.running) {

                if (nextsend <= System.currentTimeMillis()) {
                    sendBuffer();
                    nextsend = System.currentTimeMillis() + 1000l;
                }//if(nextsend)

                if (nextrefresh <= System.currentTimeMillis()) {
                    if (refreshActiveChecks())
                        nextrefresh = System.currentTimeMillis() + 
                                        Config.getRefreshActiveChecks() * 1000; //OK
                    else
                        nextrefresh = System.currentTimeMillis() + 60000l;      //Fail
                }//if(nextrefresh)

                if (nextcheck <= System.currentTimeMillis() && 
                                                        Config.getBufferSize() / 2 > buffer.pcount) {
                    processActiveChecks();
                    if (Config.getBufferSize() / 2 <= buffer.pcount) {
                        //failed to complete processing active checks
                        continue;
                    }//if
                    nextcheck = getMinNextcheck();
                    if (nextcheck <= 0l)
                        nextcheck = System.currentTimeMillis() + 60000l;
                } else {
/*
                    if (system.isConnected())
                        system.disconnectAllServices();
*/
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ex) {
                        Config.running = false;
                    }//try-catch
                }//if(nextrefresh)

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
