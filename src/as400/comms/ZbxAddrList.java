package as400.comms;
import as400.*;
import java.util.*;

public class ZbxAddrList {

    //instance variables
    ArrayList<ZbxAddr> values;
    String  err;

    public ZbxAddrList() {
        this.values = new ArrayList<ZbxAddr>();
        this.err = null;
    }//constructor ZbxAddrList()

    public ZbxAddrList(String cluster) throws ZbxException {
        this();
        String []splitted = cluster.split(";");
        for (int i=0; i<splitted.length; i++) {
            String server = splitted[i].trim();
            if (! "".equals(server))
                this.values.add(new ZbxAddr(server));
        }//for(splitted)
        if (0 == this.values.size())
            throw new ZbxException(String.format("Invalid cluster configuration in 'ServerActive=' config line: '%s'", cluster));
    }//constructor ZbxAddrList()

    public String getIp() {
        return this.values.get(0).ip;
    }//getIp()

    public int getPort() {
        return this.values.get(0).port;
    }//getPort()

    public String getErr() {
        return this.err;
    }//getErr()

    public void setErr(String err) {
        this.err = err;
    }//setErr()

    public int size() {
        return this.values.size();
    }//size()

    ZbxAddr getValue(int i) {
        return this.values.get(i);
    }//getValue()

    public void failover() {
        if (1 < this.values.size()) {
            ZbxAddr addr = this.values.remove(0);
            this.values.add(addr);
        }//if
    }//failover()

    void remove(int i) {
        this.values.remove(i);
    }//remove()

    //insert new address to the beginning of the list
    void insert(String server, long revision) {
        ZbxAddr addr = new ZbxAddr(server);
        addr.setRevision (revision);
        this.values.add(0, addr);
    }//remove()

    ZbxAddrList cloneFirstAddr() {
        ZbxAddrList addrs = new ZbxAddrList();
        addrs.values.add(this.values.get(0));
        return addrs;
    }//cloneFirstAddr()

}//class ZbxAddrList
