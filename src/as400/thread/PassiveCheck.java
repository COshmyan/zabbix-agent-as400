//import com.ibm.as400.access.*;
package as400.thread;
import as400.*;
import as400.comms.*;
import java.util.ArrayList;
import java.io.*;
import java.net.*;
import org.json.simple.*;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class PassiveCheck extends ZabbixThread {

    //class variables
    private int process_num;
    private Socket s = null;
    //static class variables
    private static int process_count = 0;
    private static int process_busy = 0;
    private static PassiveCheck[] processes;
    private static ArrayList<PassiveCheck> proc_avail;

    public PassiveCheck() {
        super();
        setName("listener");
        this.process_num = ++process_count;
        this.s = null;
    }//constructor PassiveCheck()

    public static void init() {
        Util.log(Util.LOG_DEBUG,"PassiveCheck.init(): starting");
        //start needed number of child threads
        int num_agents = Config.getStartAgents();
        processes = new PassiveCheck[num_agents];
        proc_avail = new ArrayList<PassiveCheck>(num_agents);
        for (int i = 0; i < num_agents; i++) {
            processes[i] = new PassiveCheck();
            proc_avail.add(processes[i]);
            processes[i].start();
        }//for

        ServerSocket ss = null;
        try {
            InetAddress bind_addr = null;
            if (null != Config.getListenIP()) {
                bind_addr = InetAddress.getByName(Config.getListenIP());
            }
            ss = new ServerSocket(Config.getListenPort(), Config.getListenBacklog(), bind_addr);
            try { ss.setSoTimeout(1000); } catch (SocketException ex) { ; }
            Socket s;
            while (Config.running) {
                PassiveCheck t = null;
                s = null;
                try { s = ss.accept(); } catch (SocketTimeoutException ex) { ; }
                if (null != s)
                    Util.log(Util.LOG_DEBUG," Thread [%s]: got incoming connection from %s",
                            Thread.currentThread().getName(), s.getInetAddress().getHostAddress());
                while (null != s && null == t) {
                    synchronized (proc_avail) {
                        if (0 < proc_avail.size()) {
                            t = proc_avail.remove(proc_avail.size()-1);
                        }//if
                    }//sync
                    if (null != t) {
                        Util.log(Util.LOG_DEBUG, " connection will be processed by thread %d [%s #%d]",
                                t.getId(), t.getName(), t.process_num);
                        synchronized (t) {
                            t.s = s;
                            t.notify();
                        }//sync
                    } else {
                        Thread.yield();
                    }
                }//while (there is socket to process)
            }//while (running)
        } catch (Exception ex) {
            Util.log(Util.LOG_ERROR,"PassiveCheck.init() error: %s", ex);
            ex.printStackTrace();
        } finally {
            Config.running = false;
            if (null != ss && !ss.isClosed())
                try { ss.close(); } catch (IOException ex) { ; }
        }//try-catch

        for (int i = 0; i < process_count; i++) {
            processes[i].interrupt();
            try { processes[i].join(10000); } catch (InterruptedException ex) { ; }
        }//for

        Util.log(Util.LOG_DEBUG,"PassiveCheck.init(): finished");
    }//init()

    public void run() {

        Util.log(Util.LOG_INFO,"%s #%d started [%s #%d]", "agent", server_num, Thread.currentThread().getName(), this.process_num);

        synchronized (this) {
            while (Config.running) {
                try {
                    wait ();
                    if (null != this.s) {
                        if (checkConnection(s))
                            processListener (s);
                        //if (null != s)
                        try { s.close(); } catch (IOException ex) { ; }
                        s = null;
                        synchronized (proc_avail) {
                            proc_avail.add(this);
                        }//sync
                    }//if
                } catch (InterruptedException ex) {
                    Util.log(Util.LOG_DEBUG," Thread [%s #%d] interrupted in run(): %s", Thread.currentThread().getName(), this.process_num, ex);
                } catch (Throwable ex) {
                    //There should not be, but if it's occured - it is critical: stacktrace and stop agent
                    Util.log(Util.LOG_CRITICAL, ex, "Error in PassiveCheck.run():");
                    Config.running = false;
                    continue;
                }//try-catch
            }//while(main loop)
        }//sync

        try {
/*
            com.ibm.as400.access.SocketProperties sp = system.getSocketProperties();
            Util.log(Util.LOG_DEBUG," Thread [%s #%d] has the following SocketProperties:\n  LoginTimeout=%d, SoTimeout=%d, isKeepAlive=%b, SoLinger=%d",
                Thread.currentThread().getName(), this.process_num, sp.getLoginTimeout(), sp.getSoTimeout(), sp.isKeepAlive(), sp.getSoLinger());
*/
            if (system.isConnected())
                system.disconnectAllServices();
        } catch (Exception ex) {
            Util.log(Util.LOG_WARNING, " Error in PassiveCheck.process() during closing AS/400: %s",  ex);
        }//try-catch
        Util.log(Util.LOG_INFO,"%s #%d stopped [%s #%d]", "agent", server_num, Thread.currentThread().getName(), this.process_num);
    }//run()

    private static String ipaddr2string(byte[] addr) {
        StringBuilder buf = new StringBuilder();
        buf.append('[');
        for (int i=0; i<addr.length; i++) {
            if (0 != i)
                buf.append('.');
            buf.append((int)(addr[i] & 0x00FF));
        }
        buf.append(']');
        return buf.toString();
    }//ipaddr2string()

    private static boolean subnetMatch(int prefix_size, byte[] addr1, byte[] addr2) {
        byte[] netmask = {0, 0, 0, 0};
        int i, j;

        //CIDR notation to subnet mask
        for (i = (int)prefix_size, j = 0; i > 0 && j < 4; i -= 8, j++) {
            netmask[j] = (byte)(i >= 8 ? 0xFF : ~((1 << (8 - i)) - 1));
        }//for
        if (Util.LOG_TRACE1 <= Config.getDebugLevel())
            Util.log(Util.LOG_DEBUG," TRACE: netmask=%s, addr1=%s, addr2=%s", ipaddr2string(netmask), ipaddr2string(addr1), ipaddr2string(addr2));

        //The result of the bitwise AND operation of IP address and the subnet mask is the network prefix.
        //All hosts on a subnetwork have the same network prefix.
        for (i = 0; i < 4; i++) {
            if ((addr1[i] & netmask[i]) != (addr2[i] & netmask[i]))
                return false;
        }//for
        return true;
    }//subnetMatch()

    private boolean checkConnection(Socket s) {
        Util.log(Util.LOG_DEBUG,"In PassiveCheck.checkConnection()");
        ZbxSubnet [] hosts_allowed = Config.getHostsAllowed();
        String []buf;
/* this check is made in Config.java during initial parsing of config file
        if (null == hosts_allowed)
            return true;
*/
        InetAddress peer_address = s.getInetAddress(), all_ips[] = null;
        byte[] peer_address_in_bytes = peer_address.getAddress();

        for (int i = 0; i < hosts_allowed.length; i++) {
            try {
                all_ips = InetAddress.getAllByName(hosts_allowed[i].getIp());
            } catch (UnknownHostException ex) {
                Util.log(Util.LOG_WARNING,"Host '%s' from config. file could not be resolved, ignored.",
                    hosts_allowed[i].getIp());
                continue;
            }//try-catch

            for (int j = 0; j < all_ips.length; j++) {
                if (subnetMatch(hosts_allowed[i].getPrefixSize(), all_ips[j].getAddress(), peer_address_in_bytes)) {
                    Util.log(Util.LOG_DEBUG,"End of PassiveCheck.checkConnection(): true");
                    return true;
                }
            }//for(all_ips)
        }//for (hosts_allowed)
        Util.log(Util.LOG_WARNING,
                "Connection from '%s' rejected, it is not in the list of allowed hosts",
                peer_address.getHostAddress());
        return false;
    }//checkConnection()

    private void processPassiveCheckJSON(OutputStream zbxOut, JSONObject jsonObj) throws IOException {
        ZbxAnswer result = null;
        String str;

        try {
            if (!"passive checks".equals((String)jsonObj.get("request")))
                throw new ZbxException("unknown request");
            JSONArray ja = (JSONArray)jsonObj.get("data");
            if (null == ja)
                throw new ZbxException("cannot find the \"data\" array in the received JSON object");//received empty \"data\" tag"
            for (Object o: ja) {
                JSONObject  row = (JSONObject)o;
                str             = (String)row.get("key");
                Object  timeout = row.get("timeout");
                long timeout_ms = Interval.time2long(timeout.toString());

                DataObject dobj = ZabbixAgent.zbxExecuteAgentCheck(new AgentRequest(str), Util.ZBX_PROCESS_WITH_ALIAS, timeout_ms);
                result = new ZbxAnswer(!dobj.getStateNotsupported(), dobj.getValue().toString());
            }//for(ja)
        } catch (ClassCastException|NullPointerException|ZbxException ex) {
            result = new ZbxAnswer(false,
                (ex instanceof ZbxException ? ex.getMessage() : ex.toString()) );
        }//try-catch

        str = result.toString();
        Util.log(Util.LOG_DEBUG,"PassiveCheck.process(): sending result: '%s'", str);
        zbxOut.write(ZbxSender.toBytes(str));
        zbxOut.flush();
    }//processPassiveCheckJSON()

    //real processing of incoming request
    private void processListener(Socket s) {
        Util.log(Util.LOG_DEBUG,"In PassiveCheck.process(), #%d", process_num);

        InputStream  zbxIn  = null;
        OutputStream zbxOut = null;
        String requestStr = null, responseStr = null;

        try {
            s.setSoTimeout((int)Config.getTimeout_ms());
            zbxIn  = s.getInputStream();
            zbxOut = s.getOutputStream();
            int i = -1, len = ZbxSender.header.length; //length of header only
            byte[] reqData = new byte[1024];
            int read = zbxIn.read(reqData, 0, len);
            if ( len == read && reqData[0] == ZbxSender.header[0] &&
                                reqData[1] == ZbxSender.header[1] &&
                                reqData[2] == ZbxSender.header[2] &&
                                reqData[3] == ZbxSender.header[3] &&
                                reqData[4] == ZbxSender.header[4] ) {
                len = 8;    //length of the size field
                read = zbxIn.read(reqData, read, len);
                if (len == read && (len = ZbxSender.checkHeader(reqData)) > 0) {
                    //read the length of real datas
                    Util.log(Util.LOG_DEBUG,"ZBXD header is OK, data length=%d",len);
                    reqData = new byte[len];
                    read = zbxIn.read(reqData);
                    requestStr = new String(reqData, Util.getUtf8());

                    try {
                        JSONParser parser = new JSONParser();
                        JSONObject jsonObj = (JSONObject)parser.parse(requestStr);
                        Util.log(Util.LOG_DEBUG, "PassiveCheck.process(): request is JSON: '%s'.", requestStr);
                        processPassiveCheckJSON(zbxOut, jsonObj);
                        return;
                    } catch (ParseException|ClassCastException ex) {
                        Util.log(Util.LOG_DEBUG, "PassiveCheck.process(): request is not JSON, continue using old protocol");
                    }//try-catch

                }//if (header is OK)
            } else {//header is absent: process it just as a command
                if (0 < read) {
                    while (read < reqData.length) {
                        int c = zbxIn.read();
                        if (0 > c || '\n' == c || '\r' == c) {
                            reqData[read++] = (byte)'\n';
                            break;
                        }
                        reqData[read++] = (byte)c;
                    }//wile
                }//if(read>0)
                if (0 < read) {//found
                    requestStr = new String(reqData, 0, read, Util.getUtf8());
                    Util.log(Util.LOG_DEBUG, "PassiveCheck.process(): request without header is: '%s'",
                        requestStr);
                }//if (found)
            }//if (header present)
            if (null != requestStr) {
                i = requestStr.indexOf('\n');
                if (i > 0)
                    requestStr = requestStr.substring(0, i);
            }
            if (null != requestStr && 0 < requestStr.length()) {
                try {
                    Util.log(Util.LOG_DEBUG,"PassiveCheck.process(): request is: '%s'", requestStr);
                    DataObject dobj = ZabbixAgent.zbxExecuteAgentCheck(new AgentRequest(requestStr), Util.ZBX_PROCESS_WITH_ALIAS, 0);
                    responseStr = dobj.getValue().toString();
                    if (dobj.getStateNotsupported())
                        responseStr = "ZBX_NOTSUPPORTED" + '\0' + responseStr;
                } catch (ZbxException ex) {
                    responseStr = "ZBX_NOTSUPPORTED" + '\0' + ex.getMessage();
                }//try-catch
                Util.log(Util.LOG_DEBUG,"PassiveCheck.process(): sending result: '%s'", responseStr);
                zbxOut.write(ZbxSender.toBytes(responseStr));
                zbxOut.flush();
            } else {
                Util.log(Util.LOG_WARNING,"PassiveCheck.process(): request is empty, ignored");
            }//if
        } catch (IOException ex) {
            Util.log(Util.LOG_ERROR, "Error in PassiveCheck.process(), #%d: %s", process_num, ex);
        } finally {
            if (null != zbxIn)
                try { zbxIn.close();  } catch (IOException ex) { ; }
            if (null != zbxOut)
                try { zbxOut.close(); } catch (IOException ex) { ; }
        }//try-catch

        Util.log(Util.LOG_DEBUG,"End of PassiveCheck.process(), #%d", process_num);
    }//processListener()

}//class PassiveCheck
