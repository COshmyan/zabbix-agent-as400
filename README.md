# Summary

This is a small emulator of Zabbix agent for AS/400 platform. Main purpose: to access AS/400-specific objects (message queues, output queues, subsystems and jobs). It's written on Java using the IBM Toolbox for Java API and library, implemented as Jar-file ready to start (see documentation for details).

# Limitations

* lack of IPv6 (IPv4 only supported);
* no encryption (sorry, no plans);
* only limited subset of standard metrics supported, and even in this case: some of them have a bit different semantics of parameters (see `proc.num[]` or `eventlog[]` for examples);
* some config file's parameters are recognized, but really ignored (`PidFile`, `AllowRoot`, `Include`, and `LoadModule`);
* configuration parameter `ListenIP` allows to set only one IP-address (contrary to list in original Zabbix-agent);
* minimum value for the `StartAgents` parameter is 1 (i.e. active-only mode is not supported);
* during message queue monitoring the integer part only of the message's EventID is transferred to Zabbix-server. It is restriction of Zabbix database schema (it has the integer type for the appropriate attribute). However, it's possible to use a regular expression in the item's key to filter by the full text value of EventID;

# Requirements

As this is a Java-program, you need a JRE to use it :-) This Jar-file has been compiled using JDK 1.8, but for running under JRE 1.7. So, you need minimum Java version 1.7. I tested it for monitoring our AS/400 system v 7.1 (it included just JRE 1.7) – 7.4 (with JRE 1.8); but it should, probably, work on other versions also.

I tried to have minimum dependencies, but some still exist. You need 2 libraries: *IBM Toolbox for Java* (`jt400.jar`) and *Simple JSON parser* (`json-simple-1.1.1.jar`). The first library is included in the AS/400 operating system, or you can download an open-source version (JTOpen) from the sourceforge site ([link](https://sourceforge.net/projects/jt400/files/latest/download?source=files), you need only `jt400.jar` file from the archive). The second library is a tiny (<25KB) file for parsing JSON text, you can download it here ([link](https://code.google.com/archive/p/json-simple/), you need the `json-simple-1.1.1.jar` file only).

Really, you need the following files and directories:
* `jt400.jar`;
* `json-simple-1.1.1.jar`;
* `ZabbixAgent.jar` (in fact, Zabbix-agent emulator, this project);
* config file for the agent (default is `zabbix_agentd.conf` in current directory). The example is included, check and modify it for your environment;
* directory where the log file could be written (the only place where the write access is required);
* if you run the `system.run` metric, you also need a write access to the `/tmp` directory.
