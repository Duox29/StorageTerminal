# PLAN

## Trạng thái hiện tại

Xem `git log` để biết lịch sử gần nhất. Các việc đã hoàn tất:

- Ctrl+Click trên panel: lấy 1 stack vào slot trống đầu tiên.
- Ctrl+Click trên inventory: cất stack, xử lý tại thời điểm press để không tranh chấp con trỏ.
- Vị trí panel được lưu khi drag kết thúc, có shutdown hook để không mất bản ghi debounce.
- Toggle `Send Toasts` chặn toàn bộ toast tại `ToastUtils`.
- Các tham số pacing (Chest Open Wait, Withdraw Delay, Withdraw/Tick, Sync Wait) đã thành setting.
- UI hiển thị theo `ACTIVE_CACHE` nên `Scan Range` có hiệu lực thật.

---

## 1. Tối ưu cache (chưa làm)

Hiện `ChestCache.refreshActiveCache` duyệt toàn bộ `GLOBAL_BUFFER` mỗi 10 tick, và mỗi entry phải
`CacheUtils.stringToPos()` — parse chuỗi thành `BlockPos` mỗi lần duyệt.

### Hướng 1 — Lưu buffer bằng `BlockPos` thay vì `String` (ưu tiên)
- Đổi `Map<String, Map<String,Integer>>` thành `Map<BlockPos, Map<String,Integer>>`.
- Parsing chỉ còn xảy ra một lần lúc load DB.
- Bỏ được phần lớn cấp phát object mỗi lần refresh. Không đổi hành vi.

### Hướng 2 — Index theo chunk (chỉ khi số chest thật sự lớn)
- Index buffer theo `(chunkX, chunkZ)`; khi refresh chỉ duyệt các chunk giao với `Scan Range`.
- Độ phức tạp gần O(1) thay vì O(N).
- Cần xử lý bảo trì khi chest bị xoá → chỉ làm nếu Hướng 1 không đủ.

### Hướng 3 — Chỉ refresh khi cần
- `ModuleManager` hiện refresh mỗi 10 tick bất kể có UI nào đang mở.
- Chỉ refresh khi có panel/screen hiển thị, hoặc khi người chơi đã di chuyển đủ xa (ví dụ > 1 block).

### Hướng 4 — Xoá chest ngoài range ngay
- `updateFromContainerMenu` / `updateAndSync` không xoá entry khi chest ra ngoài `Scan Range`,
  nên có thể trễ tới 10 tick. Gọi `ACTIVE_CACHE.remove(posKey)` ngay tại chỗ.

**Đề xuất:** làm Hướng 1 + 3 + 4 trước (sửa nhỏ, không đổi kiến trúc).

---

## 2. Review trùng lặp cross-file — đánh giá lại

Nguồn: một report đọc-only trên 37 file Java. Dưới đây là kết quả kiểm chứng lại từng mục.

### Đúng và nên làm

| Ưu tiên | Vị trí | Nội dung | Ghi chú |
| --- | --- | --- | --- |
| High | `AutoStash.closeContainerSafe` ↔ `StorageManager.closeSilent` | Phần đầu giống hệt nhau: kiểm tra `silentContainerId`, so `containerMenu.containerId`, gọi `closeContainerSilent()`, rồi reset `silentContainerId = -1` | **Duplication thật, nhưng không phải clone tuyệt đối**: `closeSilent` còn gọi `moveToNextTarget()`. Nên tách helper thuần `closeSilentIfOpen()` trong `InventoryUtils`, mỗi module tự xử lý phần state transition của mình. |
| Medium | `StoragePanel` ↔ `StorageScreen` | Cấu hình `EditBox` tìm kiếm giống nhau | Tách factory nhỏ. Rủi ro thấp. |
| Low | `BooleanSetting.save` ↔ `NumberSetting.save` | Hai bản `save()` giống hệt (đều `new JsonPrimitive(value)`) | Đưa `save()` vào base `Setting`. `load()` vẫn đặc thù theo kiểu nên giữ nguyên. |

### Đúng nhưng độ ưu tiên thấp hơn report

| Vị trí | Nội dung | Ghi chú |
| --- | --- | --- |
| `AutoStash` ↔ `StorageManager` (mở / chờ silent container) | Cùng dãy thao tác mở chest rồi chờ | Logic state machine khác nhau đáng kể (danh sách trạng thái khác, timeout khác, hướng xử lý khác). Gom lại sẽ phải thêm tham số state transition — lợi ích không rõ. **Để sau.** |
| `AutoStash` ↔ `StorageManager` (onDisable / onWorldChanged / reset) | Cùng hình dạng: đóng chest, reset state, clear queue | Phần giống nhau chủ yếu là 1 lời gọi `closeContainerSilent()` có guard. Tách helper sau khi làm mục High ở trên. |
| `StoragePanel.mouseReleased` ↔ `StorageScreen.mouseReleased` | Cùng forward event xuống `ItemGridView` | Đã có component grid dùng chung xử lý phần chính. Không đáng sửa. |
| `MixinGui` ↔ `MixinClientPacketListener` | Cùng đọc `isSilentMode()` | Chỉ là một predicate, hai hook làm hai việc khác nhau. Không đáng sửa. |

### Report SAI hoặc đã lỗi thời

1. **"ItemGridView.refreshFromCache() aggregates the global cache"** — sai. Trước đây đúng, nhưng
   commit `00e426d` đã đổi sang `getActiveCache()`. Hiện tại cả `ItemGridView` và
   `MixinRecipeBookComponent.getTotalInCache()` đều dùng active cache. **Report này lỗi thời.**

2. **"Exact duplicate container-ID validation and silent close sequence"** — không chính xác.
   `StorageManager.closeSilent` có thêm `moveToNextTarget()`. Phần chung chỉ khoảng 4 dòng.

3. **Report bỏ sót một chỗ trùng lặp thật:** `StorageManager.updateCache` và `StorageManager`
   (các chỗ khác dùng `ChestCache.getGlobalBuffer()`) vẫn tự xử lý map theo `String` key, trong khi
   hầu hết module khác đã dùng `BlockPos`. Đây là nguồn của phần khó chịu trong Hướng 1, nên
   gom luôn khi làm tối ưu cache.

### Kết luận về report

Report nhìn chung là hợp lý nhưng **chỉ một mục High là thực sự đáng làm ngay**, và câu "exact
duplicate" hơi quá đà. Nên ưu tiên: tối ưu cache (mục 1) → gom helper đóng silent container →
dọn nhỏ (search box, `Setting.save`).
