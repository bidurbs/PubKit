package com.roquito.platform.messaging;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.WebSocketSession;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.roquito.platform.commons.RoquitoKeyGenerator;
import com.roquito.platform.messaging.persistence.MapDB;
import com.roquito.platform.messaging.protocol.ConnAck;
import com.roquito.platform.messaging.protocol.Connect;
import com.roquito.platform.messaging.protocol.Disconnect;
import com.roquito.platform.messaging.protocol.Payload;
import com.roquito.platform.model.Application;
import com.roquito.platform.service.ApplicationService;

public class InputEventHandler implements EventHandler<PayloadEvent> {

    private static Logger logger = LoggerFactory.getLogger(InputEventHandler.class);

    private final RoquitoKeyGenerator keyGenerator = new RoquitoKeyGenerator();
    private ApplicationService applicationService = new ApplicationService();
    private MapDB dbStore = MapDB.getInstance();

    private RingBuffer<PayloadEvent> outputRingBuffer;
    private Disruptor<PayloadEvent> outputEventDisruptor;

    public InputEventHandler() {
	startOutputDisruptor();
    }

    @SuppressWarnings("unchecked")
    private void startOutputDisruptor() {
	Executor executor = Executors.newCachedThreadPool();
	int bufferSize = 1024 * 32;
	outputEventDisruptor = new Disruptor<>(PayloadEvent.EVENT_FACTORY, bufferSize, executor);

	OutputEventHandler outputEventHandler = new OutputEventHandler();
	outputEventDisruptor.handleEventsWith(outputEventHandler);

	outputEventDisruptor.start();
	outputRingBuffer = outputEventDisruptor.getRingBuffer();
    }

    @Override
    public void onEvent(PayloadEvent event, long sequence, boolean endOfBatch) throws Exception {
	logger.debug("Input event received with sequence:" + sequence);
	Payload payload = event.getPayload();
	switch (payload.getType()) {
	case Payload.CONNECT:
	    handleConnect((Connect) payload, event.getSession());
	    break;
	case Payload.SUBSCRIBE:
	    break;
	}
    }

    private void handleConnect(Connect connect, WebSocketSession session) {
	boolean valid = validateApplication(connect, session);
	if (!valid) {
	    return;
	}
	logger.info("Connecting client {" + connect.getClientId() + "}");
	Connection newConnection = new Connection(connect.getClientId(), session.getId(), connect.getApiKey(),
		connect.getApiVersion());

	// set active session
	dbStore.addSession(session);

	// add new connection
	dbStore.addConnection(connect.getClientId(), newConnection);

	String accessToken = keyGenerator.getSecureSessionId();
	boolean success = MapDB.getInstance().saveAccessToken(session.getId(), accessToken);
	if (success) {
	    sendPayload(new ConnAck(session.getId(), accessToken), session);
	}
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

    public void sendPayload(Payload payload, WebSocketSession session) {
	outputRingBuffer.publishEvent(PayloadEvent.EVENT_TRANSLATOR, payload, session);
    }
}
