package ly.count.android.sdk;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLConnection;
import java.util.Iterator;

class ModuleRemoteConfig extends ModuleBase {
    RemoteConfig remoteConfigInterface = null;

    ModuleRemoteConfig(Countly cly, CountlyConfig config) {
        super(cly);

        if (_cly.isLoggingEnabled()) {
            Log.d(Countly.TAG, "[ModuleRemoteConfig] Initialising");
        }

        remoteConfigInterface = new RemoteConfig();
    }

    /**
     * Internal call for updating remote config keys
     * @param keysOnly set if these are the only keys to update
     * @param keysExcept set if these keys should be ignored from the update
     * @param requestShouldBeDelayed this is set to true in case of update after a deviceId change
     * @param callback called after the update is done
     */
    protected void updateRemoteConfigValues(final Context context, final String[] keysOnly, final String[] keysExcept, final ConnectionQueue connectionQueue_, final boolean requestShouldBeDelayed, final RemoteConfigCallback callback){
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Updating remote config values, requestShouldBeDelayed:[" + requestShouldBeDelayed + "]");
        }
        String keysInclude = null;
        String keysExclude = null;

        if(keysOnly != null && keysOnly.length > 0){
            //include list takes precedence
            //if there is at least one item, use it
            JSONArray includeArray = new JSONArray();
            for (String key:keysOnly) {
                includeArray.put(key);
            }
            keysInclude = includeArray.toString();
        } else if(keysExcept != null && keysExcept.length > 0){
            //include list was not used, use the exclude list
            JSONArray excludeArray = new JSONArray();
            for(String key:keysExcept){
                excludeArray.put(key);
            }
            keysExclude = excludeArray.toString();
        }

        if(connectionQueue_.getDeviceId().getId() == null){
            //device ID is null, abort
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.d(Countly.TAG, "RemoteConfig value update was aborted, deviceID is null");
            }

            if(callback != null){
                callback.callback("Can't complete call, device ID is null");
            }

            return;
        }

        if(connectionQueue_.getDeviceId().temporaryIdModeEnabled() || connectionQueue_.queueContainsTemporaryIdItems()){
            //temporary id mode enabled, abort
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.d(Countly.TAG, "RemoteConfig value update was aborted, temporary device ID mode is set");
            }

            if(callback != null){
                callback.callback("Can't complete call, temporary device ID is set");
            }

            return;
        }

        ConnectionProcessor cp = connectionQueue_.createConnectionProcessor();
        URLConnection urlConnection;
        String requestData = connectionQueue_.prepareRemoteConfigRequest(keysInclude, keysExclude);
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "RemoteConfig requestData:[" + requestData + "]");
        }

        try {
            urlConnection = cp.urlConnectionForServerRequest(requestData, "/o/sdk?");
        } catch (IOException e) {
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.e(Countly.TAG, "IOException while preparing remote config update request :[" + e.toString() + "]");
            }

            if(callback != null){
                callback.callback("Encountered problem while trying to reach the server");
            }

            return;
        }

        (new ModuleRatings.ImmediateRequestMaker()).execute(urlConnection, requestShouldBeDelayed, new ModuleRatings.InternalFeedbackRatingCallback() {
            @Override
            public void callback(JSONObject checkResponse) {
                if (Countly.sharedInstance().isLoggingEnabled()) {
                    Log.d(Countly.TAG, "Processing remote config received response, received response is null:[" + (checkResponse == null) + "]");
                }
                if(checkResponse == null) {
                    if(callback != null){
                        callback.callback("Encountered problem while trying to reach the server, possibly no internet connection");
                    }
                    return;
                }

                //merge the new values into the current ones
                RemoteConfigValueStore rcvs = loadConfig(context);
                if(keysExcept == null && keysOnly == null){
                    //in case of full updates, clear old values
                    rcvs.values = new JSONObject();
                }
                rcvs.mergeValues(checkResponse);

                if (Countly.sharedInstance().isLoggingEnabled()) {
                    Log.d(Countly.TAG, "Finished remote config processing, starting saving");
                }

                saveConfig(context, rcvs);

                if (Countly.sharedInstance().isLoggingEnabled()) {
                    Log.d(Countly.TAG, "Finished remote config saving");
                }

                if(callback != null){
                    callback.callback(null);
                }
            }
        });
    }

    protected Object getValue(String key, Context context){
        RemoteConfigValueStore rcvs = loadConfig(context);
        return rcvs.getValue(key);
    }


    protected void saveConfig(Context context, RemoteConfigValueStore rcvs){
        CountlyStore cs = new CountlyStore(context);
        cs.setRemoteConfigValues(rcvs.dataToString());
    }

    protected RemoteConfigValueStore loadConfig(Context context){
        CountlyStore cs = new CountlyStore(context);
        String rcvsString = cs.getRemoteConfigValues();
        //noinspection UnnecessaryLocalVariable
        RemoteConfigValueStore rcvs = RemoteConfigValueStore.dataFromString(rcvsString);
        return rcvs;
    }

    protected void clearValueStore(Context context){
        CountlyStore cs = new CountlyStore(context);
        cs.setRemoteConfigValues("");
    }

    protected static class RemoteConfigValueStore {
        public JSONObject values = new JSONObject();

        //add new values to the current storage
        public void mergeValues(JSONObject newValues){
            if(newValues == null) {return;}

            Iterator<String> iter = newValues.keys();
            while (iter.hasNext()) {
                String key = iter.next();
                try {
                    Object value = newValues.get(key);
                    values.put(key, value);
                } catch (Exception e) {
                    if (Countly.sharedInstance().isLoggingEnabled()) {
                        Log.e(Countly.TAG, "Failed merging new remote config values");
                    }
                }
            }
        }

        private RemoteConfigValueStore(JSONObject values){
            this.values = values;
        }

        public Object getValue(String key){
            return values.opt(key);
        }

        public static RemoteConfigValueStore dataFromString(String storageString){
            if(storageString == null || storageString.isEmpty()){
                return new RemoteConfigValueStore(new JSONObject());
            }

            JSONObject values;
            try {
                values = new JSONObject(storageString);
            } catch (JSONException e) {
                if (Countly.sharedInstance().isLoggingEnabled()) {
                    Log.e(Countly.TAG, "Couldn't decode RemoteConfigValueStore successfully: " + e.toString());
                }
                values = new JSONObject();
            }
            return new RemoteConfigValueStore(values);
        }

        public String dataToString(){
            return values.toString();
        }
    }

    @Override
    public void halt() {
        remoteConfigInterface = null;
    }

    public class RemoteConfig {
        /**
         * Clear all stored remote config_ values
         */
        public synchronized void clearStoredValues(){
            if (_cly.isLoggingEnabled()) {
                Log.d(Countly.TAG, "[RemoteConfig] Calling 'clearStoredValues'");
            }

            clearValueStore(_cly.context_);
        }

        /**
         * Get the stored value for the provided remote config_ key
         * @param key
         * @return
         */
        public Object getValueForKey(String key){
            if (_cly.isLoggingEnabled()) {
                Log.d(Countly.TAG, "[RemoteConfig] Calling remoteConfigValueForKey");
            }

            if(!_cly.anyConsentGiven()) { return null; }

            return getValue(key, _cly.context_);
        }

        /**
         * Manual remote config update call. Will update all keys except the ones provided
         * @param keysToExclude
         * @param callback
         */
        public void updateExceptKeys(String[] keysToExclude, RemoteConfigCallback callback) {
            if (_cly.isLoggingEnabled()) {
                Log.d(Countly.TAG, "[RemoteConfig] Manually calling to updateRemoteConfig with exclude keys");
            }

            if(!_cly.anyConsentGiven()){
                if(callback != null){ callback.callback("No consent given"); }
                return;
            }
            if (keysToExclude == null && _cly.isLoggingEnabled()) { Log.w(Countly.TAG,"updateRemoteConfigExceptKeys passed 'keys to ignore' array is null"); }
            updateRemoteConfigValues(_cly.context_, null, keysToExclude, _cly.connectionQueue_, false, callback);
        }

        /**
         * Manual remote config_ update call. Will only update the keys provided.
         * @param keysToInclude
         * @param callback
         */
        public void updateForKeysOnly(String[] keysToInclude, RemoteConfigCallback callback){
            if (_cly.isLoggingEnabled()) {
                Log.d(Countly.TAG, "[RemoteConfig] Manually calling to updateRemoteConfig with include keys");
            }
            if(!_cly.anyConsentGiven()){
                if(callback != null){ callback.callback("No consent given"); }
                return;
            }
            if (keysToInclude == null && _cly.isLoggingEnabled()) { Log.w(Countly.TAG,"updateRemoteConfigExceptKeys passed 'keys to include' array is null"); }
            updateRemoteConfigValues(_cly.context_, keysToInclude, null, _cly.connectionQueue_, false, callback);
        }

        /**
         * Manually update remote config_ values
         * @param callback
         */
        public void update(RemoteConfigCallback callback){
            if (_cly.isLoggingEnabled()) {
                Log.d(Countly.TAG, "Manually calling to updateRemoteConfig");
            }
            if(!_cly.anyConsentGiven()){ return; }
            updateRemoteConfigValues(_cly.context_, null, null, _cly.connectionQueue_, false, callback);
        }
    }
}
