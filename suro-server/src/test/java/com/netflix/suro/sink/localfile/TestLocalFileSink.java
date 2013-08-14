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

package com.netflix.suro.sink.localfile;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.suro.connection.TestConnectionPool;
import com.netflix.suro.jackson.DefaultObjectMapper;
import com.netflix.suro.message.Message;
import com.netflix.suro.message.MessageSetReader;
import com.netflix.suro.message.serde.StringSerDe;
import com.netflix.suro.queue.MessageQueue;
import com.netflix.suro.queue.QueueManager;
import com.netflix.suro.sink.Sink;
import com.netflix.suro.thrift.ServiceStatus;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import static junit.framework.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestLocalFileSink {
    @Before
    @After
    public void clean() throws IOException {
        FileUtils.deleteDirectory(new File(TestTextFileWriter.dir));
    }

    @Test
    public void testDefaultParameters() throws IOException {
        final String localFileSinkSpec = "{\n" +
                "    \"type\": \"LocalFileSink\",\n" +
                "    \"outputDir\": \"" + TestTextFileWriter.dir + "\"\n" +
                "    }\n" +
                "}";

        ObjectMapper mapper = new DefaultObjectMapper();
        mapper.setInjectableValues(new InjectableValues() {
            @Override
            public Object findInjectableValue(Object valueId, DeserializationContext ctxt, BeanProperty forProperty, Object beanInstance) {
                if (valueId.equals("queueManager")) {
                    return new QueueManager();
                } else {
                    return null;
                }
            }
        });
        Sink sink = mapper.readValue(localFileSinkSpec, new TypeReference<Sink>(){});
        sink.open();

        assertNull(sink.recvNotify());

        for (Message m : new MessageSetReader(TestConnectionPool.createMessageSet(100000))) {
            sink.writeTo(m, new StringSerDe());
        }

        sink.close();

        int count = 0;
        File dir = new File(TestTextFileWriter.dir);
        File[] files = dir.listFiles();
        for (File file : files) {
            assertTrue(file.getName().contains(".done"));
            if (file.getName().contains("crc") == false) {
                BufferedReader br = new BufferedReader(new FileReader(file));
                String line = null;
                while ((line = br.readLine()) != null) {
                    assertTrue(line.contains("testMessage"));
                    ++count;
                }
                br.close();
            }
        }
        assertEquals(count, 100000);

    }

    @Test
    public void testWithPeriodRotation() throws IOException, InterruptedException {
        final String localFileSinkSpec = "{\n" +
                "    \"type\": \"LocalFileSink\",\n" +
                "    \"outputDir\": \"" + TestTextFileWriter.dir + "\",\n" +
                "    \"writer\": {\n" +
                "        \"type\": \"text\"\n" +
                "    },\n" +
                "    \"maxFileSize\": 100000000,\n" +
                "    \"minPercentFreeDisk\": 50,\n" +
                "    \"rotationPeriod\": \"PT5s\",\n" +
                "    \"notify\": {\n" +
                "        \"type\": \"queue\"\n" +
                "    }\n" +
                "}";

        ObjectMapper mapper = new DefaultObjectMapper();
        mapper.setInjectableValues(new InjectableValues() {
            @Override
            public Object findInjectableValue(Object valueId, DeserializationContext ctxt, BeanProperty forProperty, Object beanInstance) {
                if (valueId.equals("queueManager")) {
                    return new QueueManager();
                } else {
                    return null;
                }
            }
        });
        Sink sink = mapper.readValue(localFileSinkSpec, new TypeReference<Sink>(){});
        sink.open();
        assertNull(sink.recvNotify());

        for (Message m : new MessageSetReader(TestConnectionPool.createMessageSet(100))) {
            sink.writeTo(m, new StringSerDe());
            Thread.sleep(100);
        }

        sink.close();

        int count = 0;
        int fileCount = 0;
        File dir = new File(TestTextFileWriter.dir);
        File[] files = dir.listFiles();
        for (File file : files) {
            assertTrue(file.getName().contains(".done"));
            if (file.getName().contains("crc") == false) {
                ++fileCount;
                BufferedReader br = new BufferedReader(new FileReader(file));
                String line = null;
                while ((line = br.readLine()) != null) {
                    assertTrue(line.contains("testMessage"));
                    ++count;
                }
                br.close();
            }
        }
        assertEquals(count, 100);
        assertTrue(fileCount > 1);
    }

    @Test
    public void testSpaceChecker() throws Exception {
        final String localFileSinkSpec = "{\n" +
                "    \"type\": \"LocalFileSink\",\n" +
                "    \"outputDir\": \"" + TestTextFileWriter.dir + "\",\n" +
                "    \"writer\": {\n" +
                "        \"type\": \"text\"\n" +
                "    },\n" +
                "    \"maxFileSize\": 10240,\n" +
                "    \"minPercentFreeDisk\": 50,\n" +
                "    \"rotationPeriod\": \"PT1m\",\n" +
                "    \"notify\": {\n" +
                "        \"type\": \"queue\"\n" +
                "    }\n" +
                "}";

        ObjectMapper mapper = new DefaultObjectMapper();
        final QueueManager queueManager = new QueueManager();
        final MessageQueue queue = new MessageQueue(null, queueManager);
        queueManager.registerService(queue);

        final LocalFileSink.SpaceChecker spaceChecker = mock(LocalFileSink.SpaceChecker.class);
        when(spaceChecker.hasEnoughSpace()).thenReturn(false);

        mapper.setInjectableValues(new InjectableValues() {
            @Override
            public Object findInjectableValue(Object valueId, DeserializationContext ctxt, BeanProperty forProperty, Object beanInstance) {
                if (valueId.equals("queueManager")) {
                    return queueManager;
                } else if (valueId.equals("spaceChecker")) {
                    return spaceChecker;
                } else {
                    return null;
                }
            }
        });
        assertEquals(queue.getStatus(), ServiceStatus.ALIVE);
        assertEquals(queueManager.getStatus(), QueueManager.OK);

        Sink sink = mapper.readValue(localFileSinkSpec, new TypeReference<Sink>(){});
        sink.open();
        assertEquals(queue.getStatus(), ServiceStatus.WARNING);
        assertEquals(queueManager.getStatus(), QueueManager.IN_ERROR);
        assertNull(sink.recvNotify());

        when(spaceChecker.hasEnoughSpace()).thenReturn(true);

        for (Message m : new MessageSetReader(TestConnectionPool.createMessageSet(100000))) {
            sink.writeTo(m, new StringSerDe());
        }

        sink.close();

        int count = 0;
        File dir = new File(TestTextFileWriter.dir);
        File[] files = dir.listFiles();
        for (File file : files) {
            assertTrue(file.getName().contains(".done"));
            if (file.getName().contains("crc") == false) {
                assertTrue(file.length() < 12000);
                BufferedReader br = new BufferedReader(new FileReader(file));
                String line = null;
                while ((line = br.readLine()) != null) {
                    assertTrue(line.contains("testMessage"));
                    ++count;
                }
                br.close();
            }
        }
        assertEquals(count, 100000);
    }

    @Test
    public void testWithSizeRotation() throws IOException {
        final String localFileSinkSpec = "{\n" +
                "    \"type\": \"LocalFileSink\",\n" +
                "    \"outputDir\": \"" + TestTextFileWriter.dir + "\",\n" +
                "    \"writer\": {\n" +
                "        \"type\": \"text\"\n" +
                "    },\n" +
                "    \"maxFileSize\": 10240,\n" +
                "    \"minPercentFreeDisk\": 50,\n" +
                "    \"rotationPeriod\": \"PT1m\",\n" +
                "    \"notify\": {\n" +
                "        \"type\": \"queue\"\n" +
                "    }\n" +
                "}";

        ObjectMapper mapper = new DefaultObjectMapper();
        mapper.setInjectableValues(new InjectableValues() {
            @Override
            public Object findInjectableValue(Object valueId, DeserializationContext ctxt, BeanProperty forProperty, Object beanInstance) {
                if (valueId.equals("queueManager")) {
                    return new QueueManager();
                } else {
                    return null;
                }

            }
        });
        Sink sink = mapper.readValue(localFileSinkSpec, new TypeReference<Sink>(){});
        sink.open();
        assertNull(sink.recvNotify());

        for (Message m : new MessageSetReader(TestConnectionPool.createMessageSet(100000))) {
            sink.writeTo(m, new StringSerDe());
        }

        sink.close();

        int count = 0;
        File dir = new File(TestTextFileWriter.dir);
        File[] files = dir.listFiles();
        for (File file : files) {
            assertTrue(file.getName().contains(".done"));
            if (file.getName().contains("crc") == false) {
                assertTrue(file.length() < 12000);
                BufferedReader br = new BufferedReader(new FileReader(file));
                String line = null;
                while ((line = br.readLine()) != null) {
                    assertTrue(line.contains("testMessage"));
                    ++count;
                }
                br.close();
            }
        }
        assertEquals(count, 100000);
    }
}
