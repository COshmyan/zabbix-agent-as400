//package lv.rietumu.as400;
package as400;
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

    String request;
    String host_metadata;
    ActiveCheck.ActiveBuffer data;

    public ZbxRequest() {
        this.request = "active checks";
    }//constructor ZbxRequest()

    public ZbxRequest(String host_metadata) {
        this();
        this.host_metadata = host_metadata;
    }//constructor ZbxRequest()

    public ZbxRequest(ActiveCheck.ActiveBuffer data) {
        this.data = data;
        this.request = "agent data";
    }//constructor ZbxRequest()

    public String getRequest() {
        return request;
    }

    public void setRequest(String request) {
        this.request = request;
    }

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
        StringBuffer sb = new StringBuffer();
        sb.append("{\n\"request\":\"");
        sb.append(request);
        sb.append("\",\n");
        if (null!=data) {
            sb.append("\"data\":[\n");
            sb.append(org.json.simple.JSONValue.toJSONString(data));
            sb.append("]");
        } else {
            sb.append("\"host\":\"");
            sb.append(org.json.simple.JSONValue.escape(Config.getHostname()));
            sb.append("\"");
            if (null!=host_metadata) {
            sb.append(",\n\"host_metadata\":\"");
            sb.append(org.json.simple.JSONValue.escape(host_metadata));
            sb.append("\"");
            }
        }
        long ts = Util.currentTimeMillis();
        sb.append(",\n\"clock\":");
        sb.append(ts / 1000);           //ms -> s
        sb.append(",\n\"ns\":");
        sb.append((ts % 1000)*1000000); //ms -> ns
        sb.append("\n}");
        return sb.toString();
    }//toString()

}//class ZbxRequest
