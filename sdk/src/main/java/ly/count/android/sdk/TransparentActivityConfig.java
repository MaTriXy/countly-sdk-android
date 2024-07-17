package ly.count.android.sdk;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class TransparentActivityConfig implements Serializable {
    Integer x;
    Integer y;
    Integer width;
    Integer height;
    String url;
    List<WebViewUrlListener> listeners;

    public TransparentActivityConfig(Integer x, Integer y, Integer width, Integer height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.listeners = new ArrayList<>();
    }

    public void setUrl(String url) {
        this.url = url;
    }
}