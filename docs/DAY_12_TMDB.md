# Phone-owned TMDB artwork

Optional, default off. Each phone supplies its own TMDB API Read Access Token,
kept in platform secure storage. It is never sent to the TV, exposed by a GET,
put in URLs, or committed. No shared developer key is distributed.

The authenticated, pinned LAN API accepts empty POSTs to `/v1/provider/enable`
and `/v1/provider/disable`. Only the opt-in boolean persists privately on TV.
Unknown methods, query parameters and bodies are rejected. This is separate
from legacy TV-local provider settings: this feature never reads their secrets.

TMDB free use is for noncommercial projects subject to its terms and attribution;
commercial distribution requires reviewing licensing with TMDB. Artwork ownership
is not transferred. The phone credits include the approved TMDB logo and:
“This product uses the TMDB API but is not endorsed or certified by TMDB.”
Review current terms before release. No streaming subscriptions or logins change.

Primary references: [FAQ](https://developer.themoviedb.org/docs/faq),
[application authentication](https://developer.themoviedb.org/docs/authentication-application),
[images](https://developer.themoviedb.org/docs/image-basics),
[rate limits](https://developer.themoviedb.org/docs/rate-limiting).

Logo: Travis Bell / TMDB, approved blue-square asset (unmodified rasterization),
CC BY-SA 4.0: https://creativecommons.org/licenses/by-sa/4.0/ .
Source mirror: https://commons.wikimedia.org/wiki/File:Tmdb.new.logo.svg ;
official asset: https://www.themoviedb.org/assets/2/v4/logos/v2/blue_square_2-d537fb228cf3ded904ef09b136fe3fec72548ebc1fea3fbbd1ad9e36364db38b.svg .

## Refresh contract and phone demo

With remote access already paired, open TV artwork → Choose a movie backdrop.
Enable phone artwork, save your own read token in TMDB setup, search a movie,
then select it. The phone downloads through normal system-trusted HTTPS and
reuses the authenticated two-phase artwork uploader. Existing TV-local overrides
still win. Removing the phone token never changes TV/app sign-ins or pairing.

All routes require bearer auth over pinned LAN TLS; no query parameters or body:
- POST `/v1/provider/refresh/<package>` creates/coalesces the one pending job.
- POST the same path plus `/claim`, `/complete`, `/canceled` or `/error` uses
  `X-Job-Id`; completion is bound to the claiming phone and package.
- GET `/v1/provider/refresh` returns only apiVersion, jobId, package and state.
  Possible states: idle, phone_required, running, complete, canceled, error.
- Missing opt-in returns 409, unknown/non-visible package 404, stale/other-owner
  claim 409, revoked token 401. Disable cancels the job but retains artwork.

A foreground phone with a local key must claim a pending job. No phone/key leaves
phone_required; no scheduled worker or TV-side provider request is added. Claims
expire after 120 seconds, checked only on requests. Cancel/background stops phone
network work; interruption during commit may be uncertain, so check the TV/status
before retrying. No replay, polling, auto-retry or secret-returning GET exists.
Search is first page/20 choices, API replies 256 KiB, images 2 MiB/8 MP and w780;
401 prompts setup, 429 honors a bounded cooldown without automatic requests.

Host acceptance covers real LAN TLS auth/body/owner/revocation, coalescing/expiry,
missing-key/cancel, API header/CDN isolation, redirect and size rejection,
offline/401/429, secure token removal and synthetic secret scans. Live provider
results require the owner's token; no token or live provider call was used here.
