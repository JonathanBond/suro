/*
 * Copyright 2013 Netflix, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.netflix.suro.queue;

import com.netflix.suro.message.serde.SerDe;
import com.netflix.suro.thrift.TMessageSet;
import org.apache.hadoop.io.DataInputBuffer;
import org.apache.hadoop.io.DataOutputBuffer;
import org.apache.hadoop.io.WritableUtils;

import java.nio.ByteBuffer;

public class MessageSetSerDe implements SerDe<TMessageSet> {
    private DataOutputBuffer outBuffer = new DataOutputBuffer();
    private DataInputBuffer inBuffer = new DataInputBuffer();

    @Override
    public TMessageSet deserialize(byte[] payload) {
        inBuffer.reset(payload, payload.length);

        try {
            String hostname = WritableUtils.readString(inBuffer);
            String app = WritableUtils.readString(inBuffer);
            String serde = WritableUtils.readString(inBuffer);
            byte compression = inBuffer.readByte();
            long crc = inBuffer.readLong();
            byte[] messages = new byte[inBuffer.readInt()];
            inBuffer.read(messages);

            return new TMessageSet(
                    hostname,
                    app,
                    serde,
                    compression,
                    crc,
                    ByteBuffer.wrap(messages)
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public byte[] serialize(TMessageSet payload) {
        try {
            outBuffer.reset();

            WritableUtils.writeString(outBuffer, payload.getHostname());
            WritableUtils.writeString(outBuffer, payload.getApp());
            WritableUtils.writeString(outBuffer, payload.getSerde());
            outBuffer.writeByte(payload.getCompression());
            outBuffer.writeLong(payload.getCrc());
            outBuffer.writeInt(payload.getMessages().length);
            outBuffer.write(payload.getMessages());

            return ByteBuffer.wrap(outBuffer.getData(), 0, outBuffer.getLength()).array();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toString(byte[] payload) {
        return deserialize(payload).toString();
    }
}
