package as400;
import com.ibm.as400.access.*;
import java.io.IOException;

public class TestConnectivity {


    public static void main(String[] args) throws Exception {
        boolean running = true;
        AS400  system = null;
        SocketProperties sp = new SocketProperties();
        String as400ServerHost = "localhost";
        String as400user       = null;
        String as400Password   = "*CURRENT";

        if (args.length>0)
            as400ServerHost = args[0];
        if (args.length>1)
            as400user = args[1];
        if (args.length>2)
            as400Password = args[2];

        sp.setLoginTimeout(3000); //3 seconds
        system  = new AS400(as400ServerHost);
        system.setSocketProperties(sp);
        system.setGuiAvailable(false);//throws PropertyVetoException
        system.setUserId(as400user);
        system.setPassword(as400Password);

        //set trace on by IBM recommendations
        Trace.setTraceAllOn(true);
        Trace.setFileName("ToolboxTrace.txt");
        Trace.setTraceOn(true);

        while (running) {
            int ret = 0;
            for (int i = 0, service = 1; i < 8; i++, service <<= 1) {
                System.out.printf(" checking connection for a service %d (%d)\n", i, service);
                try  {
                    if (!system.isConnected(i))
                        system.connectService(i);
                } catch (IOException|AS400SecurityException ex) {
                    System.out.printf("---error: %s\n",ex);
                }
                if (!system.isConnectionAlive(i))
                    ret |= service;
            }//for
            System.out.printf("Result is: %d\n", ret);
            try {
                Thread.sleep(30*1000);//30 seconds
            } catch (InterruptedException ex) {
                running = false;
                System.out.printf("Interrupted, exiting.\n");
            }//try-catch
        }//while
        if (system.isConnected())
            system.disconnectAllServices();
        //Set trace off
        Trace.setTraceOn(false);
    }//main()

}//class TestConnectivity

