package as400;
import java.util.ArrayList;
import java.util.regex.*;

public class AgentRequest {

    //static class variables
    static Pattern      key_pattern         = Pattern.compile("[-0-9a-zA-Z_\\.]+(\\[.*\\])?");
//    Pattern         key_name_pattern    = Pattern.compile("[^-0-9a-zA-Z_\\.]");

    //class fields
    String              unparsed_key;
    String              key_name;
    ArrayList<String>   params;
    long                lastlogsize;
    long                mtime_ms;
/*
    public AgentRequest(String key_name, long lastlogsize, long mtime_ms) {
        this.key_name   = key_name;
        this.lastlogsize= lastlogsize;
        this.mtime_ms   = mtime_ms;
        this.params     = new ArrayList<String>();
    }//constuctor AgentRequest()

    public AgentRequest(String key_name, long lastlogsize, long mtime_ms, String str) {
        this(key_name, lastlogsize, mtime_ms);
        parseString(str);
    }//constuctor AgentRequest()
*/
    /*
     * Parse the string onto key_name and its parameters
     * Format of the key descrbed here: https://www.zabbix.com/documentation/3.0/manual/config/items/item/key
     * Throws ZbxException if format is not valid
     * @param str - input string
     */
    public AgentRequest(String str) throws ZbxException {
        int pos;
        if (!key_pattern.matcher(str).matches())
            throw new ZbxException("Key '"+str+"' has invalid format");
        this.unparsed_key = str;
        //key_name = key_name_pattern.split(str, 0)[0];
        pos = str.indexOf('[');
        if (pos<0) {
            this.key_name = str;
        } else {
            this.key_name = str.substring(0,pos);
            this.params   = new ArrayList<String>();
            parseParameters(str.substring(pos+1,str.length()-1));
        }
        if (Util.LOG_DEBUG <= Config.getDebugLevel()) {
            Util.log(Util.LOG_DEBUG, "constuctor AgentRequest(): str='%s', key_name='%s'", str, key_name);
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
            Util.log(Util.LOG_DEBUG, s.toString());
        }//if(debug)
    }//constuctor AgentRequest()

    /*
     * Parse parameters into 'params' list (unquoting if necessary)
     * @param str - input string with parameters only
     *
     */
    private void parseParameters(String str) throws ZbxException {
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
                        params.add(res.toString());
                        break;
                    }//if
                }//while inside the quoted parameter
//          } else if ('[' == str.charAt(p1)) {
                //array: not implemented yet
            } else {
                //non-quoted usual parameter: only comma is forbidden (used as separator)
                p2 = str.indexOf(',', p1);
                if (p2 < 0) {
                    params.add(str.substring(p1));
                    break;
                } else {
                    params.add(str.substring(p1, p2));
                }//if(comma (not) found)
            }//if(starts with quote)
            //parameter was processed, so p1 must point to comma or to end of string
            if (p2 < len && ',' != str.charAt(p2))
                throw new ZbxException ("parameter string '"+str+"' has invalid format (something instead of comma)");
            p1 = p2 + 1;
        }//while (loop by parameters)
    }//parseParameters()

    public String getUnparsedKey() {
        return unparsed_key;
    }//getUnparsedKey()

    public String getKeyName() {
        return key_name;
    }//getKeyName()

    public int getNparam() {
        if (params == null)
            return 0;
        return params.size();
    }//getNparam()

    public String getParam(int i) {
        if (params == null)
            return null;
        return params.get(i);
    }//getParam()

}//class AgentRequest