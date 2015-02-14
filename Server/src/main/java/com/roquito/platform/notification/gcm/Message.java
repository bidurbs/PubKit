package com.roquito.platform.notification.gcm;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GCM message.
 * <p/>
 * <p/>
 * Instances of this class are immutable and should be created using a
 * {@link Builder}. Examples:
 * <p/>
 * <strong>Simplest message:</strong>
 * <pre><code>
 * Message message = new Message.Builder().build();
 * </pre></code>
 * <p/>
 * <strong>Message with optional attributes:</strong>
 * <pre><code>
 * Message message = new Message.Builder()
 *    .collapseKey(collapseKey)
 *    .timeToLive(3)
 *    .delayWhileIdle(true)
 *    .build();
 * </pre></code>
 * <p/>
 * <strong>Message with optional attributes and payload data:</strong>
 * <pre><code>
 * Message message = new Message.Builder()
 *    .collapseKey(collapseKey)
 *    .timeToLive(3)
 *    .delayWhileIdle(true)
 *    .addData("key1", "value1")
 *    .addData("key2", "value2")
 *    .build();
 * </pre></code>
 */
public final class Message implements Serializable {
	private static final long serialVersionUID = 1549424221972519613L;
	private final String collapseKey;
    private final Boolean delayWhileIdle;
    private final Integer timeToLive;
    private final Map<String, String> data;

    private Message(Builder builder) {
        collapseKey = builder.collapseKey;
        delayWhileIdle = builder.delayWhileIdle;
        data = Collections.unmodifiableMap(builder.data);
        timeToLive = builder.timeToLive;
    }

    /**
     * Gets the collapse key.
     */
    public String getCollapseKey() {
        return collapseKey;
    }

    /**
     * Gets the delayWhileIdle flag.
     */
    public Boolean isDelayWhileIdle() {
        return delayWhileIdle;
    }

    /**
     * Gets the time to live (in seconds).
     */
    public Integer getTimeToLive() {
        return timeToLive;
    }

    /**
     * Gets the payload data, which is immutable.
     */
    public Map<String, String> getData() {
        return data;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("Message(");
        if (collapseKey != null) {
            builder.append("collapseKey=").append(collapseKey).append(", ");
        }
        if (timeToLive != null) {
            builder.append("timeToLive=").append(timeToLive).append(", ");
        }
        if (delayWhileIdle != null) {
            builder.append("delayWhileIdle=").append(delayWhileIdle).append(", ");
        }
        if (!data.isEmpty()) {
            builder.append("data: {");
            for (Map.Entry<String, String> entry : data.entrySet()) {
                builder.append(entry.getKey()).append("=").append(entry.getValue())
                        .append(",");
            }
            builder.delete(builder.length() - 1, builder.length());
            builder.append("}");
        }
        if (builder.charAt(builder.length() - 1) == ' ') {
            builder.delete(builder.length() - 2, builder.length());
        }
        builder.append(")");
        return builder.toString();
    }

    public static final class Builder {

        private final Map<String, String> data;

        // optional parameters
        private String collapseKey;
        private Boolean delayWhileIdle;
        private Integer timeToLive;

        public Builder() {
            this.data = new LinkedHashMap<String, String>();
        }

        /**
         * Sets the collapseKey property.
         */
        public Builder collapseKey(String value) {
            collapseKey = value;
            return this;
        }

        /**
         * Sets the delayWhileIdle property (default value is {@literal false}).
         */
        public Builder delayWhileIdle(boolean value) {
            delayWhileIdle = value;
            return this;
        }

        /**
         * Sets the time to live, in seconds.
         */
        public Builder timeToLive(int value) {
            timeToLive = value;
            return this;
        }

        /**
         * Adds a key/value pair to the payload data.
         */
        public Builder addData(String key, String value) {
            data.put(key, value);
            return this;
        }

        public Builder setData(Map<String, String> data) {
            this.data.clear();
            this.data.putAll(data);
            return this;
        }

        public Message build() {
            return new Message(this);
        }

    }

}
