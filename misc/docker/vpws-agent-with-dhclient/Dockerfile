FROM vproxyio/vpss-base:1.0.0

COPY vproxy.jar /vproxy.jar
COPY init-vpws-agent.sh /init-vpws-agent.sh

RUN chmod +x /init-vpws-agent.sh

ENTRYPOINT ["/init-vpws-agent.sh"]
CMD []
