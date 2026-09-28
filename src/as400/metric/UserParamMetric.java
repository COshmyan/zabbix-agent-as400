package as400.metric;
import as400.*;
import java.io.IOException;

public class UserParamMetric extends ZbxMetric {

    //class fields
    String  command;

    public UserParamMetric(String key_name, int flags, String command) throws ZbxException {
        super(key_name, flags);
        this.command = command;
    }//constructor ZbxMetric()

    public String getCommand() { return command; }

    public DataObject process(AgentRequest req) throws ZbxException, IOException {
        //the original C program has the check about CF_USERPARAMETER flag here,
        //but in out case we always have this flag for this sub-class, so it is excessively
        String cmd;
        //replace in AgentRequest params with the needed shell command
        if (0 != (this.flags & Util.CF_HAVEPARAMS)) {
            cmd = Util.replaceParam(this.command, req);
        } else {
            cmd = this.command;
        }//if(CF_HAVEPARAMS)
        return new DataObject(req.getUnparsedKey(), As400Metrics.executeStr(cmd, As400Metrics.WAIT));
    }//process()

}//class UserParamMetric
