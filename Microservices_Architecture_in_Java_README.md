
# Microservices Architecture in Java

## Overview

This project demonstrates a microservices-based system designed in Java, encompassing a User Service, Product Service, Order Service, and an Inter-service Communication Service (ISCS). It's structured to work within a local or LAN environment, focusing on API development, microservice architecture, extensible design, and distributed system development.

## Getting Started

### Prerequisites

- Java JDK 11 or later
- Any IDE that supports Java (e.g., IntelliJ IDEA, Eclipse)
- Postman or any HTTP client for testing endpoints

### Installation

1. Clone the repository to your local machine or download the zip file and extract it.

   ```
   git clone <repository-url>
   ```

2. Navigate to the project directory.

   ```
   cd <project-directory>
   ```

### Configuration

1. Modify the `config.json` file in the root directory according to your LAN settings or keep the default settings for local testing.

2. Sample configuration:

   ```json
   {
     "UserService": {
       "port": 14001,
       "ip": "127.0.0.1"
     },
     "OrderService": {
       "port": 14000,
       "ip": "142.1.46.48"
     },
     "ProductService": {
       "port": 15000,
       "ip": "142.1.46.49"
     },
     "InterServiceCommunication": {
       "port": 14000,
       "ip": "127.0.0.1"
     }
   }
   ```

### Compilation

To compile all services, run the following script from the terminal in the project root directory:

```
./runme.sh -c
```

This will compile all Java files and organize them into the `compiled` directory, as per the submission requirements.

## Running the Services

After compilation, you can start each service using the `runme.sh` script with different flags:

- To start the User Service:

  ```
  ./runme.sh -u
  ```

- To start the Product Service:

  ```
  ./runme.sh -p
  ```

- To start the ISCS:

  ```
  ./runme.sh -i
  ```

- To start the Order Service:

  ```
  ./runme.sh -o
  ```

- To run the Workload Parser with a workload file:

  ```
  ./runme.sh -w <path-to-workload-file>
  ```

Ensure that the `config.json` file is correctly set up before starting the services, as they depend on this configuration for intercommunication.

## Usage

Each microservice exposes several endpoints for creating, retrieving, updating, and deleting resources. The system is designed to handle user, product, and order management, with all communications between services routed through the ISCS for scalability and potential load balancing.

### Testing

You can test the APIs using Postman or any HTTP client by sending requests to the configured IP addresses and ports. Sample requests are provided in the project documentation.

### Documentation

For more detailed information on API endpoints and service functionalities, refer to the `docs` directory, which contains Javadocs for each service and a comprehensive writeup on the project architecture, design decisions, and generative AI usage.

## Contributing

Contributions are welcome. Please open an issue first to discuss what you would like to change or add.

## License

This project is licensed under the MIT License - see the LICENSE file for details.
