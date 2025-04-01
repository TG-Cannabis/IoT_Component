# Setting up the MQTT Broker

This guide explains how to set up the MQTT Broker, which is a central component in our IoT architecture, responsible for handling messages between the IoT Controller and backend services.

## Role in Architecture

As shown in the project architecture diagram, the MQTT Broker acts as a message intermediary:
* It **receives** sensor readings published by the `Controlador IoT`.
* It **distributes** these messages to subscribing components like the `Procesador Batch` (for storage) and `Servicios Consultas y Alertas` (for real-time processing and alerts).
* It **decouples** the data producers from the data consumers.

We will use an existing, standard MQTT broker implementation rather than building one from scratch. [Mosquitto](https://mosquitto.org/) is a popular, lightweight choice perfect for development and many production scenarios.

## Prerequisites

* **Docker:** You need Docker installed and running on your system. If you don't have it, follow the official installation guide: [Get Docker](https://docs.docker.com/get-docker/)

## Setup Steps using Docker

Using Docker is the recommended way to run the Mosquitto MQTT broker locally.

1.  **Pull the Mosquitto Image (Optional):** Docker will usually do this automatically on the first run, but you can pull it manually:
    ```bash
    docker pull eclipse-mosquitto
    ```

2.  **Run the Mosquitto Container:** Open your terminal or command prompt and execute the following command:
    ```bash
    docker run \
    -p 1883:1883 \
    -p 9001:9001 \
    --name mqtt-broker \
    -v "$(pwd)/mosquitto.conf:/mosquitto/config/mosquitto.conf" \
    -d \
    eclipse-mosquitto
    ```

3.  **Command Explanation:**
    * `docker run`: Starts a new container.
    * `-p 1883:1883`: Maps the standard MQTT port `1883` from the container to your host machine. Clients will connect to this port.
    * `-p 9001:9001`: Maps the MQTT over WebSockets port `9001` (useful for web-based clients).
    * `--name mqtt-broker`: Assigns a convenient name (`mqtt-broker`) to the container for easier management.
    * `-d`: Runs the container in detached mode (in the background).
    * `eclipse-mosquitto`: Specifies the official Mosquitto Docker image to use.

4.  **Verify the Container is Running:** You can check if the container started successfully:
    ```bash
    docker ps
    ```
    You should see an entry for a container with the name `mqtt-broker` and the image `eclipse-mosquitto` in the output, showing the port mappings.

## Connecting Clients

Your other application components (`Controlador IoT`, `Procesador Batch`, `Servicios Consultas y Alertas`) should now be configured to connect to the broker using the following details:

* **Broker Address:** `localhost` (or `127.0.0.1`) if the client is running on the same host machine. If running clients in *other* Docker containers, you might use `host.docker.internal` or the host machine's IP address depending on your Docker network setup.
* **Broker Port:** `1883` (for standard MQTT) or `9001` (for MQTT over WebSockets).

For test: 
```bash
  docker run -it --rm --network host eclipse-mosquitto mosquitto_sub -h localhost -p 1883 -t "sensores/#" -v
```

For example, the connection URI would typically be `tcp://localhost:1883`.

## Managing the Container

* **Stop:** `docker stop mqtt-broker`
* **Start:** `docker start mqtt-broker`
* **View Logs:** `docker logs mqtt-broker`
* **Remove (when stopped):** `docker rm mqtt-broker`

## Production Considerations

For a real production environment, this basic setup should be extended:

* **Configuration File:** Create a `mosquitto.conf` file to configure persistence, logging, security (authentication/authorization), and TLS/SSL encryption.
* **Volume Mounting:** Mount the configuration file, data directory (for persistence), and log directory into the container using Docker volumes (`-v` flag).
* **Security:** Implement user authentication and access control lists (ACLs) to restrict who can publish/subscribe to specific topics. Enable TLS encryption.

Refer to the [Mosquitto Documentation](https://mosquitto.org/documentation/) for details on configuration options.