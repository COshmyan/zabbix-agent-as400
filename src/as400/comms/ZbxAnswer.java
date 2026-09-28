package as400.comms;

public class ZbxAnswer {

    //instance variables
    boolean success;
    String  result;
    
    public ZbxAnswer(boolean success, String result) {
        this.success = success;
        this.result  = result;
    }//constructor ZbxAnswer()

    public String toString() {
        StringBuilder sb = new StringBuilder();
        //universal fields, present for all types of requests
        sb.append("{\"version\":\"");
        sb.append(ZbxRequest.PROTOCOL_VERSION);
        sb.append("\",\"variant\":");
        sb.append(as400.metric.GenericMetrics.VARIANT);
        sb.append(",\"data\":[{");
        if (success) {
            sb.append("\"value\":\"");
        } else {
            sb.append("\"error\":\"");
        }
        sb.append(org.json.simple.JSONValue.escape(result));
        sb.append("\"}]}");
        return sb.toString();
    }//toString()

}//class ZbxAnswer
