# Public Deployment

This project is designed to run as a single authoritative server instance on your laptop, with PostgreSQL already running locally.

## Recommended public access setup

If you do not have a domain, the simplest way to let people outside your LAN play is to keep the app running on your laptop and use a tunnel service such as Cloudflare Tunnel or ngrok.

Recommended flow:

1. Start PostgreSQL on your laptop.
2. Start the game with `./run.ps1 -SkipBuild` or `./run.ps1`.
3. Start a tunnel that forwards public traffic to `http://localhost:8080`.
4. Open the tunnel URL from a phone on mobile data or another network.

This works with just your laptop and a tunnel, even without a domain.

### One-command helper script

You can run the tunnel step with:

```powershell
./run-public.ps1
```

What it does:

1. Starts a `cloudflared` tunnel in the current window to `http://localhost:<port>`.
2. Prints the `https://...trycloudflare.com` URL to share.

The helper script defaults to Cloudflare protocol `auto`. You can force a protocol with `-Protocol http2` or `-Protocol quic`.

Start your app separately first, for example:

```powershell
./run.ps1 -SkipBuild -Port 8080
```

## Recommended setup

- Run the Spring Boot app on an internal port such as `8080`.
- Put nginx, Apache, Caddy, or a cloud load balancer in front of it.
- Terminate TLS at the proxy so browsers can use `https://` and `wss://`.
- Keep the game server state on one instance for now; do not scale out without adding shared state.

## Environment variables

- `SERVER_ADDRESS` - bind address for the app. Use `0.0.0.0` for public access.
- `SERVER_PORT` - local app port. Default is `8080`.
- `DB_URL` - PostgreSQL JDBC URL.
- `DB_USERNAME` - PostgreSQL user name.
- `DB_PASSWORD` - PostgreSQL password.

If you run PostgreSQL locally on the same laptop, the defaults already point to `localhost`.

## Reverse proxy requirements

- Forward all normal HTTP requests to the app.
- Forward `/ws` with WebSocket upgrade headers preserved.
- Preserve `Host`, `X-Forwarded-Proto`, and `X-Forwarded-For`.
- Serve the site over HTTPS so the browser uses WSS automatically.

## Tunnel notes

- Cloudflare Tunnel and ngrok both work well for quick sharing without a domain.
- The game client already builds WebSocket URLs from the page host, so the socket will follow the tunnel automatically.
- If a tunnel tool gives you an `https://` URL, the browser will use `wss://` for the game socket.

## Public access checklist

1. Start the app on your laptop with `./run.ps1 -SkipBuild`.
2. Confirm `http://localhost:8080/login.html` works locally.
3. Start a tunnel to `http://localhost:8080`.
4. Open the tunnel URL on a different network.
5. Create a room, join it, and confirm gameplay works.
6. Confirm the browser console shows no mixed-content or WebSocket upgrade errors.