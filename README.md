# CleanHomeGUI

A lightweight Minecraft Paper plugin that adds a clean GUI-based home system with cooldowns, sounds, backups, and MySQL history.

## Features

- 3-home system
- `/home` GUI
- `/home <1-3>` teleport
- `/sethome <1-3>` set home
- `/delhome <1-3>` delete home
- Rename homes from GUI
- Action-bar teleport countdown
- Movement cancels teleport
- Configurable cooldown and delay
- Configurable GUI text
- Configurable messages
- YAML backups
- MySQL coordinate history
- OP-only restore/history commands
- Tab completion

## Commands

### Player Commands

```txt
/home
/home <1-3>
/sethome <1-3>
/delhome <1-3>
```

### Admin Commands

```txt
/home reload
/home backups <player>
/home history <player>
/home restore <player> <homeNumber> <backupNumber>
/home restoredb <player> <homeNumber> <historyId>
```

## Permissions

```yaml
cleanhomegui.reload:
  description: Allows /home reload
  default: op

cleanhomegui.backup:
  description: Allows backup, history, and restore commands
  default: op
```

## Config Example

```yaml
teleport-delay-seconds: 5
cooldown-seconds: 30

database:
  host: localhost
  port: 3306
  database: cleanhomegui
  username: root
  password: ""

gui:
  title: "&8Homes"
  size: 27
  home-name-format: "&9{name}"
  empty-home-name-format: "&cHome {number} &7(Empty)"
  delete-button-name: "&cDelete {name}"
  rename-instructions:
    - "&7Right-click to rename"
    - "&7Left-click to teleport"
  empty-lore:
    - "&7Click to set this home."
    - "&7Right-click to rename."
  delete-lore:
    - "&7Click to delete this home."

default-home-names:
  1: "Home 1"
  2: "Home 2"
  3: "Home 3"

homes: {}
```

## Messages

The plugin uses `messages.yml` for editable messages.

```yaml
invalid-home: "&cUse a number from 1 to 3."
sethome-usage: "&cUse /sethome <1-3>"
delhome-usage: "&cUse /delhome <1-3>"
home-set: "&aHome {home} has been set."
home-deleted: "&aHome {home} has been deleted."
home-not-set: "&cHome is not set."
already-teleporting: "&cYou are already teleporting."
cooldown: "&cWait &e{time}s &cbefore using /home again."
teleport-start: "&eTeleporting. Don't move!"
teleported: "&aTeleported to Home {home}."
teleport-cancelled: "&cTeleport cancelled because you moved."
actionbar-countdown: "&eTeleporting in &a{time}s"
rename-prompt: "&eType the new name for Home {home} in chat."
home-renamed: "&aHome {home} renamed to &e{name}&a."
no-permission: "&cYou do not have permission to use this."
reload-success: "&aCleanHomeGUI has been reloaded."
```

## MySQL Support

CleanHomeGUI logs home coordinate history into MySQL.

The database stores:

- Player UUID
- Player name
- Home number
- Action type
- Home name
- World
- X, Y, Z
- Yaw and pitch
- Time

Logged actions:

```txt
set
deleted
overwritten
restored
```

The table is created automatically:

```txt
home_history
```

The MySQL database must already exist before starting the server.

## Backup System

CleanHomeGUI also creates local YAML backups:

```txt
plugins/CleanHomeGUI/backups.yml
```

Backups are created when:

- A home is deleted
- A home is overwritten

View backups:

```txt
/home backups <player>
```

Restore from YAML backup:

```txt
/home restore <player> <homeNumber> <backupNumber>
```

Restore from MySQL history:

```txt
/home restoredb <player> <homeNumber> <historyId>
```

## Generated Files

```txt
plugins/CleanHomeGUI/
├── config.yml
├── messages.yml
├── backups.yml
```

## Sounds

The plugin uses Minecraft sound keys:

```txt
ui.button.click
block.note_block.pling
entity.ender_pearl.throw
entity.villager.no
```

## Version Support

Designed for Paper 1.21+.

Should work on 1.21.11 and below as long as the server supports the used API and sound keys.

## Author

Developed by Frwostella.
