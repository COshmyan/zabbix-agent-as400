package as400;
import java.util.*;
import java.util.regex.*;

public class Interval {


    static class ZbxTimePeriod {
        //class variables
        static Pattern pattern_period = Pattern.compile("^(\\d)(?:-(\\d))?,(\\d{1,2}):(\\d{1,2})-(\\d{1,2}):(\\d{1,2})$");

        //object variables
        //days of week: 1=Monday, ..., 7=Sunday.
        int start_day;  //day of week when period starts
        int end_day;    //day of week when period ends, included
        int start_time; //number of seconds from the beginning of the day when period starts
        int end_time;   //number of seconds from the beginning of the day when period ends, not included

        //Supported format is d[-d],time-time where 1 <= d <= 7
        private ZbxTimePeriod(String str) throws ZbxException {
            Matcher m = pattern_period.matcher(str);
            int n;
            if (!m.matches())
                throw new ZbxException("Wrong format for period in flexible interval");

            n = Config.parseInt(m.group(1), 0, 7);
            this.start_day = (0 == n) ? 7 : n;
            if (null == (str = m.group(2)) || "".equals(str)) {
                this.end_day = start_day;
            } else {
                n = Config.parseInt(str, 0, 7);
                this.end_day = (0 == n) ? 7 : n;
            }
            this.start_time = timeParse(m.group(3), m.group(4));
            this.end_time   = timeParse(m.group(5), m.group(6));

            if (this.start_day > this.end_day || this.start_time >= this.end_time)
                throw new ZbxException("Wrong format for period in flexible interval");
        }//constructor ZbxTimePeriod()

        public boolean checkTimePeriod(Calendar cal) {
            int day = dayOfWeek(cal);
            int time = cal.get(Calendar.HOUR_OF_DAY)    /* 0-23 */ * 3600 +
                       cal.get(Calendar.MINUTE)         /* 0-59 */ * 60 +
                       cal.get(Calendar.SECOND);        /* 0-60 */
            return this.start_day <= day && day <= this.end_day && this.start_time <= time && time < this.end_time;
        }//checkTimePeriod()

        //Number of seconds since the beginning of the day
        private static int timeParse(String str_hours, String str_minutes) throws ZbxException {
            int hours, minutes;
            hours   = Config.parseInt(str_hours,   0, 24);
            minutes = Config.parseInt(str_minutes, 0, 59);
            if (24 == hours && 0 != minutes)
                throw new ZbxException("Wrong time format");
            return hours * 3600 + minutes * 60;
        }//timeParse()

    }//internal class ZbxTimePeriod


    static class ZbxFlexibleInterval {
        //object variables
        long delay_ms;
        ZbxTimePeriod period;

        private ZbxFlexibleInterval(String str) throws ZbxException {
            String []mas = str.split("/");
            if (2 != mas.length)
                throw new ZbxException("Wrong format for flexible interval");
            this.delay_ms = time2long(mas[0]);
            this.period = new ZbxTimePeriod(mas[1]);
        }//constructor ZbxFlexibleInterval()

    }//internal class ZbxFlexibleInterval


    static class ZbxSchedulerFilter {
        //object variables
        int start;
        int end;
        int step;

        private ZbxSchedulerFilter(int start, int end, int step) {
            this.start = start;
            this.end = end;
            this.step = step;
        }//constructor ZbxSchedulerFilter()

    }//internal class ZbxSchedulerFilter


    static class ZbxSchedulerInterval {
        //class static variables
        private static final int ZBX_SCHEDULER_FILTER_DAY    = 1;
        private static final int ZBX_SCHEDULER_FILTER_HOUR   = 2;
        private static final int ZBX_SCHEDULER_FILTER_MINUTE = 3;
        private static final int ZBX_SCHEDULER_FILTER_SECOND = 4;
        static Pattern pattern_scheduler_interval = Pattern.compile("(md?|wd|h|s)([-0-9,\\/]+)");
        static Pattern pattern_scheduler_filter = Pattern.compile("(?:(\\d{1,2})(?:-(\\d{1,2}))?)?(?:\\/(\\d{1,2}))?");

        //object variables
        ZbxSchedulerFilter []mdays;
        ZbxSchedulerFilter []wdays;
        ZbxSchedulerFilter []hours;
        ZbxSchedulerFilter []minutes;
        ZbxSchedulerFilter []seconds;
        int filter_level;

        private ZbxSchedulerInterval(String str) throws ZbxException {
            this.filter_level = 0;
            Matcher m = pattern_scheduler_interval.matcher(str);
            boolean ret = false;
            String unit, filters;
            String err = "Scheduled interval is invalid or empty";
            ArrayList<ZbxSchedulerFilter> mdays   = new ArrayList<ZbxSchedulerFilter>();
            ArrayList<ZbxSchedulerFilter> wdays   = new ArrayList<ZbxSchedulerFilter>();
            ArrayList<ZbxSchedulerFilter> hours   = new ArrayList<ZbxSchedulerFilter>();
            ArrayList<ZbxSchedulerFilter> minutes = new ArrayList<ZbxSchedulerFilter>();
            ArrayList<ZbxSchedulerFilter> seconds = new ArrayList<ZbxSchedulerFilter>();
            Util.log(Util.LOG_DEBUG, "ZbxSchedulerInterval(): '%s'", str);

            while (m.find()) {
                try {
                    unit = m.group(1);
                    filters = m.group(2);
                    Util.log(Util.LOG_DEBUG, " ZbxSchedulerInterval(): unit='%s', filters='%s'", unit, filters);
                    switch (unit) {
                    case "md":  //month days
                        if (ZBX_SCHEDULER_FILTER_DAY < this.filter_level || !mdays.isEmpty() || !wdays.isEmpty())
                            throw new ZbxException(null);
                        schedulerParseFilter(mdays, filters, 1, 31);
                        this.filter_level = ZBX_SCHEDULER_FILTER_DAY;
                        break;
                    case "wd":  //week days
                        if (ZBX_SCHEDULER_FILTER_DAY < this.filter_level || !wdays.isEmpty())
                            throw new ZbxException(null);
                        schedulerParseFilter(wdays, filters, 1, 7);
                        this.filter_level = ZBX_SCHEDULER_FILTER_DAY;
                        break;
                    case "h":   //hours
                        if (ZBX_SCHEDULER_FILTER_HOUR < this.filter_level || !hours.isEmpty())
                            throw new ZbxException(null);
                        schedulerParseFilter(hours, filters, 0, 23);
                        this.filter_level = ZBX_SCHEDULER_FILTER_HOUR;
                        break;
                    case "m":   //minutes
                        if (ZBX_SCHEDULER_FILTER_MINUTE < this.filter_level || !minutes.isEmpty())
                            throw new ZbxException(null);
                        schedulerParseFilter(minutes, filters, 0, 59);
                        this.filter_level = ZBX_SCHEDULER_FILTER_MINUTE;
                        break;
                    case "s":   //seconds
                        if (ZBX_SCHEDULER_FILTER_SECOND < this.filter_level || !seconds.isEmpty())
                            throw new ZbxException(null);
                        schedulerParseFilter(seconds, filters, 0, 59);
                        this.filter_level = ZBX_SCHEDULER_FILTER_SECOND;
                        break;
                    default:
                        throw new ZbxException(null);
                    }//switch-case
                } catch (ZbxException ex) {
                    ret = false;
                    String err1 = ex.getMessage();
                    if (null != err1)
                        err = "Invalid scheduled interval: " + err1;
                    break;
                }//try-catch
                ret = true;
            }//while
            if (!ret) {
                Util.log(Util.LOG_WARNING, " Error parsing '%s': %s", str, err);
                throw new ZbxException(err + " (" + str + ")");
            }
            this.mdays   = mdays.isEmpty()   ? null : mdays.toArray(new ZbxSchedulerFilter[] {});
            this.wdays   = wdays.isEmpty()   ? null : wdays.toArray(new ZbxSchedulerFilter[] {});
            this.hours   = hours.isEmpty()   ? null : hours.toArray(new ZbxSchedulerFilter[] {});
            this.minutes = minutes.isEmpty() ? null : minutes.toArray(new ZbxSchedulerFilter[] {});
            this.seconds = seconds.isEmpty() ? null : seconds.toArray(new ZbxSchedulerFilter[] {});
        }//constructor ZbxSchedulerInterval()

        private boolean schedulerGetFilterNextcheck(int level, Calendar cal) {
            ZbxSchedulerFilter []filters;
            int max, field, value;

            switch (level) {
/*
            case ZBX_SCHEDULER_FILTER_DAY:
                return schedulerGetDayNextcheck(cal);
*/
            case ZBX_SCHEDULER_FILTER_HOUR:
                max = 23;
                filters = this.hours;
                field = Calendar.HOUR_OF_DAY;   //0-23
                break;
            case ZBX_SCHEDULER_FILTER_MINUTE:
                max = 59;
                filters = this.minutes;
                field = Calendar.MINUTE;   //0-59
                break;
            case ZBX_SCHEDULER_FILTER_SECOND:
                max = 59;
                filters = this.seconds;
                field = Calendar.SECOND;   //0-60
                break;
            default:
                Util.log(Util.LOG_ERROR, "schedulerGetFilterNextcheck(): invalid level=%d", level);
                return false;
            }//switch-case

            value = cal.get(field);
            if (max < value) //should not be true in Java, as Calendar automatically tracks changes
                return false;

            if (null == filters) {
                //Empty filter matches all valid values if the filter level is less than
                //interval filter level. For example if interval filter level is minutes - m30,
                //then hour filter matches all hours.
                if (this.filter_level > level)
                    return true;
                //else:
                //If the filter level is greater than interval filter level, then filter
                //matches only 0 value. For example if interval filter level is minutes - m30,
                //then seconds filter matches the 0th second.
                return ( 0 == value );
            }//if(filters==null)

            max = schedulerGetNearestFilterValue(filters, value);
//            Util.log(Util.LOG_TRACE1, "   schedulerGetFilterNextcheck(): value=%d, schedulerGetNearestFilterValue()=%d", value, max);
            if (0 <= max) {
                cal.set(field, max);
                return true;
            } else {
                return false;
            }
        }//schedulerGetFilterNextcheck()

        private void schedulerApplyDayFilter(Calendar cal) {
            if (Util.LOG_TRACE1 <= Config.getDebugLevel())
                Util.log(Util.LOG_TRACE1, "  in schedulerApplyDayFilter() cal=%s", calendar2String(cal));
            int day = cal.get(Calendar.DAY_OF_YEAR), year = cal.get(Calendar.YEAR);

            while (!schedulerGetDayNextcheck(cal)) {
                //continue from the 1st day of the next month
                cal.set(Calendar.DAY_OF_MONTH, 1);
                //possible, increasing year; normalization will be at nearest cal.get()
                cal.add(Calendar.MONTH, 1);
            }//while

            //reset hours, minutes and seconds if the date has been changed
            if (cal.get(Calendar.DAY_OF_YEAR) != day || cal.get(Calendar.YEAR) != year) {
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
            }//if
            if (Util.LOG_TRACE1 <= Config.getDebugLevel())
                Util.log(Util.LOG_TRACE1, "  end of schedulerApplyDayFilter() cal=%s", calendar2String(cal));
        }//schedulerApplyDayFilter()

        private void schedulerApplyHourFilter(Calendar cal) {
            if (Util.LOG_TRACE1 <= Config.getDebugLevel())
                Util.log(Util.LOG_TRACE1, "  in schedulerApplyHourFilter() cal=%s", calendar2String(cal));
            int hour = cal.get(Calendar.HOUR_OF_DAY);

            while (!schedulerGetFilterNextcheck(ZBX_SCHEDULER_FILTER_HOUR, cal)){
                cal.add(Calendar.DAY_OF_MONTH, 1);
                cal.set(Calendar.HOUR_OF_DAY, 0);
                //day has been changed, we have to reapply day filter
                schedulerApplyDayFilter(cal);
            }//while

            //reset minutes and seconds if hours has been changed
            if (cal.get(Calendar.HOUR_OF_DAY) != hour) {
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
            }//if
            if (Util.LOG_TRACE1 <= Config.getDebugLevel())
                Util.log(Util.LOG_TRACE1, "  end of schedulerApplyHourFilter() cal=%s", calendar2String(cal));
        }//schedulerApplyHourFilter()
        
        private void schedulerApplyMinuteFilter(Calendar cal) {
            if (Util.LOG_TRACE1 <= Config.getDebugLevel())
                Util.log(Util.LOG_TRACE1, "  in schedulerApplyMinuteFilter() cal=%s", calendar2String(cal));
            int min = cal.get(Calendar.MINUTE);

            while (!schedulerGetFilterNextcheck(ZBX_SCHEDULER_FILTER_MINUTE, cal)){
                cal.add(Calendar.HOUR_OF_DAY, 1);
                cal.set(Calendar.MINUTE, 0);
                if (Util.LOG_TRACE1 <= Config.getDebugLevel())
                    Util.log(Util.LOG_TRACE1, "  schedulerApplyMinuteFilter(): schedulerGetFilterNextcheck() returned (-1), hour increased; cal=%s", calendar2String(cal));
                //if the date changed, we have to reapply day filter
                if (0 == cal.get(Calendar.HOUR_OF_DAY)) {
                    Util.log(Util.LOG_TRACE1, "  schedulerApplyMinuteFilter(): next date, reappying day filter");
                    schedulerApplyDayFilter(cal);
                }
                //hours have been changed, we have to reapply hour filter
                schedulerApplyHourFilter(cal);
            }//while

            //reset seconds if minutes has been changed
            if (cal.get(Calendar.MINUTE) != min) {
                cal.set(Calendar.SECOND, 0);
            }//if
            if (Util.LOG_TRACE1 <= Config.getDebugLevel())
                Util.log(Util.LOG_TRACE1, "  end of schedulerApplyMinuteFilter() cal=%s", calendar2String(cal));
        }//schedulerApplyMinuteFilter()
        
        private void schedulerApplySecondFilter(Calendar cal) {
            if (Util.LOG_TRACE1 <= Config.getDebugLevel())
                Util.log(Util.LOG_TRACE1, "  in of schedulerApplySecondFilter() cal=%s", calendar2String(cal));

            while (!schedulerGetFilterNextcheck(ZBX_SCHEDULER_FILTER_SECOND, cal)){
                int day = cal.get(Calendar.DAY_OF_YEAR);
                cal.add(Calendar.MINUTE, 1);
                cal.set(Calendar.SECOND, 0);
                Util.log(Util.LOG_TRACE1, "  schedulerApplySecondFilter(): next minute, will reappy minute filter");
                //if the hour changed, we have to reapply day filter
                if (0 == cal.get(Calendar.MINUTE)) {
                    Util.log(Util.LOG_TRACE1, "  schedulerApplySecondFilter(): next hour, reappying hour filter");
                    if (cal.get(Calendar.DAY_OF_YEAR) != day) {
                        Util.log(Util.LOG_TRACE1, "  schedulerApplySecondFilter(): but next date, reappying day filter before");
                        schedulerApplyDayFilter(cal);
                    }//if date changed
                    schedulerApplyHourFilter(cal);
                }//if hour changed
                //minutes have been changed, we have to reapply minute filter
                schedulerApplyMinuteFilter(cal);
            }//while

            if (Util.LOG_TRACE1 <= Config.getDebugLevel())
                Util.log(Util.LOG_TRACE1, "  end of schedulerApplySecondFilter() cal=%s", calendar2String(cal));
        }//schedulerApplySecondFilter()

        //calculates next day that satisfies month and week day filters
        private boolean schedulerGetDayNextcheck(Calendar cal) {
            if (null == this.mdays)
                return schedulerGetWdayNextcheck(cal);

            //iterate through month days until week day filter matches or we have run out of month days
            for ( ; ; ) {
                int mday = schedulerGetNearestFilterValue(this.mdays, cal.get(Calendar.DAY_OF_MONTH));
                if (0 > mday || mday > daysInMonth(cal)) {
                    break;
                }//if(success)

                cal.set(Calendar.DAY_OF_MONTH, mday);
                if (schedulerValidateWdayFilter(cal))
                    return true;

                cal.add(Calendar.DAY_OF_MONTH, 1);
            }//while
            return false;
        }//schedulerGetDayNextcheck()

        //adjust "cal" to the correct day of week matching the week day filters
        //(possible, increasing day of month, month and even year)
        private boolean schedulerGetWdayNextcheck(Calendar cal) {
            int value_now, value_next;

            if (null == this.wdays)
                return true;

            value_now = value_next = dayOfWeek(cal);
            //get the nearest week day from the current week day
            if ( (value_next = schedulerGetNearestFilterValue(this.wdays, value_next)) < 0) {
                //in the case of failure move month day to the next week, reset week day and try again
                cal.add(Calendar.DAY_OF_MONTH, 7 - value_now + 1);
                value_now = value_next = 1;
                if ( (value_next = schedulerGetNearestFilterValue(this.wdays, value_next)) < 0) {
                    //a valid week day filter must always match some day of a new week
                    Util.log(Util.LOG_ERROR, "ERROR in schedulerGetWdayNextcheck(): could not find any filter for date %s", cal.toString());
                    return false;
                }//if(fail)
            }//if(fail)

            //adjust the month day by the week day offset
            int offset = value_next - value_now;
            if (daysInMonth(cal) < (cal.get(Calendar.DAY_OF_MONTH) + offset))
                return false;
            cal.add(Calendar.DAY_OF_MONTH, offset);
            return true;
        }//schedulerGetWdayNextcheck()

        private boolean schedulerValidateWdayFilter(Calendar cal) {
            int value;

            if (null == this.wdays)
                return true;

            value = dayOfWeek(cal);
            for (int i=0; i<this.wdays.length; i++) {
                ZbxSchedulerFilter filter = this.wdays[i];
                if (filter.start <= value && value <= filter.end) {
                    int next = value, offset;
                    //apply step
                    offset = (next - filter.start) % filter.step;
                    if (0 != offset)
                        next += filter.step - offset;
                    //succeed if the calculated value is still in filter range
                    if (next <= filter.end)
                        return true;
                }//if(value in filter)
            }//for

            return false;
        }//schedulerValidateWdayFilter

        //static methods

        private static void schedulerParseFilter(ArrayList<ZbxSchedulerFilter> filters, String str, int min, int max) throws ZbxException {
            if (!filters.isEmpty())
                throw new ZbxException(null);

            int start, end, step;
            String start_str, end_str, step_str;
            String []mas = str.split(",");

            for (int i=0; i<mas.length; i++) {
                Matcher m = pattern_scheduler_filter.matcher(mas[i]);
                if ("".equals(mas[i]) || !m.matches())
                    throw new ZbxException(null);

                step = 1;
                start_str = m.group(1);
                end_str   = m.group(2);
                step_str  = m.group(3);
                Util.log(Util.LOG_TRACE1, "  schedulerParseFilter(): '%s'-'%s'/'%s'", start_str, end_str, step_str);
                if ( (null == start_str || "".equals(start_str)) &&
                     (null == step_str  || "".equals(step_str)) )
                    throw new ZbxException(null);
                
                if (null != start_str && !"".equals(start_str)) {
                    start = Config.parseInt(start_str, min, max);

                    if (null != end_str && !"".equals(end_str)) {
                        end = Config.parseInt(end_str, min, max);
                        if (end < start)
                            throw new ZbxException(null);
                    } else {
                        end = start;
                    }//if(end!=null)

                } else {//start==null
                    start = min;
                    end = max;
                }//if(start!=null)

                if (null != step_str && !"".equals(step_str))
                    step = Config.parseInt(step_str, 1, end - start);

                Util.log(Util.LOG_TRACE1, "  schedulerParseFilter(): %d-%d/%d", start, end, step);
                filters.add(new ZbxSchedulerFilter(start, end, step));
            }//for

            if (filters.isEmpty())
                throw new ZbxException(null);
        }//schedulerParseFilter()

        private static int schedulerGetNearestFilterValue(ZbxSchedulerFilter []filters, int value) {
            ZbxSchedulerFilter filter, filter_next = null;

            for (int i=0; null != filters && i<filters.length; i++) {
                filter = filters[i];

                //find matching filter
                if (filter.start <= value && value <= filter.end) {
                    int next = value, offset;
                    //apply step
                    offset = (next - filter.start) % filter.step;
                    if (0 != offset)
                        next += filter.step - offset;
                    //succeed if the calculated value is still in filter range
                    if (next <= filter.end) {
                        return next;
                    }//if
                }//if

                //find the next nearest filter
                if (filter.start > value && (null == filter_next || filter_next.start > filter.start))
                    filter_next = filter;
            }//for

            //The value is not in a range of any filters, but we have next nearest filter.
            if (null != filter_next) {
                return filter_next.start;
            }//if(filter_next!=null)
            return -1;  //unsuccessful
        }//schedulerGetNearestFilterValue()

    }//internal class ZbxSchedulerInterval


    //class variables
    public static final long ZBX_JAN_2038 = 2145916800000l;    //00:00:00 01.01.2038 GMT (in milliseconds)
    private static Pattern pattern_time = Pattern.compile("^(\\d+)([smhdwM]?)$");
    private static long time_dst = 0l;

    //object variables
    String               str;   //original string or error message
    long                 default_delay_ms;
    ZbxFlexibleInterval  []flexible;
    ZbxSchedulerInterval []scheduling;

    public Interval(String interval) {
        this.str = interval;
        this.default_delay_ms = -1l;
        this.flexible = null;
        this.scheduling = null;
        String []mas = interval.split(";");
        try {
            this.default_delay_ms = time2long(mas[0]);
            ArrayList<ZbxFlexibleInterval> flexible = new ArrayList<ZbxFlexibleInterval>();
            ArrayList<ZbxSchedulerInterval> scheduling = new ArrayList<ZbxSchedulerInterval>();
            for (int i=1; i<mas.length; i++) {
                if (Character.isDigit(mas[i].charAt(0))) {
                    flexible.add(new ZbxFlexibleInterval(mas[i]));
                } else {
                    scheduling.add(new ZbxSchedulerInterval(mas[i]));
                }
            }//for
            if (!flexible.isEmpty())
                this.flexible = flexible.toArray(new ZbxFlexibleInterval[] {});
            if (!scheduling.isEmpty())
                this.scheduling = scheduling.toArray(new ZbxSchedulerInterval[] {});
        } catch (ZbxException ex) {
            this.str = ex.getMessage();
            this.default_delay_ms = -1l;
        }//try-catch
    }//constructor Interval()

    public String getStr() {
        return this.str;
    }//getStr()

    public long getDefaultDelay_ms() {
        return this.default_delay_ms;
    }//getDefaultInterval_ms()

    public boolean hasFlexible() {
        return null != this.flexible;
    }//hasFlexible()

    public boolean hasScheduling() {
        return null != this.scheduling;
    }//hasScheduling()

    private long schedulerGetNextcheck(Calendar now) {
        long nextcheck_ms = 0l, current_nextcheck_ms;
        Calendar cal;
        TimeZone tz = now.getTimeZone();
        boolean indst = tz.inDaylightTime(now.getTime());

        if (Util.LOG_TRACE1 <= Config.getDebugLevel())
            Util.log(Util.LOG_TRACE1, "  schedulerGetNextcheck(): started with now=%s", calendar2String(now));
        if (null != this.scheduling) {
            for (int i=0; i<this.scheduling.length; i++) {
                ZbxSchedulerInterval interval = this.scheduling[i];
                cal = (Calendar)now.clone();
                cal.add(Calendar.SECOND, 1);

                interval.schedulerApplyDayFilter(cal);
                interval.schedulerApplyHourFilter(cal);
                interval.schedulerApplyMinuteFilter(cal);
                interval.schedulerApplySecondFilter(cal);
                current_nextcheck_ms = cal.getTimeInMillis();
                if (tz.inDaylightTime(cal.getTime()) != indst) {
                    cal.setTimeInMillis(schedulerFindDstChange(now.getTimeInMillis(), current_nextcheck_ms, tz));
                    if (Util.LOG_TRACE1 <= Config.getDebugLevel())
                        Util.log(Util.LOG_TRACE1, "  schedulerGetNextcheck(): after schedulerFindDstChange() cal=%s", calendar2String(cal));
                    interval.schedulerApplyDayFilter(cal);
                    interval.schedulerApplyHourFilter(cal);
                    interval.schedulerApplyMinuteFilter(cal);
                    interval.schedulerApplySecondFilter(cal);
                    current_nextcheck_ms = cal.getTimeInMillis();
                }

                if (0l == nextcheck_ms || current_nextcheck_ms < nextcheck_ms)
                    nextcheck_ms = current_nextcheck_ms;

            }//for(intervals)
        }//if(scheduling!=null)

        if (Util.LOG_TRACE1 <= Config.getDebugLevel())
            Util.log(Util.LOG_TRACE1, "  schedulerGetNextcheck(): returned %d (%s)", nextcheck_ms, longCalendar2String(nextcheck_ms));
        return nextcheck_ms;
    }//schedulerGetNextcheck()

    public long getAgentItemNextcheck_ms(long seed, Calendar cal) {
        long next_interval_ms = 0l, t, tmax, scheduled_check_ms = 0l, current_delay_ms = 0l, nextcheck_ms = 0l;
        int attempt = 0;
        Calendar cal_current;

        if (Util.LOG_TRACE1 <= Config.getDebugLevel())
            Util.log(Util.LOG_TRACE1, " getAgentItemNextcheck_ms() start: cal=%s", calendar2String(cal));

        if (this.default_delay_ms < 0l)
            return -1l;//sign of FAIL

        //first try to parse out and calculate scheduled intervals
        scheduled_check_ms = schedulerGetNextcheck(cal);

        t = cal.getTimeInMillis();
        tmax = t + (3600 * 24 * 365 * 1000);   //+1 year, in milliseconds
        cal_current = (Calendar)cal.clone();
        while (t < tmax) {
            current_delay_ms = getCurrentDelay_ms(cal_current);
            if (0l != current_delay_ms) {
                nextcheck_ms = current_delay_ms * (long)(t / current_delay_ms) +
                                (long)((seed * 1000) % current_delay_ms);
                if (0 == attempt) {
                    while (nextcheck_ms <= t)
                        nextcheck_ms += current_delay_ms;
                } else {
                    while (nextcheck_ms < t)
                        nextcheck_ms += current_delay_ms;
                }//if(attempt)
            } else {
                nextcheck_ms = ZBX_JAN_2038;
            }//if(current_delay_ms==0)

            if (null == this.scheduling && null == this.flexible)
                break;
            //'nextcheck' < end of the current interval ?
            //the end of the current interval is the beginning of the next interval - 1
            next_interval_ms = getNextDelayInterval(cal_current);
            if (0l < next_interval_ms && next_interval_ms <= nextcheck_ms) {
                //'nextcheck' is beyond the current interval
                t = next_interval_ms;
                cal_current.setTimeInMillis(t);
                attempt++;
            } else {
                break;  //nextcheck is within the current interval
            }//if
        }//while

        if (0l != scheduled_check_ms && scheduled_check_ms < nextcheck_ms)
            nextcheck_ms = scheduled_check_ms;

        if (Util.LOG_TRACE1 <= Config.getDebugLevel())
            Util.log(Util.LOG_TRACE1, " return from getAgentItemNextcheck_ms(): t=%d (%s), nextcheck_ms=%d (%s)",
                t, longCalendar2String(t), nextcheck_ms, longCalendar2String(nextcheck_ms));

        return nextcheck_ms;
    }//getAgentItemNextcheck_ms()

    private long getNextDelayInterval(Calendar cal) {
        int day, time, next = 0, candidate;
        long value;

        if (null == this.flexible)
            return -1l;

        day = dayOfWeek(cal);
        time = cal.get(Calendar.HOUR_OF_DAY) * 3600 + cal.get(Calendar.MINUTE) * 60 + cal.get(Calendar.SECOND);

        for (int i=0; i<this.flexible.length; i++) {
            ZbxTimePeriod p = this.flexible[i].period;

            if (p.start_day <= day && day <= p.end_day && time < p.end_time) {  //will be active today
                if (time < p.start_time)    //hasn't been active today yet
                    candidate = p.start_time;
                else    //currently active
                    candidate = p.end_time;
            } else if (day < p.end_day) {   //will be active this week
                if (day < p.start_day)  //hasn't been active this week yet
                    candidate = (3600 * 24) * (p.start_day - day) + p.start_time;
                else    //has been active this week and will be active at least once more by the end of it
                    candidate = (3600 * 24) + p.start_time; //therefore will be active tomorrow
            } else {    //will be active next week
                candidate = (3600 * 24) * (p.start_day + 7 - day) + p.start_time;
            }//if-else

            if (0l == next || next > candidate)
                next = candidate;
        }//for

        if (0 == next)
            value = -1l;
        else
            value = cal.getTimeInMillis() - 1000l * (time - next);

        if (Util.LOG_TRACE1 <= Config.getDebugLevel()) {
            Util.log(Util.LOG_TRACE1, "   in getNextDelayInterval(): cal=%d (%s), time=%d, next=%d",
                    cal.getTimeInMillis(), calendar2String(cal), time, next);
            Util.log(Util.LOG_TRACE1, "   return from getNextDelayInterval(): %d (%s)", value, longCalendar2String(value));
        }

        return value;
    }//getNextDelayInterval()

    /*
     * Purpose: returns delay value that is currently applicable
     * @param cal - current time
     * @return delay value (ms) - either default or minimum delay value
     *                            out of all applicable flexible intervals
     */
    public long getCurrentDelay_ms(Calendar cal) {
        long current_delay_ms = -1l;

        Util.log(Util.LOG_TRACE1, "  In getCurrentDelay_ms(): cal=%d (%s)", cal.getTimeInMillis(), calendar2String(cal));

        if (null != this.flexible) {
            for (int i=0; i<this.flexible.length; i++) {
                if ((-1l == current_delay_ms || flexible[i].delay_ms < current_delay_ms) &&
                        flexible[i].period.checkTimePeriod(cal)) {
                    current_delay_ms = flexible[i].delay_ms;
                }//if
            }//for
        }//if(flexible!=null)

        Util.log(Util.LOG_TRACE1, "  End of getCurrentDelay_ms(): current_delay_ms=%d, this.default_delay_ms=%d",
            current_delay_ms, this.default_delay_ms);
        return (-1l == current_delay_ms) ? this.default_delay_ms : current_delay_ms;
    }//getCurrentDelay_ms()


    //static methods

    //returns: 1=Monday, ..., 6=Saturday, 7=Sunday
    public static int dayOfWeek(Calendar cal) {
        int wday = cal.get(Calendar.DAY_OF_WEEK) - 1; //DAY_OF_WEEK: SUNDAY=1, MONDAY=2, ..., SATURDAY=7
        if (0 == wday)  //Sunday
            wday = 7;
        return wday;
    }//dayOfWeek()

    //returns the number of days in the current month
    public static int daysInMonth(Calendar cal) {
        cal = (Calendar)cal.clone();   //do not modify the original cal
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.add(Calendar.MONTH, 1);
        cal.add(Calendar.DAY_OF_MONTH, -1);
        return cal.get(Calendar.DAY_OF_MONTH);
    }//daysInMonth()

    private static long schedulerFindDstChange(long time_start, long time_end, TimeZone tz) {
        long time_mid;
        int start, end, mid;
        boolean dst_start;
        GregorianCalendar cal = new GregorianCalendar();

        if (time_dst < time_start || time_dst > time_end) {
            //assume that daylight saving will change only on 0 seconds
            start = (int)(time_start / 60000);
            end = (int)(time_end / 60000);
            dst_start = tz.inDaylightTime(new Date(time_start));

            while (end > start + 1) {
                mid = (start + end) / 2;
                time_mid = mid * 60000l;
                if (tz.inDaylightTime(new Date(time_mid)) == dst_start)
                    start = mid;
                else
                    end = mid;
            }//while

            time_dst = end * 60000l;
        }//if
 
        return time_dst;
    }//schedulerFindDstChange()

    public static long time2long(String value) throws ZbxException {
        long ret;
        String unit;
        Matcher m = pattern_time.matcher(value);

        if (m.matches()) {
            ret = Long.parseLong(m.group(1));
            unit = m.group(2);

            if (null != unit && !"".equals(unit)) {
                switch (unit.charAt(0)) {
                case 's':
                    break;
                case 'm':
                    ret *= 60;//minutes
                    break;
                case 'h':
                    ret *= 3600;//hours
                    break;
                case 'd':
                    ret *= (24 * 3600);//days
                    break;
                case 'w':
                    ret *= (7 * 24 * 3600);//week
                    break;
                case 'M':
                    ret *= (30 * 24 * 3600);//month
                    break;
                }//switch-case
            }//if(unit!="")
            return ret * 1000l; //seconds to milliseconds
        } else {
            throw new ZbxException("Invalid format for time: '" + value + "'");
        }//!matches
    }//time2long()

    public static String calendar2String(Calendar cal) {
        return String.format("%4d-%02d-%02d %02d:%02d:%02d.%03d",
            cal.get(Calendar.YEAR),
            (cal.get(Calendar.MONTH) + 1),
            cal.get(Calendar.DAY_OF_MONTH),
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            cal.get(Calendar.SECOND),
            cal.get(Calendar.MILLISECOND)
            );
    }//calendar2String()

    public static String longCalendar2String(long time) {
        GregorianCalendar cal = new GregorianCalendar();
        cal.setTimeInMillis(time);
        return calendar2String(cal);
    }//longCalendar2String()

}//class
