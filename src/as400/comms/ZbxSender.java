package as400.comms;
import as400.*;
import java.io.*;
import java.net.*;
import org.json.simple.*;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class ZbxSender {

    public static final byte header[] = { 'Z', 'B', 'X', 'D', '\1' };
    static final int ZBX_REDIRECT_FAIL  = -1;
    static final int ZBX_REDIRECT_NONE  = 0;
    static final int ZBX_REDIRECT_RESET = 1;
    static final int ZBX_REDIRECT_RETRY = 2;
//    static       int     connectTimeout = 3 * 1000;
//    static       int      socketTimeout = 3 * 1000;
    //instance variables
    ZbxAddrList  addrs;
    String       sourceIP;
    ZbxRequest   zbxReq;
    String       zbxRes    = null;
    Socket       zbxSocket = null;
    InputStream  zbxIn     = null;
    OutputStream zbxOut    = null;

    public ZbxSender(ZbxRequest zbxReq, ZbxAddrList addrs) {
        this.zbxReq     = zbxReq;
        this.addrs      = addrs;
        this.sourceIP   = Config.getSourceIP();
    }//constructor ZbxSender()

/*
    public static int getConnectTimeout() {
        return connectTimeout;
    }//getConnectTimeout()

    public static int getSocketTimeout() {
        return socketTimeout;
    }//getSocketTimeout()

    public static void setConnectTimeout(int ct) {
        connectTimeout = ct;
    }//setConnectTimeout()

    public static void setSocketTimeout(int st) {
        socketTimeout = st;
    }//setSocketTimeout()
*/
    public static byte[] toBytes(String str) {

        byte[] strBytes = str.getBytes(Util.getUtf8());
        byte[] result = new byte[header.length + 4 + 4 + strBytes.length];

        System.arraycopy(header, 0, result, 0, header.length);

        result[header.length]     = (byte) ( strBytes.length        & 0x000000FF);
        result[header.length + 1] = (byte) ((strBytes.length >> 8 ) & 0x000000FF);
        result[header.length + 2] = (byte) ((strBytes.length >> 16) & 0x000000FF);
        result[header.length + 3] = (byte) ((strBytes.length >> 24) & 0x000000FF);

        System.arraycopy(strBytes, 0, result, header.length + 4 + 4, strBytes.length);
        return result;
    }//toBytes()

    public static int checkHeader (byte[]data) {
        int len = -1;
        if (data[0]==header[0] && data[1]==header[1] &&
            data[2]==header[2] && data[3]==header[3] && data[4]==header[4]) {
                len =            data[8] & 0x00FF;
                len = (len<<8) | data[7] & 0x00FF;
                len = (len<<8) | data[6] & 0x00FF;
                len = (len<<8) | data[5] & 0x00FF;
            }
        return len;
    }//checkHeader()

    private boolean tcpConnectFailover(int level, ZbxAddrList addrs) {
        Util.log(Util.LOG_DEBUG," In tcpConnectFailover()");
        String zbxServer = null;
        int zbxPort = 0;
        boolean rc = false;

        this.addrs.setErr(null);
        try {
            for (int i=0; i<addrs.size(); i++) {
                zbxServer = addrs.getIp();
                zbxPort   = addrs.getPort();
                Util.log(Util.LOG_DEBUG,"  connecting to server '%s:%d'", zbxServer, zbxPort);
                this.zbxSocket = new Socket();
                zbxSocket.setSoTimeout((int)Config.getTimeout_ms());
                try {
                    zbxSocket.setReuseAddress(true);    //try to enable SO_REUSEADDR socket option
                } catch (SocketException ex) { ; }
                if (null != sourceIP) {
                    //local port zero - get ephemeral local port
                    try {
                        zbxSocket.bind(new InetSocketAddress(sourceIP, 0));
                    } catch (IOException ex) {
                        throw new IOException(String.format("Cannot bind to local IP=%s; %s", sourceIP, ex.toString()));
                    }//try-catch
                }//if
                try {
                    zbxSocket.connect(new InetSocketAddress(zbxServer, zbxPort), (int)Config.getTimeout_ms());
                    rc = zbxSocket.isConnected();
                    break;
                } catch (IOException ex) {
                    this.addrs.setErr(ex.getMessage());
                    Util.log(level,"Unable to connect to '%s:%d', %s", zbxServer, zbxPort, ex);
                    addrs.failover();
                    if (!zbxSocket.isClosed())
                        zbxSocket.close();
                }//try-catch
            }//for
        } catch (IOException ex) {
            this.addrs.setErr(ex.getMessage());
            Util.log(Util.LOG_ERROR, ex, "Error connecting to '%s:%d'", zbxServer, zbxPort);
        } finally {
            Util.log(Util.LOG_DEBUG," End of tcpConnectFailover(); rc=%b", rc);
        }
        return rc;
    }//tcpConnectFailover()

    public String send() throws IOException {
        Util.log(Util.LOG_DEBUG," In send()");

        try {
            zbxIn  = zbxSocket.getInputStream();
            zbxOut = zbxSocket.getOutputStream();
            String str = zbxReq.toString();
            Util.log(Util.LOG_DEBUG," sending: to %s '%s'", zbxSocket.getInetAddress(), str);
            zbxOut.write(toBytes(str));
            zbxOut.flush();

            byte[] respData = new byte[13];//'Z','B','X','D',0x01 + 8-byte length
            int len;
            int read = zbxIn.read(respData);
            if (respData.length==read && (len = checkHeader(respData)) >0) {
                //read the length of real datas
                Util.log(Util.LOG_DEBUG,"ZBXD header is OK, data length=%d",len);
                respData = new byte[len];
                int off = 0;
                while (0 < (read = zbxIn.read(respData, off, len)) ) {
                    off += read;
                    len -= read;
                }//while
                zbxRes = new String(respData, Util.getUtf8());
            }//if(header is OK)
            return zbxRes;

        } finally {
            if (null != zbxSocket)
                zbxSocket.close();
            Util.log(Util.LOG_DEBUG," End of send(); got [%s]", zbxRes);
        }//try-catch-finally
    }//send()

    private int checkRedirect(JSONObject jsonObj) {
        String address;
        long revision;

        try {

            if (!"failed".equals((String)jsonObj.get("response")))
                return ZBX_REDIRECT_FAIL;

            jsonObj = (JSONObject)jsonObj.get("redirect");

            if ("true".equals((String)jsonObj.get("reset"))) {
                this.addrs.failover();
                return ZBX_REDIRECT_RESET;
            }//if("reset":"true")

            revision = ((Number)jsonObj.get("revision")).longValue();
            address = (String)jsonObj.get("address");

            for (int i=0; i<this.addrs.size(); i++) {
                ZbxAddr addr = this.addrs.getValue(i);

                if (addr.getRevision() > revision) {
                    return ZBX_REDIRECT_NONE;
                }//if(revision<stored)

                //remove old revision of redirection
                if (0l != addr.getRevision()) {
                    this.addrs.remove(i);
                    break;
                }//
            }//for

            //Add redirected address to beginning of list
            this.addrs.insert(address, revision);
            return ZBX_REDIRECT_RETRY;

        } catch (ClassCastException|NullPointerException ex) {
            return ZBX_REDIRECT_FAIL;
        }//try-catch

    }//checkRedirect

    public JSONObject exchangeWithRedirect(int level) throws IOException {
        JSONObject jsonObj = null;
        boolean conn_ret;
        String str;
        int redirect_rc = ZBX_REDIRECT_NONE, retries = 0;

        Util.log(Util.LOG_DEBUG,"In exchangeWithRedirect()");
        conn_ret = tcpConnectFailover(level, this.addrs);

        try {
            for ( ; ; ) {

                if (!conn_ret){ // || ZBX_REDIRECT_NONE != redirect_rc) {
                    Util.log(Util.LOG_DEBUG,"Unable to connect to [%s]:%d: %s",
                        this.addrs.getIp(), this.addrs.getPort(), this.addrs.getErr());
                    throw new IOException(this.addrs.getErr());
                }//if(not connected)

                try {
                    str = send();
                } catch (IOException ioex) {
                    this.addrs.failover();
                    throw ioex;
                }//try-catch

                if (null == str)
                    return null;

                JSONParser parser = new JSONParser();
                try {
                    jsonObj = (JSONObject)parser.parse(str);
                } catch (ParseException|ClassCastException ex) {
                        Util.log(Util.LOG_ERROR," cannot parse answer from server/proxy: %s", ex.toString());
                }//try-catch

                if (ZBX_REDIRECT_FAIL != (redirect_rc = checkRedirect(jsonObj))) {
                    if (0 != retries) {
                        throw new IOException("sequential redirect responses detected");
                    }//if(retries!=0)
                    retries++;
                }//if(!REDIRECT_FAIL)

                if (ZBX_REDIRECT_RESET == redirect_rc) {
                    Util.log(Util.LOG_DEBUG," exchangeWithRedirect() redirect response found, retrying to: [%s]:%hu",
                        this.addrs.getIp(), this.addrs.getPort());
                    if (null != this.zbxSocket)
                        this.zbxSocket.close();
                    conn_ret = tcpConnectFailover(level, this.addrs);
                    continue;
                } else if (ZBX_REDIRECT_RETRY == redirect_rc) { //if(ZBX_REDIRECT_RESET)
                    //one host in addrs list will try connection without failover logic
                    ZbxAddrList  addrs = this.addrs.cloneFirstAddr();
                    conn_ret = tcpConnectFailover(level, addrs);
                    continue;
                } else if (ZBX_REDIRECT_NONE == redirect_rc) {//if(ZBX_REDIRECT_RETRY)
                    ;//connection was reset because of service being offline
                }//if(ZBX_REDIRECT_NONE)

                break;
            }//for

        return jsonObj;

        } finally {
            Util.log(Util.LOG_DEBUG,"End of exchangeWithRedirect()");
        }

    }//exchangeWithRedirect()

}//class ZbxSender
