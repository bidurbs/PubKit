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
package com.roquito.platform.messaging.protocol.pkmp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketSession;

import com.lmax.disruptor.EventHandler;
import com.roquito.platform.commons.RoquitoKeyGenerator;
import com.roquito.platform.messaging.protocol.pkmp.proto.PKMPConnAck;
import com.roquito.platform.messaging.protocol.pkmp.proto.PKMPConnect;
import com.roquito.platform.messaging.protocol.pkmp.proto.PKMPDisconnect;
import com.roquito.platform.messaging.protocol.pkmp.proto.PKMPBasePayload;
import com.roquito.platform.messaging.protocol.pkmp.proto.PKMPPayload;
import com.roquito.platform.messaging.protocol.pkmp.proto.PKMPPubAck;
import com.roquito.platform.messaging.protocol.pkmp.proto.PKMPPubMessage;
import com.roquito.platform.messaging.protocol.pkmp.proto.PKMPPublish;
import com.roquito.platform.messaging.protocol.pkmp.proto.PKMPSubsAck;
import com.roquito.platform.messaging.protocol.pkmp.proto.PKMPSubscribe;
import com.roquito.platform.messaging.protocol.pkmp.proto.PKMPUnSubsAck;
import com.roquito.platform.messaging.protocol.pkmp.proto.PKMPUnSubscribe;
import com.roquito.platform.model.Application;
import com.roquito.platform.model.DataConstants;
import com.roquito.platform.model.DeviceInfo;
import com.roquito.platform.notification.SimpleGcmPushNotification;
import com.roquito.platform.service.ApplicationService;
import com.roquito.platform.service.DeviceInfoService;
import com.roquito.platform.service.MessagingService;
import com.roquito.platform.service.QueueService;
import com.roquito.platform.service.StatsService;

public class PKMPInBoundEventHandler implements EventHandler<PKMPEvent> {
    
    private static Logger LOG = LoggerFactory.getLogger(PKMPInBoundEventHandler.class);
    
    private final RoquitoKeyGenerator keyGenerator = new RoquitoKeyGenerator();
    
    private ApplicationService applicationService;
    private MessagingService messagingService;
    private QueueService queueService;
    private DeviceInfoService deviceInfoService;
    private StatsService statsService;
    
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
    
    public void setDeviceInfoService(DeviceInfoService deviceInfoService) {
        this.deviceInfoService = deviceInfoService;
    }
    
    public void setStatsService(StatsService statsService) {
        this.statsService = statsService;
    }
    
    @Override
    public void onEvent(PKMPEvent event, long sequence, boolean endOfBatch) throws Exception {
        LOG.debug("Input event received with sequence:" + sequence);
        PKMPPayload pKMPPayload = event.getPayload();
        switch (pKMPPayload.getType()) {
            case PKMPBasePayload.CONNECT:
                handleConnect(new PKMPConnect(pKMPPayload), event.getSession());
                break;
            case PKMPBasePayload.SUBSCRIBE:
                handleSubscribe(new PKMPSubscribe(pKMPPayload), event.getSession());
                break;
            case PKMPBasePayload.UNSUBSCRIBE:
                handleUnSubscribe(new PKMPUnSubscribe(pKMPPayload), event.getSession());
                break;
            case PKMPBasePayload.PUBLISH:
                handlePublish(new PKMPPublish(pKMPPayload), event.getSession());
                break;
            case PKMPBasePayload.DISCONNECT:
                sendDisconnect(pKMPPayload.getClientId(), event.getSession(), "");
                break;
            default:
                break;
        }
    }
    
    private void handleConnect(PKMPConnect pKMPConnect, WebSocketSession session) {
        boolean valid = validateApplication(pKMPConnect, session);
        if (!valid) {
            sendPayload(new PKMPConnAck(pKMPConnect.getClientId(), PKMPConnAck.APPLICATION_INVALID), session);
            return;
        }
        LOG.debug("Connecting client {" + pKMPConnect.getClientId() + "}");
        PKMPConnection existingConnection = messagingService.getConnection(pKMPConnect.getClientId());
        if (existingConnection != null) {
            existingConnection.setSessionId(session.getId());
            messagingService.addConnection(pKMPConnect.getClientId(), existingConnection);
        } else {
            PKMPConnection newConnection = new PKMPConnection(pKMPConnect.getClientId(), pKMPConnect.getSourceUserId(),
                    session.getId(), pKMPConnect.getApplicationId(), pKMPConnect.getApiVersion());
            // add new connection
            messagingService.addConnection(pKMPConnect.getClientId(), newConnection);
        }
        String accessToken = keyGenerator.getSecureSessionId();
        boolean success = messagingService.saveAccessToken(session.getId(), accessToken);
        if (success) {
            // set active session
            messagingService.addSession(session);
            LOG.debug("Connected client {" + pKMPConnect.getClientId() + "}");
            sendPayload(new PKMPConnAck(pKMPConnect.getClientId(), session.getId(), accessToken), session);
        } else {
            LOG.debug("PKMPConnection failed for client {" + pKMPConnect.getClientId() + "}");
            sendPayload(new PKMPConnAck(pKMPConnect.getClientId(), PKMPConnAck.CONNECTION_FAILED), session);
        }
    }
    
    private void handleSubscribe(PKMPSubscribe pKMPSubscribe, WebSocketSession session) {
        String topic = pKMPSubscribe.getTopic();
        if (topic == null || StringUtils.isEmpty(topic)) {
            LOG.error("Client {" + pKMPSubscribe.getClientId() + "} failed to subscribe topic {" + pKMPSubscribe.getTopic()
                    + "}. Invalid topic");
            
            PKMPSubsAck errorAck = new PKMPSubsAck(pKMPSubscribe.getClientId(), topic, PKMPSubsAck.INVALID_TOPIC);
            sendPayload(errorAck, session);
            
            return;
        }
        boolean tokenValid = validateAccessToken(pKMPSubscribe.getClientId(), pKMPSubscribe.getSessionToken(), session);
        if (!tokenValid) {
            LOG.error("Client {" + pKMPSubscribe.getClientId() + "} failed to subscribe topic {" + topic
                    + "}. Invalid token");
            PKMPSubsAck errorAck = new PKMPSubsAck(pKMPSubscribe.getClientId(), topic, PKMPSubsAck.INVALID_TOKEN);
            sendPayload(errorAck, session);
            
            return;
        }
        
        PKMPConnection pKMPConnection = messagingService.getConnection(pKMPSubscribe.getClientId());
        if (pKMPConnection != null && pKMPConnection.getSessionId().equals(session.getId())) {
            messagingService.subscribeTopic(topic, pKMPConnection);
            LOG.debug("Client id {" + pKMPSubscribe.getClientId() + "} subscribed to topic {" + topic + "}");
            
            PKMPSubsAck pKMPSubsAck = new PKMPSubsAck(pKMPSubscribe.getClientId(), topic);
            sendPayload(pKMPSubsAck, session);
        } else {
            LOG.error("Client {" + pKMPSubscribe.getClientId() + "} failed to subscribe topic {" + topic
                    + "}. No connection found so closing connection");
            
            PKMPSubsAck errorAck = new PKMPSubsAck(pKMPSubscribe.getClientId(), topic, PKMPSubsAck.CONNECTION_ERROR);
            sendPayload(errorAck, session);
        }
    }
    
    private void handleUnSubscribe(PKMPUnSubscribe unsubscribe, WebSocketSession session) {
        String topic = unsubscribe.getTopic();
        if (topic == null || StringUtils.isEmpty(topic)) {
            LOG.error("Client {" + unsubscribe.getClientId() + "} failed to unsubscribe topic {"
                    + unsubscribe.getTopic() + "}. Invalid topic");
            
            PKMPUnSubsAck errorAck = new PKMPUnSubsAck(unsubscribe.getClientId(), topic, PKMPSubsAck.INVALID_TOPIC);
            sendPayload(errorAck, session);
            
            return;
        }
        boolean tokenValid = validateAccessToken(unsubscribe.getClientId(), unsubscribe.getSessionToken(), session);
        if (!tokenValid) {
            LOG.error("Client {" + unsubscribe.getClientId() + "} failed to unsubscribe topic {" + topic
                    + "}. Invalid token");
            PKMPUnSubsAck errorAck = new PKMPUnSubsAck(unsubscribe.getClientId(), topic, PKMPSubsAck.INVALID_TOKEN);
            sendPayload(errorAck, session);
            
            return;
        }
        
        PKMPConnection pKMPConnection = messagingService.getConnection(unsubscribe.getClientId());
        if (pKMPConnection != null && pKMPConnection.getSessionId().equals(session.getId())) {
            messagingService.subscribeTopic(topic, pKMPConnection);
            LOG.debug("Client id {" + unsubscribe.getClientId() + "} unsubscribed to topic {" + topic + "}");
            
            PKMPUnSubsAck pKMPUnSubsAck = new PKMPUnSubsAck(unsubscribe.getClientId(), topic);
            sendPayload(pKMPUnSubsAck, session);
        } else {
            LOG.error("Client {" + unsubscribe.getClientId() + "} failed to unsubscribe topic {" + topic
                    + "}. No connection found so closing connection");
            
            PKMPUnSubsAck errorAck = new PKMPUnSubsAck(unsubscribe.getClientId(), topic, PKMPSubsAck.CONNECTION_ERROR);
            sendPayload(errorAck, session);
        }
    }
    
    private void handlePublish(PKMPPublish pKMPPublish, WebSocketSession session) {
        String topic = pKMPPublish.getTopic();
        if (topic == null || "".equals(topic)) {
            LOG.debug("PKMPPublish failed. Null or empty topic name.");
            
            PKMPPubAck errorAck = new PKMPPubAck(pKMPPublish.getClientId(), topic, PKMPPubAck.INVALID_TOPIC);
            sendPayload(errorAck, session);
            
            return;
        }
        List<PKMPConnection> subscribers = messagingService.getAllSubscribers(topic);
        for (PKMPConnection subscriber : subscribers) {
            if (subscriber.getClientId().equals(pKMPPublish.getClientId())) {
                continue;
            }
            WebSocketSession subscriberSession = messagingService.getSession(subscriber.getSessionId());
            if (subscriberSession != null && subscriberSession.isOpen()) {
                PKMPPubMessage pKMPPubMessage = new PKMPPubMessage(subscriber.getClientId(), pKMPPublish.getClientId());
                
                pKMPPubMessage.setData(pKMPPublish.getData());
                pKMPPubMessage.addHeader(PKMPBasePayload.APP_ID, pKMPPublish.getApplicationId());
                pKMPPubMessage.setTopic(pKMPPublish.getTopic());
                
                sendPayload(pKMPPubMessage, subscriberSession);
            } else {
                LOG.debug("Session not found for client with client id {" + subscriber.getClientId() + "} "
                        + "and session id {" + subscriber.getSessionId() + "}");
                
                // send push notification if possible for inactive subscriber
                handleInactiveSubscriber(subscriber, pKMPPublish.getData());
                
                if (subscriberSession != null) {
                    sendDisconnect(subscriber.getClientId(), subscriberSession, "");
                }
            }
            statsService.incrMessageCount();
        }
        sendPayload(new PKMPPubAck(pKMPPublish.getClientId(), topic), session);
    }
    
    private void sendDisconnect(String clientId, WebSocketSession session, String data) {
        LOG.info("Closing and invalidating client session for {" + clientId + "}");
        
        messagingService.removeConnection(clientId);
        messagingService.removeSession(session);
        messagingService.invalidateSessionToken(clientId);
        
        PKMPDisconnect pKMPDisconnect = new PKMPDisconnect(clientId);
        pKMPDisconnect.setData(data);
        sendPayload(pKMPDisconnect, session);
    }
    
    private boolean validateAccessToken(String clientId, String accessToken, WebSocketSession session) {
        boolean tokenValid = messagingService.isAccessTokenValid(accessToken);
        if (!tokenValid) {
            PKMPDisconnect pKMPDisconnect = new PKMPDisconnect(clientId);
            LOG.debug("Invalid access token. Closing the client connection {" + clientId + "}");
            pKMPDisconnect.setData("Invalid access token. Closing the client connection {" + clientId + "}");
            sendPayload(pKMPDisconnect, session);
            
            return false;
        }
        return true;
    }
    
    private boolean validateApplication(PKMPConnect pKMPConnect, WebSocketSession session) {
        String applicationId = pKMPConnect.getApplicationId();
        
        String closeMessage = null;
        boolean valid = true;
        if (applicationId == null) {
            LOG.debug("Null application id received, closing connection");
            closeMessage = "Null application id received, closing connection";
            valid = false;
        }
        //TODO: Make sure this is not blocking this thread for too long!
        Application savedApplication = applicationService.findByApplicationId(applicationId);
        if (savedApplication == null) {
            LOG.debug("Application not found, closing connection");
            closeMessage = "Application not found, closing connection";
            valid = false;
        }
        if (!savedApplication.getApplicationKey().equals(pKMPConnect.getApiKey())) {
            LOG.debug("Application key does not match, closing connection");
            closeMessage = "Application key does not match, closing connection";
            valid = false;
        }
        if (!valid) {
            PKMPDisconnect pKMPDisconnect = new PKMPDisconnect(pKMPConnect.getClientId());
            pKMPDisconnect.setData(closeMessage);
            sendPayload(pKMPDisconnect, session);
        }
        return valid;
    }
    
    private void sendPayload(PKMPBasePayload pKMPBasePayload, WebSocketSession session) {
        this.queueService.publishOutputMessageEvent(pKMPBasePayload, session);
    }
    
    /*
     * TODO: See if this is not blocking message processor too often!!
     */
    private void handleInactiveSubscriber(PKMPConnection subscriber, String data) {
        long startTime = System.currentTimeMillis() / 1000;
        
        List<DeviceInfo> devices = deviceInfoService.getDeviceInfoForUserId(subscriber.getAppId(), subscriber.getClientId(), null);
        if (devices != null && !devices.isEmpty()) {
            List<String> registrationIds = new ArrayList<String>();
            for (DeviceInfo device : devices) {
                if (DataConstants.DEVICE_TYPE_ANDROID.equals(device.getDeviceType())) {
                    registrationIds.add(device.getRegistrationId());
                } else if (DataConstants.DEVICE_TYPE_IOS.equals(device.getDeviceType())) {
                } else {
                    //ignore!
                }
            }
            if (!registrationIds.isEmpty()) {
                SimpleGcmPushNotification gcm = new SimpleGcmPushNotification();
                
                gcm.setApplicationId(subscriber.getAppId());
                gcm.setApplicationVersion(subscriber.getApiVersion());
                gcm.setRegistrationIds(registrationIds);
                
                Map<String, String> dataMap = new HashMap<>();
                dataMap.put("data", data);
                gcm.setData(dataMap);
                
                gcm.setMulticast(registrationIds.size() > 1);
                
                queueService.sendGcmPushNotification(gcm);
            }
        }
        long endTime = System.currentTimeMillis() / 1000;
        LOG.info("Time spent on sending push notification to inactive subscriber in seconds:"+(endTime - startTime));
    }
}
