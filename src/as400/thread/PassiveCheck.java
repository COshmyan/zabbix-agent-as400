//import com.ibm.as400.access.*;
package as400.thread;
import as400.*;
import java.util.ArrayList;
import java.io.*;
import java.net.*;

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
            ss = new ServerSocket(Config.getListenPort(), 50,
                                    InetAddress.getByName(Config.getListenIP()) );
            try { ss.setSoTimeout(2000); } catch (SocketException ex) { ; }
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
                            process (s);
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

    private boolean checkConnection(Socket s) {
        Util.log(Util.LOG_DEBUG,"in PassiveCheck.checkConnection()");
        String [] hosts_allowed = Config.getHostsAllowed();
        if (null == hosts_allowed)
            return true;
        InetAddress peer_address = s.getInetAddress(), all_ips[] = null;

        for (int i = 0; i < hosts_allowed.length; i++) {
            try {
                all_ips = InetAddress.getAllByName(hosts_allowed[i]);
            } catch (UnknownHostException ex) {
                Util.log(Util.LOG_WARNING,"host '%s' from config. file could not be resolved",
                        hosts_allowed[i]);
                continue;
            }//try-catch
            for (int j = 0; j < all_ips.length; j++) {
                if (peer_address.equals(all_ips[j])) {
                    Util.log(Util.LOG_DEBUG,"end of PassiveCheck.checkConnection(): true");
                    return true;
                }
            }//for(all_ips)
        }//for (hosts_allowed)
        Util.log(Util.LOG_WARNING,
                "connection from '%s' rejected, it is not in the list of allowed hosts",
                peer_address.getHostAddress());
        return false;
    }//checkConnection()

    //real processing of incoming request
    private void process(Socket s) {
        Util.log(Util.LOG_DEBUG,"in PassiveCheck.process(), #%d", process_num);

        InputStream  zbxIn  = null;
        OutputStream zbxOut = null;
        String requestStr = null, responseStr = null;

        try {
            s.setSoTimeout(Config.getTimeout_ms());
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
                }//if (header is OK)
            } else {//header is absent: process it just as a command
                while (read < reqData.length) {
                    int c = zbxIn.read();
                    if (0 > c || '\n' == c || '\r' == c) {
                        reqData[read++] = (byte)'\n';
                        break;
                    }
                    reqData[read++] = (byte)c;
                }//
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
            if (null != requestStr || 0 < requestStr.length()) {
                try {
                    Util.log(Util.LOG_DEBUG,"PassiveCheck.process(): request is: '%s'", requestStr);
                    DataObject dobj = ZabbixAgent.process(new AgentRequest(requestStr));
                    responseStr = dobj.getValue().toString();
                } catch (ZbxException ex) {
                    responseStr = "ZBX_NOTSUPPORTED"+'\0'+ex.getMessage();
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

        Util.log(Util.LOG_DEBUG,"end of PassiveCheck.process(), #%d", process_num);
    }//process()

}//class PassiveCheck
