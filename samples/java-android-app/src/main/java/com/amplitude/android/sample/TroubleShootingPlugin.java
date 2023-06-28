package com.amplitude.android.sample;

import com.amplitude.common.Logger;
import com.amplitude.core.Amplitude;
import com.amplitude.core.events.BaseEvent;
import com.amplitude.core.platform.DestinationPlugin;
import com.google.gson.Gson;

public class TroubleShootingPlugin extends DestinationPlugin {
    private Logger logger;
    @Override
    public void setup(Amplitude amplitude) {
        logger = amplitude.getLogger();
        String apiKey = amplitude.getConfiguration().getApiKey();
        String serverZone = String.valueOf(amplitude.getConfiguration().getServerZone());
        String serverUrl = amplitude.getConfiguration().getServerUrl();
        logger.debug("Current Configuration : {\"apiKey\": "+apiKey+", \"serverZone\": "+serverZone+", \"serverUrl\": "+serverUrl+"}");
        super.setup(amplitude);
    }

    @Override
    public BaseEvent track(BaseEvent event) {
        Gson gson = new Gson();
        String eventJsonStr = gson.toJson(event);
        logger.debug("Processed event: "+eventJsonStr);
        return event;
    }
}
