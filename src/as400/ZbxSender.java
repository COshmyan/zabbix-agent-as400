//package lv.rietumu.as400;
package as400;
import java.io.*;
import java.net.*;

public class ZbxSender{

    public static final byte header[] = { 'Z', 'B', 'X', 'D', '\1' };
//    static       int     connectTimeout = 3 * 1000;
//    static       int      socketTimeout = 3 * 1000;
    //instance variables
    String       zbxServer;
    int          zbxPort;
    ZbxRequest   zbxReq;
    String       zbxRes    = null;
    Socket       zbxSocket = null;
    InputStream  zbxIn     = null;
    OutputStream zbxOut    = null;

    public ZbxSender(ZbxRequest zbxReq, String zbxServer, int zbxPort) {
        //this(zbxReq, zbxPort);
        this.zbxReq     = zbxReq;
        this.zbxServer  = zbxServer;
        this.zbxPort    = zbxPort;
    }//constructor ZbxSender()

    public String getZbxServer() {
        return this.zbxServer;
    }//getZbxServer()

    public int getZbxPort() {
        return this.zbxPort;
    }//getZbxPort()
/*
    public static int getConnectTimeout() {
        return connectTimeout;
    }//getConnectTimeout()

    public static int getSocketTimeout() {
        return socketTimeout;
    }//getSocketTimeout()
*/
    public void setZbxServer(String zbxServer) {
        this.zbxServer = zbxServer;
    }//setZbxServer()

    public void setZbxPort(int zbxPort) {
        this.zbxPort = zbxPort;
    }//setZbxPort()
/*
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

    public String send() throws IOException {

        zbxSocket = new Socket();
//        zbxSocket.setSoTimeout(socketTimeout);
        zbxSocket.setSoTimeout(Config.getTimeout_ms());

        Util.log(Util.LOG_DEBUG,"in send() to server '%s:%d'", zbxServer, zbxPort);
        try {
//            zbxSocket.connect(new InetSocketAddress(zbxServer, zbxPort), connectTimeout);
            zbxSocket.connect(new InetSocketAddress(zbxServer, zbxPort), Config.getTimeout_ms());
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
            if (null!=zbxSocket)
                zbxSocket.close();
            Util.log(Util.LOG_DEBUG,"End of send()");
        }//try-catch-finally
    }//send()

}//class ZbxSender