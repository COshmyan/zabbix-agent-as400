# Template AS400 by Zabbix Agent (Zabbix 7.0)

## 📌 Overview
This Zabbix template provides monitoring of AS/400 (IBM System i) servers using **Zabbix Agent emulator**.
The template collects key metrics such as CPU usage, disk space, OS version, uptime, and more.

## Requirements

Zabbix version: 7.0 and higher.
Zabbix agent emulator version: 0.8.0 and higher.

## Configuration

> Zabbix should be configured according to the instructions in the [Templates out of the box](https://www.zabbix.com/documentation/7.0/manual/config/templates_out_of_the_box) section.

## Setup

Install Zabbix agent emulator following its [documentation](../../..).

---

## Macros used

|Name|Description|Default|
|----|-----------|-------|
|{$MAX_AGENT_ACTIVE_UNAVAILABLE}|<p>Timeout after which agent is considered unavailable. Works only for agents reachable from Zabbix server/proxy (active mode).</p>|`10m`|
|{$MAX_AGENT_UNAVAILABLE}|<p>Timeout after which agent is considered unavailable. Works only for agents reachable from Zabbix server/proxy (active mode).</p>|`5m`|
|{$MAX_CPU_FOR_DISCOVERY}|<p>Jobs that consumed this amount of CPU time will be discovered for monitoring.</p>|`30s`|
|{$MAX_CPU_PER_PROCESS_PUSED}|<p>Threshold for CPU usage by a separate job (percentage).</p>|`30`|
|{$MAX_DISK_PUSED}|<p>Threshold for ASP usage (percentage). Can be used with context for a specific ASP.</p>|`65`|
|{$MAX_QEZ_INCREASE}|<p>Threshold for `QEZ*` output queues size increasing in 15 minutes.</p>|`400`|
|{$MAX_QEZ_SIZE}|<p>Threshold for `QEZ*` output queues size (number of spool files in the queue).</p>|`50000`|
|{$MAX_TIME_DIFF}|<p>Threshold for system clock discrepancy (in seconds).</p>|`120`|

## 🔍 Items

|Name|Description|Type|Key and additional info|
|----|-----------|----|-----------------------|
|Zabbix agent host name|<p>Host name of Zabbix agent running.</p>|Zabbix agent(active)|agent.hostname|
|Zabbix agent ping|<p>The agent always returns "1" for this item. May be used in combination with `nodata()` for the availability check.</p>|Zabbix agent(active)|agent.ping|
|Version of Zabbix agent running||Zabbix agent|agent.version|
|Size of output queue QEZDEBUG in library QUSRSYS|<p>Number of spool files in this output queue.</p>|Zabbix agent(active)|as400.outputqueue.size[QEZDEBUG,QUSRSYS]|
|Size of output queue QEZJOBLOG in library QUSRSYS|<p>Number of spool files in this output queue.</p>|Zabbix agent(active)|as400.outputqueue.size[QEZJOBLOG,QUSRSYS]|
|Size of output queue QPRINT in library QGPL|<p>Number of spool files in this output queue.</p>|Zabbix agent(active)|as400.outputqueue.size[QPRINT,QGPL]|
|Services availability|<p>Checks if all AS/400 services are available.</p>|Zabbix agent(active)|as400.services|
|Messages in the queue QSYSOPR (errors)|<p>Error messages in the QSYSOPR queue.</p>|Zabbix agent(active)|eventlog[QSYSOPR,,70,,,100,skip]|
|CPU Usage for all process| |Zabbix agent(active)|proc.cpu.util[,,total,,,]|
|Number of jobs in state LCKW| |Zabbix agent(active)|proc.num[,,LCKW,]|
|Number of running jobs (total)| |Zabbix agent(active)|proc.num[,,run]|
|System FQDN| |Zabbix agent(active)|system.hostname[fqdn,lower]|
|Host local time|<p>The local system time of the host.</p>|Zabbix agent|system.localtime|
|System information|<p>Information about operating system.</p>|Zabbix agent|system.uname|
|System uptime| |Zabbix agent(active)|system.uptime|
|Number of users|<p>The number of users who are currently logged in.</p>|Zabbix agent(active)|system.users.num|
|Get disk data|<p>The `as400.disk.get` key acquires raw information set about the disk devices. Later to be extracted by preprocessing in dependent items.</p>|Zabbix agent(active)|as400.disk.get|
|Get ASP data|<p>The `vfs.fs.get` key acquires raw information set about ASP's. Later to be extracted by preprocessing in dependent items.</p>|Zabbix agent(active)|vfs.fs.get|
|Get jobs data|<p>The `proc.cpu.util.get` key acquires raw information set about the jobs that use a lot of CPU. Later to be extracted by preprocessing in dependent items.</p>|Zabbix agent(active)|proc.cpu.util.get[{$MAX_CPU_FOR_DISCOVERY},avg1]|
|Get debug info|<p>This item is used for troubleshooting only; it is disabled by default on the agent's side.</p><p>To enable it use `AllowKey=` directive in the agent's configuration file.</p>|Zabbix agent|agent.debug.active.thread|
|Debug: ActiveCount|<p>Number of Java active threads.</p>|Dependent item|debug.ActiveCount<p>**Preprocessing**</p><ul><li><p>Regular expression: `ActiveCount=(\d+)` => `\1`</p><p>⛔️Custom on fail: Discard value</p></li></ul>|
|Debug: Memory (free)|<p>Amount of memory returned by `Runtime.freeMemory()` call.</p>|Dependent item|debug.memory[free]<p>**Preprocessing**</p><ul><li><p>Regular expression: `Memory \(max/total/free\): (\d+)/(\d+)/(\d+)` => `\3`</p><p>⛔️Custom on fail: Discard value</p></li></ul>|
|Debug: Memory (max)|<p>Amount of memory returned by `Runtime.maxMemory()` call.</p>|Dependent item|debug.memory[max]<p>**Preprocessing**</p><ul><li><p>Regular expression: `Memory \(max/total/free\): (\d+)/(\d+)/(\d+)` => `\1`</p><p>⛔️Custom on fail: Discard value</p></li></ul>|
|Debug: Memory (total)|<p>Amount of memory returned by `Runtime.totalMemory()` call.</p>|Dependent item|debug.memory[total]<p>**Preprocessing**</p><ul><li><p>Regular expression: `Memory \(max/total/free\): (\d+)/(\d+)/(\d+)` => `\2`</p><p>⛔️Custom on fail: Discard value</p></li></ul>|
|Debug: Timeouts count|<p>Number of timeout events since the agent was started.</p>|Dependent item|debug.timeoutCount<p>**Preprocessing**</p><ul><li><p>Regular expression: `Timeout count=(\d+)` => `\1`</p><p>⛔️Custom on fail: Discard value</p></li></ul>|
||<p></p>|Zabbix agent(active)||
||<p></p>|Zabbix agent(active)||

---

## 🚨 Triggers

|Name|Description|Expression|Severity|Dependencies and additional info|
|----|-----------|----------|--------|--------------------------------|
|Host name of zabbix agent was changed on {HOST.NAME}| |`last(/AS400 by Zabbix Agent/agent.hostname,#1)<>last(/AS400 by Zabbix Agent/agent.hostname,#2)`|Info| |
|New critical message in AS400 message queue| |`nodata(/AS400 by Zabbix Agent/eventlog[QSYSOPR,,70,,,100,skip],3d)<>1 and find(/AS400 by Zabbix Agent/eventlog[QSYSOPR,,70,,,100,skip],,"regexp","^(CPPEA02|CPI0949) ")=1`|High|Allow manual close<p>**Meaning:**</p><ul><li>**CPPEA02** \*Attention\*  Contact your hardware service provider now.</li><li>**CPI0949** Mirrored protection suspended on disk unit &1.</li></ul>|
|New error message in AS400 message queue| |`(find(/AS400 by Zabbix Agent/eventlog[QSYSOPR,,70,,,100,skip],,"regexp","^CPPEA33 ")=1 or (logsource(/AS400 by Zabbix Agent/eventlog[QSYSOPR,,70,,,100,skip],,"^OVERRIDE$")=0 and find(/AS400 by Zabbix Agent/eventlog[QSYSOPR,,70,,,100,skip],,"regexp","^(CPA5201|CPA7025|CPI096E|CPI5906|CPF8198|CPF9E7E|CPF9E7D) ")=0))`|Average|Allow manual close<p>Some messages are ignored to avoid spam, as these events are monitored separately.</p><p>**Meaning:**</p><ul><li>**CPPEA33** Warning - A storage subsystem disk unit connection has failed.</li><li>**CPA5201** Hardware failure on device &3. *(ignore)*</li><li>**CPA7025** Receiver &1 in &2 never fully saved. (I C) *(warning)*</li><li>**CPI096E** Disk unit connection is missing. *(ignore)*</li><li>**CPI5906** Local system sent SNA negative response data to controller &1 on device &2. *(ignore)*</li><li>**CPF8198** Damaged object found. *(ignore)*</li><li>**CPF9E7E** IBM i license key not valid in &8 days on &9. *(ignore)*</li><li>**CPF9E7D** IBM i grace period expires in &6 days on &5. *(ignore)*</li></ul>|
|New warning message in AS400 message queue| |`logsource(/AS400 by Zabbix Agent/eventlog[QSYSOPR,,70,,,100,skip],,"^QCLNSYSLOG$")=0 and find(/AS400 by Zabbix Agent/eventlog[QSYSOPR,,70,,,100,skip],,"regexp","^CPA7025 ")=1`|Warning|Allow manual close<p>**Meaning:**</p><ul><li>**CPA7025** Receiver &1 in &2 never fully saved. (I C)</li></ul>|
|Output queue QEZDEBUG has a lot of spool files (above {$MAX_QEZ_SIZE})| |`last(/AS400 by Zabbix Agent/as400.outputqueue.size[QEZDEBUG,QUSRSYS])>{$MAX_QEZ_SIZE}`|Warning| |
|Output queue QEZDEBUG is growing too fast (above {$MAX_QEZ_INCREASE} in 15m)| |`count(/AS400 by Zabbix Agent/as400.outputqueue.size[QEZDEBUG,QUSRSYS],20m)>1 and change(/AS400 by Zabbix Agent/as400.outputqueue.size[QEZDEBUG,QUSRSYS])>{$MAX_QEZ_INCREASE}`|Warning| |
|Output queue QEZJOBLOG has a lot of spool files (above {$MAX_QEZ_SIZE})| |`last(/AS400 by Zabbix Agent/as400.outputqueue.size[QEZJOBLOG,QUSRSYS])>{$MAX_QEZ_SIZE}`|Warning| |
|Output queue QEZJOBLOG is growing too fast (above {$MAX_QEZ_INCREASE} in 15m)| |`count(/AS400 by Zabbix Agent/as400.outputqueue.size[QEZJOBLOG,QUSRSYS],20m)>1 and change(/AS400 by Zabbix Agent/as400.outputqueue.size[QEZJOBLOG,QUSRSYS])>{$MAX_QEZ_INCREASE}`|Warning| |
|Output queue QPRINT has a lot of spool files (above {$MAX_QPRINT_SIZE})| |`last(/AS400 by Zabbix Agent/as400.outputqueue.size[QPRINT,QGPL])>{$MAX_QPRINT_SIZE}`|Warning| |
|There are jobs in state LCKW| |`min(/AS400 by Zabbix Agent/proc.num[,,LCKW,],#2)>0`|Warning| |
|Time is differs more than {$MAX_TIME_DIFF} seconds!| |`fuzzytime(/AS400 by Zabbix Agent/system.localtime,{$MAX_TIME_DIFF})=0`|Average| |
|Version of zabbix agent was changed on {HOST.NAME}|`last(/AS400 by Zabbix Agent/agent.version,#1)<>last(/AS400 by Zabbix Agent/agent.version,#2)`|Info| |
|Zabbix agent (active mode) on {HOST.NAME} is unreachable| |`nodata(/AS400 by Zabbix Agent/agent.ping,{$MAX_AGENT_ACTIVE_UNAVAILABLE})=1`|Average|**Depends on**:<br><ul><li>AS400 by Zabbix Agent: Zabbix agent on {HOST.NAME} is unreachable</li></ul>|
|Zabbix agent on {HOST.NAME} is unreachable| |`nodata(/AS400 by Zabbix Agent/system.localtime,{$MAX_AGENT_UNAVAILABLE})=1`|High| |

---

## LLD's

### LLD rule ASP discovery

|Name|Description|Type|Key and additional info|
|----|-----------|----|-----------------------|
|ASP discovery (dependent)|<p>The discovery of ASP's.</p>|Dependent item|vfs.fs.dependent.discovery<p>**Preprocessing**</p><ul><li><p>JavaScript: `The text is too long. Please see the template.`</p></li><li><p>Discard unchanged with heartbeat: `1h`</p></li></ul>|

### Item prototypes for ASP discovery

|Name|Description|Type|Key and additional info|
|----|-----------|----|-----------------------|
|ASP [{#FSNAME}]: Get data|<p>Intermediate data of `{#FSNAME}` ASP.</p>|Dependent item|vfs.fs.dependent[{#FSNAME},data]<p>**Preprocessing**</p><ul><li><p>JSON Path: `$.[?(@.fsname=='{#FSNAME}')].first()`</p><p>⛔️Custom on fail: Discard value</p></li></ul>|
|ASP{#FSNAME}: Space: Available|<p>Available storage space expressed in bytes.</p>|Dependent item|vfs.fs.dependent.size[{#FSNAME},free]<p>**Preprocessing**</p><ul><li><p>JSON Path: `$.bytes.free`</p></li></ul>|
|ASP{#FSNAME}: Space: Available, in %|<p>Calculated as the percentage of currently available space compared to the maximum available space.</p>|Dependent item|vfs.fs.dependent.size[{#FSNAME},pfree]<p>**Preprocessing**</p><ul><li><p>JSON Path: `$.bytes.pfree`</p></li></ul>|
|ASP{#FSNAME}: Space: Total|<p>Total space expressed in bytes.</p>|Dependent item|vfs.fs.dependent.size[{#FSNAME},total]<p>**Preprocessing**</p><ul><li><p>JSON Path: `$.bytes.total`</p></li><li><p>Discard unchanged with heartbeat: `1h`</p></li></ul>|
|ASP{#FSNAME}: Space: Used|<p>Used storage expressed in bytes.</p>|Dependent item|vfs.fs.dependent.size[{#FSNAME},used]<p>**Preprocessing**</p><ul><li><p>JSON Path: `$.bytes.used`</p></li></ul>|
|ASP{#FSNAME}: Space: Used, in %|<p>Calculated as the percentage of currently used space compared to the maximum available space.</p>|Dependent item|vfs.fs.dependent.size[{#FSNAME},pused]<p>**Preprocessing**</p><ul><li><p>JSON Path: `$.bytes.pused`</p></li></ul>|
|ASP{#FSNAME}: status| |Dependent item|vfs.fs.dependent.state[{#FSNAME}]<p>**Preprocessing**</p><ul><li><p>JSON Path: `$.state`</p></li></ul>|

### Trigger prototypes for ASP discovery

|Name|Description|Expression|Severity|Dependencies and additional info|
|----|-----------|----------|--------|--------------------------------|
|ASP{#FSNAME} used more than {$MAX_DISK_PUSED:"{#FSNAME}"}%| |`min(/AS400 by Zabbix Agent/vfs.fs.dependent.size[{#FSNAME},pused],#2)>{$MAX_DISK_PUSED:"{#FSNAME}"} and count(/AS400 by Zabbix Agent/vfs.fs.dependent.state[{#FSNAME}],#2,"regexp","^(1|2)$")=0`|Average|The threshold can be tuned via user macro with context `{$MAX_DISK_PUSED:"{#FSNAME}"}`|

### LLD rule Disk discovery

|Name|Description|Type|Key and additional info|
|----|-----------|----|-----------------------|
|Disks discovery (dependent)|<p>The discovery of disks.</p>|Dependent item|as400.disk.dependent.discovery<p>**Preprocessing**</p><ul><li><p>JavaScript: `The text is too long. Please see the template.`</p></li><li><p>Discard unchanged with heartbeat: `1h`</p></li></ul>|

### Item prototypes for Disk discovery

|Name|Description|Type|Key and additional info|
|----|-----------|----|-----------------------|
|Disk [{#DSK_SN}]: Get data|<p>Intermediate data of `{#DSK_SN}` disk.</p>|Dependent item|as400.disk.dependent[{#DSK_SN},data]<p>**Preprocessing**</p><ul><li><p>JSON Path: `$.[?(@.dsk_sn=='{#DSK_SN}')].first()`</p><p>⛔️Custom on fail: Discard value</p></li></ul>|
|Disk {#DSK_ID}: {#DSK_NAME} ({#DSK_TYPE} {#DSK_MODEL}, {#DSK_SN}) status| |Dependent item|as400.disk.dependent.state[{#DSK_SN}]<p>**Preprocessing**</p><ul><li><p>JSON Path: `$.state`</p></li></ul>|
|Disk {#DSK_ID}: {#DSK_NAME} capacity| |Dependent item|as400.disk.dependent.size[{#DSK_SN},total]<p>**Preprocessing**</p><ul><li><p>JSON Path: `$.bytes.total`</p></li><li><p>Discard unchanged with heartbeat: `1h`</p></li></ul>|
|Disk {#DSK_ID}: {#DSK_NAME} free space| |Dependent item|as400.disk.dependent.size[{#DSK_SN},free]<p>**Preprocessing**</p><ul><li><p>JSON Path: `$.bytes.free`</p></li></ul>|
|Disk {#DSK_ID}: {#DSK_NAME} free space, %| |Dependent item|as400.disk.dependent.size[{#DSK_SN},pfree]<p>**Preprocessing**</p><ul><li><p>JSON Path: `$.bytes.pfree`</p></li></ul>|
|Disk {#DSK_ID}: {#DSK_NAME} used space| |Dependent item|as400.disk.dependent.size[{#DSK_SN},used]<p>**Preprocessing**</p><ul><li><p>JSON Path: `$.bytes.used`</p></li></ul>|
|Disk {#DSK_ID}: {#DSK_NAME} used space, %| |Dependent item|as400.disk.dependent.size[{#DSK_SN},pused]<p>**Preprocessing**</p><ul><li><p>JSON Path: `$.bytes.pused`</p></li></ul>|

### Trigger prototypes for Disk discovery

|Name|Description|Expression|Severity|Dependencies and additional info|
|----|-----------|----------|--------|--------------------------------|
|Disk {#DSK_ID}: {#DSK_NAME} ({#DSK_TYPE} {#DSK_MODEL}, {#DSK_SN}) failed| |`last(/AS400 by Zabbix Agent/as400.disk.dependent.state[{#DSK_SN}])<>1 and last(/AS400 by Zabbix Agent/as400.disk.dependent.state[{#DSK_SN}])<>4294967295`|Average| |

### LLD rule Jobs discovery

|Name|Description|Type|Key and additional info|
|----|-----------|----|-----------------------|
|Jobs discovery (dependent)|<p>Discovery of jobs that consume significant amount of CPU.</p>|Dependent item|proc.cpu.util.dependent.discovery[{$MAX_CPU_FOR_DISCOVERY}]<p>**Preprocessing**</p><ul><li><p>JavaScript: `The text is too long. Please see the template.`</p></li><li><p>Discard unchanged with heartbeat: `1h`</p></li></ul>|

### Item prototypes for Jobs discovery

|Name|Description|Type|Key and additional info|
|----|-----------|----|-----------------------|
|CPU Usage by job {#NUM}/{#USER}/{#NAME}|<p>Percentage of time the specified job used the CPU during the last minute.</p>|Dependent item|vfs.fs.dependent[{#FSNAME},data]<p>**Preprocessing**</p><ul><li><p>JSON Path: `$.[?(@.num=='{#NUM}' && @.user == '{#USER}' && @.name == '{#NAME}')].usage.first()`</p><p>⛔️Custom on fail: Discard value</p></li></ul>|

### Trigger prototypes for Jobs discovery

|Name|Description|Expression|Severity|Dependencies and additional info|
|----|-----------|----------|--------|--------------------------------|
|Job {#NUM}/{#USER}/{#NAME} has large CPU usage (>{$MAX_CPU_PER_PROCESS_PUSED}%)| |`count(/AS400 by Zabbix Agent/proc.cpu.dependent.util[{#NAME},{#USER},{#NUM}],#6,"ge","{$MAX_CPU_PER_PROCESS_PUSED}")=6`|Warn| |

## 📊 Graphs

**Predefined Graphs:**
- None

---

## 📈 Dashboard

**Predefined Dashboards:**
- None

---

## 📌 Notes
- Triggers about message queues (besides criticals) have option "PROBLEM event generation mode"="Multiple" and tag "close:auto". It assumed that you have [script](https://blog.zabbix.com/close-problem-automatically-via-zabbix-api/12461/) to close them automatically.

---
