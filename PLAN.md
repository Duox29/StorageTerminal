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

## 1. Tối ưu cache

Hiện `ChestCache.refreshActiveCache` duyệt toàn bộ `GLOBAL_BUFFER` mỗi 10 tick, và mỗi entry phải
`CacheUtils.stringToPos()` — parse chuỗi thành `BlockPos` mỗi lần duyệt.

### Hướng 1 — Lưu buffer bằng `BlockPos` thay vì `String` — **ĐÃ LÀM**
- `GLOBAL_BUFFER` / `ACTIVE_CACHE` giờ là `Map<BlockPos, Map<String,Integer>>`.
- Parsing chỉ xảy ra một lần lúc load DB (`CacheUtils.tryStringToPos`, bỏ qua row hỏng).
- `ChestScoringEngine`, `StashPlanner`, `StorageManager.calculateRetrievalPlan` bỏ hẳn parse chuỗi
  mỗi vòng lặp.
- `StorageManager.updateCache` copy trước khi sửa (R1) và `updateAndSync` snapshot dữ liệu.
- `posToString` chỉ còn ở biên giới DB; schema SQLite không đổi.

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

**Đề xuất ban đầu (đã sửa, xem mục 1.1):** làm Hướng 1 + 3 + 4.

---

## 1.1 Phân tích rủi ro của các hướng tối ưu

Kết quả rà soát lại toàn bộ đường đọc/ghi cache. Một số hướng tưởng là "sửa nhỏ" nhưng thực chất
thay đổi hành vi và có thể gây lỗi.

### Rủi ro thật

#### R1. Chia sẻ object giữa `GLOBAL_BUFFER` và `ACTIVE_CACHE`

`StorageManager.updateCache` (line 665-686) lấy `chestContents` từ `GLOBAL_BUFFER`, **sửa mutation
tại chỗ** (`remove`/`put`), rồi truyền chính object đó vào `ChestCache.updateAndSync`, nơi lại
`GLOBAL_BUFFER.put(posKey, newData)` bằng cùng reference. `refreshActiveCache` cũng đưa cùng object
vào `ACTIVE_CACHE`. Kết quả: hai map chia sẻ reference, sửa một bên là bên kia đổi theo.

- Đây là lỗi **đã có sẵn**, không do refactor sinh ra.
- Khi đổi key type rất dễ viết `new HashMap<>(contents)` ở nhánh này mà quên nhánh kia → hai nguồn
  dữ liệu lệch nhau.
- **Bắt buộc:** snapshot (`Map.copyOf`) tại ranh giới `updateAndSync`; `updateCache` tạo bản sao mới
  thay vì sửa in-place.

#### R2. `ConcurrentModificationException` tiềm tàng

`updateCache` được gọi trong lúc đang rút đồ và sửa map trước khi `refreshActiveCache` duyệt
`GLOBAL_BUFFER`. Hiện không lỗi vì `updateAndSync` **thay thế** map trước khi duyệt. Nếu refactor
làm `refreshActiveCache` duyệt đúng collection đang bị sửa → CME ngay trên main thread.

#### R3. `BlockPos` là `Vec3i` — `equals`/`hashCode` không theo kiểu

`Vec3i.equals` so tọa độ, không so class. `BlockPos` và `Vec3i` cùng tọa độ sẽ `equals` nhau. Hiện
mọi nguồn đều là `BlockPos` nên an toàn, nhưng phải chắc không có `Vec3i` lọt vào map, và key phải
bất biến (`pos.immutable()`) vì `BlockPos.MutableBlockPos` là mutable và `hashCode` thay đổi.

#### R4. Hướng 4 (`ACTIVE_CACHE.remove` ngay) có thể gỡ nhầm chest còn trong tầm

`updateFromContainerMenu` được gọi từ `ManualContainerTracker` khi người chơi **mở chest thủ công**.
Nếu vị trí người chơi sai lệch (đổi chiều, teleport trong lúc sync) → chest bị gỡ khỏi active dù
vẫn trong tầm. Không nên thêm `remove` vào đường manual; chỉ làm ở đường destroy/replace.

#### R5. Hướng 3 ("chỉ refresh khi di chuyển > 1 block") có thể **bỏ sót cập nhật**

Người chơi đứng yên nhưng dữ liệu đổi (AutoStash Build cache, người khác đặt chest trong tầm).
So sánh chỉ theo vị trí người chơi sẽ **không refresh** → hiển thị sai tới khi đi lại.

- Điều kiện phải là: *(người chơi di chuyển)* **hoặc** *(GLOBAL_BUFFER vừa thay đổi)*.
- `forceRefreshActiveCache()` được gọi trực tiếp từ nhiều chỗ (`StoragePanel` ctor,
  `StorageScreen` ctor, `ModuleManager` tick, `AutoStash`). Nếu gate đặt trong `refreshActiveCache`
  thì các chỗ này **hết hiệu lực**. Cần tách `refreshActiveCache` (unconditional) và
  `refreshActiveCacheIfNeeded` (gated).

#### R6. Hướng 1 đổi public API — vỡ 6 điểm gọi

Các chỗ đang dùng key `String`:
- `StorageManager` line 302 (`getGlobalBuffer().size()`), 326, 669
- `StorageManager` line 342-343, 351 (sort/parse `stringToPos`)
- `ChestScoringEngine` line 46 (loop `Map.Entry<String, ...>`)
- `ItemGridView` line 70
- `MixinRecipeBookComponent` line 195
- `ManualContainerTracker` (gián tiếp qua `updateFromContainerMenu`)

Phải sửa **đồng bộ**, rất dễ sót một chỗ.

### Rủi ro thấp (chỉ khi làm ẩu)

| # | Rủi ro | Ghi chú |
| --- | --- | --- |
| R7 | `CacheDatabase.upsertChest` vẫn nhận `String` | `persist` phải `posToString` tại biên giới DB; không đổi schema. Đổi luôn sang `BlockPos` là viết lại cả layer DB. |
| R8 | `StorageManager.updateCache` tra bằng chuỗi | line 668-671, phải đổi cùng lúc với Hướng 1. |
| R9 | `ChestScoringEngine` chọn chest theo score | Khi đổi key phải giữ nguyên thứ tự score, không đổi kết quả chọn chest. |
| R10 | `CacheUtils.stringToPos` thành dead code | Trước khi xoá, kiểm tra layer DB và recipe mixin. |

### Thứ tự triển khai an toàn

1. **Chỉ Hướng 1**, tách biệt hoàn toàn:
   - Thêm map `BlockPos` song song, giữ nguyên API `String`.
   - So khớp `ACTIVE_CACHE` cũ/mới (log hoặc `runClient`) cho tới khi trùng hoàn toàn.
   - Chỉ khi khớp mới xoá API cũ.
2. **Đánh giá lại Hướng 3 và 4 riêng**, sau khi Hướng 1 đã ổn định.
   - Hướng 3 cần sửa điều kiện gate như R5; phải tách hàm gated/unconditional.
   - Hướng 4 không áp dụng cho đường manual (R4).
3. Hướng 2 chỉ cân nhắc nếu cache thực tế vượt vài nghìn chest.

**Sửa lại đề xuất:** không làm Hướng 1+3+4 cùng lúc. Hướng 3 và 4 là hai chỗ dễ gây bug nhất vì
chúng thay đổi **hành vi**, không chỉ cấu trúc dữ liệu.

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

---

## 3. Thứ tự ưu tiên chung

1. ~~**Hướng 1 tối ưu cache**~~ — **XONG**.
2. ~~**Helper đóng silent container**~~ — **XONG**: `InventoryUtils.closeSilentIfOpen`.
3. ~~**Dọn nhỏ:** factory `EditBox`, `Setting.save` lên base class~~ — **XONG**.
4. **Hướng 3 / Hướng 4** — đánh giá lại, cần test tay.
5. Hướng 2 / gom silent-container lifecycle — chỉ khi thực tế cần.
