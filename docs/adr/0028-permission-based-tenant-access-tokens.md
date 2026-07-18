# Permission-based tenant access tokens

IAM expands the selected Tenant Membership's Tenant Roles into stable Permission codes when issuing a tenant-scoped access token. Domain services authorize locally against those Permission authorities and do not depend on tenant-defined role names; role names may appear in Profile views but are not authorization contracts. The shared security module validates signature, issuer, audience, expiry, and `tenant_id`, then maps the token's `permissions` claim into Spring Security authorities.
