package ly.count.android.sdk;

import java.util.Collection;
import java.util.List;
import java.util.Map;

interface StorageProvider {
    String[] getRequests();

    String[] getEvents();

    List<Event> getEventList();

    void addRequest(final String requestStr);

    void removeRequest(final String requestStr);

    void replaceRequests(final String[] newConns);

    void replaceRequestList(final List<String> newConns);

    void addEvent(final String key, final Map<String, String> segmentation, final Map<String, Integer> segmentationInt, final Map<String, Double> segmentationDouble, final Map<String, Boolean> segmentationBoolean,
        final long timestamp, final int hour, final int dow, final int count, final double sum, final double dur);

    void removeEvents(final Collection<Event> eventsToRemove);

    String getDeviceID();

    String getDeviceIDType();

    void setDeviceID(String id);

    void setDeviceIDType(String type);

    void setStarRatingPreferences(String preferences);//not integrated

    String getStarRatingPreferences();//not integrated

    void setCachedAdvertisingId(String advertisingId);//not integrated

    String getCachedAdvertisingId();//not integrated

    void setRemoteConfigValues(String values);//not integrated

    String getRemoteConfigValues();//not integrated

    //fields for data migration
    int getDataSchemaVersion();

    void setDataSchemaVersion(int version);

    boolean anythingSetInStorage();
}
