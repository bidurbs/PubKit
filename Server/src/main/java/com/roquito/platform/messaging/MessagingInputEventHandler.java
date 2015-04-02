/* Copyright (c) 2015 32skills Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.roquito.platform.messaging;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.WebSocketSession;

import com.lmax.disruptor.EventHandler;
import com.roquito.platform.commons.RoquitoKeyGenerator;
import com.roquito.platform.messaging.protocol.ConnAck;
import com.roquito.platform.messaging.protocol.Connect;
import com.roquito.platform.messaging.protocol.Disconnect;
import com.roquito.platform.messaging.protocol.Payload;
import com.roquito.platform.messaging.protocol.PubMessage;
import com.roquito.platform.messaging.protocol.Publish;
import com.roquito.platform.messaging.protocol.SubsAck;
import com.roquito.platform.messaging.protocol.Subscribe;
import com.roquito.platform.model.Application;
import com.roquito.platform.service.ApplicationService;
import com.roquito.platform.service.MessagingService;
import com.roquito.platform.service.QueueService;

public class MessagingInputEventHandler implements EventHandler<MessagingEvent> {
    
    private static Logger logger = LoggerFactory.getLogger(MessagingInputEventHandler.class);
    
    private final RoquitoKeyGenerator keyGenerator = new RoquitoKeyGenerator();

    private ApplicationService applicationService;
    private MessagingService messagingService;
    private QueueService queueService;
    
    public ApplicationService getApplicationService() {
        return applicationService;
    }

    public void setApplicationService(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    public MessagingService getMessagingService() {
        return messagingService;
    }

    public void setMessagingService(MessagingService messagingService) {
        this.messagingService = messagingService;
    }

    public QueueService getQueueService() {
        return queueService;
    }

    public void setQueueService(QueueService queueService) {
        this.queueService = queueService;
    }

    @Override
    public void onEvent(MessagingEvent event, long sequence, boolean endOfBatch) throws Exception {
        logger.debug("Input event received with sequence:" + sequence);
        Payload payload = event.getPayload();
        switch (payload.getType()) {
            case Payload.CONNECT:
                handleConnect((Connect) payload, event.getSession());
                break;
            case Payload.SUBSCRIBE:
                handleSubscribe((Subscribe) payload, event.getSession());
                break;
            case Payload.PUBLISH:
                handlePublish((Publish) payload, event.getSession());
        }
    }
    
    private void handleConnect(Connect connect, WebSocketSession session) {
        boolean valid = validateApplication(connect, session);
        if (!valid) {
            return;
        }
        logger.info("Connecting client {" + connect.getClientId() + "}");
        Connection newConnection = new Connection(connect.getClientId(), session.getId(), connect.getApplicationId(),
                connect.getApiVersion());
        
        // set active session
        messagingService.addSession(session);
        
        // add new connection
        messagingService.addConnection(connect.getClientId(), newConnection);
        
        String accessToken = keyGenerator.getSecureSessionId();
        boolean success = messagingService.saveAccessToken(session.getId(), accessToken);
        if (success) {
            sendPayload(new ConnAck(session.getId(), accessToken), session);
        }
    }
    
    private void handleSubscribe(Subscribe subscribe, WebSocketSession session) {
        boolean tokenValid = validateAccessToken(subscribe.getClientId(), subscribe.getSessionToken(), session);
        if (!tokenValid) {
            return;
        }
        String topic = subscribe.getTopic();
        Connection connection = messagingService.getConnection(subscribe.getClientId());
        if (connection != null) {
            messagingService.subscribeTopic(topic, connection);
            SubsAck subsAck = new SubsAck(subscribe.getClientId());
            sendPayload(subsAck, session);
        } else {
            logger.debug("Subscription failed. No connection found so closing connection");
            Disconnect disconnect = new Disconnect(subscribe.getClientId());
            disconnect.setData("Subscription failed. No connection found so closing connection");
            sendPayload(disconnect, session);
        }
    }
    
    private void handlePublish(Publish publish, WebSocketSession session) {
        String topic = publish.getTopic();
        if (topic == null || "".equals(topic)) {
            logger.debug("Null or empty topic name. Cannot publish data");
            return;
        }
        List<Connection> subscribers = messagingService.getAllSubscribers(topic);
        for (Connection subscriber : subscribers) {
            WebSocketSession subscriberSession = messagingService.getSession(subscriber.getSessionId());
            if (subscriberSession != null) {
                PubMessage pubMessage = new PubMessage(subscriber.getClientId(), publish.getClientId());
                pubMessage.setData(publish.getData());
                pubMessage.addHeader(Payload.APP_ID, publish.getApplicationId());
                pubMessage.setTopic(publish.getTopic());
                
                sendPayload(pubMessage, subscriberSession);
            } else {
                logger.debug("Session not client with client id {" + subscriber.getClientId() + "} "
                        + "and session id {" + subscriber.getSessionId() + "}");
                
                // send push notification if possible for inactive subscriber
                handleInactiveSubscriber(subscriber);
            }
        }
    }
    
    private boolean validateAccessToken(String clientId, String accessToken, WebSocketSession session) {
        boolean tokenValid = messagingService.isAccessTokenValid(accessToken);
        Disconnect disconnect = new Disconnect(clientId);
        if (!tokenValid) {
            disconnect.setData("Access token invalid. Closing the client connection {" + clientId + "}");
            sendPayload(disconnect, session);
            return false;
        }
        return true;
    }
    
    private boolean validateApplication(Connect connect, WebSocketSession session) {
        String applicationId = connect.getApplicationId();
        Disconnect disconnect = new Disconnect(connect.getClientId());
        String closeMessage = null;
        
        boolean valid = true;
        if (applicationId == null) {
            logger.debug("Null application id received, closing connection");
            closeMessage = "Null application id received, closing connection";
            valid = false;
        }
        Application savedApplication = applicationService.findByApplicationId(applicationId);
        if (savedApplication == null) {
            logger.debug("Application not found, closing connection");
            closeMessage = "Application not found, closing connection";
            valid = false;
        }
        if (!savedApplication.getApplicationKey().equals(connect.getApiKey())) {
            logger.debug("Application key does not match, closing connection");
            closeMessage = "Application key does not match, closing connection";
            valid = false;
        }
        if (!valid) {
            disconnect.setData(closeMessage);
            sendPayload(disconnect, session);
        }
        return valid;
    }
    
    private void handleInactiveSubscriber(Connection subscriber) {
    }
    
    public void sendPayload(Payload payload, WebSocketSession session) {
        this.queueService.publishOutputMessageEvent(payload, session);
    }
}