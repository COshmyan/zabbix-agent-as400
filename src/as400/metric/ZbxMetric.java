package as400.metric;
import as400.*;
import java.io.IOException;

abstract public class ZbxMetric {

    //class fields
    String  key_name;
    int     flags;

    ZbxMetric(String key_name, int flags) throws ZbxException {
        this.key_name   = key_name;
        this.flags      = flags;
        if (Config.commands.containsKey(key_name))
            throw new ZbxException("ZbxMetric with key_name '" + key_name + "' already exists");
        Config.commands.put(key_name, this);
        Util.log(Util.LOG_DEBUG,"ZbxMetric() constructor: metric '%s' successfully added", key_name);
    }//constructor ZbxMetric()

    public int getFlags() { return flags; }

    abstract public DataObject process(AgentRequest req) throws ZbxException, IOException;

}//class ZbxMetric
