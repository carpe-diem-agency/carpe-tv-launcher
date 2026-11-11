package agency.carpediem.tv.prototype;
interface ISystemPackages {
 int readAnalyticsState(String packageName) = 0;
 int disableAnalytics(String packageName) = 1;
 int restoreAnalytics(String packageName, int originalState) = 2;
 boolean enableStockLauncher() = 3;
 boolean sleepDevice() = 4;
 boolean openBluetoothSettings() = 5;
 void destroy() = 16777114;
}
