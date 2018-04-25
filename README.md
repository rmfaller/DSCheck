# DSCheck
> The code is provided on an "as is" basis, without warranty of any kind, to the fullest extent permitted by law. 
> 
> ForgeRock does not warrant or guarantee the individual success developers may have in implementing the code on their platforms or in production configurations.
> 
> ForgeRock does not warrant, guarantee or make any representations regarding the use, results of use, accuracy, timeliness or completeness of any data or information relating to the alpha release of unsupported code. ForgeRock disclaims all warranties, expressed or implied, and in particular, disclaims all warranties of merchantability, and warranties related to the code, or any service or software related thereto.
> 
> ForgeRock shall not be liable for any direct, indirect or consequential damages or costs of any type arising out of any action taken by you or others related to the code.

DSCheck is a utility to check entries on all Directory Service (DS) instances in a replicated DS environment. DSCheck validates that all of the entries of a particular Distinguished Name (DN) match using the object's `etag` value. For example DSCheck validates that `uid=user.1234,ou=people,dc=example,dc=com` has the same etag value on every replicated DS instance that persists that object.

Why is DSCheck necessary? From a Directory Service standpoint and the reliability of replication it is not. Directory Service replication is a long established mechanism that works.

Replication can generally recover from conflicts and transient issues. Replication does, however, require that update operations be copied from server to server. It is possible to experience temporary delays while replicas converge, especially when the write operation load is heavy. DS's tolerance for temporary divergence between replicas is what allows DS to remain available to serve client applications even when networks linking the replicas go down.

In other words, the fact that directory services are loosely convergent rather than transactional is a feature. 

For more information on DS replication please see: [https://backstage.forgerock.com/docs/ds/5.5/admin-guide/#about-replication]()

What we do find is that some external event can cause a lack of confidence for the Directory Service operators in the consistency of the data persisted by the Directory Service. Some of these external events can include:

* Importing the wrong data into one of the instances
* Restoring data that contains obsolete generation-ids
* Instance(s) improperly taken off line
* Instance(s) returned to a replication topology but with a different hostname
* Instance(s) off-line beyond the purge delay setting [https://backstage.forgerock.com/docs/ds/5.5/admin-guide/#troubleshoot-repl]()

Since no environment is failsafe DSCheck provides a way to check and validate data consistency across all replicas. The goal of DSCheck is to help operators gain confidence in the consistency of the data. If object inconsistency is discovered the offending objects will be listed and the data inconsistency rectified by those who are responsible for the data. *DSCheck does NOT determine what data is the most correct*.

DSCheck is not a real time utility. From the time DSCheck starts until completion data could change. Depending on the speed in which replication can complete across all instances at certain points in time specific objects may NOT have the exact same data. DSCheck can accommodate this by rechecking unmatched objects based on flags/switches selected.

Running DSCheck is best started using `./scripts/dscheck.sh full` as it enables the setting of environmental parameters. The script also extracts and processes a list of DNs from each instance in the replication topology.

Once the preprocessing is complete the actual examining of the each object by checking the etag value is done by `java -jar ./dist/DSCheck.jar` with the appropriate switches. See DSCheck usage below. 

Running DSCheck can place load on the system running the DSCheck as well as the target DS instances. The ldapsearch commands used allow multiple, simultaneous threads of execution. DO NOT run DSCheck against a production environment unless you fully understand the potential load impact. 

**Running DSCheck during off-hours as well as on a system not running a production service are highly recommended.**

```
DSCheck usage:
required:
	--instances    | -i in a specific format using a tilde "~" to separate instances i.e. ds0.example.com:1389~ds1.example.com:1389~ds2.example.com:1389
	--bindDn       | -b identity to use when binding to DS i.e. cn=Directory Manager
	--bindPassword | -p password to use when binding to DS
	/path/to/DNs/file where each line in this file is a single DN - example: uid=user.1234,ou=people,dc=example,dc=com
options:
	--threads      | -t {default = 1} number of threads to run; suggest start with 4x number of cores of system running DSCheck
	--verbose      | -v {default = false} display DSCheck runtime stats
	--fulldisplay  | -f {default = false} display the entire object in question on standard error i.e. 2> error.out
	--repeat n     | -r n (default n = 1} maximum number of times to check object validity if found not valid across all instances
	--sleep t      | -s t {default t = 0} seconds to sleep until repeating validity check
	--help         | -h this output

Examples:
java -jar ${DSCHECKHOME}/dist/DSCheck.jar \
          --bindDn "cn=Directory Manager" \
          --bindPassword "dmpassword" \
          --instances ds0.example.com:1389~ds1.example.com:1389~ds2.example.com:1389 \
          ${DSCHECKHOME}/tmp/dns.txt
```

DSCheck can scan all objects in a certain BaseDN or scan only those objects created and modified after a specified time in a certain BaseDN.

The following is a possible scenario that could lead to a data discrepancy:

Three DS instances in fully meshed replication with purge delay set at the default of 3 days:

```
ds0---ds1
 | \ / 
 |  V 
 | / 
ds2
```
At Day 0 an export-ldif of 40,000,000 objects is taken from ds0.
From Day 0 through Day 4 changes are made across all three instances with purge delay removing all changes on Day 4 that occurred on Day 0.
On Day 5 ds3 is instantiated but unfortunately the wrong backup - Day 0 - is used instead of the Day 4 backup.

```
ds0---ds1
 | \ / |
 |  X  |
 | / \ |
ds2---ds3
```
As a result of using the wrong backup (the Day 0 backup) ds3 has an incorrect data set that replication can NOT rectify. 
This mistake is not discovered until Day 7 at which point unraveling the correct data from the incorrect data is a challenge.
Do we see the above scenario happen often? Nope not really. But given the imperfect nature of the human race anything is possible.
Hence DSCheck. Stuff happens. But known "stuff" is better than unknown "stuff".

The following is an example of the output of DSCheck using the --verbose switch. It was produced by a Macbook running DSCheck against two Directory Service instances running on two VirtualBox VMs also running on the Macbook (`ds0.example.com` and `ds1.example.com`). While DSCheck was running on the Macbook one of the Directory Service instances ran modrate. The steps to create the test environment:
1. install at least two DS instances following the documentation [https://backstage.forgerock.com/docs/ds/5.5/install-guide/]()
2. when installing allow DS to prepopulate with test entries on one instance (200,000 or more)
3. establish replication between DS instances [https://backstage.forgerock.com/docs/ds/5.5/admin-guide/#configuring-repl]()
4. let the instances sync up via replication [https://backstage.forgerock.com/docs/ds/5.5/admin-guide/#init-repl]()
5. pick an instance and perform an export-ldif [https://backstage.forgerock.com/docs/ds/5.5/reference/#export-ldif-1]()
6. edit the resulting LDIF with your favorite text editor (i.e. vi) (*if you need guidance for this step than maybe you should not be doing this ; )* )
7. mangle the first few entries such as:
	1. deleting the entire LDIF entries for user.1 and user.3
	2. changing the telephonenumber attribute's value to all 9s
8. save the LDIF
9. use import-ldif on only one DS instance [https://backstage.forgerock.com/docs/ds/5.5/reference/#import-ldif-1]()

Using this process, export-ldif and import-ldif, will cause one DS instance to have different data as importing does NOT have the same affect as ldapmodify. Importing does not place anything into the changelog. 

```
./opendj/bin/modrate -p 1389 \
                      -D "cn=directory manager" \
                      -w password \
                      -F -c 1 -t 1 \
                      -b "uid=user.%d,ou=people,dc=example,dc=com" \
                      -g "rand(10,200000)" \
                      -g "randstr(16)" \
                      'description:%2$s'
```

By running `modrate` while DSCheck was also running it enabled DSCheck to find the occasional object that was in the process of being replicated.


```
Hosts:ports = ds0.example.com:1389~ds1.example.com:1389
Replication hosts shown in ds0.example.com:
 ds0.example.com
 ds1.example.com
Replication hosts shown in ds1.example.com:
 ds0.example.com
 ds1.example.com
Searching for objects...done performing full scan of all objects in ou=People,dc=example,dc=com.
Now collating and sorting...
Total entries in ds0.example.com  =   199998 /Users/rmfaller/projects/DSCheck/tmp/full-ds0.example.com.txt
Total entries in ds1.example.com  =   199999 /Users/rmfaller/projects/DSCheck/tmp/full-ds1.example.com.txt
Done collating and sorting.
If --fulldisplay is used data is written to /Users/rmfaller/projects/DSCheck/tmp/objects-20180424071745Z.err
Checking all objects...
Starting dscheck.DSCheck with 8 threads
Checking 199999 objects listed in /Users/rmfaller/projects/DSCheck/tmp/dns.txt in the following instances:
	ds0.example.com:1389
	ds1.example.com:1389
=============================================

Object uid=user.1,ou=People,dc=example,dc=com checked 3 time(s) = NOT MATCHING
Instance: ds0.example.com:1389 etag = 000000000580d466
Instance: ds1.example.com:1389 etag = 00000000d10ed433

Object uid=user.155054,ou=People,dc=example,dc=com checked 1 time(s) due to inconsistencies but is now MATCHED
Instance: ds0.example.com:1389 etag = 00000000aae9eaba
Instance: ds1.example.com:1389 etag = 00000000aae9eaba

Object uid=user.2,ou=People,dc=example,dc=com checked 3 time(s) = NONEXISTENT
Instance: ds0.example.com:1389 etag = NULL
Instance: ds1.example.com:1389 etag = 00000000175fc400

Object uid=user.48709,ou=People,dc=example,dc=com checked 1 time(s) due to inconsistencies but is now MATCHED
Instance: ds0.example.com:1389 etag = 000000007939e29d
Instance: ds1.example.com:1389 etag = 000000007939e29d

Object uid=user.165005,ou=People,dc=example,dc=com checked 1 time(s) due to inconsistencies but is now MATCHED
Instance: ds0.example.com:1389 etag = 00000000b274e126
Instance: ds1.example.com:1389 etag = 00000000b274e126

Object uid=user.3,ou=People,dc=example,dc=com checked 3 time(s) = NOT MATCHING
Instance: ds0.example.com:1389 etag = 000000008e11d8a1
Instance: ds1.example.com:1389 etag = 000000005cd7d871

thread-0 pass = 24998 fail = 1; Average time per check = 7ms against 2 instances
thread-1 pass = 24999 fail = 0; Average time per check = 7ms against 2 instances
thread-2 pass = 24999 fail = 0; Average time per check = 7ms against 2 instances
thread-3 pass = 24999 fail = 0; Average time per check = 7ms against 2 instances
thread-4 pass = 24997 fail = 2; Average time per check = 7ms against 2 instances
thread-5 pass = 24999 fail = 0; Average time per check = 7ms against 2 instances
thread-6 pass = 24999 fail = 0; Average time per check = 7ms against 2 instances
thread-7 pass = 25006 fail = 0; Average time per check = 7ms against 2 instances
---------------------------------------------------
   Total	 pass = 199996	 fail = 3

Stats from DSCheck perspective with lapsed clock time = 183323000 seconds:
ds0.example.com:1389 called = 200008 times; Average time/call = 3ms; tx rate = 1091.0143/sec
ds1.example.com:1389 called = 200008 times; Average time/call = 3ms; tx rate = 1091.0143/sec
Cumulative transaction rate across all instances = 2182.0286/sec
+++++++++++++++++++++++++++++++++
End of dscheck

Time stamp to be used: Create = 20180424071745Z & Modify = 20180424071745Z
Hosts:ports = ds0.example.com:1389~ds1.example.com:1389
Replication hosts shown in ds0.example.com:
 ds0.example.com
 ds1.example.com
Replication hosts shown in ds1.example.com:
 ds0.example.com
 ds1.example.com
Searching for objects...done performing partial scan of all objects in ou=People,dc=example,dc=com.
Now collating and sorting...
--------------------------------
Created entries after 20180424071745Z in ds0.example.com: 0
Modified entries after 20180424071745Z in ds0.example.com: 130331
--------------------------------
Created entries after 20180424071745Z in ds1.example.com: 0
Modified entries after 20180424071745Z in ds1.example.com: 130331
--------------------------------
Done collating and sorting.
Checking objects that meet criteria...
Starting dscheck.DSCheck with 8 threads
Checking 130331 objects listed in /Users/rmfaller/projects/DSCheck/tmp/dns.txt in the following instances:
	ds0.example.com:1389
	ds1.example.com:1389
=============================================

thread-0 pass = 16291 fail = 0; Average time per check = 5ms against 2 instances
thread-1 pass = 16291 fail = 0; Average time per check = 5ms against 2 instances
thread-2 pass = 16291 fail = 0; Average time per check = 5ms against 2 instances
thread-3 pass = 16291 fail = 0; Average time per check = 5ms against 2 instances
thread-4 pass = 16291 fail = 0; Average time per check = 5ms against 2 instances
thread-5 pass = 16291 fail = 0; Average time per check = 5ms against 2 instances
thread-6 pass = 16291 fail = 0; Average time per check = 5ms against 2 instances
thread-7 pass = 16294 fail = 0; Average time per check = 5ms against 2 instances
---------------------------------------------------
   Total	 pass = 130331	 fail = 0

Stats from DSCheck perspective with lapsed clock time = 83978000 seconds:
ds0.example.com:1389 called = 130331 times; Average time/call = 2ms; tx rate = 1551.966/sec
ds1.example.com:1389 called = 130331 times; Average time/call = 2ms; tx rate = 1551.966/sec
Cumulative transaction rate across all instances = 3103.932/sec
+++++++++++++++++++++++++++++++++
End of dscheck

Time stamp to be used: Create = 20180424072244Z & Modify = 20180424072244Z
Hosts:ports = ds0.example.com:1389~ds1.example.com:1389
Replication hosts shown in ds0.example.com:
 ds0.example.com
 ds1.example.com
Replication hosts shown in ds1.example.com:
 ds0.example.com
 ds1.example.com
Searching for objects...done performing partial scan of all objects in ou=People,dc=example,dc=com.
Now collating and sorting...
--------------------------------
Created entries after 20180424072244Z in ds0.example.com: 0
Modified entries after 20180424072244Z in ds0.example.com: 0
--------------------------------
Created entries after 20180424072244Z in ds1.example.com: 0
Modified entries after 20180424072244Z in ds1.example.com: 0
--------------------------------
Done collating and sorting.

-->> There are NO objects to check <<--
+++++++++++++++++++++++++++++++++
End of dscheck


```
