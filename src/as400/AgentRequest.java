package as400;
import java.util.ArrayList;
import java.util.regex.*;

public class AgentRequest {

    //static class variables
    static final Pattern key_pattern = Pattern.compile("([-0-9a-zA-Z_\\.]+)(?:\\[(.*)\\])?");

    //class fields
    String              unparsed_key;
    String              key_name;
    ArrayList<String>   params;
    long                timeout_ms;

    /*
     * Parse the string onto key_name and its parameters
     * Format of the key descrbed here: https://www.zabbix.com/documentation/3.0/manual/config/items/item/key
     * Throws ZbxException if format is not valid
     * @param str - input string
     */
    public AgentRequest(String str) throws ZbxException {
        Matcher m = key_pattern.matcher(str);
        if (!m.matches())
            throw new ZbxException("Key '" + str + "' has invalid format");
        this.unparsed_key = str;
        this.key_name = m.group(1);
        String p = m.group(2);
        if (null != p) {
            this.params = new ArrayList<String>();
            parseParameters(p);
        } else {
            this.params = null;
        }//if(params)

        if (Util.LOG_TRACE1 <= Config.getDebugLevel()) {
            Util.log(Util.LOG_TRACE1, "constuctor AgentRequest(): str='%s', key_name='%s'", str, key_name);
            StringBuilder s = new StringBuilder(" parameters list: ");
            if (null == params) {
                s.append("null");
            } else {
                s.append('[');
                for (int i=0; i<params.size(); i++) {
                    if (i>0)
                        s.append(", ");
                    s.append("'");
                    s.append(params.get(i));
                    s.append("'");
                }//for
                s.append(']');
            }//if(exists)
            Util.log(Util.LOG_TRACE1, s.toString());
        }//if(debug)
    }//constuctor AgentRequest()

    //can be used and extended in sub-classes
    protected AgentRequest() throws ZbxException {
    }//constuctor AgentRequest()

    /*
     * Parse parameters into 'params' list (unquoting if necessary)
     * @param str - input string with parameters only
     *
     */
    protected void parseParameters(String str) throws ZbxException {
        int p1 = 0, p2, len = str.length();

        while (p1 < len) {
            if ('\"' == str.charAt(p1)) {
                //this is a "quoted string" parameter
                StringBuilder res = new StringBuilder();
                p2 = ++p1;
                while (true) {
                    p2 = str.indexOf('\"', p2);
                    if (p2 < 0)
                        throw new ZbxException("parameter string '"+str+"' contains uncompleted quoting");
                    if ('\\' == str.charAt(p2-1)) {
                        res.append(str.substring(p1, p2-1));
                        p1 = p2++;
                    } else {
                        res.append(str.substring(p1, p2++));
                        this.params.add(res.toString());
                        break;
                    }//if
                }//while inside the quoted parameter
//          } else if ('[' == str.charAt(p1)) {
                //array: not implemented yet
            } else {
                //non-quoted usual parameter: only comma is forbidden (used as separator)
                p2 = str.indexOf(',', p1);
                if (p2 < 0) {
                    this.params.add(str.substring(p1));
                    break;
                } else {
                    this.params.add(str.substring(p1, p2));
                }//if(comma (not) found)
            }//if(starts with quote)
            //parameter was processed, so p2 must point to comma or to end of string
            if (p2 < len && ',' != str.charAt(p2))
                throw new ZbxException ("parameter string '"+str+"' has invalid format (something instead of comma)");
            p1 = p2 + 1;
        }//while (loop by parameters)
    }//parseParameters()

    public void replaceParams(String param) {
        this.params.clear();
        this.params.add(param);
    }//replaceParams()

    public String getUnparsedKey() {
        return unparsed_key;
    }//getUnparsedKey()

    public String getKeyName() {
        return key_name;
    }//getKeyName()

    public int getNparam() {
        if (null == this.params)
            return 0;
        return this.params.size();
    }//getNparam()

    public long getTimeout() {
        return this.timeout_ms;
    }//getTimeout()

    public void setTimeout(long timeout_ms) {
        this.timeout_ms = timeout_ms;
    }//setTimeout

    //either 'key[]' or 'key[""]', but not 'key'
    public boolean emptyArguments() {
        return (null != this.params && 
            (0 == this.params.size() || 1 == this.params.size() && 0 == this.params.get(0).length())
            );
    }//emptyArguments()

    public boolean nullArguments() {
        return (null == this.params);
    }//emptyArguments()

    public String getParam(int i) {
        if (getNparam() <= i)
            return null;
        return this.params.get(i);
    }//getParam()

}//class AgentRequest