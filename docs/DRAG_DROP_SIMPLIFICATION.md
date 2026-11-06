# Plan: ultra simplify drag-drop giữa panel và inventory

## Kết quả cần đạt

- Người chơi có thể bắt đầu drag tiếp ngay sau khi thả chuột, kể cả khi lệnh trước đang mở chest, chuyển đồ hoặc khôi phục màn hình. UI nhận lệnh ngay; thao tác với server vẫn chạy lần lượt để không tranh `containerMenu`.
- Mỗi lần thả hợp lệ có phản hồi ngay trên panel (số lệnh đang chờ hoặc trạng thái queued); kết quả chuyển đồ đến sau. Không khóa input theo trạng thái worker hoặc timeout khôi phục màn hình.
- Giữ click, shift-click, quick-craft và kéo giữa các slot vanilla khi không thả lên panel. Panel vẫn hoạt động khi màn hình chest cũ đang hiển thị nhưng `containerMenu` đã chuyển sang silent menu.
- Panel → inventory: thả lên slot trống thì ưu tiên slot đó; thả lên slot có item thì tìm slot trống hợp lệ. Inventory → panel: chỉ stash stack nguồn, kể cả hotbar. Không làm rơi item ra thế giới khi thả lên overlay.
- Giảm ít nhất một nửa số dòng thêm bởi commit `cfe2e620` (mốc: 1.067 dòng thêm), đo bằng `git diff cfe2e620^ --numstat`. Độ đúng và khả năng drag liên tục là điều kiện bắt buộc, nên không cắt guard chỉ để đạt số dòng.

## Vấn đề của cấu trúc hiện tại

- `StorageDragController` giữ trạng thái pointer, snapshot stack, vanilla pickup, slot đích, render và quyết định chuyển đồ trong cùng một lớp.
- `StorageTransferCoordinator` có hàng đợi FIFO nhưng còn kiêm giữ màn hình cũ, timeout khôi phục và điều phối hai module. `hasPendingTransfers()` vừa biểu thị queue, vừa biểu thị trạng thái màn hình; mixin dùng nó để quyết định có nhận input trên màn hình stale hay không.
- `StorageManager` và `AutoStash` giữ thêm nhiều trường `targeted*` song song với command trong coordinator. `StorageManager.deferredRetrieval` là một hàng chờ thứ hai.
- Khi silent menu đang mở, click vanilla không thể xử lý màn hình chest cũ. Vì vậy cần nhận drag ảo trên màn hình stale, nhưng không nên đổi thời điểm xử lý click thông thường của vanilla.

## Thiết kế đích

1. **Input chỉ tạo ý định.** Một gesture gồm nguồn, snapshot item, vị trí nhấn và ngưỡng kéo. Khi mouse-up hợp lệ, tạo `TransferIntent(direction, itemKey, maxCount, sourceSlot/preferredSlot, sourceSnapshot, screenSessionId)` và thêm vào một FIFO ngay trong callback. Xóa trạng thái gesture ngay sau đó. Không kiểm tra `StorageManager.isRetrieving()`, `AutoStash.isEnabled()` hay queue đang chạy để từ chối gesture mới.
2. **Một worker thực thi.** Worker chỉ lấy intent đầu hàng khi không module nào đang dùng silent menu và không còn cursor stack vanilla. Nó gọi API có sẵn của `StorageManager` để request item hoặc `AutoStash` để stash một slot. Worker không chứa thuật toán chọn chest hay click chuyển item.
3. **Một nguồn dữ liệu cho mỗi lệnh.** Intent giữ yêu cầu bất biến; module đang chạy giữ tiến độ chuyển thực tế. Bỏ các snapshot, count và trạng thái “đang đợi” bị lặp giữa controller, coordinator và module. Kết quả hoàn thành/hủy trả về worker bằng một trạng thái rõ ràng, thay vì suy đoán từ nhiều cờ.
4. **Một phiên màn hình cho cả đợt.** Chụp màn hình/container thủ công một lần khi worker bắt đầu đợt. Chỉ khôi phục khi queue trống, worker idle và không có gesture đang giữ chuột. Trong lúc silent menu hoặc khôi phục màn hình, overlay vẫn nhận gesture mới vào FIFO; việc thực thi chờ menu an toàn, việc nhập không chờ.

Luồng: `mouse-up → enqueue ngay → FIFO → requestItem(...) / stashSlot(...) → cập nhật cache → lệnh kế tiếp → khôi phục màn hình khi idle`.

## Quy tắc khi nhiều drag đến liên tiếp

- **Hai lệnh panel → cùng slot:** giữ cả hai theo thứ tự. Khi bắt đầu từng lệnh, chọn lại slot dựa trên inventory hiện tại; nếu slot ưu tiên đã có item thì tìm slot trống khác. Giới hạn số lượng theo chỗ trống và cache lúc thực thi, không dựa hoàn toàn vào preview lúc thả chuột.
- **Hai lệnh inventory → cùng stack:** không chuyển hai lần một snapshot. Gộp intent stash trùng nguồn và cùng snapshot, hoặc cho lệnh sau kết thúc `no-op` khi lệnh trước đã lấy stack. Không stash item khác mới xuất hiện ở slot đó.
- **Stack nguồn đổi trước khi tới lượt:** hủy đúng intent đó, báo ngắn gọn và tiếp tục FIFO. Không hủy cả đợt.
- **Không còn chỗ hoặc item trong cache:** hoàn tất phần có thể chuyển, báo phần còn thiếu, rồi chạy lệnh tiếp theo.
- **Đóng UI, đổi world/dimension hoặc tắt module:** hủy intent thuộc phiên cũ; dọn cursor và silent menu an toàn. Không để lệnh cũ chạy ở phiên mới.

## Các bước triển khai

1. **Chốt hành vi hiện tại bằng kịch bản kiểm chứng.** Ghi nhận click vanilla, panel click, kéo hai chiều, slot trống/đã có item, partial stack, chest/shulker, màn hình stale, đóng UI. Đây là mốc để tránh đổi hành vi không chủ ý.
2. **Thu gọn API module.** Cho request item nhận tùy chọn slot đích; cho AutoStash/Dump nhận tùy chọn một slot nguồn. Cả hai đi qua planner và state machine đang có. Gộp code chuyển partial/full stack hiện có; không tạo state machine chuyển đồ thứ ba.
3. **Đổi coordinator thành FIFO nhỏ.** Coordinator chỉ nhận intent, quản lý một active job, chờ module rảnh và quản lý một `ScreenSession`. Bỏ `deferredRetrieval` và các guard đối chiếu queue rải ở module sau khi request thường cũng đi qua cùng thứ tự thực thi. Không giữ hai loại hàng chờ cạnh tranh.
4. **Thu gọn controller và mixin.** Controller chỉ hit-test, preview, tạo intent. Mixin chỉ chuyển input overlay và chặn vanilla khi màn hình stale/overlay sở hữu gesture. Khi menu thật đang hiển thị, vanilla tiếp tục xử lý click thường; chỉ hoàn tác pickup trước khi enqueue stash. Khi menu stale, dùng geometry màn hình cũ và inventory hiện tại để tạo drag ảo.
5. **Xóa code cũ sau khi nối luồng mới.** Xóa các `targeted*`/timeout/return flags không còn nguồn sử dụng, helper trùng nhau và nhánh `hasPendingTransfers()` dùng như quyền nhận input. Đo lại diff so với `cfe2e620^` và rà các hook còn lại.

## Kiểm chứng bắt buộc

- Trong cùng một phiên UI, thả liên tiếp ít nhất 3 lệnh panel → inventory trước khi lệnh đầu xong; lặp lại với inventory → panel và chuỗi xen kẽ hai chiều. Mỗi mouse-up hợp lệ phải được nhận ngay, thứ tự xử lý FIFO, không cần chờ lệnh trước.
- Lặp drag vào cùng slot trống; lệnh sau phải tìm slot khác khi slot đó đã đầy. Lặp stash cùng stack; không nhân đôi chuyển đồ hoặc lấy stack thay thế. Kiểm tra không còn slot trống, item hết trong cache, stack đổi, partial transfer.
- Kiểm tra click/shift-click/quick-craft vanilla, kéo giữa slot vanilla, panel search/scroll/button, chest và shulker, khôi phục màn hình, đóng UI và đổi dimension giữa hàng đợi. Không có cursor stack kẹt hoặc item rơi ngoài ý muốn.
- Chạy `gradlew compileJava`, `git diff --check` và kiểm thử thao tác trong client Minecraft. Compile thành công chỉ chứng minh API hợp lệ; tiêu chí drag liên tục phải được xác nhận bằng thao tác runtime.
