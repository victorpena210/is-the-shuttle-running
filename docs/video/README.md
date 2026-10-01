# Is the Shuttle Running? — product walkthrough

This replaces the former-brand walkthrough in the supplied project. The new film is an **80-second, 1920 × 1080, 30 fps MP4**, encoded as H.264 / yuv420p with fast-start metadata for web playback. It is silent, with on-screen explanations, matching the original video's silent format.

The visuals use the current shuttle mark, charcoal `#17201c`, warm white `#f7f8f2`, and signal green `#c8f36b`. The name and `istheshuttlerunning.com` appear throughout. There is no former branding in the generated film.

## What the video shows

These are illustrated workflow screens, not a recording of a running production instance. Every scene is labeled as an illustrated walkthrough with sample data. Names, counts, hours, and organization details are fictional examples. The content follows the supplied schedule-upload, demand, driver-coverage, checkpoint, rider-status, and driving-record templates.

| Start | Scene |
| --- | --- |
| 00:00 | One shuttle day, three perspectives |
| 00:08 | Import the Dayforce schedule |
| 00:18 | Review transportation demand |
| 00:27 | Review coverage and a manager override |
| 00:37 | Confirm a driver checkpoint |
| 00:48 | Check rider status, pause/resume, and confirm a ride |
| 01:00 | Compare scheduled and actual driving |
| 01:10 | New-brand closing and demo invitation |

## Website integration

- Video: `src/main/resources/static/video/istheshuttlerunning-manager-driver-rider-demo.mp4`
- Poster: `src/main/resources/static/images/istheshuttlerunning-demo-poster.png`
- Production player: `src/main/resources/templates/public/landing.html`
- Offline preview: `docs/preview.html`
- Transcript styles: `src/main/resources/static/css/pages/landing.css`
- Renderer: `docs/video/render.mjs`

Both players reference the new assets. The former-name video note is replaced by a clear description of the new film. The expandable transcript covers each scene for visitors who prefer text. The player retains explicit controls, inline playback, lazy media loading, and a download fallback.

The old MP4 is omitted from this updated package. The previous video remains available in the original uploaded ZIP. Historical image assets and internal Java identifiers are unchanged.

## Use the updated project

Copy this project's contents into your existing checkout, preserving its `.git` directory and local environment configuration. Review your changes with `git status`, then commit and deploy through your existing workflow. This deliverable does not publish the site.

After deployment, play the homepage walkthrough and confirm the new poster and video load. Opening `docs/preview.html` from the extracted project also provides a local media preview.

## Rebuild the video

The renderer uses Node.js 20 or newer, `@napi-rs/canvas`, and FFmpeg with the libx264 encoder. It has no network requests, credentials, database access, or external application requirements. Nimbus Sans / Nimbus Mono PS are used when available, with Arial and system fallbacks; different installed fonts may change text metrics.

Install `@napi-rs/canvas` in `docs/video` and make `ffmpeg` available on your PATH. From the project root:

```bash
node docs/video/render.mjs --preview
node docs/video/render.mjs
```

The first command creates preview frames in `target/video-preview` and refreshes the poster. The second also writes the production MP4. Set `VIDEO_PREVIEW` to change the preview directory or `VIDEO_OUTPUT` to change the MP4 directory. The production poster is always updated in the image directory. No JavaScript dependencies are required by the deployed Spring application to play the completed video.

## Verification

The exported video was checked for duration, resolution, frame count, encoding, and full decoding without errors. Representative frames covering every scene and state change were inspected visually. Video/poster paths and transcript integration were checked in both templates. A byte comparison verified that Java sources, application configuration, database migrations, and unrelated assets were not changed.

The Spring application was not started or deployed, and live browser playback was not tested in this environment. The changes are limited to presentation assets, the landing player, and supporting documentation.
