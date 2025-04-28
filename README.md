# FDK Dataset Preview Service

This application provides an API to preview a dataset distribution. Currently this service only supports CSV (text/csv
and application/vnd.ms-excel) to render as table.

For a broader understanding of the system’s context, refer to
the [architecture documentation](https://github.com/Informasjonsforvaltning/architecture-documentation) wiki. For more
specific context on this application, see the **Portal** subsystem section.

## Getting Started

These instructions will give you a copy of the project up and running on your local machine for development and testing
purposes.

### Prerequisites

Ensure you have the following installed:

- Java 21
- Maven

### Running locally

Clone the repository

```sh
git clone https://github.com/Informasjonsforvaltning/fdk-dataset-preview-service.git
cd fdk-dataset-preview-service
```

#### Expose environment variables

* `API_KEY` - API KEY used to access the endpoint
* `ALLOWED_ORIGINS` - ALLOWED_ORIGINS used to configure cors settings

#### Start application

```sh
mvn spring-boot:run -Dspring-boot.run.profiles=develop
```

#### Security

Add the `X-API-KEY` header to your requests and use the value configured for `API_KEY`.
Add the `X-XSRF-TOKEN` header to your requests and use the cookie value of `DATASET-PREVIEW-CSRF-TOKEN`.

### API Documentation (OpenAPI)

Once the application is running locally, the API documentation can be accessed
at http://localhost:8080/swagger-ui/index.html

### Running tests

```sh
mvn verify
```

