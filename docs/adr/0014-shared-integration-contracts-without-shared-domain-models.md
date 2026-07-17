# Shared integration contracts without shared domain models

CloudForge defines each Integration Event once in a producer-owned contract module under `shared/contracts`, and both producers and consumers compile against that contract. Contract modules contain versioned transport-only Java records and compatibility metadata; they must not contain JPA entities, aggregates, repositories, or business behavior. This Published Language prevents independently handwritten DTOs from drifting while preserving bounded-context domain ownership.
