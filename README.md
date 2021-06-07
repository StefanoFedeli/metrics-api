# Case Study TV Insight Metrics API

Kotlin RestFul API built on top of a Kafka Cluster

## Architecture
A picture is better than hundreds of words
![Alt text](./docs/schema.png "Architecture")

## 📦 Installation
1. Build the Docker images. One for Kotlin Javalin deployment and the other for Python 
```console
username@hostname:~$ docker build -t javalin:<tag> -f ./serverDeploy .
```
```console
username@hostname:~$ docker build -t python_kafka:<tag> -f ./pythonDockerFile .
```
2. Run docker-compose to setup the architecture
```console
username@hostname:~/Docker$ docker-compose up -d
```
3. BUG: Restart the the restful server container after a dozen of seconds
4. Query the server via any preferred HTTP client.

## API Docs
The API documention can be found [here](./docs/api.raml)

## API Requirements
- [X]  The user needs to specify the channel for which tracking statistics are returned
- [X]  The user should be able to retrieve tracking statistics on either a second or minute granularity
- [X]  The user should be able to fetch up to 24 hours worth of data
- [X]  The user needs to specify a “from” timestamp in ISO 8601 format
- [X]  Optionally, the user should be able to specify a “to” timestamp in ISO 8601 format
- [X]  The API should return data in JSON format

## Requirements
- [X]  Application should be capable of handling duplicate values
- [X]  Avoid using an additional external database to store the data
- [X]  The application should be able to read the tracking data from a kafka topic
- [X]  The application server should be implemented in any JVM compatible language
- [X]  To aggregate from second statistics to minute statistics an average should be used
- [ ]  The application should be production ready and able to scale to high workloads, a high number of concurrent reads

## Known Issues
1. If the Kafka Topic is not yet set up the server crashes
2. Very hard to do unit test, therefore no testing is done.
3. Everything about time is processing time
4. 

## Dependencies and Technologies
This project leverages a different set of technologies
- Docker: Fast deployment and portability. It makes easier to development
- Python: Fast development, easy threading. Perfect language for simulating jobs
- Kafka: Message Broker for easy scale. 
- Kotlin: JVM Language, fast and very robust.

![Alt text](https://tqrhsn.gallerycdn.vsassets.io/extensions/tqrhsn/vscode-docker-registry-explorer/0.1.3/1533881464222/Microsoft.VisualStudio.Services.Icons.Default "Docker") | ![Alt text](https://icons.iconarchive.com/icons/cornmanthe3rd/plex/128/Other-python-icon.png "Python") | ![Alt text](https://upload.wikimedia.org/wikipedia/commons/thumb/0/0a/Apache_kafka-icon.svg/120px-Apache_kafka-icon.svg.png "Kafka") | ![Alt text](https://fwcd.gallerycdn.vsassets.io/extensions/fwcd/kotlin/0.2.18/1593283481846/Microsoft.VisualStudio.Services.Icons.Default "Kotlin")