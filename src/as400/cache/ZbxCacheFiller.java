package as400.cache;
import as400.ZbxException;
import java.io.IOException;

public interface ZbxCacheFiller {

    public void fill() throws ZbxException, IOException;

}//interface ZbxCacheFiller
