# MVC gateway and imperative runtime

CloudForge standardizes its initial HTTP runtime on the imperative Servlet stack, including Spring Cloud Gateway Server MVC, and enables Java 25 virtual threads where validated. WebFlux is not part of the initial shared platform because there is no current WebSocket, SSE, or long-lived streaming requirement, avoiding duplicate Servlet and Reactor security, tenant, and observability context implementations.
