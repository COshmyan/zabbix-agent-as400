package as400.comms;
import as400.*;

public class ZbxSubnet {

    //instance variables
    String  ip;
    int     prefix_size;
/*
    public ZbxSubnet(String ip, int prefix_size) {
        this.ip         = ip;
        this.prefix_size= prefix_size;
    }//constructor ZbxSubnet()
*/
    public ZbxSubnet(String address) throws ZbxException {
        String []splitted = address.split("/");

        this.ip = splitted[0].trim();
        if (0 == this.ip.length())
            throw new ZbxException("Invalid empty server name");

        switch (splitted.length) {
        case 1:
            this.prefix_size = 32;
            break;
        case 2:
            try {
                this.prefix_size = Config.parseInt(splitted[1], 0, 32);
            } catch (ZbxException ex) {
                throw new ZbxException("Invalid mask length='" + splitted[1] +
                    "'; " + ex.getMessage());
            }//try-catch
            break;
        default:    //wrong format
            throw new ZbxException("Wrong subnet mask length: '" + address + "'");
        }//switch-case

    }//constructor ZbxSubnet()

    public String getIp() {
        return this.ip;
    }//getIp()

    public int getPrefixSize() {
        return this.prefix_size;
    }//getPrefixSize()
/*
    public void setIp(String ip) {
        this.ip = ip;
    }//setIp()

    public void setPrefixSize(int prefix_size) {
        this.prefix_size = prefix_size;
    }//setPrefixSize()
*/

}//class ZbxSubnet
