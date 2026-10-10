# ADR: SSRF protection and DNS rebinding

**Status:** Accepted (known gap, revisit before general availability) · **Date:** 2026-10-09

## Context
Relay sends HTTP requests to URLs that customers register, so a customer could point an endpoint at
an internal address (cloud metadata at `169.254.169.254`, `localhost`, private ranges) and use Relay
to reach it. `SsrfGuard` blocks non-http(s) schemes and any host that resolves to a loopback,
private, link-local, carrier-grade NAT or unique-local address.

A hostname can resolve differently over time. An attacker who controls DNS can answer with a public
address when we validate and with an internal one when we connect (DNS rebinding). The JDK
`HttpClient` resolves the host again on connect and offers no hook to pin the address we validated.

## Decision
- Validate at **registration** (`EndpointService`) and again at **send time** (`DeliverySender`),
  so a hostname that later starts pointing inward is caught on the next attempt.
- Never follow redirects (`HttpClient.Redirect.NEVER`); a 3xx counts as a failed delivery, so a
  public URL cannot bounce us to an internal one.
- Do **not** pin the validated IP yet. `SsrfGuard.resolveSafe` already returns the vetted address
  so pinning can be added without changing callers.

## Alternatives considered
- **Apache HttpClient 5 with a custom `DnsResolver`**: resolve once, reject internal addresses, and
  connect to exactly that address while keeping the original `Host` header and TLS SNI. This closes
  the gap fully. Costs a new dependency and a second HTTP stack to configure and test.
- **Connect to the literal IP with a `Host` header override on the JDK client**: the JDK restricts
  the `Host` header, and TLS certificate and SNI checks would break for HTTPS.
- **Egress proxy or network policy that blocks internal ranges**: strongest, and independent of
  application code, but it is an infrastructure change outside this milestone. Worth doing in addition.

## Consequences
- A narrow window remains between our DNS lookup and the client's own lookup in which a malicious
  resolver could return an internal address. Exploiting it needs control of the target's DNS and
  precise timing, but it is possible.
- Blocking internal destinations at the network layer (egress rules) should be treated as the real
  backstop in production until pinning is implemented.
- Moving to pinning later is a change to `DeliverySender` and the HTTP client configuration only.
