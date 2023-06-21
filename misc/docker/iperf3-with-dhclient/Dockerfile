FROM vproxyio/vpss-base:2023-01-23

RUN apt-get install -y iperf3

COPY init.sh /init.sh
RUN chmod +x /init.sh

ENTRYPOINT ["/init.sh"]
CMD ["-s"]
