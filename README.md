# WiFi RTT Radar Distance Sender

Ung dung Android nay la nguon do khoang cach that cho WiFi Radar Viewer.

## Cach hoat dong

- Dien thoai Android quet cac modem/access point co ho tro WiFi RTT / 802.11mc.
- Khi chon mot AP, Android `WifiRttManager` do khoang cach bang `RangingResult.getDistanceMm()`.
- App gui ket qua sang WiFi Radar Bridge qua endpoint `/api/distance`.
- Web GitHub Pages chi la man hinh xem; no khong the tu doc song WiFi RTT vi trinh duyet khong cap quyen radio.

## Dieu kien bat buoc

- Dien thoai Android 9 tro len va phan cung co WiFi RTT.
- Modem/AP tai noi do phai ho tro RTT / IEEE 802.11mc.
- May tinh chay WiFi Radar Bridge va dien thoai o cung mang WiFi.

Neu modem/AP khong ho tro RTT, app van mo duoc nhung se bao khong co AP do duoc khoang cach.

## Ket noi voi viewer

1. Chay WiFi Radar Bridge tren may tinh.
2. Mo app Android, bam `Tim bridge`.
3. Bam `Quet modem RTT`.
4. Chon modem/AP trong danh sach.
5. Bam `Bat dau do`.
6. Mo viewer GitHub Pages hoac local viewer de xem `Khoang cach` tinh bang met.

## Link build

GitHub Actions tu build APK moi nhat va dua vao GitHub Release tag `latest`.
