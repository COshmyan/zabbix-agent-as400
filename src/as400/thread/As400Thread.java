package as400.thread;
import com.ibm.as400.access.AS400;

public interface As400Thread {

    public AS400 getAs400();

    public boolean isAs400CommError();

    public void setAs400CommError(boolean value);

}//class As400Thread
