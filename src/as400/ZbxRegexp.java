package as400;
import as400.thread.ActiveCheck;
import java.util.ArrayList;
import java.util.regex.*;

public class ZbxRegexp {

    //constants
    private static final int EXPRESSION_TYPE_INCLUDED     = 0;
    private static final int EXPRESSION_TYPE_ANY_INCLUDED = 1;
    private static final int EXPRESSION_TYPE_NOT_INCLUDED = 2;
    private static final int EXPRESSION_TYPE_TRUE         = 3;
    private static final int EXPRESSION_TYPE_FALSE        = 4;

    public static class Subexp {
        //class variables
        int type;
        String expression = null;
        Pattern pattern = null;
        String[] expr_list = null;
        boolean case_sensitive;

        private Subexp(int type, String str, char delimiter, boolean case_sensitive) {
            this.type = type;
            this.case_sensitive = case_sensitive;
            if (!case_sensitive)
                str = str.toUpperCase();
            switch (type) {
            case EXPRESSION_TYPE_TRUE:
            case EXPRESSION_TYPE_FALSE:
                int flags = Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;
                if (!case_sensitive)
                    flags |= Pattern.CASE_INSENSITIVE;
                this.pattern = Pattern.compile(str, flags);
                break;
            case EXPRESSION_TYPE_INCLUDED:
            case EXPRESSION_TYPE_NOT_INCLUDED:
                this.expression = str;
                break;
            case EXPRESSION_TYPE_ANY_INCLUDED:
                this.expr_list = str.split(Pattern.quote(String.valueOf(delimiter)));
                break;
            default://never should occur
                ;
            }//switch-case
        }//constructor Subexp()

        private boolean matches(String str) {
            if (!case_sensitive)
                str = str.toUpperCase();
            switch (this.type) {
            case EXPRESSION_TYPE_TRUE:
                return  pattern.matcher(str).matches();
            case EXPRESSION_TYPE_FALSE:
                return !pattern.matcher(str).matches();
            case EXPRESSION_TYPE_INCLUDED:
                return  str.contains(this.expression);
            case EXPRESSION_TYPE_NOT_INCLUDED:
                return !str.contains(this.expression);
            case EXPRESSION_TYPE_ANY_INCLUDED:
                for (int i = 0; i < expr_list.length; i++)
                    if (str.contains(expr_list[i]))
                        return true;
                return false;
            default://should never occur, but for compiler be happy
                return true;
            }//switch-case
        }//matches()

    }//inner class Subexp

    //local variables
    private String name;
    private Pattern pattern;
    private ArrayList<Subexp> subexpr_list;

    public ZbxRegexp(String str, boolean case_sensitive) {
        this.name = str;

        if (null != str && 0 < str.length() && '@' == str.charAt(0)) {
            str = str.substring(1);
            ZbxRegexp globalRegex = null;
            try {
                globalRegex = ((ActiveCheck)Thread.currentThread()).getGlobalRegex(str);
            } catch (ClassCastException ex) {
            }//try-catch
            if (null == globalRegex) {
                Util.log(Util.LOG_WARNING, " There is no global regular expression '@%s'", str);
            } else {
                this.pattern = null;
                this.subexpr_list = globalRegex.subexpr_list;
            }
        } else {
            int flags = Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;
            if (!case_sensitive)
                flags |= Pattern.CASE_INSENSITIVE;
            this.pattern = (null == str || "".equals(str)) ? null : Pattern.compile(".*" + str + ".*", flags);
            this.subexpr_list = null;
        }//if (global RE)
    }//constructor ZbxRegexp()

    public ZbxRegexp(String name, int type, String str, char delimiter, boolean case_sensitive) {
        this.name = name;
        this.pattern = null;
        this.subexpr_list = new ArrayList<Subexp>();
        this.subexpr_list.add(new Subexp(type, str, delimiter, case_sensitive));
    }//constructor ZbxRegexp()

    public void addSubexp(int type, String str, char delimiter, boolean case_sensitive) {
        if (null == this.subexpr_list)
            this.subexpr_list = new ArrayList<Subexp>();
        this.subexpr_list.add(new Subexp(type, str, delimiter, case_sensitive));
    }//addSubexp()

    public boolean matches(String str) {
        //empty RE always match
        if (null == name || "".equals(name))
            return true;
        //if there is pattern - just match it
        if (null != this.pattern)
            return this.pattern.matcher(str).matches();
        //global RE must have subexpressions, otherwise don't match
        if (null == subexpr_list)
            return false;
        //for global RE: match only if all subexpressions match
        for (int i = 0; i < subexpr_list.size(); i++) {
            if (! subexpr_list.get(i).matches(str))
                return false;
        }//for
        return true;
    }//matches()

    public String toString() {
        return this.name;
    }//toString()

}//class ZbxRegexp
