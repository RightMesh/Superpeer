FROM ubuntu:bionic

WORKDIR /home/ubuntu/superpeer

RUN \
apt-get update && \
apt-get install --no-install-recommends -y \
  default-jre

COPY build/install/Superpeer/bin/Superpeer bin/Superpeer
COPY build/install/Superpeer/lib lib
RUN chmod +x bin/Superpeer

EXPOSE 40000

CMD /home/ubuntu/superpeer/bin/Superpeer