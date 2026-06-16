Based on your `application.properties` file, you have exposed all Spring Boot Actuator endpoints. You can access them at the base URL `http://localhost:12345/actuator`.

Here are some of the common endpoints you can access:

*   **`http://localhost:12345/actuator/health`**: Shows application health information. Since you have `management.endpoint.health.show-details=always`, it will show full details.
*   **`http://localhost:12345/actuator/info`**: Displays application information. You have configured this to show project name, description, version, and other details.
*   **`http://localhost:12345/actuator/beans`**: Displays a complete list of all Spring beans in your application.
*   **`http://localhost:12345/actuator/mappings`**: Displays a collated list of all `@RequestMapping` paths.
*   **`http://localhost:12345/actuator/env`**: Displays the current environment properties.
*   **`http://localhost:12345/actuator/metrics`**: Shows various metrics for your application.
*   **`http://localhost:12345/actuator/shutdown`**: This will shut down the application. You have enabled it with `management.endpoint.shutdown.enabled=true`.

You have explicitly excluded `conditions`, `configprops`, and `scheduledtasks` endpoints, so you won't be able to access them.
