# Online-Auction-System-Project

## 1. Sơ đồ thiết kế lớp UML
```mermaid
classDiagram
    %% Base Entity
    class Entity{
       <<Abstract>>
       #String id
       +getId() String
    }

    %% Items Hierarchy
    class Item{
       <<Abstract>>
       -String nameItem
       -String descriptionItem
       -double startingPrice
       +printInfo()* void
    }
    class Electronics{
       -String brand
       -int warrantyMonths
    }
    class Art{
       -String artist
       -int creationYear
    }
    class Vehicle{
       -String engineType
       -double mileage
    }

    %% Users Hierarchy
    class User{
       <<Abstract>>
       -String userName
       -String passWord
       -String email
       -boolean isBanned
       +login(String pass) boolean
       +logout() void
    }
    
    %% Observer Interface
    class AuctionObserver {
        <<Interface>>
        +update(String message) void
    }

    class Bidder{
       -double balance
       -List~BidTransaction~ transactionHistory
       +placeBid(Auction auction, double amount) void
       +setupAutoBid(Auction auction, double maxBid, double increment) void
       +update(String message) void
    }
    
    class Seller{
       -float rating
       -List~Item~ inventory
       +createAuction(Item item, LocalDateTime start, LocalDateTime end) void
       +addItem(Item item) void
       +removeItem(Item item) void
       +updateItem(Item item) void
       
    }
    
    class Admin{
       -String roleLevel
       +cancelAuction(Auction auction, String reason) void
       +banUser(User user, String reason) void
    }

    %% Business Logic
    class Auction{
       -Item item
       -Seller seller
       -double currentHighestBid
       -Bidder highestBidder
       -LocalDateTime startTime
       -LocalDateTime endTime
       -AuctionStatus status
       -List~AuctionObserver~ observers
       -List~BidTransaction~ bidHistory
       +processBid(BidTransaction transaction) void
       -validateBid(BidTransaction transaction) void
       +addObserver(AuctionObserver observer) void
       +notifyObservers(String message) void
       +startAuction() void
       +closeAuction() void
    }

    %% Design Patterns & Utils
    class AuctionManager {
       <<Singleton>>
       -static AuctionManager instance
       -List~Auction~ activeAuctions
       +static getInstance() AuctionManager
       +createAuction(Item item, Seller seller, LocalDateTime start, LocalDateTime end) Auction
    }

    class ItemFactory {
        <<Interface>>
        +createItem(String name, String desc, double price, Map~String, Object~ attrs) Item
    }
    class ArtFactory { +createItem(...) Item }
    class ElectronicsFactory { +createItem(...) Item }
    class VehicleFactory { +createItem(...) Item }

    %% Enums
    class AuctionStatus{
       <<Enum>>
       +OPEN
       +RUNNING
       +FINISHED
       +CANCELED
       +PAID
    }

    %% Exceptions
    class AuctionException { <<Exception>> }
    class InvalidBidException { }
    class AuctionClosedException { }
    class InsufficientBalanceException { }

    %% Relationships
    Entity <|-- User
    Entity <|-- Item
    Entity <|-- Auction

    User <|-- Bidder
    User <|-- Seller
    User <|-- Admin
    
    AuctionObserver <|.. Bidder : implements
    
    Item <|-- Electronics
    Item <|-- Art
    Item <|-- Vehicle

    ItemFactory <|.. ArtFactory
    ItemFactory <|.. ElectronicsFactory
    ItemFactory <|.. VehicleFactory

    AuctionException <|-- InvalidBidException
    AuctionException <|-- AuctionClosedException
    AuctionException <|-- InsufficientBalanceException

    Auction "1" *-- "1" Item
    Auction "1" --> "1" AuctionStatus : has status
    Auction "1" o-- "*" AuctionObserver : notifies
    AuctionManager "1" o-- "*" Auction : manages
    Seller ..> AuctionManager : requests creation
    Bidder ..> Auction : interacts
 
```
## 2. Bảng phân công công việc


| Module | Công việc / Class / Method cụ thể | Thành viên thực hiện | Trạng thái |
| :--- | :--- | :--- | :---: |
| **1. Cơ sở hạ tầng** | Cấu hình Maven `pom.xml` & Quản lý thư viện (JUnit, JavaFX) | Hiếu | 
| | Thiết kế lớp trừu tượng `Entity` và cơ chế sinh ID tự động | Hiếu | 
| **2. Thực thể Người dùng** | Lớp `User` (Base) & thuộc tính xác thực (Username, Password) | Hiếu | 
| | Phân quyền `Admin` (Role) và `Seller` (Rating, Inventory) | Hiếu | 
| | Logic tài khoản `Bidder`: Quản lý `balance` & `placeBid` | Hoàng | 
| **3. Thực thể Sản phẩm** | Lớp trừu tượng `Item` & các thuộc tính chung (StartingPrice) | Hiếu | 
| | Đặc thù sản phẩm: `Art` (Artist, Year) & `Electronics` (Brand) | Huy + Điệp | 
| | Đặc thù sản phẩm: `Vehicle` (EngineType, Mileage) | Huy | 
| **4. Design Patterns** | Singleton: `AuctionManager` (getInstance & quản lý List) | Hiếu | 
| | Factory: Interface `ItemFactory` & lớp `ArtFactory` | Hiếu | 
| | Factory: `ElectronicsFactory` & `VehicleFactory` (Xử lý Map) | Hiếu | 
| | Observer: Interface `AuctionObserver` & phương thức `update()` | Hiếu | 
| **5. Logic Đấu giá Core** | `Auction`: Quản lý `AuctionStatus` & Trạng thái thời gian | Điệp + Huy | 
| | `Auction`: Cơ chế thông báo `notifyObservers` (Real-time) | Hiếu | 
| | `Auction`: Refactor hàm `validateBid` (Kiểm tra điều kiện lỗi) | Hoàng | 
| | `Auction`: Hàm `processBid` (Đảm bảo an toàn đa luồng) | Hoàng | 
| | `BidTransaction`: Lưu trữ lịch sử giao dịch & Timestamp | Hoàng | 
| **6. Xử lý Ngoại lệ** | Thiết kế cây kế thừa `AuctionException` (Base class) | Huy | 
| | Cài đặt `InvalidBidException` & `InsufficientBalanceException` | Huy | 
| | Cài đặt `AuctionClosedException` & `AuthenticationException` | Huy | 
| **7. Kiểm thử (Unit Test)** | JUnit: Khởi tạo dữ liệu mẫu (@BeforeEach) & Test đặt giá hợp lệ | Hiếu |
| | JUnit: Test bắt lỗi giá thầu thấp & Lỗi người bán tự đặt giá | Hiếu |
| | JUnit: Test bắt lỗi đặt giá khi phiên đã đóng/kết thúc | Hiếu |
| **8. Tài liệu & UI** | Thiết kế sơ đồ lớp UML (Patterns & Relationships) | Hiếu |
| | Viết tài liệu README, phân công việc & Nhật ký thay đổi | Cả nhóm |
| | Thiết kế giao diện FXML: Màn hình Login & Danh sách đấu giá | Tương lai |
| **9. Interface Dao** | Thiết kế interface dao | Điệp|
| **10. AuctionStatus** | Thiết kế Auction Status | Huy |
| **11. AuctionDaoImpl** | Thiết kế AuctionDaoImpl | Hoàng |
| **12. ConcurrencyTest** | Thiết kế ConcurrencyTest | Điệp |

---


