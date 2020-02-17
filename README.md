# redcap-report-listener

[![Build Status](https://travis-ci.org/AriHealth/redcap-report-listener.svg?branch=master)](https://travis-ci.org/AriHealth/redcap-report-listener) 
[![codecov.io](https://codecov.io/gh/AriHealth/redcap-report-listener/branch/master/graphs/badge.svg)](http://codecov.io/gh/AriHealth/redcap-report-listener)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=net.atos.ari.sdk:redcap-report-listener&metric=alert_status)](https://sonarcloud.io/dashboard/index/net.atos.ari.sdk:redcap-report-listener)
[![Docker Build](https://img.shields.io/docker/cloud/build/arihealth/redcap-report-listener)](https://cloud.docker.com/u/arihealth/repository/docker/arihealth/redcap-report-listener)
[![Docker Pulls](https://img.shields.io/docker/pulls/arihealth/redcap-report-listener)](https://cloud.docker.com/u/arihealth/repository/docker/arihealth/redcap-report-listener)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](https://opensource.org/licenses/Apache-2.0)

## Description

Listen from REDCap and when an instrument is complete and locked, retrieve the report and send it to HIS using a HL7 v2 ORU message

## Technology

- Java 8+
- Maven for Java dependency management
- Spring Boot 
- Lombok for the models

## Functionalities

- Listen for pdf reports generation in REDCap. If the instrument is complete and blocked then the pdf is uploaded to a FHIR instance and a HL7 v2 ORU is sent to the HIS.

## How to deploy

Compile and package the project with

```
mvn clean package
```

and execute

```
java -jar target/xcare-listener.jar
```

It can also be run as:

```
mvn spring-boot:run
```

## Environment variables

	ORU_URL=
	REDCAP_URL=
	REDCAP_TOKEN=
	REDCAP_INSTRUMENT=
	FHIR_URL=
    LOGGING_FOLDER=
    LOGGING_MODE=

## Docker deployment

Build the image:

```
docker build -t health/xcare-listener .
```

Simple deployment:

```
docker run --name xcare-listener -d health/xcare-listener
```

Logging can be also configured using `LOGGING_FOLDER` and sharing a volume (this is useful for example for [ELK](https://www.elastic.co/elk-stack) processing). The level of the logging can be configured with `LOGGING_MODE` (dev|prod):

```
docker run --name xcare-listener -d -v /tmp/log/xcare-listener:/log/xcare-listener -e LOGGING_FOLDER=/log/test -e LOGGING_MODE=dev health/xcare-listener
```

## License

Apache 2.0

By downloading this software, the downloader agrees with the specified terms and conditions of the License Agreement and the particularities of the license provided.
