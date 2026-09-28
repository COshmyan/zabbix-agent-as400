package as400.comms;
import as400.*;

public class ZbxAddr {

    //instance variables
    String  ip;
    int     port;
    long    revision;
/*
    public ZbxAddr(String ip, int port, long revision) {
        this.ip         = ip;
        this.port       = port;
        this.revision   = revision;
    }//constructor ZbxAddr()
*/
    public ZbxAddr(String server) {
        String []splitted = server.split(":");
        this.ip = splitted[0].trim();
        this.port = 10051;
        if (1 < splitted.length)
            try {
                this.port = Config.parseInt(splitted[1].trim(), 1, 65535);
            } catch (ZbxException ex) {
                Util.log(Util.LOG_ERROR,"port number in line '%s' invalid, using default %d",
                        server, this.port);
            }//try-catch
        this.revision   = 0l;
    }//constructor ZbxAddr()

    public String getIp() {
        return this.ip;
    }//getIp()

    public int getPort() {
        return this.port;
    }//getPort()

    public long getRevision() {
        return this.revision;
    }//getRevision()

    public void setIp(String ip) {
        this.ip = ip;
    }//setIp()

    public void setPort(int port) {
        this.port = port;
    }//setPort()

    public void setRevision(long revision) {
        this.revision = revision;
    }//getRevision()

}//class ZbxAddr
