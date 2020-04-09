FROM openjdk:8

MAINTAINER NewMotion, ti@newmotion.com

RUN mkdir app

ADD cmd/target/scala-2.12/docile.jar /app
ADD examples /app/examples

WORKDIR /app

ENV CHARGEPOINT_SERIAL=56565656
ENV BACKOFFICE_URL=wss://example.com/ocppws/
ENV SCRIPT=examples/ocpp1x/continuous-remote-listening.scala

CMD ["sh", "-c", "java -jar docile.jar -c $CHARGEPOINT_SERIAL --forever $BACKOFFICE_URL  $SCRIPT"]

