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
