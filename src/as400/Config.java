package as400;
import as400.metric.ZbxMetric;
import as400.comms.*;
import as400.thread.*;
import java.beans.PropertyVetoException;
import java.io.*;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Enumeration;

public class Config {

    //constants
    public static final int HOST_METADATA_LEN  = 2034;
    public static final int HOST_METADATA_ITEM_LEN = 65535;
    public static final int HOST_INTERFACE_LEN = 255;
    public static final int DEFAULT_LISTEN_PORT=10050;
           static final int TYPE_INT           = 0;
           static final int TYPE_STRING        = 1;
           static final int TYPE_MULTISTRING   = 2;
           static final int TYPE_UINT64        = 3;
           static final int TYPE_STRING_LIST   = 4;
           static final String ip_regexp       = "[0-9]{1,3}.[0-9]{1,3}.[0-9]{1,3}.[0-9]{1,3}";
           static final boolean ZBX_KEY_ACCESS_ALLOW = true;
           static final boolean ZBX_KEY_ACCESS_DENY  = false;

    //internal (nested) classes
    static class ZbxAlias {
        //instance variables
        String name;
        AgentRequest value;
        boolean name_wildcard, value_wildcard;
    
        private ZbxAlias(String name, AgentRequest value) {
            this.name  = name;
            this.value = value;
            this.name_wildcard = name.endsWith("[*]");
            this.value_wildcard = value.getUnparsedKey().endsWith("[*]");
        }//constructor ZbxAlias()
    }//internal class ZbxAlias

    public static class KeyAccessAgentRequest extends AgentRequest {
        static final java.util.regex.Pattern key_pattern = java.util.regex.Pattern.compile("([-0-9a-zA-Z_\\.\\*]+)(?:\\[(.*)\\])?");

        public KeyAccessAgentRequest(String str) throws ZbxException {
            super();
            java.util.regex.Matcher m = key_pattern.matcher(str);
            if (!m.matches())
                throw new ZbxException("Key '"+str+"' has invalid format");
            this.unparsed_key = str;
            this.key_name = m.group(1);
            String p = m.group(2);
            if (null != p) {//for AllowKey/DenyKey we need to differ "key" and "key[]"
                this.params = new ArrayList<String>();
                parseParameters(p);
            }//if(params)
        }//KeyAccessAgentRequest
    }//internal class KeyAccessAgentRequest

    public static class ZbxKeyAccessRule {

        //instance variables
        String pattern;
        String key;
        String[] params;
        boolean type;
        boolean empty_arguments;

        private ZbxKeyAccessRule(String str, boolean type) throws ZbxException {
            AgentRequest req = new KeyAccessAgentRequest(str);
            this.pattern = str;
            this.key = Util.zbxWildcardMinimize(req.getKeyName());
            this.params = new String[] {};
            this.type = type;
            this.empty_arguments = req.emptyArguments();

            int n = req.getNparam();
            if (0 < n) {
                String s = null;
                ArrayList<String> p = new ArrayList<String>(n);
                for (int i=0; i<n; i++) {
                    s = Util.zbxWildcardMinimize(req.getParam(i));
                    p.add(s);
                }//for
                //remove repeated trailing "*" parameters
                if ("*".equals(s)) {
                    while (0 < (n = p.size() - 1) && "*".equals((String)p.get(n-1))) {
                        p.remove(n);
                    }//while
                }//if(last parameter is "*")
                this.params = p.toArray(this.params);
                p.clear();
            }//if(n>0)
        }//constructor ZbxKeyAccessRule()

        public String toString() {
            StringBuilder buf = new StringBuilder();
            buf.append("Type: ");
            buf.append(this.type ? "Allow" : " Deny");
            buf.append(", Pattern: '");
            buf.append(this.pattern);
            buf.append("', Key: '");
            buf.append(this.key);
            buf.append("', Params: ");
            if (0 < this.params.length || this.empty_arguments) {
                buf.append('[');
                for (int i=0; i<this.params.length; i++) {
                    if (0 != i)
                        buf.append(',');
                    buf.append('\'');
                    buf.append(this.params[i]);
                    buf.append('\'');
                }
                buf.append(']');
            } else {
                buf.append("none");
            }
            buf.append(", empty_arguments: ");
            buf.append(empty_arguments);
            buf.append('.');
            return buf.toString();
        }//toString()

        public boolean equals(Object o) {
            if (! (o instanceof ZbxKeyAccessRule) || 
                ! this.key.equals( ((ZbxKeyAccessRule)o).key ) || 
                ( this.empty_arguments != ((ZbxKeyAccessRule)o).empty_arguments ) || 
                ( this.params.length != ((ZbxKeyAccessRule)o).params.length )
               )
                return false;
            for (int i = 0; i < this.params.length; i++)
                if (! this.params[i].equals(((ZbxKeyAccessRule)o).params[i]) )
                    return false;
            return true;
        }//equals()

    }//internal class ZbxKeyAccessRule

    //static variables
    public static volatile boolean running                      = true;
    public static Hashtable<String, ZbxMetric> commands         = new Hashtable<String, ZbxMetric>();
    private static boolean configured                           = false;
    private static Hashtable<String, ZbxAlias> aliases          = null;
    private static ArrayList<ZbxKeyAccessRule> key_access_rules = new ArrayList<ZbxKeyAccessRule>();
    public  static ZbxKeyAccessRule[] key_access_rules_array    = null;

    //AS400 related variables
    private static       String asPassword      = "*CURRENT";
    private static       String as400ServerHost = "localhost";
    private static      boolean as400EventIdAsMessagePrefix = true;
    private static      boolean as400JobAsMessagePrefix     = false;
    private static      boolean as400UserAsMessagePrefix    = false;

    //variables from config file
    private static boolean AllowRoot            = false;
    private static boolean EnableRemoteCommands = false;
    private static boolean LogRemoteCommands    = false;
    private static boolean UnsafeUserParameters = false;
    private static ZbxSubnet[] HostsAllowed     = null;
    private static String HostInterface         = null;
    private static String HostInterfaceItem     = null;
    private static String HostMetadata          = null;
    private static String HostMetadataItem      = null;
    private static String Hostname              = null;
    private static String HostnameItem          = null;
    private static String ListenIP              = null;
    private static String LoadModulePath        = null;
    private static String LogFile               = "/tmp/zabbix_agentd.log";
    private static String LogType               = "file";
    private static String PidFile               = "/tmp/zabbix_agentd.pid";
    private static String[] ServerActive        = null;
    private static String SourceIP              = null;
    private static String User                  = null;
    private static String UserParameterDir      = null;
    private static int    BufferSend            = 5;
    private static int    BufferSize            = 100;
    private static int    DebugLevel            = 3;
    private static int    HeartbeatFrequency    = 60;
    private static int    ListenBacklog         = 0;
    private static int    ListenPort            = DEFAULT_LISTEN_PORT;
    private static long   LogFileSize           = 1 * 1024 * 1024;
    private static int    MaxLinesPerSecond     = 100;
    private static int    RefreshActiveChecks   = 5;
    private static int    StartAgents           = 3;
    private static int    Timeout_ms            = 3000;
    private static ActiveCheck []activeChecks   = new ActiveCheck [0];
    //include
    private static ArrayList<String> UserParameter  = null;
    private static ArrayList<String> LoadModule     = null;

    public static boolean as400EventIdAsMessagePrefix(){return as400EventIdAsMessagePrefix;}
    public static boolean as400JobAsMessagePrefix() {return as400JobAsMessagePrefix;}
    public static boolean as400UserAsMessagePrefix(){return as400UserAsMessagePrefix;}
    public static String  getAs400Password()        {return asPassword;             }
    public static String  getAs400ServerHost()      {return as400ServerHost;        }
    public static boolean getAllowRoot()            {return AllowRoot;              }
    public static int     getBufferSend()           {return BufferSend;             }
    public static int     getBufferSize()           {return BufferSize;             }
    public static int     getDebugLevel()           {return DebugLevel;             }
    public static boolean getEnableRemoteCommands() {return EnableRemoteCommands;   }
    public static int     getHeartbeatFrequency()   {return HeartbeatFrequency;     }
    public static ZbxSubnet[] getHostsAllowed()     {return HostsAllowed;           }
    public static String  getHostInterface()        {return HostInterface;          }
    public static String  getHostInterfaceItem()    {return HostInterfaceItem;      }
    public static String  getHostMetadata()         {return HostMetadata;           }
    public static String  getHostMetadataItem()     {return HostMetadataItem;       }
    public static String  getHostname()             {return Hostname;               }
    public static String  getHostnameItem()         {return HostnameItem;           }
    public static int     getListenBacklog()        {return ListenBacklog;          }
    public static String  getListenIP()             {return ListenIP;               }
    public static int     getListenPort()           {return ListenPort;             }
    public static String  getLogFile()              {return LogFile;                }
    public static String  getLogType()              {return LogType;                }
    public static long    getLogFileSize()          {return LogFileSize;            }
    public static boolean getLogRemoteCommands()    {return LogRemoteCommands;      }
    public static int     getMaxLinesPerSecond()    {return MaxLinesPerSecond;      }
    public static String  getPidFile()              {return PidFile;                }
    public static int     getRefreshActiveChecks()  {return RefreshActiveChecks;    }
    public static String[] getServerActive()        {return ServerActive;           }
    public static String  getSourceIP()             {return SourceIP;               }
    public static int     getStartAgents()          {return StartAgents;            }
    public static int     getTimeout_ms()           {return Timeout_ms;             }
    public static String  getUser()                 {return User;                   }
    public static String  getUserParameterDir()     {return UserParameterDir;       }
    public static boolean getUnsafeUserParameters() {return UnsafeUserParameters;   }

    public static boolean isConfigured()            {return configured;             }
    public static ActiveCheck []getActiveChecks()   {return activeChecks;           }
    public static void    setActiveCheck(ActiveCheck []ac){activeChecks = ac;       }

    public static ZbxMetric getZbxMetric(String key) throws ZbxException {
        ZbxMetric command = commands.get(key);
        if (null == command) {
            Util.log(Util.LOG_ERROR, "Unsupported item key name: %s", key);
            throw new ZbxException("Unsupported item key name: " + key);
        }
        return command;
    }//getZbxMetric()

    public static int parseInt(String param_value, int min, int max) throws ZbxException {
        int ret;
        try {
            ret = Integer.parseInt(param_value);
        } catch (NumberFormatException ex) {
            throw new ZbxException("Invalid format for integer");
        }//try-catch
        if (min > ret)
            throw new ZbxException("Integer value must be minimum " + min);
        if (0 != max && max < ret)
            throw new ZbxException("Integer value must be maximum " + max);
        return ret;
    }//parseInt()

    private static boolean parseBoolean(String param_value) throws ZbxException {
        if (0 == parseInt(param_value, 0, 1))
            return false;
        else
            return true;
    }//parseBoolean()

    private static String[] parseList(String param_value) throws ZbxException {
        String []splitted = param_value.split(",");
        ArrayList<String> ret = new ArrayList<String>(splitted.length);

        for (int i = 0; i < splitted.length; i++) {
            String current = splitted[i].trim();
            if (0 < current.length())
                ret.add(current);
        }//for

        if (0 < ret.size())
            return ret.toArray(new String[] {});
        else
            return null;
    }//parseList()

    private static void parseZbxAlias(String line) throws ZbxException {

        if (null == aliases)
            aliases = new Hashtable<String, ZbxAlias>();

        int ind;
        AgentRequest name=null, value=null;

        for (ind=line.indexOf(':'); ind>0; ind=line.indexOf(':', ++ind)) {
            try {
                name  = new AgentRequest(line.substring(0, ind));
                value = new AgentRequest(line.substring(ind+1) );
                break;
            } catch (ZbxException ex) {
                Util.log(Util.LOG_TRACE1, "  parseZbxAliases(): ind=%d, line='%s', exception '%s' ",
                    ind, line, ex.getMessage());
            }//try-catch
        }//for(':')
        if (0 >= ind)
            throw new ZbxException("Wrong format of 'Alias=' parameter: '" + line +"'");

        if (aliases.containsKey(name.getUnparsedKey()))
            throw new ZbxException("Failed to add Alias '" + name.getUnparsedKey() + "': duplicate name");
        aliases.put(name.getUnparsedKey(), new ZbxAlias(name.getUnparsedKey(), value));
    }//parseZbxAliases()

    public static String zbxAliasGet(String key) {
        try {
            AgentRequest req = new AgentRequest(key);
            return zbxAliasGet(req).getUnparsedKey();
        } catch (ZbxException ex) {
            return key;
        }
    }//zbxAliasGet()

    public static AgentRequest zbxAliasGet(AgentRequest req) throws ZbxException {
        if (null != aliases) {
            String key = req.getUnparsedKey();

            ZbxAlias alias = aliases.get(key);
            if (null != alias) {
                Util.log(Util.LOG_DEBUG,"zbxAliasGet(): '" + key + "' -> '" + alias.value.getUnparsedKey() + "'");
                return alias.value;
            }

            int ind = key.indexOf('[');
            if (0 < ind) {
                String key_part = key.substring(0, ind+1);
                for (Enumeration<ZbxAlias> e = aliases.elements(); e.hasMoreElements(); ) {
                    alias = e.nextElement();
                    if (alias.name_wildcard) {
                        if (alias.name.startsWith(key_part)) {
                            if (alias.value_wildcard) {
                                key_part = alias.value.getUnparsedKey();
                                key = key_part.substring(0, key_part.length() - 3) + key.substring(ind);
                                Util.log(Util.LOG_DEBUG,"zbxAliasGet(): '" + req.getUnparsedKey() + "' -> '" + key + "'");
                                return new AgentRequest(key);
                            } else {
                                Util.log(Util.LOG_DEBUG,"zbxAliasGet(): '" + key + "' -> '" + alias.value.getUnparsedKey() + "'");
                                return alias.value;
                            }
                        }//found
                    }//if
                }//for()
            }//if

        }//if(aliases!=null)
        return req;
    }//zbxAliasGet()

    private static ZbxSubnet[] parseZbxSubnets(String param_value) throws ZbxException {
        String []servers = parseList(param_value);
        ArrayList<ZbxSubnet> ret = new ArrayList<ZbxSubnet>(servers.length);

        for (int i=0; i<servers.length; i++) {
            try {
                ret.add(new ZbxSubnet(servers[i]));
            } catch (ZbxException ex) {
                Util.log(Util.LOG_WARNING, "Wrong value in the 'Server=' config parameter: '%s'; %s, this address ignored.",
                    servers[i], ex.getMessage());
                continue;
            }//try-catch
        }//for

        if (0 < ret.size())
            return ret.toArray(new ZbxSubnet[] {});
        else
            return null;
    }//parseZbxSubnets()

    private static void logWarn(String param) {
        Util.log(Util.LOG_WARNING,"Parameter '%s' is defined, but not implemented - ignored", param);
    }//logWarn()

    private static void addZbxKeyAccessRule(String param, String str, boolean type) throws ZbxException {
        ZbxKeyAccessRule rule = new ZbxKeyAccessRule(str, type);
        Util.log(Util.LOG_TRACE1,"  %s", rule.toString());
        int ind = key_access_rules.indexOf(rule);
        if (0 > ind) {
            key_access_rules.add(rule);
        } else {
            Util.log(Util.LOG_WARNING,"%s access rule \"%s\" was not added because it %s another rule defined above",
                param, str, (key_access_rules.get(ind).type == type ? "duplicates" : "conflicts with"));
        }//if(contains)
    }//addZbxKeyAccessRule()

    private static void zbxFinalizeKeyAccessRulesConfiguration() throws ZbxException {
        ZbxKeyAccessRule rule, sysrun_deny = null, agent_exit_deny = null, agent_debug_deny = null;
        int i, rules_num, sysrun_index, agent_exit_index, agent_debug_index;

        try {
            sysrun_deny      = new ZbxKeyAccessRule("system.run[*]", false);
            agent_exit_deny  = new ZbxKeyAccessRule("agent.exit", false);
            agent_debug_deny = new ZbxKeyAccessRule("agent.debug.active.thread", false);
        } catch (ZbxException ex) {
            Util.log(Util.LOG_ERROR, ex, "Service default access rule creation error");
        }

        rules_num = key_access_rules.size();
        if ( 0 <= (sysrun_index = key_access_rules.indexOf(sysrun_deny)) )
            rules_num--;
        if ( 0 <= (agent_exit_index = key_access_rules.indexOf(agent_exit_deny)) )
            rules_num--;
        if ( 0 <= (agent_debug_index = key_access_rules.indexOf(agent_debug_deny)) )
            rules_num--;

        if (0 != rules_num) {
            //throw out all rules after '*', because they would never match
            for (i=0; i<key_access_rules.size(); i++) {
                rule = key_access_rules.get(i);
                if ( 0 == rule.params.length && "*".equals(rule.key) ) {
                    //'match all' rule also matches system.run[*] and all other rules
                    if (i < sysrun_index)
                        sysrun_index = i;
                    if (i < agent_exit_index)
                        agent_exit_index = i;
                    if (i < agent_debug_index)
                        agent_debug_index = i;
                    break;
                }//if(found "*")
            }//for

            if (i != key_access_rules.size()) {
                for (int j = key_access_rules.size() - 1; j > i; j--) {
                    rule = key_access_rules.get(j);
                    Util.log(Util.LOG_WARNING,"removed unreachable %s \"%s\" rule",
                        (rule.type ? "AllowKey" : "DenyKey"), rule.pattern);
                    key_access_rules.remove(j);
                }//for
            }//if (found)

            //trailing AllowKey rules are meaningless, because AllowKey=* is default behavior
            for (i = key_access_rules.size() - 1; 0 <= i; i--) {
                rule = key_access_rules.get(i);
                if (true != rule.type)
                    break;
                //system.run allow rules are not redundant because of default system.run[*] deny rule
                if (!"system.run".equals(rule.key)) {
                    if (i != sysrun_index && i != agent_exit_index && i != agent_debug_index ) {
                        Util.log(Util.LOG_WARNING,"removed redundant trailing AllowKey \"%s\" rule",
                            rule.pattern);
                        key_access_rules.remove(i);
                    }//if
                }//if
            }//for
  
            if (0 == key_access_rules.size()) {
                throw new ZbxException("Item key access rules are configured to match all keys, " +
                                       "indicating possible configuration problem.");
            }//if all explicit rules are removed as AllowKey
        }//if(rules_num!=0)

        if (0 > sysrun_index)
            key_access_rules.add(sysrun_deny);
        if (0 > agent_exit_index)
            key_access_rules.add(agent_exit_deny);
        if (0 > agent_debug_index)
            key_access_rules.add(agent_debug_deny);

        //optimizization: relocate rules to array, remove initial patterns
        key_access_rules_array = key_access_rules.toArray(new ZbxKeyAccessRule[] {});
        key_access_rules.clear();
        key_access_rules = null;
        StringBuilder buf = new StringBuilder();
        buf.append("Resulting access rules are (total: ");
        buf.append(key_access_rules_array.length);
        buf.append("):");
        for (i = 0; i < key_access_rules_array.length; i++) {
            buf.append(System.lineSeparator());
            buf.append("  ");
            buf.append(key_access_rules_array[i].toString());
            key_access_rules_array[i].pattern = null;
        }//for
        Util.log(Util.LOG_DEBUG,"%s", buf.toString());
    }//zbxFinalizeKeyAccessRulesConfiguration()

    /*
     * checks agent metric request against configured access rules
     * returns: true: metric access allowed, false: denied
     */
    public static boolean zbxCheckRequestAccessRules(AgentRequest req) {
        ZbxKeyAccessRule rule;
        //empty arguments flag means key is followed by empty brackets, which is not the same as no brackets
        boolean empty_arguments = req.emptyArguments();

        for (int i = 0; i < Config.key_access_rules_array.length; i++) {
            rule = Config.key_access_rules_array[i];

            if ("*".equals(rule.key) && 0 == rule.params.length)
                return rule.type;   //match all

            if (0 < rule.params.length) {
                if ("*".equals(rule.params[rule.params.length - 1])) {
                    if (1 == rule.params.length && req.nullArguments())
                        continue;   //rule: key[*], request: key
                } else {
                    if (req.getNparam() < rule.params.length)
                        continue;   //too few parameters
                    if (req.getNparam() > rule.params.length && !empty_arguments)
                        continue;   //too many parameters
                }//if(last parameter == "*")
            }//if(rule has parameters)

            if (!Util.zbxWildcardMatch(req.getKeyName(), rule.key))
                continue;   //key doesn't match

            if (rule.empty_arguments) { //rule expects empty argument list: key[]
                if (empty_arguments)
                    return rule.type;
                continue;
            }//if(rule.empty_arguments)

            if (empty_arguments && 0 == rule.params.length)
                continue;   //no parameters expected by rule

            if (0 == req.getNparam() && 0 == rule.params.length)
                return rule.type;   //no parameters

            for (int j = 0; j < rule.params.length; j++) {
                if (rule.params.length - 1 == j) {  //last parameter
                    if ("*".equals(rule.params[j]))
                        return rule.type;   //skip next parameter checks
                    if (req.getNparam() <= j)
                        break;              //number of parameters in request less than in rule
                    if (!Util.zbxWildcardMatch(req.getParam(j), rule.params[j]))
                        break;              //parameter doesn't match pattern
                    return rule.type;
                }//last parameter

                if (req.getNparam() <= j || !Util.zbxWildcardMatch(req.getParam(j), rule.params[j]))
                    break;  //parameter doesn't match pattern
            }//for(rule parameters)
        }//for(access rules)

        //by default - allow
        return true;
    }//zbxCheckRequestAccessRules()

    private static void parseConfigLine(String param_name, String param_value) throws ZbxException {
        if ("".equals(param_value))
            throw new ZbxException("Invalid empty value");

        switch (param_name) {
        case "Alias":
            parseZbxAlias(param_value);
            break;
        case "AllowKey":
            addZbxKeyAccessRule(param_name, param_value, true);
            break;
        case "AllowRoot":
            AllowRoot = parseBoolean(param_value);
            logWarn(param_name);
            break;
        case "BufferSend":
            BufferSend = parseInt(param_value, 1, 3600);
            break;
        case "BufferSize":
            BufferSize = parseInt(param_value, 2, 65535);
            break;
        case "DebugLevel":
            DebugLevel = parseInt(param_value, 0, 5);
            break;
        case "DenyKey":
            addZbxKeyAccessRule(param_name, param_value, false);
            break;
        case "EnableRemoteCommands":
            EnableRemoteCommands = parseBoolean(param_value);
            break;
        case "HeartbeatFrequency":
            HeartbeatFrequency = parseInt(param_value, 0, 3600);
            break;
        case "HostInterface":
            HostInterface = param_value;
            break;
        case "HostInterfaceItem":
            HostInterfaceItem = param_value;
            break;
        case "HostMetadata":
            HostMetadata = param_value;
            break;
        case "HostMetadataItem":
            HostMetadataItem = param_value;
            break;
        case "Hostname":
            Hostname = param_value;
            break;
        case "HostnameItem":
            HostnameItem = param_value;
            break;
        case "Include":
            logWarn(param_name);
            break;
        case "ListenBacklog":
            ListenBacklog = parseInt(param_value, 0, 0);
            break;
        case "ListenIP":
            //Originally it was a list of IP-addresses; but we use only a single value
            if (!"0.0.0.0".equals(param_value))
                ListenIP = param_value;
            break;
        case "ListenPort":
            ListenPort = parseInt(param_value, 1024, 32767);
            break;
        case "LoadModule":
            if (null == LoadModule)
                LoadModule = new ArrayList<String>();
            //!!check for validity
            LoadModule.add(param_value);
            logWarn(param_name);
            break;
        case "LoadModulePath":
            LoadModulePath = param_value;
            logWarn(param_name);
            break;
        case "LogFile":
            LogFile = param_value;
            break;
        case "LogType":
            LogType = param_value;
            break;
        case "LogFileSize":
            LogFileSize = parseInt(param_value, 0, 1024) * 1024 * 1024;
            break;
        case "LogRemoteCommands":
            LogRemoteCommands = parseBoolean(param_value);
            break;
        case "MaxLinesPerSecond":
            MaxLinesPerSecond = parseInt(param_value, 1, 1000);
            break;
        case "PidFile":
            PidFile = param_value;
            break;
        case "RefreshActiveChecks":
            RefreshActiveChecks = parseInt(param_value, 1, 86400);
            break;
        case "Server":
            //It is a list of servers (name or IP-address), possible with CIDR mask length
            HostsAllowed = parseZbxSubnets(param_value);
            break;
        case "ServerActive":
            //It is a list of servers (name or IP-address) and (optional) its port number
            ServerActive = parseList(param_value);
            break;
        case "SourceIP":
            SourceIP = param_value;
            break;
        case "StartAgents":
            StartAgents = parseInt(param_value, 1, 100);
            break;
        case "Timeout":
            Timeout_ms = parseInt(param_value, 1, 30) * 1000;
            break;
        case "UserParameter":
            if (null == UserParameter)
                UserParameter = new ArrayList<String>();
            //!!check for validity
            UserParameter.add(param_value);
            logWarn(param_name);
            break;
        case "UserParameterDir":
            UserParameterDir = param_value;
            logWarn(param_name);
            break;
        case "UnsafeUserParameters":
            UnsafeUserParameters = parseBoolean(param_value);
            break;
        case "User":
            User = param_value;
            break;
        case "as400Password":
            asPassword = param_value;
            break;
        case "as400ServerHost":
            as400ServerHost = param_value;
            break;
        case "as400EventIdAsMessagePrefix":
            as400EventIdAsMessagePrefix = parseBoolean(param_value);
            break;
        case "as400JobAsMessagePrefix":
            as400JobAsMessagePrefix = parseBoolean(param_value);
            break;
        case "as400UserAsMessagePrefix":
            as400UserAsMessagePrefix = parseBoolean(param_value);
            break;
        case "TLSAccept":
        case "TLSCAFile":
        case "TLSCertFile":
        case "TLSCipherAll":
        case "TLSCipherAll13":
        case "TLSCipherCert":
        case "TLSCipherCert13":
        case "TLSCipherPSK":
        case "TLSCipherPSK13":
        case "TLSConnect":
        case "TLSCRLFile":
        case "TLSKeyFile":
        case "TLSPSKFile":
        case "TLSPSKIdentity":
        case "TLSServerCertIssuer":
        case "TLSServerCertSubject":
            logWarn(param_name);
            break;
        default:
            throw new ZbxException("Invalid parameter");
        }//switch-case
    }//parseConfigLine()

    public static boolean parseConfig(String config_file_name) {
        boolean ret = true;
        int i = 0;
        BufferedReader reader = null;
        String line = null;

        try {
            reader = new BufferedReader(new FileReader(config_file_name));
            for (i = 1; null != (line = reader.readLine()); i++) {
                if (0 == line.length() || '#' == line.charAt(0))
                    continue;
                String []mas = line.split("=", 2);
                if (2 != mas.length)
                    throw new ZbxException("invalid entry '" + line + "' (not following \"parameter=value\" notation)");
                parseConfigLine(mas[0].trim(), mas[1].trim());
            }//for (config file lines)
            i = 0;
        } catch (IOException|ZbxException ex) {
            ret = false;
            Util.log(Util.LOG_CRITICAL,"Error during config file '%s' processing%s:\n%s",
                config_file_name, (i > 0 ? " (line " + i + "): '" + line +"'" : ""), 
                (ex instanceof ZbxException ? ex.getMessage() : ex.toString()) );
        } finally {
            if (null != reader)
                try { reader.close(); } catch (IOException ex) { ; }
        }//try-catch-finally

        return (configured = ret);
    }//parseConfig()

    public static boolean setDefaultsAndValidate(String config_file_name) {
        boolean ret = true;
        com.ibm.as400.access.AS400 system = ((As400Thread)Thread.currentThread()).getAs400();
        try {
            //set defaults
            if (null == User) {
                if (system.isLocal())
                    User = "*CURRENT";
                else
                    User = "zabbix";
            }//if (User)
            try {
                system.setUserId(User);
                system.setPassword(asPassword);
            } catch (PropertyVetoException ex) {
                throw new ZbxException("Could not set default value for \"User\" parameter ("
                                        + User + "): " + ex.getMessage());
            }//try-catch(set User/Password)
            if (null == Hostname) {
                if (null == HostnameItem)
                    HostnameItem = "system.hostname";
                if (!commands.containsKey(HostnameItem))
                    throw new ZbxException("Invalid value for \"HostnameItem\" parameter: " + HostnameItem);
                try {
                    Hostname = ZabbixAgent.zbxExecuteAgentCheck(new AgentRequest(HostnameItem),
                        (Util.ZBX_PROCESS_LOCAL_COMMAND | Util.ZBX_PROCESS_WITH_ALIAS), 0).getValue().toString();
                } catch (ZbxException ex) {
                    throw new ZbxException("Could not obtain value for \"HostnameItem\" parameter: "
                                            + ex.getMessage());
                }//try-catch
            } else {
                if (null != HostnameItem)
                    Util.log(Util.LOG_WARNING,"both Hostname and HostnameItem defined, using [%s]",
                            Hostname);
            }//if (Hostname defined)
            if (null != HostMetadata && null != HostMetadataItem)
                Util.log(Util.LOG_WARNING,"both HostMetadata and HostMetadataItem defined, using [%s]",
                        HostMetadata);
            if (null != HostInterface && null != HostInterfaceItem)
                Util.log(Util.LOG_WARNING,"both HostInterface and HostInterfaceItem defined, using [%s]",
                        HostInterface);
            //validate for consistency
            if (null == Hostname)
                throw new ZbxException("\"Hostname\" configuration parameter is not defined");
            else if (!Hostname.matches("^[-._ a-zA-Z0-9]*$"))
                throw new ZbxException("\"Hostname\" configuration '" + Hostname + "' contains invalid character(s)");
            if (null == HostsAllowed && 0 < StartAgents)
                throw new ZbxException("StartAgents is not 0, parameter Server must be defined");
            if (null != HostMetadata && HOST_METADATA_LEN < HostMetadata.getBytes(Util.getUtf8()).length)
                throw new ZbxException("the value of \"HostMetadata\" configuration parameter cannot be longer than "
                                        + HOST_METADATA_LEN + " bytes");
            if (null != HostMetadataItem && HOST_METADATA_ITEM_LEN < HostMetadataItem.length())
                throw new ZbxException("the value of \"HostMetadataItem\" configuration parameter cannot be longer than "
                                        + HOST_METADATA_ITEM_LEN + " characters");
            if (null != HostInterface && HOST_INTERFACE_LEN < HostInterface.length())
                throw new ZbxException("the value of \"HostInterface\" configuration parameter cannot be longer than "
                                        + HOST_INTERFACE_LEN + " characters");
            if (null == HostMetadata && null != HostMetadataItem && !commands.containsKey(HostMetadataItem))
                    throw new ZbxException("Invalid value for \"HostMetadataItem\" parameter: " + HostMetadataItem);
            if (null == HostInterface && null != HostInterfaceItem && !commands.containsKey(HostInterfaceItem))
                    throw new ZbxException("Invalid value for \"HostInterfaceItem\" parameter: " + HostInterfaceItem);
            if (null == HostsAllowed && null == ServerActive)
                throw new ZbxException("either active or passive checks must be enabled");
            if (null != SourceIP && (0 == SourceIP.length() || !SourceIP.matches(ip_regexp)) )
                throw new ZbxException("invalid \"SourceIP\" configuration parameter: '" + SourceIP + "'");
            if (null != ListenIP && (0 == ListenIP.length() || !ListenIP.matches(ip_regexp)) )
                throw new ZbxException("invalid \"ListenIP\" configuration parameter: '" + ListenIP + "'");
            if (!"file".equals(LogType))
                throw new ZbxException("invalid \"LogType\" configuration parameter: '" + LogType + "'; only 'file' is supported");
            zbxFinalizeKeyAccessRulesConfiguration();
        } catch (ZbxException ex) {
            ret = false;
            Util.log(Util.LOG_CRITICAL,"Error during config file '%s' validation:\n%s\n", config_file_name,
                            ex.getMessage() );
        }//try-catch
        return ret;
    }//setDefaultsAndValidate()

}//class Config
