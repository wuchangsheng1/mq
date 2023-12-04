package com.example.mq;

import com.example.mq.common.MqException;
import com.example.mq.mqserver.core.MSGQueue;
import com.example.mq.mqserver.core.Message;
import com.example.mq.mqserver.datacenter.MessageFileManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import sun.awt.image.ImageWatched;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

@SpringBootTest
public class MessageFileManagerTests {
    private MessageFileManager messageFileManager = new MessageFileManager();

    private static final String queueName1 = "testQueue1";
    private static final String queueName2 = "testQueue2";

    // 这个方法是每个用例执行之前的准备工作
    @BeforeEach
    public void setUp() throws IOException {
        // 准备阶段, 创建出两个队列, 以备后用
        messageFileManager.createQueueFiles(queueName1);
        messageFileManager.createQueueFiles(queueName2);
    }

    // 这个方法就是每个用例执行完毕之后的收尾工作
    @AfterEach
    public void tearDown() throws IOException {
        // 收尾阶段, 就把刚才的队列给干掉.
        messageFileManager.destroyQueueFiles(queueName1);
        messageFileManager.destroyQueueFiles(queueName2);
    }

    @Test
    public void testCreateFiles() {
        // 创建队列文件已经在上面 setUp 阶段执行过了. 此处主要是验证看看文件是否存在.
        File queueDataFile1 = new File("./data/" + queueName1 + "/queue_data.txt");
        Assertions.assertEquals(true, queueDataFile1.isFile());
        File queueStatFile1 = new File("./data/" + queueName1 + "/queue_stat.txt");
        Assertions.assertEquals(true, queueStatFile1.isFile());

        File queueDataFile2 = new File("./data/" + queueName2 + "/queue_data.txt");
        Assertions.assertEquals(true, queueDataFile2.isFile());
        File queueStatFile2 = new File("./data/" + queueName2 + "/queue_stat.txt");
        Assertions.assertEquals(true, queueStatFile2.isFile());
    }

    @Test
    public void testReadWriteStat() {
        MessageFileManager.Stat stat = new MessageFileManager.Stat();
        stat.totalCount = 100;
        stat.validCount = 50;
        // 此处就需要使用反射的方式, 来调用 writeStat 和 readStat 了.
        // Java 原生的反射 API 其实非常难用~~
        // 此处使用 Spring 帮我们封装好的 反射 的工具类.
        ReflectionTestUtils.invokeMethod(messageFileManager, "writeStat", queueName1, stat);

        // 写入完毕之后, 再调用一下读取, 验证读取的结果和写入的数据是一致的.
        MessageFileManager.Stat newStat = ReflectionTestUtils.invokeMethod(messageFileManager, "readStat", queueName1);
        Assertions.assertEquals(100, newStat.totalCount);
        Assertions.assertEquals(50, newStat.validCount);
        System.out.println("测试 readStat 和 writeStat 完成!");
    }

    private MSGQueue createTestQueue(String queueName) {
        MSGQueue queue = new MSGQueue();
        queue.setName(queueName);
        queue.setDurable(true);
        return queue;
    }

    private Message createTestMessage(String content) {
        Message message = Message.createMessageWithId("testRoutingKey", null, content.getBytes());
        return message;
    }

    @Test
    public void testSendMessage() throws IOException, MqException, ClassNotFoundException {
        // 构造出消息, 并且构造出队列.
        Message message = createTestMessage("testMessage");
        // 此处创建的 queue 对象的 name, 不能随便写, 只能用 queueName1 和 queueName2. 需要保证这个队列对象
        // 对应的目录和文件啥的都存在才行.
        MSGQueue queue = createTestQueue(queueName1);

        // 调用发送消息方法
        messageFileManager.sendMessage(queue, message);

        // 检查 stat 文件.
        MessageFileManager.Stat stat = ReflectionTestUtils.invokeMethod(messageFileManager, "readStat", queueName1);
        Assertions.assertEquals(1, stat.totalCount);
        Assertions.assertEquals(1, stat.validCount);

        // 检查 data 文件
        LinkedList<Message> messages = messageFileManager.loadAllMessageFromQueue(queueName1);
        Assertions.assertEquals(1, messages.size());
        Message curMessage = messages.get(0);
        Assertions.assertEquals(message.getMessageId(), curMessage.getMessageId());
        Assertions.assertEquals(message.getRoutingKey(), curMessage.getRoutingKey());
        Assertions.assertEquals(message.getDeliverMode(), curMessage.getDeliverMode());
        // 比较两个字节数组的内容是否相同, 不能直接使用 assertEquals 了.
        Assertions.assertArrayEquals(message.getBody(), curMessage.getBody());

        System.out.println("message: " + curMessage);
    }

    @Test
    public void testLoadAllMessageFromQueue() throws IOException, MqException, ClassNotFoundException {
        // 往队列中插入 100 条消息, 然后验证看看这 100 条消息从文件中读取之后, 是否和最初是一致的.
        MSGQueue queue = createTestQueue(queueName1);
        List<Message> expectedMessages = new LinkedList<>();
        for (int i = 0; i < 100; i++) {
            Message message = createTestMessage("testMessage" + i);
            messageFileManager.sendMessage(queue, message);
            expectedMessages.add(message);
        }

        // 读取所有消息
        LinkedList<Message> actualMessages = messageFileManager.loadAllMessageFromQueue(queueName1);
        Assertions.assertEquals(expectedMessages.size(), actualMessages.size());
        for (int i = 0; i < expectedMessages.size(); i++) {
            Message expectedMessage = expectedMessages.get(i);
            Message actualMessage = actualMessages.get(i);
            System.out.println("[" + i + "] actualMessage=" + actualMessage);

            Assertions.assertEquals(expectedMessage.getMessageId(), actualMessage.getMessageId());
            Assertions.assertEquals(expectedMessage.getRoutingKey(), actualMessage.getRoutingKey());
            Assertions.assertEquals(expectedMessage.getDeliverMode(), actualMessage.getDeliverMode());
            Assertions.assertArrayEquals(expectedMessage.getBody(), actualMessage.getBody());
            Assertions.assertEquals(0x1, actualMessage.getIsValid());
        }
    }

    @Test
    public void testDeleteMessage() throws IOException, MqException, ClassNotFoundException {
        // 创建队列, 写入 10 个消息. 删除其中的几个消息. 再把所有消息读取出来, 判定是否符合预期.
        MSGQueue queue = createTestQueue(queueName1);
        List<Message> expectedMessages = new LinkedList<>();
        for (int i = 0; i < 10; i++) {
            Message message = createTestMessage("testMessage" + i);
            messageFileManager.sendMessage(queue, message);
            expectedMessages.add(message);
        }

        // 删除其中的三个消息
        messageFileManager.deleteMessage(queue, expectedMessages.get(7));
        messageFileManager.deleteMessage(queue, expectedMessages.get(8));
        messageFileManager.deleteMessage(queue, expectedMessages.get(9));

        // 对比这里的内容是否正确.
        LinkedList<Message> actualMessages = messageFileManager.loadAllMessageFromQueue(queueName1);
        Assertions.assertEquals(7, actualMessages.size());
        for (int i = 0; i < actualMessages.size(); i++) {
            Message expectedMessage = expectedMessages.get(i);
            Message actualMessage = actualMessages.get(i);
            System.out.println("[" + i + "] actualMessage=" + actualMessage);

            Assertions.assertEquals(expectedMessage.getMessageId(), actualMessage.getMessageId());
            Assertions.assertEquals(expectedMessage.getRoutingKey(), actualMessage.getRoutingKey());
            Assertions.assertEquals(expectedMessage.getDeliverMode(), actualMessage.getDeliverMode());
            Assertions.assertArrayEquals(expectedMessage.getBody(), actualMessage.getBody());
            Assertions.assertEquals(0x1, actualMessage.getIsValid());
        }
    }

    @Test
    public void testGC() throws IOException, MqException, ClassNotFoundException {
        // 先往队列中写 100 个消息. 获取到文件大小.
        // 再把 100 个消息中的一半, 都给删除掉(比如把下标为偶数的消息都删除)
        // 再手动调用 gc 方法, 检测得到的新的文件的大小是否比之前缩小了.
        MSGQueue queue = createTestQueue(queueName1);
        List<Message> expectedMessages = new LinkedList<>();
        for (int i = 0; i < 100; i++) {
            Message message = createTestMessage("testMessage" + i);
            messageFileManager.sendMessage(queue, message);
            expectedMessages.add(message);
        }

        // 获取 gc 前的文件大小
        File beforeGCFile = new File("./data/" + queueName1 + "/queue_data.txt");
        long beforeGCLength = beforeGCFile.length();

        // 删除偶数下标的消息
        for (int i = 0; i < 100; i += 2) {
            messageFileManager.deleteMessage(queue, expectedMessages.get(i));
        }

        // 手动调用 gc
        messageFileManager.gc(queue);

        // 重新读取文件, 验证新的文件的内容是不是和之前的内容匹配
        LinkedList<Message> actualMessages = messageFileManager.loadAllMessageFromQueue(queueName1);
        Assertions.assertEquals(50, actualMessages.size());
        for (int i = 0; i < actualMessages.size(); i++) {
            // 把之前消息偶数下标的删了, 剩下的就是奇数下标的元素了.
            // actual 中的 0 对应 expected 的 1
            // actual 中的 1 对应 expected 的 3
            // actual 中的 2 对应 expected 的 5
            // actual 中的 i 对应 expected 的 2 * i + 1
            Message expectedMessage = expectedMessages.get(2 * i + 1);
            Message actualMessage = actualMessages.get(i);

            Assertions.assertEquals(expectedMessage.getMessageId(), actualMessage.getMessageId());
            Assertions.assertEquals(expectedMessage.getRoutingKey(), actualMessage.getRoutingKey());
            Assertions.assertEquals(expectedMessage.getDeliverMode(), actualMessage.getDeliverMode());
            Assertions.assertArrayEquals(expectedMessage.getBody(), actualMessage.getBody());
            Assertions.assertEquals(0x1, actualMessage.getIsValid());
        }
        // 获取新的文件的大小
        File afterGCFile = new File("./data/" + queueName1 + "/queue_data.txt");
        long afterGCLength = afterGCFile.length();
        System.out.println("before: " + beforeGCLength);
        System.out.println("after: " + afterGCLength);
        Assertions.assertTrue(beforeGCLength > afterGCLength);
    }
}
