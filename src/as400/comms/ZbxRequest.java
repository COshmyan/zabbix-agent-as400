package as400.comms;
import as400.*;
import as400.thread.*;
import as400.thread.ActiveCheck;
import java.util.List;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2016</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class ZbxRequest {

    //constants
    public static final int REQUEST_CONFIG    = 0;
    public static final int REQUEST_DATA      = 1;
    public static final int REQUEST_HEARTBEAT = 2;
           static final String REQUEST_TEXT[] = {
                "active checks",
                "agent data",
                "active check heartbeat"
           };
    public static final String PROTOCOL_VERSION = "7.0.0";

    int    req_type;
    String session;
    String host_metadata;
    String host_interface;
    String command_results;
    long   config_revision;
    ActiveCheck.ActiveBuffer data;

    public ZbxRequest() {
        this.req_type = REQUEST_HEARTBEAT;
    }//constructor ZbxRequest()

    public ZbxRequest(String session, long config_revision, String host_metadata, String host_interface) {
        this.session = session;
        this.config_revision = config_revision;
        this.host_metadata = host_metadata;
        this.host_interface = host_interface;
        this.command_results = null;
        this.req_type = REQUEST_CONFIG;
    }//constructor ZbxRequest()

    public ZbxRequest(String session, ActiveCheck.ActiveBuffer data, String command_results) {
        this.session = session;
        this.data = data;
        this.req_type = REQUEST_DATA;
        this.command_results = command_results;
    }//constructor ZbxRequest()

    public String getMetadata() {
        return host_metadata;
    }

    public void setMetadata(String host_metadata) {
        this.host_metadata = host_metadata;
    }

    public ActiveCheck.ActiveBuffer getData() {
        return data;
    }

    public void setData(ActiveCheck.ActiveBuffer data) {
        this.data = data;
    }

    public void setData(DataObject dobj) {
        this.data = new ActiveCheck.ActiveBuffer(dobj);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        //universal fields, present for all types of requests
        sb.append("{\n\"request\":\"");
        sb.append(REQUEST_TEXT[req_type]);
        sb.append("\",\n\"host\":\"");
        sb.append(org.json.simple.JSONValue.escape(Config.getHostname()));
        sb.append("\",\n\"version\":\"");
//        sb.append(as400.metric.GenericMetrics.VERSION);
        sb.append(PROTOCOL_VERSION);
        sb.append("\",\n\"variant\":" + as400.metric.GenericMetrics.VARIANT + ",\n");
        //"session" for all requests types excluding "active check heartbeat"
        if (REQUEST_HEARTBEAT == req_type) {
            sb.append("\"heartbeat_freq\":");
            sb.append(Config.getHeartbeatFrequency());
        } else {//req_type!=HEARTBEAT
            sb.append("\"session\":\"");
            sb.append(session);
            sb.append("\"");
            if (REQUEST_DATA == req_type) {
                if (null != data) {
                    sb.append(",\n\"data\":[\n");
                    sb.append(org.json.simple.JSONValue.toJSONString(data));
                    sb.append("\n]");
                }//if(data!=null)
                sb.append(command_results);
                long ts = Util.currentTimeMillis();
                sb.append(",\n\"clock\":");
                sb.append(ts / 1000);           //ms -> s
                sb.append(",\n\"ns\":");
                sb.append((ts % 1000)*1000000); //ms -> ns
            } else {//REQUEST_CONFIG
                if (null != host_metadata) {
                    sb.append(",\n\"host_metadata\":\"");
                    sb.append(org.json.simple.JSONValue.escape(host_metadata));
                    sb.append("\"");
                }//if(host_netadata!=null)
                if (null != host_interface) {
                    sb.append(",\n\"interface\":\"");
                    sb.append(org.json.simple.JSONValue.escape(host_interface));
                    sb.append("\"");
                }//if(host_interface!=null)
                String listen_ip = Config.getListenIP();
                if (null != listen_ip) {
                    sb.append(",\n\"ip\":\"");
                    sb.append(org.json.simple.JSONValue.escape(listen_ip));
                    sb.append("\"");
                }//if(listen_ip!=null)
                int listen_port = Config.getListenPort();
                if (Config.DEFAULT_LISTEN_PORT != listen_port) {
                    sb.append(",\n\"port\":");
                    sb.append(listen_port);
                }//if(listen_port)
                sb.append(",\n\"config_revision\":");
                sb.append(config_revision);
            }//if(req_type)
        }//if(req_type==HEARTBEAT)

        sb.append("\n}");
        return sb.toString();
    }//toString()

}//class ZbxRequest
