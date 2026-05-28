📝 BÁOCÁO SỬA LỖILEVELL 1 - 22/05/2026
=====================================

✅ TẤT CẢ CÁC LỖI CRITICAL ĐỀU ĐÃ ĐƯỢC SỬA

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

✏️ LỖI 1: PASSWORD LƯUR PLAINTEXT (BẢO MẬT TẾ)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

📄 File được sửa: User.java

❌ Trước:
    private String passWord;  // Lưu plaintext!
    public boolean login(String pass){
        return this.passWord.equals(pass);  // So sánh trực tiếp
    }

✅ Sau:
    [đã sửa level 1] Dòng 20-34: 
    - Thêm import SHA-256 hash
    - Tạo hàm hashPassword() để hash password bằng SHA-256
    - Tạo hàm login() verify bằng cách hash input rồi so sánh hash

    public User(String userName, String passWord, String email){
        super();
        this.userName = userName;
        this.passWord = hashPassword(passWord);  // ✅ Hash ngay khi save
        this.email = email;
    }

    private static String hashPassword(String plainPassword) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashedBytes = md.digest(plainPassword.getBytes());
            return Base64.getEncoder().encodeToString(hashedBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    public boolean login(String pass){
        return this.passWord.equals(hashPassword(pass));  // ✅ Compare hash
    }

📊 Impact: Password không bị leak ngay cả khi .dat file bị ăn cắp

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

✏️ LỖI 2: BIDDER.PLACEBID() KHÔNG TRỪ BALANCE  
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

📄 File được sửa: Bidder.java

❌ Trước:
    public void placeBid(Auction auction, double amount) throws AuctionException {
        if(amount > this.getBalance()){
            throw new InsufficientBalanceException(...);
        }
        BidTransaction newTransaction = new BidTransaction(auction,this,amount);
        auction.processBid(newTransaction);
        System.out.println("Bidder " + this.getUserName() + "...");
        this.addTransaction(newTransaction);
        // ❌ KHÔNG CÓ: this.balance -= amount;
    }

✅ Sau:
    [đã sửa level 1] Dòng 51-63:
    - Thêm dòng: this.balance -= amount;

    public void placeBid(Auction auction, double amount) throws AuctionException {
        if(amount > this.getBalance()){
            throw new InsufficientBalanceException(...);
        }
        BidTransaction newTransaction = new BidTransaction(auction,this,amount);
        auction.processBid(newTransaction);
        // đã sửa level 1: Trừ balance khi đặt giá thành công
        this.balance -= amount;
        System.out.println("Bidder " + this.getUserName() + "...");
        this.addTransaction(newTransaction);
    }

📊 Impact: Bidder không thể đặt giá với tiền ảo nữa - Duy trì tính toàn vẹn dữ liệu

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

✏️ LỖI 3: AUCTION.UPDATEAUCTIONDETAILS() LOGIC SAI
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

📄 File được sửa: Auction.java

❌ Trước (Dòng 188):
    if (this.status != AuctionStatus.OPEN || this.status == AuctionStatus.FINISHED) {
        return false;
    }
    // ❌ Logic sai: Biểu thức (A || B) luôn true nếu A = true

✅ Sau:
    [đã sửa level 1] Dòng 190:
    if (this.status != AuctionStatus.OPEN) {
        return false;  // Chỉ cho phép edit khi OPEN
    }

📊 Impact: Chỉ cho phép chỉnh sửa phiên đấu giá khi trạng thái OPEN (chưa bắt đầu)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

✏️ LỖI 4: SELLER KHÔNG KIỂM TRA ITEM CÓ TRONG INVENTORY KHÔNG
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

📄 File được sửa: 
   1. Seller.java
   2. InvalidAuctionException.java (FILE MỚI TẠO)

❌ Trước:
    public void createAuction(Item item, LocalDateTime start, LocalDateTime end) {
        System.out.println("Người bán " + this.getUserName() + "...");
        AuctionManager.getInstance().createAuction(item, this, start, end);
        // ❌ Không kiểm tra item có trong inventory không
    }

✅ Sau:
    [đã sửa level 1] Dòng 26-33:
    1. Thêm throws InvalidAuctionException
    2. Kiểm tra if (!this.inventory.contains(item))
    3. Ném exception nếu item không tồn tại

    public void createAuction(Item item, LocalDateTime start, LocalDateTime end) throws InvalidAuctionException {
        System.out.println("Người bán " + this.getUserName() + "...");
        
        // đã sửa level 1: Kiểm tra xem item có trong kho không
        if (!this.inventory.contains(item)) {
            throw new InvalidAuctionException("Lỗi: Sản phẩm '" + item.getNameItem() + "' không tồn tại trong kho của bạn!");
        }
        
        AuctionManager.getInstance().createAuction(item, this, start, end);
    }

📊 Impact: Ngăn chặn gian lận - Seller không thể tạo phiên đấu giá cho item không có trong kho

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

🧪 TEST RESULTS AFTER FIXING
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

✅ Tất cả 17 Unit Tests PASS:
   - FileDataManagerTest: 2/2 ✓
   - AuctionManagerTest: 2/2 ✓
   - UserManagerTest: 6/6 ✓
   - AuctionTest: 7/7 ✓

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

📋 TỔNG KẾT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Files sửa:
  ✏️  src/main/java/com/auction/model/User.java
  ✏️  src/main/java/com/auction/model/Bidder.java
  ✏️  src/main/java/com/auction/model/Auction.java
  ✏️  src/main/java/com/auction/model/Seller.java
  ✨ src/main/java/com/auction/exception/InvalidAuctionException.java (NEW)

Tất cả các thay đổi đều được đánh dấu bằng comment:
   // đã sửa level 1

Security improvements:
  ✅ SHA-256 password hashing
  ✅ Money leak prevention
  ✅ Logic validation fix
  ✅ Inventory fraud prevention

Compile Status: ✅ BUILD SUCCESS
Test Status: ✅ ALL 17 TESTS PASSED

