FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y --no-install-recommends nfs-ganesha nfs-ganesha-vfs dbus rpcbind && rm -rf /var/lib/apt/lists/* && mkdir -p /run/ganesha /export
COPY apps/file-access-demo/ganesha.conf /etc/ganesha/ganesha.conf
COPY apps/file-access-demo/start-nfs.sh /usr/local/bin/start-nfs
RUN chmod 755 /usr/local/bin/start-nfs
EXPOSE 2049
CMD ["/bin/sh", "/usr/local/bin/start-nfs"]
