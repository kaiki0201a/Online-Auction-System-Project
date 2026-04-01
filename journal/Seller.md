# Hệ thống Đấu giá Trực tuyến (Online Auction System)

##  Lộ trình Phát triển (Roadmap & TODOs)
Danh sách các công việc và logic nghiệp vụ cần hiện thực hóa trong các giai đoạn tiếp theo của dự án.

---

### 1. Logic cho lớp `Seller` (Người bán)

#### `createAuction(Item item)`
* **Kiểm tra tính hợp lệ:** Đảm bảo `item` này chưa từng nằm trong một phiên đấu giá nào đang diễn ra (tránh tình trạng một vật phẩm được đấu giá ở hai nơi cùng lúc).
* **Thiết lập thời gian:** Khởi tạo đối tượng `Auction` mới. Tự động thiết lập thời gian bắt đầu (`startTime`) là thời gian hiện tại và thời gian kết thúc (`endTime`) theo cấu hình mặc định (ví dụ: 24h) hoặc do người bán chọn.
* **Chuyển trạng thái:** Cập nhật `AuctionStatus` thành `DRAFT` (Lưu nháp) hoặc `RUNNING` (Đang chạy).
* **Gắn kết dữ liệu:** Liên kết đối tượng `item` và `this` (chính người bán đang thao tác) vào phiên đấu giá vừa tạo.

#### `updateItem(Item item)`
* **Xác thực quyền sở hữu:** Chỉ cho phép cập nhật nếu `item` này thực sự nằm trong danh sách `inventory` của người bán (`Seller`) đang thực hiện yêu cầu.
* **Quy tắc nghiệp vụ (Business Rule):** Nếu vật phẩm này đang nằm trong một phiên đấu giá có trạng thái `RUNNING` (đang có người tham gia), hệ thống **nghiêm cấm** việc thay đổi giá khởi điểm (`startingPrice`) để đảm bảo tính minh bạch.
* **Đồng bộ cơ sở dữ liệu:** Lưu lại các thay đổi mới nhất (tên, mô tả) vào Database.

---

### 2. Logic cho lớp `Auction` (Phiên đấu giá)

#### `processBid(BidTransaction bid)`
* **Xử lý đồng thời (Concurrency):** Cần đảm bảo an toàn luồng (Thread-safe). Tại một thời điểm, chỉ có một giao dịch đặt giá được xử lý để tránh lỗi hai người cùng đặt một giá.
* **Kiểm tra số dư (Balance):** Kiểm tra xem người mua (`Bidder`) có đủ tiền trong tài khoản để đặt cọc mức giá này không.
* **Cập nhật người thắng tạm thời:** Nếu giá đặt (`bidAmount`) cao hơn `currentHighestBid`, cập nhật lại biến `currentHighestBid` và gán người này thành `winningBidder`.

#### `extendTime(int seconds)`
* **Logic "Phút bù giờ":** Giống như các sàn đấu giá thực tế, nếu có người đặt giá thành công vào 30 giây cuối cùng của phiên, tự động cộng thêm thời gian (ví dụ: 1 phút) vào `endTime` để tránh tình trạng "săn giây cuối" (sniping) và tạo cơ hội cho những người khác.

---

### 3. Các Dịch vụ (Services) cần xây dựng

* **`AutoBidService` (Dịch vụ Đấu giá Tự động):** Xây dựng thuật toán chạy ngầm để liên tục theo dõi các phiên đấu giá. Nếu có người trả giá cao hơn, hệ thống sẽ tự động đặt giá thay cho người dùng (dựa trên `maxBid` và `increment` họ đã cài đặt) cho đến khi chạm trần.
* **`PaymentService` (Dịch vụ Thanh toán):** Tích hợp giả lập thanh toán. Khi phiên đấu giá kết thúc (`ENDED`), tiến hành trừ tiền từ `balance` của người thắng (`winningBidder`), trừ phí hoa hồng của sàn, và chuyển số tiền còn lại cho `Seller`.