#!/bin/bash
# The code is provided on an "as is" basis, without warranty of any kind, 
# to the fullest extent permitted by law. 
#
# ForgeRock does not warrant or guarantee the individual success 
# developers may have in implementing the code on their 
# platforms or in production configurations.
#
# ForgeRock does not warrant, guarantee or make any representations 
# regarding the use, results of use, accuracy, timeliness or completeness
# of any data or information relating to the release of unsupported code.
# ForgeRock disclaims all warranties, expressed or implied, and in particular,
# disclaims all warranties of merchantability, and warranties related to
# the code, or any service or software related thereto.
#
# ForgeRock shall not be liable for any direct, indirect or consequential damages
# or costs of any type arising out of any action taken by you or others related to the code.

# location of DS binaries such as ldapsearch
DSHOME=/Volumes/twoTBdrive/zips/opendj

# number of DSCheck threads to run simultaneously
THREADS=16

# location of the DSCheck /dist folder|directory
DSCHECKHOME=$HOME/projects/DSCheck

# One of the DS instances that is part of the replication topology
# If there are more than one replication topology best to make copies of
# this script per replication topology
DSHOST=ds1.example.com
DSPORT=1389
DSID="cn=Directory Manager"
DSPASSWORD=""
DSPASSWORDFILE="${DSCHECKHOME}/password.txt"

if [[ -n ${DSPASSWORDFILE} ]]
  then
    if [[ -e ${DSPASSWORDFILE} ]]
      then
      DSPASSWORD=`head -1 ${DSPASSWORDFILE}`
    else
      echo "Password file ${DSPASSWORDFILE} does not exist"
      echo -n "Enter ${DSID} password: "
      read -s DSPASSWORD
    fi
else
  if [[ -z ${DSPASSWORD} ]]
    then
    echo -n "Enter ${DSID} password: "
    read -s DSPASSWORD
    echo "Processing..."
  fi
fi

# Base DN for compare objects
# As is the case with more than one replication topology it is advised that a copies of
# this script be made per base DN. For example:
#   o dscheck-people.sh 
#   o dscheck-devices.sh
# and make the needed changes per objects that need to be checked
BASEDN="ou=People,dc=example,dc=com"

# Using the instance specified above use the following to discover instances listed in the replication topology
REPBASEDN="cn=replication server,cn=Multimaster Synchronization,cn=Synchronization Providers,cn=config"
MONITORBASEDN="cn=monitor"

# location to place output 
TMPFILES=$DSCHECKHOME/tmp

STARTTIMESTAMP=`date '+%Y%m%d%H%M%S'`

# set current time based on DS instance(s) NOT of the current time of the system running DSCheck
DSCURRENTTIME=`${DSHOME}/bin/ldapsearch \
              --hostname "${DSHOST}" \
              --port "${DSPORT}" \
              --bindDn "${DSID}" \
              --bindPassword "${DSPASSWORD}" \
              --baseDn "${MONITORBASEDN}" "(cn=monitor)" currentTime \
              | grep currentTime \
              | cut -d":" -f2 \
              | sed -e 's/^[ \t]*//' \
              | sed 's/[ \t]*$//'`

# if a timestamp is included in the execution of this script then use that time stamp
# if not included then use the time stamp(s) as specified in this script
if [[ ${1} ]] 
  then
  if [[ ${1} == "full" ]] 
    then
# run a full check against all objects
    FULLCHECK=true
  else 
    CREATETIMESTAMP="${1}"
    MODIFYTIMESTAMP="${1}"
    FULLCHECK=false
    echo "Time stamp to be used: Create = ${CREATETIMESTAMP} & Modify = ${MODIFYTIMESTAMP}"
  fi 
  else 
    CREATEYEAR=2018
    CREATEMONTH=04
    CREATEDAY=22
    CREATEHOUR=14
    CREATEMINUTE=45
    CREATESECOND=59
    CREATETIMESTAMP="${CREATEYEAR}${CREATEMONTH}${CREATEDAY}${CREATEHOUR}${CREATEMINUTE}${CREATESECOND}Z"

    MODIFYYEAR=2018
    MODIFYMONTH=04
    MODIFYDAY=22
    MODIFYHOUR=14
    MODIFYMINUTE=45
    MODIFYSECOND=32
    MODIFYTIMESTAMP="${MODIFYYEAR}${MODIFYMONTH}${MODIFYDAY}${MODIFYHOUR}${MODIFYMINUTE}${MODIFYSECOND}Z"
fi 

# for each instance gathered from instance specified above check that these instances share the same replication topology information
hosts=`${DSHOME}/bin/ldapsearch \
       --hostname "${DSHOST}" \
       --port "${DSPORT}" \
       --bindDn "${DSID}" \
       --bindPassword "${DSPASSWORD}" \
       --baseDn "${REPBASEDN}" "(objectclass=ds-cfg-replication-server)" ds-cfg-replication-server \
         | grep ^ds-cfg-replication-server \
         | cut -d":" -f2 \
         | sed -e 's/^[ \t]*//' \
         | sed 's/[ \t]*$//'`
instances=`echo ${hosts} | sed -e 's/ /:'"${DSPORT}"'~/g'`
instances="${instances}:${DSPORT}"
echo "Hosts:ports = ${instances}"

# NOTE: the following ldapsearch operations can have a significant impact on both the system running DSCheck 
#       as well as in the systems hosting the target DS instances

for host in ${hosts}
  do
# list out each instance replication topology
  echo "Replication hosts shown in ${host}:"
  ${DSHOME}/bin/ldapsearch \
    --hostname "${host}" \
    --port "${DSPORT}" \
    --bindDn "${DSID}" \
    --bindPassword "${DSPASSWORD}" \
    --baseDn "${REPBASEDN}" "(objectclass=ds-cfg-replication-server)" ds-cfg-replication-server \
    | grep ^ds-cfg-replication-server \
    | cut -d":" -f2 | sort

# Run full scan of all objects in the baseDn; the ldapsearch is run as a background process
# one background process per DS instance is created
  if [[ ${FULLCHECK} == "true" ]]
    then
    ${DSHOME}/bin/ldapsearch \
      --hostname "${host}" \
      --port "${DSPORT}" \
      --bindDn "${DSID}" \
      --bindPassword "${DSPASSWORD}" \
      --baseDn "${BASEDN}" "(objectclass=*)" 1.1 \
      | grep ^dn > ${TMPFILES}/full-${host}.txt &

  else

# if not a full scan then perform background ldapsearch based on creation time and modification time
# two background processes per DS instance are created
      ${DSHOME}/bin/ldapsearch \
      --hostname "${host}" \
      --port "${DSPORT}" \
      --bindDn "${DSID}" \
      --bindPassword "${DSPASSWORD}" \
      --baseDn "${BASEDN}" "(createTimestamp>=${CREATETIMESTAMP})" 1.1 \
      | grep ^dn > ${TMPFILES}/create-${host}.txt &

    ${DSHOME}/bin/ldapsearch \
      --hostname "${host}" \
      --port "${DSPORT}" \
      --bindDn "${DSID}" \
      --bindPassword "${DSPASSWORD}" \
      --baseDn "${BASEDN}" "(modifyTimestamp>=${MODIFYTIMESTAMP})" 1.1 \
      | grep ^dn > ${TMPFILES}/modify-${host}.txt &
  fi
done
echo -n "Searching for objects..."
# wait for ldapsearch operations to complete
wait

# Process results from ldapsearch operations
if  [[ ${FULLCHECK} == "true" ]]
  then
  echo "done performing full scan of all objects in ${BASEDN}."
  echo "Now collating and sorting..."
# check object entry count of each DS instance
  for host in ${hosts}
    do
    echo -n "Total entries in ${host}  = "
    if [[ -e "${TMPFILES}/full-${host}.txt" ]]
      then
      wc -l ${TMPFILES}/full-${host}.txt 
    else
      echo "0"
      echo -n > ${TMPFILES}/full-${host}.txt
    fi
  done
# collect all the DNs from each host into a single sorted file
# check for uniqueness of each DN counting the number of recurrences which should match the number of DS instances 
# use the head and tail command to verify there are no discrepancies in object count
#  cat ${TMPFILES}/full-*.txt | sort | uniq -c | sort | sed -e 's/^[ \t]*//'> ${TMPFILES}/checkentries-${DSCURRENTTIME}.txt
  cat ${TMPFILES}/full-*.txt | sort | uniq -c | sed -e 's/^[ \t]*//'> ${TMPFILES}/checkentries-${DSCURRENTTIME}.txt
# clean up temporary files
  rm ${TMPFILES}/full-*.txt
else
  echo "done performing partial scan of all objects in ${BASEDN}."
#  echo "Time stamp to be used: Create = ${CREATETIMESTAMP} & Modify = ${MODIFYTIMESTAMP}."
  echo "Now collating and sorting..."
  echo "--------------------------------"
  for host in ${hosts}
    do
    echo -n "Created entries after ${CREATETIMESTAMP} in ${host}: "
    if [[ -e "${TMPFILES}/create-${host}.txt" ]]
      then
        echo `wc -l ${TMPFILES}/create-${host}.txt | sed -e 's/^[ \t]*//' | cut -d" " -f1`
    else
      echo "0"
      echo -n > ${TMPFILES}/create-${host}.txt
    fi
    echo -n "Modified entries after ${MODIFYTIMESTAMP} in ${host}: "
    if [[ -e "${TMPFILES}/modify-${host}.txt" ]]
      then
      echo `wc -l ${TMPFILES}/modify-${host}.txt | sed -e 's/^[ \t]*//' | cut -d" " -f1`
    else
      echo "0"
      echo -n > ${TMPFILES}/modify-${host}.txt
    fi
  echo "--------------------------------"
  done
# collect all the DNs from each host that were created and/or modified on or after the specified time stamp into a single sorted file
# check for uniqueness of each DN counting the number of recurrences which should match the number of DS instances 
# use the head and tail command to verify there are no discrepancies in object count
  cat ${TMPFILES}/modify-*.txt ${TMPFILES}/create-*.txt | sort | uniq -c | sort | sed -e 's/^[ \t]*//'> ${TMPFILES}/checkentries-${DSCURRENTTIME}.txt
#clean up temporary files
  rm ${TMPFILES}/modify-*.txt ${TMPFILES}/create-*.txt
fi

# strip off the unique entry count value
cat ${TMPFILES}/checkentries-${DSCURRENTTIME}.txt | cut -d" " -f3 > ${TMPFILES}/dns.txt
totaldns=`wc -l ${TMPFILES}/dns.txt | sed -e 's/^[ \t]*//' | cut -d" " -f1`
echo "Done collating and sorting."
if [[ ($totaldns < 1) ]]
  then
  echo ""
  echo "-->> There are NO objects to check <<--"
else
  if  [[ ${FULLCHECK} == "true" ]]
    then
    echo "If --fulldisplay is used data is written to ${TMPFILES}/objects-${DSCURRENTTIME}.err"
    echo "Checking all objects..."
    java -jar ${DSCHECKHOME}/dist/DSCheck.jar \
              --bindDn "${DSID}" \
              --bindPassword "${DSPASSWORD}" \
              --threads ${THREADS} \
              --verbose \
              --fulldisplay \
              --repeat 3 \
              --sleep 2 \
              --instances ${instances} \
              ${TMPFILES}/dns.txt 2> ${TMPFILES}/objects-${DSCURRENTTIME}.err
  else
    echo "Checking objects that meet criteria..."
    java -jar ${DSCHECKHOME}/dist/DSCheck.jar \
              --bindDn "${DSID}" \
              --bindPassword "${DSPASSWORD}" \
              --threads ${THREADS} \
              --verbose \
              --fulldisplay \
               --repeat 4 \
               --sleep 1 \
               --instances ${instances} \
               ${TMPFILES}/dns.txt 2> ${TMPFILES}/objects-${DSCURRENTTIME}.err
  fi
fi

# retain the checkentries files for historical purposes if desired
# if not then uncomment the following command
# rm ${TMPFILES}checkentries-${DSCURRENTTIME}.txt 

ENDTIMESTAMP=`date '+%Y%m%d%H%M%S'`
# echo "Started on ${STARTTIMESTAMP}; Completed on ${ENDTIMESTAMP}" 
echo "+++++++++++++++++++++++++++++++++"
echo "End of dscheck"
echo ""
${DSCHECKHOME}/scripts/dscheck.sh ${DSCURRENTTIME}
