# 此 fork 的 AirPlay Direct

這個 repo 有兩個 Android 安裝檔：Tigris 首頁（`com.leslie.tvhome`）與**唯一一個** AirPlay 接收器（`io.github.jqssun.airplay.cleanhome`）。控制中心的 AirPlay 圖示會打開已安裝的接收器；Tigris 本身不提供第二個投放服務。

接收器原始碼位於 [`airplay-direct/`](../airplay-direct/)。設定另一台電視時，不必再取用原本的私人 Chromecast repo。Tigris 仍採 MIT 授權；AirPlay Direct 是獨立的 GPLv3 程式，授權條款見其 [`LICENSE`](../airplay-direct/LICENSE)。兩個 APK 可用同一流程建置與安裝，但不是合併成一個 APK。

## 建置安裝包

需要 JDK 21，以及 Android SDK Platform 36、37。在 repo 根目錄執行：

```sh
bash tools/build-with-airplay.sh
```

產物為 `dist/Tigris-standard.apk`、`dist/Tigris-guard.apk`、`dist/AirPlay-Direct.apk`、`dist/SHA256SUMS.txt`。接收器需要四個原生程式庫；建置時會從 [官方 AirPlay Server v0.0.36 APK](https://github.com/jqssun/android-airplay-server/releases/tag/v0.0.36) 取得，並核對 APK 與每個程式庫的 SHA-256。若電腦已有該檔，可直接指定，避免重複下載：

```sh
AIRPLAY_OFFICIAL_APK=/absolute/path/to/app-release.apk bash tools/build-with-airplay.sh
```

也可在此 fork 的 GitHub Actions 執行 **Tigris with AirPlay Direct → Run workflow**，取得同一套安裝包。Actions 產物只代表建置結果，不代表已安裝到電視。若沒有固定的簽署金鑰，每次雲端建置的簽章可能不同，無法直接更新已安裝的 APK；請保留首次安裝所用的金鑰。

Android/Kotlin 原始碼與 JNI 橋接程式來自原私人 repo 的 AirPlay Direct 0.0.37，基於[上游提交 `a46cca0`](https://github.com/jqssun/android-airplay-server/tree/a46cca0a6a1274e9e33c0e750f0f1c1eb1d881ea)。原生程式庫取自上游 v0.0.36。原始專案鎖定 UxPlay `df67c212a433cf6dda3676dd40c097900d24e645`、FFmpeg `38b88335f99e76ed89ff3c93f877fdefce736c13`、libplist `f41b1ea67045e0c09339974d83e389972d84f166`、openssl-cmake `4edd36a8dab5f85a8f92650b5bdf6e0cab13aab8`。這個流程沒有宣稱重新編譯這些原生程式庫。

目前打包的原生程式庫只有 `armeabi-v7a` 版本；換電視前，先確認新裝置支援 32 位元 ARM App。

## 安裝到另一台 Android TV

先在電視開啟並授權 ADB，再執行：

```sh
TV=192.168.1.20:5555 bash tools/install-with-airplay.sh
```

只有在電視拒絕宣告 HOME 的首頁時，才使用 `FLAVOR=guard`。安裝程式會核對 APK 雜湊；若未安裝 AirPlay Direct，就先安裝它，再以本機 Tigris APK 呼叫既有安裝流程。既有原廠首頁會保留。若已有 AirPlay Direct，預設保留現有版本及資料；確定要更新時才加上 `REPLACE_AIRPLAY=1`。簽章不同時，Android 會拒絕覆蓋；腳本不會為了繞過錯誤而移除 App 或清除資料。

最後在電視開啟 Tigris 控制中心 → AirPlay，檢查接收器名稱與狀態，並用同一區域網路上的 iPhone 或 Mac 實際鏡像測試。測試前確認沒有其他接收器公布相同名稱。建置或安裝成功本身不代表這台電視的投放已通過驗收。
