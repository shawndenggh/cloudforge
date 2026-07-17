# IAM-authoritative Current Tenant selection

CloudForge authenticates the User independently of a Tenant, then requires IAM to validate and bind exactly one Current Tenant during authorization. A BFF may supply a `tenant_hint`, but IAM verifies the active Tenant Membership before binding the authorization code and access token to that `tenant_id`; a sole membership may be selected automatically and multiple memberships require selection. Switching Tenant performs a new no-password OIDC authorization and token issuance rather than mutating BFF Session state locally.
