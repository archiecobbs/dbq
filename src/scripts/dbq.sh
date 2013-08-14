#!/bin/sh

# Get path separators, etc
case `uname -o` in
    Cygwin)
        PATH_SEP=';'
        TRANSFORM="sed -r -e s|:|;C:\\\\cygwin|g -e s|/|\\\\|g"
        ;;
    *)
        PATH_SEP=':'
        TRANSFORM='cat'
        ;;
esac

PKGDIR="/usr/share/dbq"
MAINCLASS="org.dellroad.dbq.Main"
CLASSPATH=`find "${PKGDIR}" -type f -name '*.jar' -print0 | xargs -0 -n 1 printf ':%s' | ${TRANSFORM}`

exec java -classpath "${CLASSPATH}" "${MAINCLASS}" ${1+"$@"}

