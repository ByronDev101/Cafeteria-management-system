# HTTPS and abuse limits (Sprint 6)

Nothing in this file is a secret. Certificate passwords are never written into the project: they are read from
environment variables when the application starts.

## 1. Running with HTTPS

There are two ways to run in production. Pick one.

### A. Spring Boot serves HTTPS itself (profile `prod`)

1. Make a certificate file (a PKCS12 keystore). For a real server, get one from your certificate authority. For a
   test machine you can make a self-signed one. `keytool` asks you for a password; choose a strong one and keep it
   out of Git.

   ```
   keytool -genkeypair -alias smartcafeteria -keyalg RSA -keysize 3072 -validity 365 ^
     -storetype PKCS12 -keystore smartcafeteria.p12 ^
     -dname "CN=cafeteria.example.ac.ke" -ext "san=dns:cafeteria.example.ac.ke"
   ```

2. Set these environment variables in the window that starts the application (PowerShell shown):

   ```
   $env:SSL_KEYSTORE          = "file:C:/secure/smartcafeteria.p12"
   $env:SSL_KEYSTORE_PASSWORD = "<the password you chose>"
   $env:DB_URL  = "jdbc:mysql://localhost:3307/smart_cafeteria?useUnicode=true&characterEncoding=UTF-8"
   $env:DB_USER = "<database user>"
   $env:DB_PASSWORD = "<database password>"
   $env:PUBLIC_BASE_URL = "https://cafeteria.example.ac.ke:8443"
   ```

3. Start it: `java -jar target/smart-cafeteria-*.jar --spring.profiles.active=prod`
4. Open `https://<server>:8443/login`. Plain HTTP is not served at all.

### B. A reverse proxy handles HTTPS (profile `behind-proxy`)

The proxy (nginx, Apache, IIS, a cloud load balancer) holds the certificate and talks plain HTTP to the application on
a private network. It must send `X-Forwarded-Proto: https` and `X-Forwarded-For`. Start with
`--spring.profiles.active=behind-proxy`. The application then treats the connection as secure and uses the real
client address for the sign-in limit. Do not expose the application's own port to the internet in this setup, or
anyone could send those headers themselves.

### What the profiles switch on

| Setting | Effect |
|---|---|
| `app.require-https=true` | Any plain-HTTP request is redirected to HTTPS, including sign-in. |
| `server.servlet.session.cookie.secure=true` | The session cookie is only ever sent over HTTPS. |
| HSTS header (always set, sent only on HTTPS) | Browsers keep using HTTPS for a year, subdomains included. |
| `server.ssl.enabled-protocols=TLSv1.3,TLSv1.2` | Older TLS versions are refused (profile `prod`). |

The `dev` profile is unchanged: HTTP on localhost, cookie not marked secure.

Phones scanning table QR codes need an address they trust. A self-signed certificate shows a warning on phones, so
use a certificate from a real authority for the live system, and set `PUBLIC_BASE_URL` to the HTTPS address so the
printed QR codes point there.

Health checks: with HTTPS required, `/actuator/health` also needs HTTPS (or the proxy's forwarded header).

## 2. Limits on abuse

All limits are in memory, per running application. They restart from zero when the application restarts, and each
copy of the application counts separately.

| Limit | Default | Settings | What happens |
|---|---|---|---|
| Failed sign-ins from one address | 10 in 15 minutes | `app.rate-limit.login.max-failures`, `app.rate-limit.login.window-minutes` | Further sign-in attempts from that address are sent back to the sign-in page with "Too many failed sign-in attempts" until the oldest failure is 15 minutes old. Other addresses are unaffected. |
| Orders from one student | 5 in 10 minutes | `app.rate-limit.orders.max`, `app.rate-limit.orders.window-minutes` | The student is asked to wait, with the number of minutes. Repeated submits that return the earlier order, and orders refused for any reason, do not count. |
| Wrong passwords on one account (already existed) | see `AppUser` | | The account is locked for a while. |

Notes:

- A successful sign-in never resets the address count, so one valid account cannot be used to keep guessing others.
- The sign-in limit uses only the address of the connection. Forwarding headers are not trusted by the application
  itself; the `behind-proxy` profile makes the server set the real address from the proxy's header.
- Many students on one campus network can share an address. If that causes trouble, raise
  `app.rate-limit.login.max-failures`.

## 3. Browser protection headers (every response)

Content-Security-Policy (own files only, no framing), `Referrer-Policy: no-referrer`, `X-Content-Type-Options: nosniff`,
`Permissions-Policy` (camera, microphone, location and payment switched off), and HSTS on HTTPS.
