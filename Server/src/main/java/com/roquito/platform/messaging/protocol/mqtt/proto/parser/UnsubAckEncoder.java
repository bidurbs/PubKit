/*
 * Copyright (c) 2012-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package com.roquito.platform.messaging.protocol.mqtt.proto.parser;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

import com.roquito.platform.messaging.protocol.mqtt.proto.messages.AbstractMessage;
import com.roquito.platform.messaging.protocol.mqtt.proto.messages.UnsubAckMessage;

/**
 *
 * @author andrea
 */
class UnsubAckEncoder extends DemuxEncoder<UnsubAckMessage> {
    
    @Override
    protected void encode(ChannelHandlerContext chc, UnsubAckMessage msg, ByteBuf out) {
        out.writeByte(AbstractMessage.UNSUBACK << 4).
                writeBytes(Utils.encodeRemainingLength(2)).
                writeShort(msg.getMessageID());
    }
}