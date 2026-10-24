# AstraxTeam (AstraxBox)

Plugin team cho Paper/Spigot 1.20.x (Java 17). Soft-depend: PlaceholderAPI.

## Build
```
mvn clean package
```
File jar: `target/AstraxTeam.jar` → bỏ vào `plugins/`. Sau lần chạy đầu sẽ có `config.yml` và `messages.yml`.

## Admin setup (làm một lần)
1. Đấu trường: đứng ở điểm spawn của team A → `/ateamadmin arena set1`, điểm spawn team B → `/ateamadmin arena set2`.
2. Khu vực claim: đứng góc 1 → `/ateamadmin zone pos1`, góc 2 → `/ateamadmin zone pos2`, rồi `/ateamadmin zone create <tên>`.
   (Zone tính theo X/Z, bao trùm toàn bộ chiều cao.)
3. Chỉnh `config.yml` / `messages.yml` rồi `/ateamadmin reload`.

## Lệnh người chơi (`/ateam`, alias `/t`)
create, disband, rename, invite, accept, deny, kick, leave, promote, demote, transfer, info, list, pvp, chat,
claim, unclaim, zones, war declare|accept|deny|status, top [points|kills|wins], gui. `/ateam` không tham số mở menu GUI. `/tc` = bật/tắt chat team.

## Lệnh admin (`/ateamadmin`, quyền `astraxteam.admin`)
reload · arena set1|set2|info · zone pos1|pos2|create|delete|release|list · disband <team> · setpoints <team> <n> · war stop

## Quyền
- `astraxteam.use` (mặc định true) · `astraxteam.admin` (op) · `astraxteam.bypass` (op, bỏ qua bảo vệ claim)

## Placeholder (PlaceholderAPI)
`%astraxteam_name%` `_role` `_leader` `_members` `_maxmembers` `_online` `_points` `_kills` `_deaths` `_kdr`
`_wins` `_losses` `_rank` `_pvp` `_zones` · `%astraxteam_top_<n>_name%` `%astraxteam_top_<n>_value%`

## API
```java
AstraxTeamAPI api = AstraxTeamAPI.get();
api.hasTeam(uuid); api.areTeammates(a, b); api.getTeamName(uuid); api.getRole(uuid);
api.getPoints(uuid); api.addPoints(uuid, 10); api.getRank(uuid); api.getTop("POINTS", 5);
api.isInWar(uuid); api.getZoneOwnerAt(location);
```
