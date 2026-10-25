# Security policy

OpenRemoTV drives a TV through ADB, so security reports matter to us. Thank you for taking the time.

## Reporting a vulnerability

Please report it privately, not in a public issue:

- Use GitHub's **Report a vulnerability** button on the repository's Security tab
  (private vulnerability reporting), or
- open a minimal public issue that only asks for a private contact, with no details.

Include what you found, how to reproduce it, and the app and TV versions. You'll get an answer within 7 days.
Fixes ship in a new release, credited to you unless you'd rather not.

## Supported versions

Only the latest release gets security fixes.

## How OpenRemoTV is built to be safe

- **ADB stays the owner's choice.** The app uses ADB over Wi-Fi (port 5555), which the TV owner turns on and approves
  once per phone. The app's ADB key never leaves the phone's private storage, and Android backup is off for it.
- **The TV helper answers one phone.** For instant keys the app starts a small helper on the TV (port 8766). It
  closes any connection that doesn't come from the phone that started it, and it signs in with an HMAC-SHA256
  challenge, so its secret never crosses the network. The secret is handed over in a file only the shell user can
  read, and the helper deletes it after reading.
- **Disable, never uninstall.** Sweeping turns apps off for the current user (`pm disable-user`). A backup comes
  first, apps the TV needs can't be switched off, and Undo restores the first backup.
- **No accounts, no analytics.** See [PRIVACY.md](PRIVACY.md).

When you're done sweeping, turn network debugging off again on the TV. ADB's port 5555 is not encrypted.
