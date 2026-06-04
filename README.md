# CleanHomeGUI

CleanHomeGUI is a lightweight and customizable GUI-based home system for Paper servers.  
It provides a clean `/home` experience with teleport countdowns, sounds, backups, and MySQL history — without unnecessary clutter.

---

## ✨ Features

- ✅ `/home` GUI system (3 homes)
- ✅ `/home <1-3>` teleport support
- ✅ `/sethome` and `/delhome`
- ✅ Rename homes via right-click in GUI
- ✅ Action-bar teleport countdown
- ✅ Cancel teleport when player moves
- ✅ Configurable cooldown system
- ✅ Clean GUI with dynamic bed colors
- ✅ Delete buttons under each home
- ✅ Custom sounds (UI click, pling, ender pearl)
- ✅ Fully customizable messages (`messages.yml`)
- ✅ YAML backup system (`backups.yml`)
- ✅ MySQL coordinate history system
- ✅ OP-only backup & restore commands
- ✅ Tab completion for all commands
- ✅ Reload command (`/home reload`)
- ✅ Lightweight & optimized
- ✅ Supports Paper 1.21+

---

## 📦 Commands

| Command | Description |
|--------|------------|
| `/home` | Open home GUI |
| `/home <1-3>` | Teleport to home |
| `/sethome <1-3>` | Set a home |
| `/delhome <1-3>` | Delete a home |
| `/home reload` | Reload plugin |
| `/home backups <player>` | View YAML backups |
| `/home history <player>` | View MySQL history |
| `/home restore <player> <home> <backup>` | Restore YAML backup |
| `/home restoredb <player> <home> <id>` | Restore from MySQL |

---

## 🔐 Permissions

| Permission | Description | Default |
|-----------|------------|---------|
| `cleanhomegui.reload` | Allows `/home reload` | OP |
| `cleanhomegui.backup` | Allows backup & restore commands | OP |

---

## 🗄️ MySQL History

Table created automatically:
home_history

Tracks:
set, deleted, overwritten, restored

---

## 💾 Backup System

CleanHomeGUI automatically saves backups in:
plugins/CleanHomeGUI/backups.yml

Backups are created when:
- A home is deleted
- A home is overwritten

---

## ⚙️ Configuration

config.yml → GUI, cooldowns, MySQL, behavior  
messages.yml → all messages  
backups.yml → stored coordinates  

⚠️ If you update the plugin:  
Delete the plugin folder once to regenerate configs.

---

## 🚀 Notes

- MySQL database must exist before startup  
- Plugin auto-creates required tables  
- Works even if MySQL fails (YAML backup fallback)  
- Designed to be simple, fast, and reliable  
- Easy setup
---
