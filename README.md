# DSCheck
Utility to check entries on all Directory Service (DS) instances in a replicated DS environment to valid that all the entries of a particular Distinguished Name (DN) match using the object's `etag` value. For example DSCheck validates that `uid=user.1234,ou=people,dc=example,dc=com` has the same etag value on every replicated DS instance of that particular object.

Why is DSCheck necessary? From a Directory Server standpoint and the reliability of replication it is not. Directory Server replication is a long established mechanism that works.

Replication can generally recover from conflicts and transient issues. Replication does, however, require that update operations be copied from server to server. It is possible to experience temporary delays while replicas converge, especially when the write operation load is heavy. DS's tolerance for temporary divergence between replicas is what allows DS to remain available to serve client applications even when networks linking the replicas go down.

In other words, the fact that directory services are loosely convergent rather than transactional is a feature.

What we do find is that some external event can cause a lack of confidence by the Directory Server operators with the consistency of the data persisted by Directory Server. Some of these external events can include:

* Importing the wrong data into one of the instances
* Instance(s) improperly taken off line
* Instance(s) returned to a replication topology but with a different hostname
* Instance(s) off-line beyond the purge delay setting [https://backstage.forgerock.com/docs/ds/5.5/admin-guide/#troubleshoot-repl]()

Since no environment is failsafe DSCheck provides a way to check and validate data consistency across all replicas. The goal of DSCheck is to help operators gain confidence in the consistency of the data. If object inconsistency/validity is discovered the offending objects will be listed and the data inconsistency rectified by those who are responsible for the data. DSCheck does NOT determine what data is the most correct.

DSCheck is not a real time utility. From the time DSCheck starts until completion data could change. Depending on the speed in which replication can complete across all instances at certain points in time specific objects may NOT have the exact same data. DSCheck can accommodate this by rechecking unmatched objects based on flags/switches selected.

Running DSCheck is best done using ./scripts/dscheck.sh that enables the setting of environmental parameters. The script also extracts and processes a list of DNs from each instance in the replication topology.

Once the preprocessing is complete the actual examining of the each object by checking the etag value is done within ./dist/DSCheck.jar. 

Running DSCheck can place load on the system running the DSCheck as well as the target DS instances. The ldapsearch commands used allow multiple, simultaneous threads of execution. DO NOT run DSCheck against a production environment unless you fully understand the potential load impact. Off-hours are recommended.

```
DSCheck usage:
java -jar ./dist/DSCheck.jar --instances INSTANCE0:PORT0~INSTANCEn:PORTn /path/to/DNs/file
required:
	--instances  | -i in a specific format using a tilde "~" to separate instances i.e. ds0.example.com:1389~ds1.example.com:1389~ds2.example.com:1389
	/path/to/DNs/file where each line in this file is a single DN
options:
	--verbose  | -v {default = false} full display of objects that did not match
	--repeat n | -r n (default n = 1} maximum number of times to check object validity if found not valid across all instances
	--sleep t  | -s t {default t = 0} seconds to sleep until repeating validity check
	--help     | -h this output

Examples:
java -jar ${DSCHECKHOME}/dist/DSCheck.jar --instances ${instances} ${TMPFILES}dns.txt
```

DSCheck can scan all objects in a certain BaseDN or scan only those objects created and modified after a specified time in a certain BaseDN.

Possible scenario that could lead to a data discrepancy.
Three DS instances in fully meshed replication:

```
ds0---ds1
 | \ / 
 |  V 
 | / 
ds2
```



ds0---ds1
 | \ / |
 |  X  |
 | / \ |
ds2---ds3