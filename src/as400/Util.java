package as400;
import java.io.*;
import java.nio.charset.Charset;
import java.util.Locale;

public class Util {

    static class MyLong {
        //class variables
        long value;

        //Constructor
        MyLong (long value) {
            this.value = value;
        }//Constructor

        void setValue(long value) {
            this.value = value;
        }//setValue()

        long getValue() { return this.value; }
    }//internal class

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

    //originally from "sysinfo.h"
    public static final int ZBX_PROCESS_LOCAL_COMMAND   = 0x01;
    public static final int ZBX_PROCESS_MODULE_COMMAND  = 0x02; //we really don't use this constant as we don't use modules
    public static final int ZBX_PROCESS_WITH_ALIAS      = 0x04;

    private static final java.text.SimpleDateFormat ts = new java.text.SimpleDateFormat("yyyyMMdd:HHmmss.SSS");
    private static java.text.NumberFormat nf = null;
    static Charset utf8 = null;

    private static MyLong stored_ts = new MyLong(0l);

    //Static variables
    private static PrintWriter out = null;
    private static File outFile = null;
    private static StringBuilder buf = null;

    public static synchronized void log(int level, String message, Object... args) {
        Thread t = Thread.currentThread();
        if (t instanceof as400.thread.RequestThread)
            t = ((as400.thread.RequestThread)t).getParentThread();
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
                            out.printf("%n%s%n", buf.toString());
                            buf = null;
                            if (LOG_WARNING <= Config.getDebugLevel()) {
                                out.printf("%6d:%s Logging switched from the buffer to the log file '%s'%n%n",
                                    t.getId(), ts.format(new java.util.Date()), Config.getLogFile());
                            }
                        }//if (buf)
                    } else {
                        //Config is not processed yet, so we know nothing about log file name; therefore store to the buffer
                        if (null == buf)
                            buf = new StringBuilder();
                        buf.append(String.format("%6d:%s ", t.getId(), ts.format(new java.util.Date())));
                        buf.append(String.format(message, args));
                        buf.append(System.lineSeparator());
                        return;
                    }//if (configured)
                } else {
                    if (0l != Config.getLogFileSize() && Config.getLogFileSize() < outFile.length()) {
                        rotateLog();
                    }//if needed to rotate
                }//if (out initialized)
                out.printf("% 6d:%s ", t.getId(), ts.format(new java.util.Date()));
                out.printf(message, args);
                out.println();
            } catch (IOException ie) {
                    System.out.printf ("Error writing to log file '%s':%n%s%n", Config.getLogFile(), ie.toString());
                    System.exit(1);
            }//try-catch block
        }//if
    }//log()

    public static synchronized void log(int level, Throwable ex, String message, Object... args) {
        log(level, message, args);
        if (null != out)
            ex.printStackTrace(out);
        else
            log(level, "%n%s%n", ex);
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
                System.out.printf ("Error writing to log file '%s':%n%s%n", Config.getLogFile(), ie.toString());
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
            if (current_ts <= stored_ts.getValue())
                current_ts = stored_ts.getValue() + 1;
            stored_ts.setValue(current_ts);
        }//sync
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

    public static String createToken(int hash) {
        long current_ts = System.currentTimeMillis();
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            md.update(long2key((long)hash));
            md.update(long2key(current_ts));
            return String.format("%032x", new java.math.BigInteger(1, md.digest()));
        } catch (java.security.NoSuchAlgorithmException ex) {
            return String.format("%08x%016x1234abcd",hash,current_ts);
        }//try-catch
    }//createToken()

    public static String zbxWildcardMinimize(String str) {
        while (0 <= str.indexOf("**")) {
            str = str.replace("**", "*");
        }//while
        return str;
    }//zbxWildcardMinimize()

    public static boolean zbxWildcardMatch(String str, String pattern) {
        int s1 = 0, s2 = 0, p1 = 0, p2 = 0, len;
        int str_len = str.length(), pat_len = pattern.length();

        if (0 > (p2 = pattern.indexOf('*')) )
            //if pattern does not contain '*', then just compare them as strings
            return str.equals(pattern);

        str_len = str.length();
        pat_len = pattern.length();
        len = p2;   //length of substring before '*'

        if (0 < len) {
            if (!str.regionMatches(0, pattern, 0, len))
                return false;
            s1 += len;  //next unchecked char (can be outside of str)
        }//if

        for ( ; ; ) {
            //is '*' the last char of pattern?
            if (pat_len == ++p2)
                return true;
            p1 = p2;  //next char after '*' (it is NOT the end of string, we checked it already)

            if (0 > (p2 = pattern.indexOf('*', p1)) ) {
                //If there is no more '*' in pattern, then str matches if it ends with the rest of pattern
                //and this matching part starts at least at s1 position (not before).
                //I.e. pattern 'abc*abcd' should not match with 'abcd' string.
                len = pat_len - p1;
                return (s1 + len <= str_len) && str.regionMatches(str_len - len, pattern, p1, len);
            }
            //We are here if a new '*' found.
            //So, we need to find next substring part of pattern before '*'.
            if (0 > (s2 = str.indexOf(pattern.substring(p1, p2), s1)) )
                return false;   //if not found - there is no matching
            //otherwise: found
            len = p2 - p1;
            p1 = p2;
            s1 = s2 + len;  //can be end of str
        }//for()

    }//zbxWildcardMatch()


    public static String roundFloat(double value) {
        if (null == nf) {
            nf = java.text.NumberFormat.getNumberInstance(Locale.US);
            nf.setMaximumFractionDigits(6);
            nf.setRoundingMode(java.math.RoundingMode.HALF_UP);
        }
        return nf.format(value);
    }//roundFloat()

    private static void zbxCheckUserParameter(String param) throws ZbxException {
        if (Config.getUnsafeUserParameters())
            return;
        char suppressed_chars[] = {'\\', '\'', '\"', '`', '*', '?', '[', ']', '{', '}', '~', '$', '!', '&', ';', '(', ')', '<', '>', '|', '#', '@', '\n'};
        for (int i = 0; i<suppressed_chars.length; i++) {
            if (0 > param.indexOf(suppressed_chars[i]))
                continue;
            StringBuilder buf = new StringBuilder();
            for (i = 0; i<suppressed_chars.length; i++) {
                if (0 < i)
                    buf.append(", ");
                if ( Character.isWhitespace(suppressed_chars[i]) )
                    buf.append(String.format("0x%02x", suppressed_chars[i]));
                else
                    buf.append(suppressed_chars[i]);
            }//for
            throw new ZbxException("Special characters\"" + buf.toString() + "\" are not allowed in the parameters.");
        }//for
    }//zbxCheckUserParameter()

    public static String replaceParam(String cmd, AgentRequest request) throws ZbxException {
        StringBuilder res = new StringBuilder();
        int p1 = 0, p2, len = cmd.length();
        char c;
        while ( 0 <= (p2 = cmd.indexOf('$', p1)) ) {
            res.append(cmd.substring(p1, p2));
            if (p2 < (len-1))
                p2++;
            if ('0' == (c = cmd.charAt(p2)) )
                res.append(cmd);
            else if ('1' <= c && c <= '9') {
                String param = request.getParam(c - '1');
                if (null != param) {
                    zbxCheckUserParameter(param);
                    res.append(param);
                }
            } else {
                if ('$' != c)
                    res.append('$');
                res.append(c);
            }//if
            p1 = p2 + 1;
        }//while
        return res.toString();
    }//replaceParam()

}//class Util
