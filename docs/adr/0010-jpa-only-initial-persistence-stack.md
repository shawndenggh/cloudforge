# JPA-only initial persistence stack

CloudForge standardizes its initial persistence stack on Spring Data JPA and Hibernate, with Flyway as the only schema migration mechanism. Services use repositories, specifications, HQL, DTO projections, and JPA native queries as needed; jOOQ and a separate JDBC data-access stack are deferred until a demonstrated query or performance requirement justifies the additional abstraction.
