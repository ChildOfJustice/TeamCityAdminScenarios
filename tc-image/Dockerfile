ARG baseImage='amazoncorretto:17.0.10-al2-native-headless'

FROM ${baseImage}

RUN yum -y update && \
    yum install -y unzip
    
ENV TEAMCITY_DIST=/opt/teamcity
RUN mkdir -p $TEAMCITY_DIST

ADD run-server.sh /run-server.sh
RUN chmod +x /run-server.sh && sync && \
    groupadd --gid 1000 teamcity && \
    useradd -r -u 1000 -g teamcity -d $TEAMCITY_DIST teamcity && \
    chown -R teamcity:teamcity $TEAMCITY_DIST

COPY --chown=teamcity:teamcity TeamCity $TEAMCITY_DIST

USER teamcity:teamcity

CMD ["/run-server.sh"]
