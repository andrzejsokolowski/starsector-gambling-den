# Gambling Den

These instructions apply to this repository, including when it is opened without the parent mod workspace.
For requested gameplay changes and bug fixes, complete the release workflow unless the user requests otherwise.
Questions, investigations, and instruction-only changes do not require a new version or tag.
Respect tool approval decisions. These instructions do not change sandbox permissions.

## Required release workflow

1. Preserve unrelated changes from the user and other agents.
2. Implement the requested change and update the version in mod_info.json and gambling_den.version.
3. Run ./gradlew releaseZip to build, test, and package GamblingDen.zip.
4. Make sure that the ZIP contains the updated metadata, current JAR, artwork, and licenses.
5. Commit the completed change on main with the existing personal Git identity.
6. Tag the commit v<version> and push the commit and tag to the existing personal GitHub origin.
7. Create a draft GitHub Release for that exact tag.
8. Attach the tested GamblingDen.zip to the draft.
9. Publish the release after the upload succeeds.
10. Mark a new stable release as latest unless the user requests otherwise.
11. Make sure that the public version file and the downloaded ZIP report the tagged version.
12. Compare the downloaded ZIP's SHA-256 hash with the tested local ZIP.
13. Give the user the release link and local ZIP location.

Every new version tag created or pushed by an agent requires a matching published release with the ZIP attached.
Do not stop after pushing a tag. A tag is not a release download.
If a matching draft exists, inspect and complete it instead of creating a duplicate.
If publication fails, report the blocker and the incomplete release.
Do not overwrite published assets, replace tags, or mark older releases as latest without a specific request.
This is an agent workflow rule, not a server-side tag trigger.

## Update metadata

Keep mod_info.json, gambling_den.version, and data/config/version/version_files.csv consistent.
Use forum topic 35993 in both metadata files.
Use this update URL in updateCheckURL and masterVersionFile:

https://raw.githubusercontent.com/andrzejsokolowski/starsector-gambling-den/main/gambling_den.version

Use this directDownloadURL and the exact asset name GamblingDen.zip:

https://github.com/andrzejsokolowski/starsector-gambling-den/releases/latest/download/GamblingDen.zip

## Release notes: changelog only

Write only the changelog since the previous published GitHub Release, not since an unpublished tag or local test build.
Read the intervening changes. Include only additions, behavior changes, fixes, and removals that matter to players.
Use concise entries. Do not copy historical README sections or list unchanged features.

Do not add dependency reminders, installation guides, forum links, build narration, or test-status statements to release notes.
Do not include "LunaLib is required", "the full automated test suite passed", checksums, or other non-changelog descriptions.
If a dependency or compatibility requirement changed, describe the change itself.
Keep any useful verification details in the chat handoff instead.
Do not rewrite older release notes unless requested.

## Account and packaging safety

Use GitHub account andrzejsokolowski and the existing personal Git identity with odiihinia@gmail.com.
Check the authenticated account before publishing. Use gh only when it authenticates as andrzejsokolowski.
Use Git Credential Manager or the GitHub API with that personal account when needed.
Do not print credentials, save them in files, or change the user's global authentication settings.
Do not force-push or discard unrelated work.

Update the usual GamblingDen.zip instead of creating extra version-named ZIP copies.
Do not install the mod into the live game folder. The user installs it through a mod manager.
Do not post release notes to Discord or the forum unless the user asks.
