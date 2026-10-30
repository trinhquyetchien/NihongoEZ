# Onis Kanji (NihongoEZ) 🌸

<div align="center">
  <img src="app/src/main/res/drawable/onis_logo.png" alt="Onis Kanji Logo" width="120" style="border-radius: 24px;" />
  <h3>Ứng dụng nhận diện chữ Hán (Kanji) vẽ tay và phân tích câu tiếng Nhật 100% Offline</h3>

  <p>
    <a href="https://github.com/trinhquyetchien/NihongoEZ/releases/latest">
      <img src="https://img.shields.io/github/v/release/trinhquyetchien/NihongoEZ?color=blue&label=T%E1%BA%A3i%20v%E1%BB%81%20APK" alt="Download APK" />
    </a>
    <img src="https://img.shields.io/badge/Platform-Android-green?logo=android" alt="Platform" />
    <img src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-purple?logo=kotlin" alt="Tech" />
    <img src="https://img.shields.io/badge/Offline-100%25-orange" alt="Offline" />
    <img src="https://img.shields.io/badge/License-MIT-blue" alt="License" />
  </p>
</div>

---

## 📖 Giới thiệu

**Onis Kanji** là công cụ hỗ trợ học tiếng Nhật toàn diện hoạt động **100% Offline**, không cần kết nối Internet:
1. **Nhận diện nét vẽ Kanji**: Vẽ chữ Hán trực tiếp trên màn hình, mô hình AI (PyTorch Mobile ResNet) tích hợp sẵn trong ứng dụng sẽ nhận diện chữ Hán chính xác theo thời gian thực kèm âm Hán Việt, cách đọc On/Kun, nghĩa, giải thích chi tiết, ảnh gợi ý ghi nhớ (Mnemonic) và ảnh động thứ tự nét (SVG Stroke Order).
2. **Phân tích câu & đoạn văn tiếng Nhật (Sentence Analyzer)**: Nhập văn bản hoặc vẽ chữ Hán để chèn vào câu. Hệ thống sử dụng bộ phân tích hình thái học Kuromoji phân tách từ vựng, tự động hiển thị Furigana trên đầu chữ Hán, phát hiện mẫu ngữ pháp JLPT (N5 - N1) và tra cứu từ điển tiếng Việt offline tức thì.

---

## ✨ Tính năng nổi bật

### 1. ✍️ Nhận diện nét vẽ Kanji Offline
- Bảng vẽ trực quan với lưới phụ trợ 4 ô.
- Hỗ trợ thao tác kéo nét mượt mà, tính năng **Undo** (hoàn tác từng nét) và **Xóa nét**.
- Nhận diện trên 3.000 chữ Hán thường dùng (Jōyō Kanji & Jinmeiyō Kanji).
- Trả về top 5 kết quả có độ tương đồng cao nhất kèm xác suất nhận diện.
- Hiển thị thông tin toàn diện:
  - **Âm Hán Việt & Nghĩa tiếng Việt**.
  - **Âm On (Onyomi) & Âm Kun (Kunyomi)**.
  - **Thứ tự từng nét vẽ (Animated SVG)**.
  - **Mẹo nhớ (Mnemonic)** kèm hình ảnh minh họa vui nhộn.
  - **Từ vựng liên quan** chứa chữ Hán đang tra cứu.

### 2. 🔍 Phân tích ngữ pháp & Tra từ vựng tiếng Nhật
- **Tự động gắn Furigana**: Hiển thị cách đọc Hiragana ngay phía trên chữ Kanji tương ứng giúp người học dễ đọc hiểu.
- **Tách từ vựng thông minh**: Sử dụng thư viện Kuromoji IPADIC chia nhỏ câu thành các từ tố, xác định từ loại (Danh từ, Động từ, Tính từ,...), dạng gốc (Dictionary form / Base form).
- **Bộ nhận diện ngữ pháp JLPT**: Tự động phát hiện các cấu trúc ngữ pháp từ N5 đến N1 xuất hiện trong câu, giải thích ý nghĩa và cách sử dụng.
- **Từ điển tiếng Việt Offline**: Tích hợp sẵn cơ sở dữ liệu từ vựng cô đọng giúp tra nghĩa ngay lập tức mà không cần mạng Internet.
- **Tùy chọn chế độ xem**: Hỗ trợ xem tổng hợp "Toàn bộ văn bản" hoặc chọn chi tiết "Từng câu".
- **Vẽ Kanji chèn vào câu**: Nút vẽ Kanji nhanh trên thanh nhập liệu cho phép người dùng vẽ chữ Hán lạ chưa biết cách gõ để chèn trực tiếp vào câu cần phân tích.

---

## 🛠️ Công nghệ sử dụng

- **Ngôn ngữ**: [Kotlin](https://kotlinlang.org/)
- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose) (Material 3, FlowRow, Canvas API)
- **Machine Learning**: [PyTorch Android Lite](https://pytorch.org/mobile/android/) (Mô hình ResNet phân loại chữ Hán)
- **Hình thái học tiếng Nhật**: [Kuromoji IPADIC](https://github.com/atilika/kuromoji)
- **Xử lý hình ảnh & SVG**: [Coil Compose](https://coil-kt.github.io/coil/) & Coil SVG Decoder
- **CI/CD**: GitHub Actions tự động kiểm thử và xuất bản file APK Release.

---

## 📥 Tải về và Cài đặt

Người dùng có thể tải bản phát hành mới nhất:

👉 **[Tải file APK trực tiếp tại GitHub Releases](https://github.com/trinhquyetchien/NihongoEZ/releases/latest)**

*Yêu cầu hệ điều hành: Android 7.0 (API Level 24) trở lên.*

---

## 🚀 Hướng dẫn Build từ mã nguồn

### Yêu cầu môi trường
- **JDK**: Phiên bản 21 (Temurin hoặc OpenJDK)
- **Android SDK**: API 35+
- **Gradle**: 8.x+ (Khuyên dùng Gradle Wrapper đi kèm dự án)

### Các bước thực hiện

1. Clone mã nguồn:
```bash
git clone https://github.com/trinhquyetchien/NihongoEZ.git
cd NihongoEZ
```

2. Chạy kiểm thử tự động (Unit Tests & Lint):
```bash
./gradlew test
./gradlew check
```

3. Build bản Debug APK:
```bash
./gradlew assembleDebug
```
File APK sẽ nằm tại: `app/build/outputs/apk/debug/app-debug.apk`

4. Build bản Release APK:
```bash
./gradlew assembleRelease
```
File APK ký tên hoàn chỉnh sẽ nằm tại: `app/build/outputs/apk/release/app-release.apk`

---

## 📄 Bản quyền (License)

Dự án này được phát hành dưới giấy phép [MIT License](LICENSE).
Tác giả: [Trịnh Quyết Chiến](https://github.com/trinhquyetchien).
