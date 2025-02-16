package ly.count.android.sdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import ly.count.android.sdk.messaging.ModulePush;

public class ModuleEvents extends ModuleBase implements EventProvider {
    static final Map<String, Event> timedEvents = new HashMap<>();

    final static String ACTION_EVENT_KEY = "[CLY]_action";
    final static String VISIBILITY_KEY = "cly_v";
    final static String PREVIOUS_EVENT_NAME_KEY = "cly_pen";
    final static String PREVIOUS_VIEW_NAME_KEY = "cly_pvn";
    final static String CURRENT_VIEW_NAME_KEY = "cly_cvn";

    //interface for SDK users
    final Events eventsInterface;

    //used for tracking recorded custom event ID's. This is not updated when internal events are recorded
    String previousEventId = "";
    String previousEventName = "";
    EventQueueProvider eventQueueProvider;
    ViewIdProvider viewIdProvider;
    SafeIDGenerator safeEventIDGenerator;
    private final boolean viewNameRecordingEnabled;
    private final boolean visibilityTracking;

    ModuleEvents(Countly cly, CountlyConfig config) {
        super(cly, config);
        L.v("[ModuleEvents] Initialising");

        eventProvider = this;
        config.eventProvider = this;
        eventQueueProvider = config.eventQueueProvider;
        safeEventIDGenerator = config.safeEventIDGenerator;
        viewNameRecordingEnabled = config.experimental.viewNameRecordingEnabled;
        visibilityTracking = config.experimental.visibilityTrackingEnabled;

        eventsInterface = new Events();
    }

    void checkCachedPushData(CountlyStore cs) {
        L.d("[ModuleEvents] Starting cache call");

        String[] cachedData = cs.getCachedPushData();

        if (cachedData != null && cachedData[0] != null && cachedData[1] != null) {
            //found valid data cached, record it
            L.d("[ModuleEvents] Found cached push event, recording it");

            Map<String, Object> map = new HashMap<>();
            map.put(ModulePush.PUSH_EVENT_ACTION_PLATFORM_KEY, ModulePush.PUSH_EVENT_ACTION_PLATFORM_VALUE);
            map.put(ModulePush.PUSH_EVENT_ACTION_ID_KEY, cachedData[0]);
            map.put(ModulePush.PUSH_EVENT_ACTION_INDEX_KEY, cachedData[1]);
            recordEventInternal(ModulePush.PUSH_EVENT_ACTION, map, 1, 0, 0, null, null);
        }

        if (cachedData != null && (cachedData[0] != null || cachedData[1] != null)) {
            //if something was recorded, clear it
            cs.clearCachedPushData();
        }
    }

    /**
     * @param key
     * @param segmentation
     * @param count
     * @param sum
     * @param dur
     * @param instant
     * @param eventIdOverride
     */
    public void recordEventInternal(@Nullable final String key, @Nullable Map<String, Object> segmentation, int count, final double sum, final double dur, UtilsTime.Instant instant, final String eventIdOverride) {
        //assert key != null;
        assert count >= 1;
        assert _cly.isInitialized();

        long pccTsStartRecordEventInternal = 0L;
        if (pcc != null) {
            pccTsStartRecordEventInternal = UtilsTime.getNanoTime();
        }

        L.v("[ModuleEvents] calling 'recordEventInternal'");
        if (key == null || key.length() == 0) {
            L.e("[ModuleEvents] recordEventInternal, Valid Countly event key is required. Event will be ignored.");
            return;
        }
        if (count < 1) {
            L.e("[ModuleEvents] recordEventInternal, event count should be greater than zero. Key:[" + key + "] count:[" + count + "]");
            count = 1;
        }

        L.d("[ModuleEvents] recordEventInternal, key:[" + key + "] eventIdOverride:[" + eventIdOverride + "] segmentation:[" + segmentation + "] count:[" + count + "] sum:[" + sum + "] dur:[" + dur + "] instant:[" + instant + "]");

        if (segmentation != null) {
            UtilsInternalLimits.removeUnsupportedDataTypes(segmentation, L);
        }

        //record the current event timestamps
        //if a past event is recorded, instant value will not be null
        if (instant == null) {
            instant = UtilsTime.getCurrentInstant();
        }

        String eventId;

        if (eventIdOverride == null) { // if eventIdOverride not provided generate an event ID
            eventId = safeEventIDGenerator.GenerateValue();
        } else if (eventIdOverride.length() == 0) {
            L.w("[ModuleEvents] provided event ID override value is empty. Will generate a new one.");
            eventId = safeEventIDGenerator.GenerateValue();
        } else { // if eventIdOverride is provided use it the event ID
            eventId = eventIdOverride;
        }

        final long timestamp = instant.timestampMs;
        final int hour = instant.hour;
        final int dow = instant.dow;

        String pvid = null; // Previous View ID
        String cvid = null; // Current View ID

        String pvn = null;
        String pen = null;
        String cvn = null;

        if (key.equals(ModuleViews.VIEW_EVENT_KEY)) {
            pvid = viewIdProvider.getPreviousViewId();
            if (viewNameRecordingEnabled) {
                pvn = _cly.moduleViews.previousViewName;
                if (pvn == null) {
                    pvn = "";
                }
            }
        } else {
            cvid = viewIdProvider.getCurrentViewId();
            if (viewNameRecordingEnabled) {
                pen = previousEventName;
                cvn = _cly.moduleViews.currentViewName;
                if (pen == null) {
                    pen = "";
                }
                if (cvn == null) {
                    cvn = "";
                }
            }
        }

        if (pcc != null) {
            pcc.TrackCounterTimeNs("ModuleEvents_recordEventInternalGenID", UtilsTime.getNanoTime() - pccTsStartRecordEventInternal);
        }

        //before each event is recorded, check if user profile data needs to be saved
        _cly.moduleUserProfile.saveInternal();

        if (visibilityTracking) {
            if (segmentation == null) {
                segmentation = new HashMap<>();
            }

            if (ModuleViews.VIEW_EVENT_KEY.equals(key) && !segmentation.containsKey("visit")) {
                L.d("[ModuleEvents] recordEventInternal, visibility key will not be added to the end view event");
            } else {
                String appInBackground = deviceInfo.isInBackground();
                int state = 1; // in foreground
                if ("true".equals(appInBackground)) {
                    state = 0; // in background
                }
                L.d("[ModuleEvents] recordEventInternal, Adding visibility tracking to segmentation app in background:[" + appInBackground + "] cly_v:[" + state + "]");

                segmentation.put(VISIBILITY_KEY, state);
            }
        }

        switch (key) {
            case ModuleFeedback.NPS_EVENT_KEY:
            case ModuleFeedback.SURVEY_EVENT_KEY:
                if (consentProvider.getConsent(Countly.CountlyFeatureNames.feedback)) {
                    eventQueueProvider.recordEventToEventQueue(key, segmentation, count, sum, dur, timestamp, hour, dow, eventId, pvid, cvid, null);
                    _cly.moduleRequestQueue.sendEventsIfNeeded(true);
                }
                break;
            case ModuleFeedback.RATING_EVENT_KEY: //these events can be reported from a lot of sources, therefore multiple consents could apply
                if (consentProvider.getConsent(Countly.CountlyFeatureNames.starRating) || consentProvider.getConsent(Countly.CountlyFeatureNames.feedback)) {
                    eventQueueProvider.recordEventToEventQueue(key, segmentation, count, sum, dur, timestamp, hour, dow, eventId, pvid, cvid, null);
                    _cly.moduleRequestQueue.sendEventsIfNeeded(false);
                }
                break;
            case ModuleViews.VIEW_EVENT_KEY:
                if (consentProvider.getConsent(Countly.CountlyFeatureNames.views)) {

                    if (segmentation == null) {
                        segmentation = new HashMap<>();
                    }

                    if (viewNameRecordingEnabled) {
                        segmentation.put(PREVIOUS_VIEW_NAME_KEY, pvn);
                    }

                    eventQueueProvider.recordEventToEventQueue(key, segmentation, count, sum, dur, timestamp, hour, dow, eventId, pvid, cvid, null);
                    _cly.moduleRequestQueue.sendEventsIfNeeded(false);
                }
                break;
            case ModuleViews.ORIENTATION_EVENT_KEY:
                if (consentProvider.getConsent(Countly.CountlyFeatureNames.users)) {
                    eventQueueProvider.recordEventToEventQueue(key, segmentation, count, sum, dur, timestamp, hour, dow, eventId, pvid, cvid, null);
                    _cly.moduleRequestQueue.sendEventsIfNeeded(false);
                }
                break;
            case ModulePush.PUSH_EVENT_ACTION:
                if (consentProvider.getConsent(Countly.CountlyFeatureNames.push)) {
                    eventQueueProvider.recordEventToEventQueue(key, segmentation, count, sum, dur, timestamp, hour, dow, eventId, pvid, cvid, null);
                    _cly.moduleRequestQueue.sendEventsIfNeeded(true);
                }
                break;
            case ACTION_EVENT_KEY:
                if (consentProvider.getConsent(Countly.CountlyFeatureNames.clicks) || consentProvider.getConsent(Countly.CountlyFeatureNames.scrolls)) {
                    if (segmentation != null) {
                        UtilsInternalLimits.removeUnsupportedDataTypes(segmentation, L);
                    }
                    eventQueueProvider.recordEventToEventQueue(key, segmentation, count, sum, dur, timestamp, hour, dow, eventId, pvid, cvid, null);
                    _cly.moduleRequestQueue.sendEventsIfNeeded(false);
                }
                break;
            default:
                if (consentProvider.getConsent(Countly.CountlyFeatureNames.events)) {
                    String keyTruncated = UtilsInternalLimits.truncateKeyLength(key, _cly.config_.sdkInternalLimits.maxKeyLength, L, "[ModuleEvents] recordEventInternal");
                    if (segmentation == null) {
                        segmentation = new HashMap<>();
                    }
                    UtilsInternalLimits.applySdkInternalLimitsToSegmentation(segmentation, _cly.config_.sdkInternalLimits, L, "[ModuleEvents] recordEventInternal");

                    if (viewNameRecordingEnabled) {
                        segmentation.put(CURRENT_VIEW_NAME_KEY, cvn);
                        segmentation.put(PREVIOUS_EVENT_NAME_KEY, pen);
                    }

                    eventQueueProvider.recordEventToEventQueue(keyTruncated, segmentation, count, sum, dur, timestamp, hour, dow, eventId, pvid, cvid, previousEventId);
                    previousEventId = eventId;
                    previousEventName = keyTruncated;
                    _cly.moduleRequestQueue.sendEventsIfNeeded(false);
                }
                break;
        }

        if (pcc != null) {
            pcc.TrackCounterTimeNs("ModuleEvents_recordEventInternal", UtilsTime.getNanoTime() - pccTsStartRecordEventInternal);
        }
    }

    boolean startEventInternal(final String key) {
        if (key == null || key.length() == 0) {
            L.e("[ModuleEvents] Can't start event with a null or empty key");
            return false;
        }
        if (timedEvents.containsKey(key)) {
            return false;
        }
        L.d("[ModuleEvents] Starting event: [" + key + "]");
        UtilsTime.Instant instant = UtilsTime.getCurrentInstant();
        timedEvents.put(key, new Event(key, instant.timestampMs, instant.hour, instant.dow));
        return true;
    }

    boolean endEventInternal(@Nullable final String key, @Nullable final Map<String, Object> segmentation, int count, final double sum) {
        L.d("[ModuleEvents] Ending event: [" + key + "]");

        if (key == null || key.length() == 0) {
            L.e("[ModuleEvents] Can't end event with a null or empty key");
            return false;
        }

        Event event = timedEvents.remove(key);

        if (event != null) {
            if (!consentProvider.getConsent(Countly.CountlyFeatureNames.events)) {
                return true;
            }

            if (count < 1) {
                L.e("[ModuleEvents] endEventInternal, event count should be greater than zero, key [" + key + "], dur:[" + count + "]. Count will be reset to '1'.");
                count = 1;
            }
            L.d("[ModuleEvents] Ending event: [" + key + "]");

            long currentTimestamp = UtilsTime.currentTimestampMs();
            double duration = (currentTimestamp - event.timestamp) / 1000.0;
            UtilsTime.Instant instant = new UtilsTime.Instant(event.timestamp, event.hour, event.dow);

            eventProvider.recordEventInternal(key, segmentation, count, sum, duration, instant, null);
            return true;
        } else {
            return false;
        }
    }

    boolean cancelEventInternal(final String key) {
        if (key == null || key.length() == 0) {
            L.e("[ModuleEvents] Can't cancel event with a null or empty key");
            return false;
        }

        Event event = timedEvents.remove(key);

        return event != null;
    }

    @Override
    void initFinished(@NonNull CountlyConfig config) {
        checkCachedPushData(_cly.countlyStore);
    }

    @Override
    void halt() {
        timedEvents.clear();
    }

    public class Events {
        /**
         * Record a event with a custom timestamp.
         * Use this in case you want to record events that you have tracked
         * and stored internally
         *
         * @param key event key
         * @param segmentation custom segmentation you want to set, leave null if you don't want to add anything
         * @param timestamp unix timestamp in milliseconds of when the event occurred
         */
        public void recordPastEvent(@NonNull final String key, @Nullable final Map<String, Object> segmentation, long timestamp) {
            synchronized (_cly) {
                recordPastEvent(key, segmentation, 1, 0, 0, timestamp);
            }
        }

        /**
         * Record a event with a custom timestamp.
         * Use this in case you want to record events that you have tracked
         * and stored internally
         *
         * @param key event key
         * @param segmentation custom segmentation you want to set, leave null if you don't want to add anything
         * @param count how many of these events have occurred, default value is "1"
         * @param sum set sum if needed, default value is "0"
         * @param dur duration of the event, default value is "0"
         * @param timestamp unix timestamp in milliseconds of when the event occurred
         */
        public void recordPastEvent(@NonNull final String key, @Nullable final Map<String, Object> segmentation, final int count, final double sum, final double dur, long timestamp) {
            synchronized (_cly) {
                L.i("[Events] Calling recordPastEvent: [" + key + "]");

                if (timestamp <= 0) {
                    L.e("Provided timestamp has to be greater that zero. Replacing that timestamp with the current time");
                    timestamp = UtilsTime.currentTimestampMs();
                }

                UtilsTime.Instant instant = UtilsTime.Instant.get(timestamp);
                recordEventInternal(key, segmentation, count, sum, dur, instant, null);
            }
        }

        /**
         * Start timed event with a specified key
         *
         * @param key name of the custom event, required, must not be the empty string or null
         * @return true if no event with this key existed before and event is started, false otherwise
         */
        public boolean startEvent(@NonNull final String key) {
            synchronized (_cly) {
                L.i("[Events] Calling startEvent: [" + key + "]");

                return startEventInternal(key);
            }
        }

        /**
         * End timed event with a specified key
         *
         * @param key name of the custom event, required, must not be the empty string or null
         * @return true if event with this key has been previously started, false otherwise
         */
        public boolean endEvent(@NonNull final String key) {
            synchronized (_cly) {
                return endEvent(key, null, 1, 0);
            }
        }

        /**
         * End timed event with a specified key
         *
         * @param key name of the custom event, required, must not be the empty string
         * @param segmentation segmentation dictionary to associate with the event, can be null
         * @param count count to associate with the event, should be more than zero, default value is 1
         * @param sum sum to associate with the event, default value is 0
         * @return true if event with this key has been previously started, false otherwise
         */
        public boolean endEvent(@NonNull final String key, @Nullable final Map<String, Object> segmentation, final int count, final double sum) {
            synchronized (_cly) {
                L.i("[Events] Calling endEvent: [" + key + "]");

                return endEventInternal(key, segmentation, count, sum);
            }
        }

        /**
         * Cancel timed event with a specified key
         *
         * @return true if event with this key has been previously started, false otherwise
         **/
        public boolean cancelEvent(@NonNull final String key) {
            synchronized (_cly) {
                L.i("[Events] Calling cancelEvent: [" + key + "]");

                return cancelEventInternal(key);
            }
        }

        /**
         * Records a custom event with no segmentation values, a count of one and a sum of zero.
         *
         * @param key name of the custom event, required, must not be the empty string
         */
        public void recordEvent(@NonNull final String key) {
            synchronized (_cly) {
                recordEvent(key, null, 1, 0);
            }
        }

        /**
         * Records a custom event with no segmentation values, the specified count, and a sum of zero.
         *
         * @param key name of the custom event, required, must not be the empty string
         * @param count count to associate with the event, should be more than zero
         */
        public void recordEvent(@NonNull final String key, final int count) {
            synchronized (_cly) {
                recordEvent(key, null, count, 0);
            }
        }

        /**
         * Records a custom event with no segmentation values, and the specified count and sum.
         *
         * @param key name of the custom event, required, must not be the empty string
         * @param count count to associate with the event, should be more than zero
         * @param sum sum to associate with the event
         */
        public void recordEvent(@NonNull final String key, final int count, final double sum) {
            synchronized (_cly) {
                recordEvent(key, null, count, sum);
            }
        }

        /**
         * Records a custom event with the specified segmentation values and count, and a sum of zero.
         *
         * @param key name of the custom event, required, must not be the empty string
         * @param segmentation segmentation dictionary to associate with the event, can be null. Allowed values are String, int, double, boolean
         */
        public void recordEvent(@NonNull final String key, @Nullable final Map<String, Object> segmentation) {
            synchronized (_cly) {
                recordEvent(key, segmentation, 1, 0);
            }
        }

        /**
         * Records a custom event with the specified segmentation values and count, and a sum of zero.
         *
         * @param key name of the custom event, required, must not be the empty string
         * @param segmentation segmentation dictionary to associate with the event, can be null. Allowed values are String, int, double, boolean
         * @param count count to associate with the event, should be more than zero
         */
        public void recordEvent(@NonNull final String key, @Nullable final Map<String, Object> segmentation, final int count) {
            synchronized (_cly) {
                recordEvent(key, segmentation, count, 0);
            }
        }

        /**
         * Records a custom event with the specified values.
         *
         * @param key name of the custom event, required, must not be the empty string
         * @param segmentation segmentation dictionary to associate with the event, can be null. Allowed values are String, int, double, boolean
         * @param count count to associate with the event, should be more than zero
         * @param sum sum to associate with the event
         */
        public void recordEvent(@NonNull final String key, @Nullable final Map<String, Object> segmentation, final int count, final double sum) {
            synchronized (_cly) {
                recordEvent(key, segmentation, count, sum, 0);
            }
        }

        /**
         * Records a custom event with the specified values.
         *
         * @param key name of the custom event, required, must not be the empty string
         * @param segmentation segmentation dictionary to associate with the event, can be null
         * @param count count to associate with the event, should be more than zero
         * @param sum sum to associate with the event
         * @param dur duration of an event
         */
        public void recordEvent(@NonNull final String key, @Nullable final Map<String, Object> segmentation, final int count, final double sum, final double dur) {
            synchronized (_cly) {
                L.i("[Events] Calling recordEvent: [" + key + "]");

                if (segmentation != null) {
                    UtilsInternalLimits.truncateSegmentationValues(segmentation, _cly.config_.sdkInternalLimits.maxSegmentationValues, "[Events] recordEvent,", L);
                }

                eventProvider.recordEventInternal(key, segmentation, count, sum, dur, null, null);
            }
        }
    }
}
