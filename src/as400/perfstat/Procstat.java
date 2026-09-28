package as400.perfstat;
import as400.*;
import as400.thread.ZabbixThread;
import com.ibm.as400.access.*;
import java.io.*;
import java.util.*;
import java.beans.PropertyVetoException;

public class Procstat {

    //constants
    public static final int TIMES_PER_MIN           = 20;
    public static final int MAX_COLLECTOR_HISTORY   = 15 * TIMES_PER_MIN + 1;

    static class ProcstatData {
        long   cpu_time_used_per_tick;
        long   timestamp;

        ProcstatData(long cpu_time_used, long timestamp) {
            this.cpu_time_used_per_tick = cpu_time_used;
            this.timestamp = timestamp;
        }//constructor ProcstatData

    }//inner class ProcstatData

    static class Query {
        //local fields
        String jobnum;
        String usrname;
        String jobname;
        ZbxRegexp subsystem;
        long   last_updated;  //last update time (milliseconds, timestamp of JobList)
        long   last_accessed; //last access time (request from server), milliseconds
        ArrayList<ProcstatData> h_data; //the cpu utilization history data (ring buffer)
        HashSet<Jobinfo> job_list;
        
        Query(String jobnum, String usrname, String jobname, String subsystem) throws ZbxException {
            this.jobnum  = (null == jobnum  || "".equals(jobnum) ) ? null : jobnum .toUpperCase();
            this.usrname = (null == usrname || "".equals(usrname)) ? null : usrname.toUpperCase();
            this.jobname = "".equals(jobname) ? null : jobname;
//this part was to process library name only (irrespective of its IFS full path),
//but now we extract the library name in the JobInfo constructor
//            if (null != subsystem)
//                subsystem = "^(.*/)?" + subsystem + "(\\.SBSD)?$";
            this.subsystem = new ZbxRegexp(subsystem, false);
            this.last_updated = 0l;
            this.last_accessed = System.currentTimeMillis();
            this.h_data = new ArrayList<ProcstatData>(MAX_COLLECTOR_HISTORY);

            if (null != this.jobnum) {
                //add single job by jobnum/usrname/jobname
                String job_fullname = jobnum + '/' + usrname + '/' + jobname;
                Jobinfo ji = jobinfo_list.get(job_fullname);
                if (null != ji) {
                    this.h_data.add(new ProcstatData(0l, ji.timestamp));
                    this.last_updated = ji.timestamp;
                    addJob(ji);
                    ji.addQuery(this);
                } else {
                    throw new ZbxException("There is no such job: " + job_fullname);
                }//if(job found)
            } else {
                //loop by every job in joblist
                synchronized(jobinfo_list) {
                    for (Enumeration<Jobinfo> e = jobinfo_list.elements(); e.hasMoreElements(); ) {
                        Jobinfo ji = e.nextElement();
                        if (match(ji)) {
                            this.add_time(0l, ji.timestamp);
                            addJob(ji);
                            ji.addQuery(this);
                        }//if(job matched)
                    }//for(jobinfo_list)
                }//sync(jobinfo_list)
            }//if(procnum defined)
        }//constructor Query()

        synchronized void addJob(Jobinfo ji) {
            if (null == this.job_list)
                this.job_list = new HashSet<Jobinfo>();
            this.job_list.add(ji);
        }//addJob()

        synchronized void removeJob(Jobinfo ji) {
            if (null != this.job_list) {
                this.job_list.remove(ji);
                if (0 == this.job_list.size())
                    this.job_list = null;
            }//if(!null)
        }//addJob()

        boolean match (Jobinfo ji) {
            if (null != this.jobnum) {
                return (ji.jobnum .equals(this.jobnum ) &&
                        ji.usrname.equals(this.usrname) &&
                        ji.jobname.equals(this.jobname) );
            } else {
                return ( (null == this.jobname || this.jobname.equals(ji.jobname)) &&
                         (null == this.usrname || this.usrname.equals(ji.usrname)) &&
                          this.subsystem.matches(ji.subsystem) );
            }//if
        }//match()

        synchronized void add_time(long cpu_time_used, long timestamp) {
            if (this.last_updated < timestamp) {
                //it is new timestamp, so add new item
                int size = h_data.size();
                if (MAX_COLLECTOR_HISTORY == size)
                    h_data.remove(--size);
                h_data.add(0, new ProcstatData(cpu_time_used, timestamp));
                this.last_updated = timestamp;
            } else {
                h_data.get(0).cpu_time_used_per_tick += cpu_time_used;
            }//if
            if (0l > cpu_time_used)
                Util.log(Util.LOG_ERROR,"ERROR: Procstat.Query.add_time(): negative value of cpu_time_used (%d)!!", cpu_time_used);
        }//add_time()

        synchronized Float getPercentage(int minutes) throws ZbxException {
            this.last_accessed = System.currentTimeMillis();
            long time_delta, time_sum = 0l;
            int size = this.h_data.size();
            if (size < 2)
                throw new ZbxException("Datas still not collected yet");
            int max_index = minutes * TIMES_PER_MIN;
            if (size <= max_index)
                max_index = size - 1;
            time_delta = h_data.get(0).timestamp - h_data.get(max_index).timestamp;
            if (0l >= time_delta) {
                //should be impossible!!
                Util.log(Util.LOG_ERROR,"  ERROR: Query.getPercentage(): time_delta=%s, jobnum=%s, usrname=%s, jobname=%s, subsystem=%s, time_delta is zero, max_index=%d",
                        time_delta, this.jobnum, this.usrname, this.jobname, this.subsystem, max_index);
                throw new ZbxException("There is no such job anymore");
            }
            for (int i = 0; i < max_index; i++) {
                time_sum += h_data.get(i).cpu_time_used_per_tick;
                //Util.log(Util.LOG_DEBUG,"  Query.getPercentage(): cpu_time_used_per_tick[%d] = %d]",
                //        i, h_data.get(i).cpu_time_used_per_tick);
            }
            Util.log(Util.LOG_DEBUG,"  Query.getPercentage(): max_index=%d, time_delta=%d, time_sum=%d",
                                    max_index, time_delta, time_sum);
            //percentage with 0.01% accuracy, "+0.005%" for round-up instead of fractional part truncation
            double res = ( ( ( (time_sum * 100000) / time_delta ) + 5 ) / 10 ) * 0.01;
            if (0.0 > res)
                Util.log(Util.LOG_ERROR,"ERROR: Procstat.Query.getPercentage(): negative value of res (%f)!! time_sum=%d, time_delta=%d", res, time_sum, time_delta);
            return new Float(res);
        }//getPercentage()

    }//inner class Query

    static class Jobinfo {
        //local fields
        String jobnum;
        String usrname;
        String jobname;
        String subsystem;
        long   timestamp;
        long   cpu_used;
        HashSet<Query> query_list;

        Jobinfo(String jobnum, String usrname, String jobname, String subsystem,
                long timestamp, long cpu_used) {
            this.jobnum    = jobnum;
            this.usrname   = usrname;
            this.jobname   = jobname;
            //remove path and suffix from the library IFS full path, store and process the library name only
            int i = subsystem.lastIndexOf('/') + 1;
            int j = subsystem.length() - (subsystem.endsWith(".SBSD") ? 5 : 0);
            this.subsystem = subsystem.substring(i, j);
            this.timestamp = timestamp;
            this.cpu_used  = cpu_used;
            this.query_list= null;
        }//constructor Jobinfo()

        synchronized void addQuery(Query query) {
            if (null == query_list)
                query_list = new HashSet<Query>();
            query_list.add(query);
        }//addQuery()

        synchronized void removeQuery(Query query) {
            if (null != query_list) {
                query_list.remove(query);
                if (0 == query_list.size())
                    query_list = null;
            }//if(!null)
        }//addQuery()

    }//inner class Jobinfo

    //global variables
    private static Hashtable<String, Jobinfo> jobinfo_list = new Hashtable<String, Jobinfo>();
    private static Hashtable<String, Query>   query_list   = new Hashtable<String, Query>();

    //
    public static void updateJobinfoList() {
//        if (0 == query_list.size())
//            return;
        Util.log(Util.LOG_TRACE1,"Procstat.updateJobinfoList() started");
        AS400 system = ((ZabbixThread)Thread.currentThread()).getAs400();
        JobList jl = new JobList(system);
        try {
            jl.clearJobSelectionCriteria();
            jl.addJobSelectionCriteria(JobList.SELECTION_PRIMARY_JOB_STATUS_ACTIVE, true );
            jl.addJobSelectionCriteria(JobList.SELECTION_PRIMARY_JOB_STATUS_JOBQ  , false);
            jl.addJobSelectionCriteria(JobList.SELECTION_PRIMARY_JOB_STATUS_OUTQ  , false);
            jl.clearJobAttributesToRetrieve();
            jl.addJobAttributeToRetrieve(Job.SUBSYSTEM);
            jl.addJobAttributeToRetrieve(Job.CPU_TIME_USED_LARGE);
            //It is not guaranted that implicit call of load() method from jl.getJobs() throws an exception upon communication errors,
            //therefore we performs this call explicitly
            jl.load();
            Enumeration jobs = jl.getJobs();
            long current_ts = System.currentTimeMillis();

            while (jobs.hasMoreElements()) {
                if (!Config.running)
                    break;
                Job job = (Job)jobs.nextElement();
                String jobname = null, jobnum = null, usrname = null, job_fullname = null, subsystem = null;
                Object res;
                //in rare cases some job is disappeared during the job list processing, just ignore such job
                try {
                    jobname = job.getName();
                    jobnum  = job.getNumber();
                    usrname = job.getUser();
                    job_fullname = jobnum + '/' + usrname + '/' + jobname;
                    subsystem = job.getSubsystem();
                    res = job.getValue(Job.CPU_TIME_USED_LARGE);
                } catch (ErrorCompletingRequestException|ObjectDoesNotExistException ex) {
                    res = null;
                }//try-catch
                if (null == res)
                    continue;   //according to manual, API "may return null in the rare case"
                long cpu_used = ((Long)res).longValue();
                if (0l > cpu_used) {
                    Util.log(Util.LOG_ERROR," ERROR: Procstat.updateJobinfoList(): negative value of cpu_used %d for job %s in subsystem %s",
                        cpu_used, job_fullname, subsystem);
                }
                Jobinfo ji = jobinfo_list.get(job_fullname);
                if (null == ji) {
                    //new job, there was no such job in previous iteration
    	            Util.log(Util.LOG_TRACE1," Procstat.updateJobinfoList(): new job %20s, cpu_used=%d",
	            			job_fullname, cpu_used);
                    ji = new Jobinfo(jobnum, usrname, jobname, subsystem, current_ts, cpu_used);
                    //if this job was started since the previous check, then we will process all queries
                    //otherwise the job was running long time but collector thread was started recently
                    //  job.getJobActiveDate() really can return null sometimes! surprise...
                    boolean isNew = true;
                    Date d = job.getJobActiveDate();
                    if (null != d)
                        isNew = (60000 / TIMES_PER_MIN) > (current_ts - d.getTime());
                    for (Enumeration<Query> e = query_list.elements(); e.hasMoreElements(); ) {
                        Query query = e.nextElement();
                        if (query.match(ji)) {
                            query.addJob(ji);
                            ji.addQuery(query);
                            //take into account the CPU used by this new job
                            if (isNew)
                                query.add_time(cpu_used, current_ts);
                        }//match to query
                    }//for(global query_list)
                    synchronized (jobinfo_list) {
                        jobinfo_list.put(job_fullname, ji);
                    }//sync(jobinfo_list)
                } else {
                    //job found in jobinfo_list
    	            //Util.log(Util.LOG_DEBUG," Procstat.updateJobinfoList(): old job %s, cpu_used=%d",
                    //          job_fullname, cpu_used);
                    long cpu_used_per_tick = cpu_used - ji.cpu_used;
                    if (0l > cpu_used_per_tick) {
                        Util.log(Util.LOG_DEBUG," WARNING: Procstat.updateJobinfoList(): negative value of cpu_used_per_tick %d for job %s in subsystem %s",
                            cpu_used_per_tick, job_fullname, subsystem);
                        Util.log(Util.LOG_TRACE1,"  current cpu_used=%d, ji.cpu_used=%d", cpu_used, ji.cpu_used);
                        cpu_used_per_tick = 0l;
                    }
                    synchronized (ji) {
                        if (null != ji.query_list) {
                            for (Query query: ji.query_list) {
                                query.add_time(cpu_used_per_tick, current_ts);
                            }//for(query_list of job info)
                        }//if
                        ji.cpu_used = cpu_used;
                        ji.timestamp = current_ts;
                    }//sync(ji)
                }//if(job info found)
            }//while(JobList)

            //update rest of queries by zero values (for taking into account any completed jobs)
            for (Enumeration<Query> e = query_list.elements(); e.hasMoreElements(); ) {
                if (!Config.running)
                    break;
                Query query = e.nextElement();
                if (query.last_updated < current_ts) {
                    query.add_time(0l, current_ts);
                }//if(query.last_updated<current_ts)
            }//for(global query_list)

            if (((ZabbixThread)Thread.currentThread()).isAs400CommError()) {
                Util.log(Util.LOG_WARNING," Procstat.updateJobinfoList() communication to AS/400 is working again");
                ((ZabbixThread)Thread.currentThread()).setAs400CommError(false);
            }//if

        } catch (IOException ex) {
            if (!((ZabbixThread)Thread.currentThread()).isAs400CommError()) {
                Util.log(Util.LOG_WARNING," Procstat.updateJobinfoList() communication to AS/400 error: %s", ex);
                ((ZabbixThread)Thread.currentThread()).setAs400CommError(true);
            }//if
            if (system.isConnected())
                system.disconnectAllServices();
        } catch (PropertyVetoException|InterruptedException|AS400SecurityException|ErrorCompletingRequestException|ObjectDoesNotExistException ex) {
            Util.log(Util.LOG_WARNING," Procstat.updateJobinfoList() error: %s", ex);
            //throw new ZbxException(ex.getMessage());
        } finally {
            try { jl.close(); } catch (InterruptedException|IOException|AS400SecurityException|ErrorCompletingRequestException|ObjectDoesNotExistException ex) { ; }
            Util.log(Util.LOG_TRACE1,"Procstat.updateJobinfoList() ended");
        }//try-catch-finally
    }//updateJobinfoList()

    public static void cleanJobinfoList(long minimum_ts) {
        synchronized (jobinfo_list) {
            for (Iterator<Jobinfo> it = jobinfo_list.values().iterator(); it.hasNext(); ) {
                Jobinfo ji = it.next();
                if (minimum_ts > ji.timestamp) {
                    //remove ji from jobinfo_list
                    it.remove();
                    synchronized (ji) {
                        if (null != ji.query_list) {
                            for (Query q: ji.query_list) {
                                //remove ji from all queries
                                q.removeJob(ji);
                            }//for(ji.query_list)
                            //clear query_list
                            ji.query_list.clear();
                        }//if(ji.query_list not null)
                    }//sync(ji)
                }//if(Job Infor needed to clean)
            }//for(jobinfo_list)
        }//sync(jobinfo_list)
    }//cleanJobinfoList()

    public static void cleanQueryList(long minimum_ts) {
        synchronized (query_list) {
            for (Iterator<Query> it = query_list.values().iterator(); it.hasNext(); ) {
                Query query = it.next();
                if (minimum_ts > query.last_accessed) {
                    it.remove();
                    synchronized (query) {
                        if (null != query.job_list) {
                            for (Jobinfo ji: query.job_list)
                                ji.removeQuery(query);
                            query.job_list.clear();
                        }//if (query.job_list not null)
                    }//sync(query)
                }//if(needed to remove this Query)
            }//for(query_list)
        }//sync(query_list)
    }//cleanQueryList()

    public static String jobDiscovery(int seconds) {
        long ms = seconds * 1000;
        StringBuilder buf = new StringBuilder();
        buf.append("{\"data\":[");
        boolean first = true;
        synchronized(jobinfo_list) {
            for (Enumeration<Jobinfo> e = jobinfo_list.elements(); e.hasMoreElements(); ) {
                Jobinfo ji = e.nextElement();
                if (ms >= ji.cpu_used)
                    continue;
                if (first) 
                    first = false;
                else
                    buf.append(",");
                buf.append("\n {\"{#NUM}\":\"");
                buf.append(ji.jobnum);
                buf.append("\",\"{#USER}\":\"");
                buf.append(ji.usrname);
                buf.append("\",\"{#NAME}\":\"");
                buf.append(ji.jobname);
                buf.append("\"}");
            }//for
        }//sync(jobinfo_list)
        buf.append("\n]}\n");
        return buf.toString();
    }//jobDiscovery()

    public static Float getPercentage(int minutes, String jobnum, String usrname, String jobname,
                                      String subsystem) throws ZbxException {
        Query query = null;
        String key = jobnum + '/' + usrname + '/' + jobname + '/' + subsystem;
        synchronized (query_list) {
            query = query_list.get(key);
            if (null == query) {
                query = new Query(jobnum, usrname, jobname, subsystem);
                query_list.put(key, query);
            }//(query was absent in the list)
        }//sync(query_list)
        return query.getPercentage(minutes);
    }//getPercentage()

}//class Procstat()
