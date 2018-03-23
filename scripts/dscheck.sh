#!/bin/bash
DSHOME=/Volumes/twoTBdrive/zips/opendj/
DSCHECKHOME=$HOME/projects/DSCheck
DSHOST=ds1.example.com
DSPORT=1389
DSID="cn=Directory Manager"
DSPASSWORD="password"
REPBASEDN="cn=replication server,cn=Multimaster Synchronization,cn=Synchronization Providers,cn=config"
MONITORBASEDN="cn=monitor"
BASEDN="ou=People,dc=example,dc=com"
TMPFILES=$DSCHECKHOME/tmp/
FULLCHECK=true

STARTTIMESTAMP=`date '+%Y%m%d%H%M%S'`
DSCURRENTTIME=`${DSHOME}bin/ldapsearch \
              --hostname "${DSHOST}" \
              --port "${DSPORT}" \
              --bindDn "${DSID}" \
              --bindPassword "${DSPASSWORD}" \
              --baseDn "${MONITORBASEDN}" "(cn=monitor)" currentTime \
              | grep currentTime \
              | cut -d":" -f2 \
              | sed -e 's/^[ \t]*//' \
              | sed 's/[ \t]*$//'`

if [[ $1 ]]
  then
    echo "Time to be used = ${1}"
    CREATETIMESTAMP="${1}"
    MODIFYTIMESTAMP="${1}"
    echo "Time stamp to be used: Create = ${CREATETIMESTAMP} & Modify = ${MODIFYTIMESTAMP}"
  else
    CREATEYEAR=2018
    CREATEMONTH=03
    CREATEDAY=16
    CREATEHOUR=14
    CREATEMINUTE=45
    CREATESECOND=59
    CREATETIMESTAMP="${CREATEYEAR}${CREATEMONTH}${CREATEDAY}${CREATEHOUR}${CREATEMINUTE}${CREATESECOND}Z"

    MODIFYYEAR=2018
    MODIFYMONTH=03
    MODIFYDAY=16
    MODIFYHOUR=14
    MODIFYMINUTE=45
    MODIFYSECOND=32
    MODIFYTIMESTAMP="${MODIFYYEAR}${MODIFYMONTH}${MODIFYDAY}${MODIFYHOUR}${MODIFYMINUTE}${MODIFYSECOND}Z"
fi

hosts=`${DSHOME}bin/ldapsearch \
       --hostname "${DSHOST}" \
       --port "${DSPORT}" \
       --bindDn "${DSID}" \
       --bindPassword "${DSPASSWORD}" \
       --baseDn "${REPBASEDN}" "(objectclass=ds-cfg-replication-server)" ds-cfg-replication-server \
         | grep ^ds-cfg-replication-server \
         | cut -d":" -f2 \
         | sed -e 's/^[ \t]*//' \
         | sed 's/[ \t]*$//'`
instances=`echo ${hosts} | sed -e 's/ /:'"${DSPORT}"'~/'`
instances="${instances}:${DSPORT}"
echo "Hosts:ports = ${instances}"

for host in ${hosts}
  do
  echo "Replication hosts shown in ${host}:"
  ${DSHOME}bin/ldapsearch \
    --hostname "${host}" \
    --port "${DSPORT}" \
    --bindDn "${DSID}" \
    --bindPassword "${DSPASSWORD}" \
    --baseDn "${REPBASEDN}" "(objectclass=ds-cfg-replication-server)" ds-cfg-replication-server \
    | grep ^ds-cfg-replication-server \
    | cut -d":" -f2 | sort
  if [[ ${FULLCHECK} == "true" ]]
    then
    ${DSHOME}bin/ldapsearch \
      --hostname "${host}" \
      --port "${DSPORT}" \
      --bindDn "${DSID}" \
      --bindPassword "${DSPASSWORD}" \
      --baseDn "${BASEDN}" "(objectclass=*)" 1.1 \
      | grep ^dn > ${TMPFILES}full-${host}.txt &
  else
      ${DSHOME}bin/ldapsearch \
      --hostname "${host}" \
      --port "${DSPORT}" \
      --bindDn "${DSID}" \
      --bindPassword "${DSPASSWORD}" \
      --baseDn "${BASEDN}" "(createTimestamp>=${CREATETIMESTAMP})" 1.1 \
      | grep ^dn > ${TMPFILES}create-${host}.txt &

    ${DSHOME}bin/ldapsearch \
      --hostname "${host}" \
      --port "${DSPORT}" \
      --bindDn "${DSID}" \
      --bindPassword "${DSPASSWORD}" \
      --baseDn "${BASEDN}" "(modifyTimestamp>=${MODIFYTIMESTAMP})" 1.1 \
      | grep ^dn > ${TMPFILES}modify-${host}.txt &
  fi
done

echo -n "Searching for objects..."
wait
echo "Done searching and now collating and sorting..."

if  [[ ${FULLCHECK} == "true" ]]
  then
  for host in ${hosts}
    do
    echo -n "Total entries in ${host}  = "
    if [[ -e "${TMPFILES}full-${host}.txt" ]]
      then
      wc -l ${TMPFILES}full-${host}.txt 
    else
      echo "0"
      echo -n > ${TMPFILES}full-${host}.txt
    fi
  done
  cat ${TMPFILES}full-*.txt | sort | uniq -c | sort | sed -e 's/^[ \t]*//'> ${TMPFILES}checkentries-${DSCURRENTTIME}.txt
  rm ${TMPFILES}full-*.txt
else
  echo "Time stamp to be used: Create = ${CREATETIMESTAMP} & Modify = ${MODIFYTIMESTAMP}"
  echo "--------------------------------"
  for host in ${hosts}
    do
    echo -n "Total created entries after ${CREATETIMESTAMP} in ${host}  = "
    if [[ -e "${TMPFILES}create-${host}.txt" ]]
      then
      wc -l ${TMPFILES}create-${host}.txt 
    else
      echo "0"
      echo -n > ${TMPFILES}create-${host}.txt
    fi
    echo -n "Total modified entries after ${MODIFYTIMESTAMP} in ${host} = "
    if [[ -e "${TMPFILES}modify-${host}.txt" ]]
      then
      wc -l ${TMPFILES}modify-${host}.txt 
    else
      echo "0"
      echo -n > ${TMPFILES}modify-${host}.txt
    fi
  echo "--------------------------------"
  done
  cat ${TMPFILES}modify-*.txt ${TMPFILES}create-*.txt | sort | uniq -c | sort | sed -e 's/^[ \t]*//'> ${TMPFILES}checkentries-${DSCURRENTTIME}.txt
  rm ${TMPFILES}modify-*.txt ${TMPFILES}create-*.txt
fi

cat ${TMPFILES}checkentries-${DSCURRENTTIME}.txt | cut -d" " -f3 > ${TMPFILES}dns.txt
echo "Done collating and sorting. Checking object validity..."
echo "+++++++++++++++++++++++++++++++++"
echo "java -jar ${DSCHECKHOME}/dist/DSCheck.jar --instances ${instances} --verbose --repeat 4 ${TMPFILES}dns.txt"
java -jar ${DSCHECKHOME}/dist/DSCheck.jar --instances ${instances} --verbose --repeat 4 ${TMPFILES}dns.txt
echo "End of dscheck"
echo ""
# ${DSCHECKHOME}/scripts/dscheck.sh ${DSCURRENTTIME}
