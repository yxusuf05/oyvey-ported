# Deploying PRISMA

The server serves the built client **and** the WebSocket on a single HTTP port. That means
one container, one URL, no port forwarding and nothing for your friend to install: they
open the link, type the six-character room code, and they are in.

## Locally (LAN)

```bash
cd game
pnpm install
pnpm run build
pnpm start            # http://localhost:8787
```

Anyone on the same network can join at `http://<your-lan-ip>:8787`. Find your address with
`ip addr` (Linux), `ipconfig` (Windows) or `ifconfig` (macOS).

For development with hot reload, run the client and server side by side instead:

```bash
pnpm run dev          # client on :5173, server on :8787, socket proxied
```

## Docker

```bash
cd game
docker compose up --build
```

Or without compose:

```bash
docker build -t prisma game
docker run -p 8787:8787 prisma
```

## Over the internet

Any host that can run a container and keep a WebSocket open works. Three that need almost
no configuration:

### Fly.io

```bash
cd game
fly launch --no-deploy          # accept the detected Dockerfile
fly deploy
```

Add to the generated `fly.toml` so the container listens where Fly expects:

```toml
[env]
  PORT = "8080"
  HOST = "0.0.0.0"

[http_service]
  internal_port = 8080
  force_https = true
  auto_stop_machines = "suspend"
  min_machines_running = 0
```

`auto_stop_machines` is worth keeping: the machine sleeps when nobody is playing and wakes
on the next request, so an idle evening costs nothing. The first connection after a sleep
takes a second or two.

### Persistent progress

Credits and unlocks live in a SQLite file under `DATA_DIR` (default `./data`). **Give that
directory a volume.** Without one the file lives inside the container, and every restart or
redeploy silently resets everyone's progress — the game keeps working, which is exactly why
it takes a while to notice. `docker-compose.yml` already mounts one; on a platform that
gives you a disk, mount it and point `DATA_DIR` at it.

`PRISMA_MEMORY_DB=1` keeps everything in memory instead. That is what the browser tests use;
there is no reason to set it in production.

### Railway / Render

Point the service at this repository with the root directory set to `game`. Both detect the
Dockerfile automatically. Set `PORT` to whatever the platform injects (Railway and Render
both provide it) — the server reads it from the environment.

### A VPS you already own

```bash
git clone <this repo>
cd <repo>/game
docker compose up -d --build
```

Then put any reverse proxy in front of it. The only requirement is that it forwards the
WebSocket upgrade on `/ws`. For Caddy that is the default; for nginx:

```nginx
location / {
    proxy_pass http://127.0.0.1:8787;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host $host;
    proxy_read_timeout 3600s;   # rooms are long-lived; do not time them out mid-run
}
```

`proxy_read_timeout` matters. The default of 60 seconds will drop players mid-run, and the
symptom — everyone disconnecting after exactly a minute — looks like a game bug.

## Configuration

| Variable | Default | Meaning |
| --- | --- | --- |
| `PORT` | `8787` | HTTP and WebSocket port |
| `HOST` | `0.0.0.0` | Bind address |
| `CLIENT_DIST` | `../../client/dist` | Where to serve static files from |

## Operating notes

- **Health check:** `GET /healthz` returns `{"ok":true,"rooms":N,"players":N}`.
- **State is in memory.** Rooms are ephemeral by design and are collected after 30 minutes
  idle. Restarting the server ends any run in progress; there is nothing to back up yet.
- **Reconnection:** a dropped player's body stays in the world for 90 seconds and they
  resume it on reconnect. Losing a fifteen-minute run to a wifi hiccup is the most annoying
  failure in this genre, so it is handled rather than left to chance.
- **Capacity:** four players per room, and the simulation is a fraction of a millisecond per
  tick per room. A single small instance handles far more concurrent rooms than you are
  likely to need. The server logs a warning if any tick exceeds 8 ms.
- **Scaling out is not supported.** Room state lives in the process, so multiple replicas
  would each hold different rooms and a code created on one would not be found on another.
  Run a single instance.
