//package lv.rietumu.as400;
package as400;
import java.io.*;
import java.nio.charset.Charset;

public class Util {

    //Constants
    public static final int LOG_NO      = 0;
    public static final int LOG_CRITICAL= 1;
    public static final int LOG_ERROR   = 2;
    public static final int LOG_WARNING = 3;
    public static final int LOG_INFO    = -1;
    public static final int LOG_DEBUG   = 4;
    public static final int LOG_TRACE1  = 5;

    public static final int CF_HAVEPARAMS   = 0x01; //item accepts either optional or mandatory parameters
    public static final int CF_MODULE       = 0x02; //item is defined in a loadable module
    public static final int CF_USERPARAMETER= 0x04; //item is defined as user parameter
    public static final int CF_AS400COMM    = 0x80000000; //real communication to AS/400 system is needed for obtaining value of this item

    private static final java.text.SimpleDateFormat ts = new java.text.SimpleDateFormat("yyyyMMdd:HHmmss.SSS");
    static Charset utf8 = null;

    private static Long stored_ts   = new Long(0l);

    //Static variables
    private static PrintWriter out = null;
    private static File outFile = null;
    private static StringBuilder buf = null;

    public static synchronized void log(int level, String message, Object... args) {
        if (Config.getDebugLevel() >= level) {
            try {
                if (null == out) {
                    if (Config.isConfigured()) {
                        outFile = new File(Config.getLogFile());
                        if (!outFile.exists()) {
                            outFile.createNewFile();
                            outFile.setWritable(true,false);
                        }//if (not exists)
                        //create new  PrintWriter from the File with autoflushing using the specified charset
                        out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(outFile,true), Util.getUtf8()), true);
                        if (null != buf) {
                            out.println(buf.toString());
                            buf = null;
                            if (LOG_WARNING <= Config.getDebugLevel()) {
                                out.printf("\n% 6d:%s ", Thread.currentThread().getId(), ts.format(new java.util.Date()));
                                out.println("Logging switched from the buffer to the log file '" + Config.getLogFile() + "'\n");
                            }
                        }//if (buf)
                    } else {
                        //Config is not processed yet, so we know nothing about log file name; therefore store to the buffer
                        if (null == buf)
                            buf = new StringBuilder();
                        buf.append(String.format("% 6d:%s ", Thread.currentThread().getId(), ts.format(new java.util.Date())));
                        buf.append(String.format(message, args));
                        buf.append('\n');
                        return;
                    }//if (configured)
                } else {
                    if (0l != Config.getLogFileSize() && Config.getLogFileSize() < outFile.length()) {
                        rotateLog();
                    }//if needed to rotate
                }//if (out initialized)
                out.printf("% 6d:%s ", Thread.currentThread().getId(), ts.format(new java.util.Date()));
                out.printf(message, args);
                out.println();
            } catch (IOException ie) {
                    System.out.println ("Error writing to log file " + Config.getLogFile() + ":\n" + ie.toString());
                    System.exit(1);
            }//try-catch block
        }//if
    }//log()

    public static synchronized void log(int level, Throwable ex, String message, Object... args) {
        log(level, message, args);
        if (null != out)
            ex.printStackTrace(out);
        else
            log(level, "\n%s\n", ex);
    }//log()

    private static void rotateLog() {
        out.close();
        out = null;
        String log_name = Config.getLogFile();
        File new_file = new File(log_name + ".old");
        if ( (! new_file.exists() || new_file.delete() ) && outFile.renameTo(new_file)) {
            log (LOG_NO,"Current log file rotated");
        } else {
            //exists and could not delete or could not be renamed
            //so, truncate the log file
            outFile.delete();
            log (LOG_NO,"Logfile \"%s\" size reached configured limit but rotating failed. It was truncated.", log_name);
        }//if (successfully rotated)
    }//rotateLog()

    public static void flushLogAndExit() {
        if (null != buf) {
            try {
                outFile = new File(Config.getLogFile());
                if (!outFile.exists()) {
                    outFile.createNewFile();
                    outFile.setWritable(true,false);
                }//if (not exists)
                //create new  PrintWriter from the File with autoflushing using the specified charset
                out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(outFile,true), Util.getUtf8()), true);
                out.println(buf.toString());
                out.close();
            } catch (IOException ie) {
                System.out.println ("Error writing to log file " + Config.getLogFile() + ":\n" + ie.toString());
                System.out.println (buf.toString());
            }//try-catch
        }//if(buf)
        System.exit(1);
    }//flushLogAndExit()

    public static PrintWriter getPrintWriter() {
        return out;
    }//getPrintWriter()

    public synchronized static Charset getUtf8() {
        if (null == utf8)
            try {
                utf8 = java.nio.charset.Charset.forName("UTF-8");
            } catch (IllegalArgumentException ex) {
                System.out.println("Critical error upon creating UTF-8 charset: %s" + ex.toString());
                System.exit(1);
            }//try-catch
        return utf8;
    }//getUtf8

    public static long currentTimeMillis() {
        long current_ts = System.currentTimeMillis();
        synchronized (stored_ts) {
            if (current_ts <= stored_ts.longValue())
                current_ts = stored_ts.longValue() + 1;
            stored_ts = new Long(current_ts);
        }
        return current_ts;
    }//currentTimeMillis

    public static long key2long(byte[] key) {
        long rez = 0l;
        if (null != key)
            for (int i = 0; i < key.length; i++) {
                rez = (rez << 8) | (key[i] & 0x00FF);
            }//for
        return rez;
    }//key2long()

    public static byte[] long2key(long key) {
        byte[] rez = new byte[4];
        for (int i = rez.length - 1; i >= 0; i--) {
            rez[i] = (byte)(key & 0x000000FF);
            key >>= 8;
        }//for
        return rez;
    }//long2key()

}//class Util
