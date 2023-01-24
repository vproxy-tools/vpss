FROM vproxyio/vpss-base:1.0.0

COPY vpss.jar /vproxy/vpss/vpss.jar
COPY ./ui /vproxy/vpss/ui

COPY vpss.sh /vpss.sh
RUN chmod +x vpss.sh

ENTRYPOINT ["/vpss.sh"]
