package com.roquito.platform.messaging.protocol;

public class Connect extends BasePayload {
    
    private static final String API_KEY = "api_key";
    private static final String API_VERSION = "api_version";
    
    public Connect(Payload payload) {
        super(payload);
    }
    
    public String getApiKey() {
        return getHeaders().get(API_KEY);
    }
    
    public String getApiVersion() {
        return getHeaders().get(API_VERSION);
    }
}
