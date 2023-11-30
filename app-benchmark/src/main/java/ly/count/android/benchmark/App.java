package ly.count.android.benchmark;

import android.app.Application;
import ly.count.android.sdk.Countly;
import ly.count.android.sdk.CountlyConfig;
import ly.count.android.sdk.CountlyStore;
import ly.count.android.sdk.ModuleLog;
import ly.count.android.sdk.PerformanceCounterCollector;

public class App extends Application {
    final String COUNTLY_SERVER_URL = "https://xxx.count.ly";
    final String COUNTLY_APP_KEY = "YOUR_APP_KEY";
    final String DEVICE_ID = "YOUR_DEVICE_ID";

    public static PerformanceCounterCollector appPcc;

    @Override
    public void onCreate() {
        super.onCreate();

        CountlyConfig config = (new CountlyConfig(this, COUNTLY_APP_KEY, COUNTLY_SERVER_URL))
            .setDeviceId(DEVICE_ID)
            .setLoggingEnabled(true)
            .giveAllConsents()
            .setParameterTamperingProtectionSalt("test-benchmark-salt");

        appPcc = new PerformanceCounterCollector();
        config.pcc = appPcc;

        Countly.sharedInstance().init(config);
        Benchmark.countlyStore = new CountlyStore(this, new ModuleLog());
    }
}
