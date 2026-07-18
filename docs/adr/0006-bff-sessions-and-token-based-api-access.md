# BFF sessions and token-based API access

CloudForge uses host-only secure Cookie sessions for browser-facing BFFs and IAM redirect-based SSO between applications, rather than a parent-domain session cookie shared by all subdomains. BFFs keep browser credentials and tokens server-side, while API clients use short-lived tenant-scoped access tokens and refresh through IAM; downstream domain services receive access tokens and enforce authorization locally.
