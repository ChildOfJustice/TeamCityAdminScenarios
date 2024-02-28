#!/bin/bash
# Set traps to gently shutdown server on `docker stop`, `docker restart` or `docker kill -s 15`
trap "'${TEAMCITY_DIST}/bin/teamcity-server.sh' stop ${TEAMCITY_STOP_WAIT_TIME} -force; exit \$?" TERM INT HUP

# & and wait required for traps to work
"${TEAMCITY_DIST}/bin/teamcity-server.sh" run &
wait $!
exit $?
