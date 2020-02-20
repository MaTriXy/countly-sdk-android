package ly.count.android.demo;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.util.Random;
import java.util.regex.Pattern;

import ly.count.android.sdk.Countly;
import ly.count.android.sdk.CountlyStarRating;
import ly.count.android.sdk.DeviceId;

@SuppressWarnings("UnusedParameters")
public class ActivityExampleOthers extends Activity {
    Activity activity;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        activity = this;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example_others);
        Countly.onCreate(this);
    }

    public void onClickViewOther05(View v) {
        //set user location
        String countryCode = "us";
        String city = "Houston";
        String latitude = "29.634933";
        String longitude = "-95.220255";
        String ipAddress = null;

        Countly.sharedInstance().setLocation(countryCode, city, latitude + "," + longitude, ipAddress);
    }

    public void onClickViewOther06(View v) {
        //disable location
        Countly.sharedInstance().disableLocation();
    }

    public void onClickViewOther08(View v) {
        //Clearing request queue
        Countly.sharedInstance().flushRequestQueues();
    }

    public void onClickViewOther10(View v) {
        //Doing internally stored requests
        Countly.sharedInstance().doStoredRequests();
    }

    public void onClickTestcrashFilter(View v) {
        Log.d(Countly.TAG, "Testing crash filter");

        Pattern[] regexFilters = new Pattern[]{Pattern.compile("secretNumber\\d*"), Pattern.compile(".*1337"), Pattern.compile(".*secret.*", Pattern.DOTALL)};
        String[] crashes = new String[]{"secretNumber2331", "fdfd]1337", "nothing here",
                "java.lang.Exception: A really secret exception\n" +
                "\tat ly.count.android.demo.ActivityExampleOthers.onClickTestcrashFilterSample(ActivityExampleOthers.java:104)\n" +
                "\tat java.lang.reflect.Method.invoke(Native Method)\n" +
                "\tat android.view.View$DeclaredOnClickListener.onClick(View.java:5629)\n" +
                "\tat android.view.View.performClick(View.java:6597)\n" +
                "\tat android.view.View.performClickInternal(View.java:6574)\n" +
                "\tat android.view.View.access$3100(View.java:778)\n" +
                "\tat android.view.View$PerformClick.run(View.java:25885)\n" +
                "\tat android.os.Handler.handleCallback(Handler.java:873)\n" +
                "\tat android.os.Handler.dispatchMessage(Handler.java:99)\n" +
                "\tat android.os.Looper.loop(Looper.java:193)\n" +
                "\tat android.app.ActivityThread.main(ActivityThread.java:6718)\n" +
                "\tat java.lang.reflect.Method.invoke(Native Method)\n" +
                "\tat com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:493)\n" +
                "\tat com.android.internal.os.ZygoteInit.main(ZygoteInit.java:858)\n"};

        boolean[] res = Countly.sharedInstance().crashes().crashFilterTest(regexFilters, crashes);

        String ret = "";
        for(int a = 0 ; a < res.length ; a++){
            ret += res[a] + ", ";
        }

        Log.d(Countly.TAG, "Received crash filter results: " + ret);

        Toast.makeText(getBaseContext(), "Testing crash filter: [" + ret + "]", Toast.LENGTH_LONG).show();
    }

    public void onClickTestcrashFilterSample(View v) {
        Countly.sharedInstance().recordUnhandledException(new Throwable("A really secret exception"));
    }

    public void onClickRemoveAllConsent(View v){
        Countly.sharedInstance().removeConsentAll();
    }

    public void onClickGiveAllConsent(View v){
        Countly.sharedInstance().giveConsentAll();
    }

    @Override
    public void onStart()
    {
        super.onStart();
        Countly.sharedInstance().onStart(this);
    }

    @Override
    public void onStop()
    {
        Countly.sharedInstance().onStop();
        super.onStop();
    }

    @Override
    public void onConfigurationChanged (Configuration newConfig){
        super.onConfigurationChanged(newConfig);
        Countly.sharedInstance().onConfigurationChanged(newConfig);
    }
}
