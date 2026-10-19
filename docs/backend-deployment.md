# Automated backend deployment

## Cloudflare Tunnel and Access

The production API is published through a remotely managed Cloudflare Tunnel and protected by Cloudflare Access. The origin port is bound to `127.0.0.1`, so it is not directly reachable from the LAN or Internet.

1. In **Cloudflare Zero Trust → Networks → Tunnels**, create a remotely managed tunnel and copy its token.
2. Add a published application route such as `mampfi.example.com` with service URL `http://mampfi:8080`. The service name works because `cloudflared` and Mampfi share the Compose network.
3. In **Access controls → Applications**, create a self-hosted application for the same hostname.
4. In **Access controls → Service credentials → Service tokens**, create a separate token for each phone. Save each Client ID and Client Secret immediately; Cloudflare only shows the secret at creation time.
5. Add a **Service Auth** policy to the application that includes those service tokens. Do not add a public Bypass policy.
6. On the homeserver, create the untracked `.env` from `.env.example` and set `CLOUDFLARE_TUNNEL_TOKEN`.
7. Start the stack and verify both the origin and Access boundary:

   ```bash
   docker compose up -d
   curl http://127.0.0.1:8080/api/mahlzeiten
   curl -i https://mampfi.example.com/api/mahlzeiten
   curl -i \
     -H "CF-Access-Client-Id: PHONE_CLIENT_ID" \
     -H "CF-Access-Client-Secret: PHONE_CLIENT_SECRET" \
     https://mampfi.example.com/api/mahlzeiten
   ```

   The unauthenticated public request must be denied; the request with an allowed token must return `200`.

Enter the hostname and one phone-specific credential pair in Mampfi on each phone. To rotate a token, create or rotate it in Cloudflare, enter the new pair in Mampfi, test the connection, save, and then revoke the old token. A lost phone can be blocked independently by deleting only its token.

For migration, keep the old Tailscale path available until the tunnel is verified and both phones have installed and configured the new app. Afterwards, remove the Tailscale exposure.

The backend image is published to `ghcr.io/patertuck/mampfi-backend` when relevant files are pushed to `master`. The image is tagged both as `latest` and as `sha-<full-commit-sha>`.

## Build platform

The workflow reads the optional GitHub Actions repository variable `DOCKER_BUILD_PLATFORM`:

| Value | Result |
| --- | --- |
| Unset or `linux/amd64` | Native AMD64 build without QEMU |
| `linux/arm64` | ARM64 build using QEMU |
| `linux/amd64,linux/arm64` | Multi-platform image using QEMU |

Configure it under **Settings → Secrets and variables → Actions → Variables**. An invalid Docker platform value makes the image build fail and leaves the existing `latest` image unchanged.

## First deployment

1. Push the deployment workflow and let **Backend image** finish successfully.
2. Open the new `mampfi-backend` package on GitHub and change its visibility to public.
3. On the homeserver, update the checkout and start the stack:

   ```bash
   git pull --ff-only
   docker compose pull
   docker compose up -d
   docker compose ps
   curl http://localhost:8080/api/mahlzeiten
   ```

Watchtower checks for a new image every five minutes. It only updates containers carrying the explicit enable label. Before replacing Mampfi, its lifecycle hook runs `/app/bin/server backup-before-update` inside the running container.

Backups are consistent SQLite snapshots stored in `data/backups/`. The newest ten `pre-update` snapshots are retained. Migration snapshots are not removed by that retention policy. Watchtower lifecycle-hook failures are visible in `docker compose logs watchtower`; because Watchtower does not provide transactional rollback, every schema migration also creates and verifies its own mandatory snapshot before changing the schema.

## Verification

```bash
docker compose logs --tail=100 watchtower
docker compose logs --tail=100 mampfi
docker inspect mampfi-mampfi-1 --format '{{.Config.Image}} {{index .Config.Labels "org.opencontainers.image.revision"}}'
curl http://localhost:8080/api/mahlzeiten
```

The Compose project or container name can differ when the checkout directory has another name.

## Rollback

For a code-only failure, run the previous immutable image without changing the database:

```bash
docker compose stop watchtower
MAMPFI_IMAGE_TAG=sha-PREVIOUS_FULL_COMMIT_SHA docker compose up -d --force-recreate mampfi
curl http://localhost:8080/api/mahlzeiten
```

If the failed version migrated the schema, stop both services and restore the snapshot created immediately before that deployment:

```bash
docker compose stop watchtower mampfi
mv data/mampfi.db "data/mampfi.failed-$(date +%Y%m%d-%H%M%S).db"
cp data/backups/SELECTED_BACKUP.db data/mampfi.db
MAMPFI_IMAGE_TAG=sha-PREVIOUS_FULL_COMMIT_SHA docker compose up -d mampfi
curl http://localhost:8080/api/mahlzeiten
```

After resolving the problem, return to automatic updates:

```bash
docker compose down
docker compose up -d
```

The deployment snapshots cover SQLite rollback. Continue backing up the complete `data/` directory separately so uploaded pictures are also protected.

Watchtower updates the labeled backend and `cloudflared` images. Changes to `compose.yaml`, ports, volumes, environment variables, or Watchtower itself still require a manual `git pull --ff-only` and `docker compose up -d` on the homeserver.
