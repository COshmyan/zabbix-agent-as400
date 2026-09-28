package as400;
import as400.metric.ZbxMetric;
import as400.thread.*;
import java.beans.PropertyVetoException;
import java.io.*;
import java.util.ArrayList;
import java.util.Hashtable;

public class Config {

    //static variables
    public static volatile boolean running                 = true;
    public static Hashtable<String, ZbxMetric> commands    = new Hashtable<String, ZbxMetric>();
    private static boolean configured                      = false;

    //constants
    public static final int HOST_METADATA_LEN  = 255;
           static final int TYPE_INT           = 0;
           static final int TYPE_STRING        = 1;
           static final int TYPE_MULTISTRING   = 2;
           static final int TYPE_UINT64        = 3;
           static final int TYPE_STRING_LIST   = 4;
           static final String ip_regexp       = "[0-9]{1,3}.[0-9]{1,3}.[0-9]{1,3}.[0-9]{1,3}";

    //AS400 related variables
    private static       String asPassword      = "*CURRENT";
    private static       String as400ServerHost = "localhost";
    private static      boolean as400EventIdAsMessagePrefix = true;
    private static      boolean as400UserAsMessagePrefix = false;

    //variables from config file
    private static String PidFile               = "/tmp/zabbix_agentd.pid";
    private static String LogFile               = "/tmp/zabbix_agentd.log";
    private static long   LogFileSize           = 1 * 1024 * 1024;
    private static int    DebugLevel            = 3;
    private static String SourceIP              = null;
    private static boolean EnableRemoteCommands = false;
    private static boolean LogRemoteCommands    = false;
    private static String[] HostsAllowed        = null;
    private static int    ListenPort            = 10050;
    private static String ListenIP              = "0.0.0.0";
    private static int    StartAgents           = 3;
    private static String[] ServerActive        = null;
    private static String Hostname              = null;
    private static String HostnameItem          = null;
    private static String HostMetadata          = null;
    private static String HostMetadataItem      = null;
    private static int    RefreshActiveChecks   = 120;
    private static int    BufferSend            = 5;
    private static int    BufferSize            = 100;
    private static int    MaxLinesPerSecond     = 100;
    private static int    Timeout_ms            = 3000;
    private static boolean AllowRoot            = false;
    private static String User                  = null;
    private static boolean UnsafeUserParameters = false;
    //include
    private static ArrayList<String> Alias          = null;
    private static ArrayList<String> UserParameter  = null;
    private static String LoadModulePath            = null;
    private static ArrayList<String> LoadModule     = null;

    public static String getAs400Password()         {return asPassword;             }
    public static String getAs400ServerHost()       {return as400ServerHost;        }
    public static boolean as400EventIdAsMessagePrefix(){return as400EventIdAsMessagePrefix;}
    public static boolean as400UserAsMessagePrefix(){return as400UserAsMessagePrefix;}
    public static String getPidFile()               {return PidFile;                }
    public static String getLogFile()               {return LogFile;                }
    public static long   getLogFileSize()           {return LogFileSize;            }
    public static int    getDebugLevel()            {return DebugLevel;             }
    public static String getSourceIP()              {return SourceIP;               }
    public static boolean getEnableRemoteCommands() {return EnableRemoteCommands;   }
    public static boolean getLogRemoteCommands()    {return LogRemoteCommands;      }
    public static String[] getHostsAllowed()        {return HostsAllowed;           }
    public static int    getListenPort()            {return ListenPort;             }
    public static String getListenIP()              {return ListenIP;               }
    public static int    getStartAgents()           {return StartAgents;            }
    public static String[] getServerActive()        {return ServerActive;           }
    public static String getHostname()              {return Hostname;               }
    public static String getHostnameItem()          {return HostnameItem;           }
    public static String getHostMetadata()          {return HostMetadata;           }
    public static String getHostMetadataItem()      {return HostMetadataItem;       }
    public static int    getRefreshActiveChecks()   {return RefreshActiveChecks;    }
    public static int    getBufferSend()            {return BufferSend;             }
    public static int    getBufferSize()            {return BufferSize;             }
    public static int    getMaxLinesPerSecond()     {return MaxLinesPerSecond;      }
    public static int    getTimeout_ms()            {return Timeout_ms;             }
    public static boolean getAllowRoot()            {return AllowRoot;              }
    public static String getUser()                  {return User;                   }
    public static boolean getUnsafeUserParameters() {return UnsafeUserParameters;   }
    public static boolean isConfigured()            {return configured;             }

    public static ZbxMetric getZbxMetric(String key) throws ZbxException {
        ZbxMetric command = commands.get(key);
        if (null == command) {
            Util.log(Util.LOG_ERROR, "Unsupported item key name: %s", key);
            throw new ZbxException("Unsupported item key name: " + key);
        }
        return command;
    }//getZbxMetric()

    private static int parseInt(String param_value, int min, int max) throws ZbxException {
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
    }//parseList

    private static void parseConfigLine(String param_name, String param_value) throws ZbxException {
        if ("".equals(param_value))
            throw new ZbxException("Invalid empty value");

        switch (param_name) {
        case "Server":
            //It is a list of servers (name or IP-address)
            HostsAllowed = parseList(param_value);
            break;
        case "ServerActive":
            //It is a list of servers (name or IP-address) and (optional) its port number
            ServerActive = parseList(param_value);
            break;
        case "Hostname":
            Hostname = param_value;
            break;
        case "HostnameItem":
            HostnameItem = param_value;
            break;
        case "HostMetadata":
            HostMetadata = param_value;
            break;
        case "HostMetadataItem":
            HostMetadataItem = param_value;
            break;
        case "BufferSize":
            BufferSize = parseInt(param_value, 2, 65535);
            break;
        case "BufferSend":
            BufferSend = parseInt(param_value, 1, 3600);
            break;
        case "PidFile":
            PidFile = param_value;
            break;
        case "LogFile":
            LogFile = param_value;
            break;
        case "LogFileSize":
            LogFileSize = parseInt(param_value, 0, 1024) * 1024 * 1024;
            break;
        case "Timeout":
            Timeout_ms = parseInt(param_value, 1, 30) * 1000;
            break;
        case "ListenPort":
            ListenPort = parseInt(param_value, 1024, 32767);
            break;
        case "ListenIP":
            //Originally it was a list of IP-addresses; but we use only single value
            ListenIP = param_value;
            break;
        case "SourceIP":
            SourceIP = param_value;
            break;
        case "DebugLevel":
            DebugLevel = parseInt(param_value, 0, 5);
            break;
        case "StartAgents":
            StartAgents = parseInt(param_value, 1, 100);
            break;
        case "RefreshActiveChecks":
            RefreshActiveChecks = parseInt(param_value, 60, 3600);
            break;
        case "MaxLinesPerSecond":
            MaxLinesPerSecond = parseInt(param_value, 1, 1000);
            break;
        case "EnableRemoteCommands":
            EnableRemoteCommands = parseBoolean(param_value);
            break;
        case "LogRemoteCommands":
            LogRemoteCommands = parseBoolean(param_value);
            break;
        case "UnsafeUserParameters":
            UnsafeUserParameters = parseBoolean(param_value);
            break;
        case "Alias":
            if (null == Alias)
                Alias = new ArrayList<String>();
            //!!check for validity
            Alias.add(param_value);
            break;
        case "UserParameter":
            if (null == UserParameter)
                UserParameter = new ArrayList<String>();
            //!!check for validity
            UserParameter.add(param_value);
            break;
        case "LoadModulePath":
            LoadModulePath = param_value;
            break;
        case "LoadModule":
            if (null == LoadModule)
                LoadModule = new ArrayList<String>();
            //!!check for validity
            LoadModule.add(param_value);
            break;
        case "AllowRoot":
            AllowRoot = parseBoolean(param_value);
            break;
        case "User":
            User = param_value;
            break;
        case "as400ServerHost":
            as400ServerHost = param_value;
            break;
        case "as400Password":
            asPassword = param_value;
            break;
        case "as400EventIdAsMessagePrefix":
            as400EventIdAsMessagePrefix = parseBoolean(param_value);
            break;
        case "as400UserAsMessagePrefix":
            as400UserAsMessagePrefix = parseBoolean(param_value);
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
        com.ibm.as400.access.AS400 system = ((ZabbixThread)Thread.currentThread()).getAs400();
        try {
            //set defaults
            //system = ((ZabbixThread)Thread.currentThread()).getAs400();
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
                    Hostname = ZabbixAgent.process(new AgentRequest(HostnameItem)).getValue().toString();
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
            //validate for consistency
            if (null == Hostname)
                throw new ZbxException("\"Hostname\" configuration parameter is not defined");
            else if (!Hostname.matches("^[-._ a-zA-Z0-9]*$"))
                throw new ZbxException("\"Hostname\" configuration '" + Hostname + "' contains invalid character(s)");
            if (null == HostsAllowed && 0 < StartAgents)
                throw new ZbxException("StartAgents is not 0, parameter Server must be defined");
            if (null != HostMetadata && HOST_METADATA_LEN < HostMetadata.length())
                throw new ZbxException("the value of \"HostMetadata\" configuration parameter cannot be longer than "
                                        + HOST_METADATA_LEN + " characters");
            if (null == HostMetadata && null != HostMetadataItem && !commands.containsKey(HostMetadataItem))
                    throw new ZbxException("Invalid value for \"HostMetadataItem\" parameter: " + HostMetadataItem);
            if (null == HostsAllowed && null == ServerActive)
                throw new ZbxException("either active or passive checks must be enabled");
            if (null != SourceIP && (0 == SourceIP.length() || !SourceIP.matches(ip_regexp)) )
                throw new ZbxException("invalid \"SourceIP\" configuration parameter: '" + SourceIP + "'");
            if (null == ListenIP || 0 == ListenIP.length() || !ListenIP.matches(ip_regexp) )
                throw new ZbxException("invalid \"ListenIP\" configuration parameter: '" + ListenIP + "'");
        } catch (ZbxException ex) {
            ret = false;
            Util.log(Util.LOG_CRITICAL,"Error during config file '%s' validation:\n%s\n", config_file_name,
                            ex.getMessage() );
        }//try-catch
        return ret;
    }//setDefaultsAndValidate()

}//class Config
