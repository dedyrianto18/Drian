# CryptoQuant PC — Exness MT5 & Android Bridge

Aplikasi desktop ringan untuk Windows PC yang terhubung **langsung** ke **MetaTrader 5 (MT5) Broker Exness** (akun **Demo / Trial** maupun **Riil / Real**) sekaligus menyediakan **Bridge Server bawaan** agar aplikasi Android **CryptoQuant** bisa login ke akun Exness, membaca pasar Exness, mengeksekusi order, dan menyinkronkan **Histori Trading AI**.

## Cara Menjalankan & Menghubungkan ke Android
1. Pastikan **MetaTrader 5 Desktop (Exness MT5)** sudah terbuka di Windows PC.
2. Pastikan **Python 3.10+** sudah terinstal.
3. Klik dua kali **`START_WINDOWS.bat`**.
4. Di jendela aplikasi PC:
   - Pilih **Demo (`Exness-MT5Trial`)** atau **Riil (`Exness-MT5Real`)**, masukkan **Login MT5** & **Password Trading**, lalu klik **Login & Hubungkan Exness**.
   - Catat **URL Bridge untuk Android App** (contoh: `http://192.168.1.10:8000`) dan **Token Bridge** (`exness-ai-2026`).
5. Di **Aplikasi Android CryptoQuant** (pastikan HP & PC berada di jaringan Wi-Fi yang sama):
   - Buka menu **Akun Exness / Settings**, masukkan URL Bridge dari PC, pilih tipe akun **Demo** atau **Riil**, isi Login/Password/Server Exness, lalu tekan **Login & Hubungkan ke Exness**.
   - Semua pasar broker Exness (`BTCUSDm`, `ETHUSDm`, `SOLUSDm`, `XAUUSDm`, `XAGUSDm`, `EURUSDm`, `GBPUSDm`, `USDJPYm`, `USOILm`, `USTECm`, `US30m`) serta **Histori Trading AI** otomatis tersinkron antara PC dan Android.
