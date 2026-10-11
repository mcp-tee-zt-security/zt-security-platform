#!/bin/sh
set -eu
mkdir -p /run/dbus /run/ganesha /var/lib/nfs/ganesha
dbus-daemon --system --fork
rpcbind -w
exec ganesha.nfsd -F -L STDOUT -f /etc/ganesha/ganesha.conf
