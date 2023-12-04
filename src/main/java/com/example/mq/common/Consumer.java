package com.example.mq.common;

import com.example.mq.mqserver.core.BasicProperties;

import java.io.IOException;

/*
 * 只是一个单纯的函数式接口(回调函数). 收到消息之后要处理消息时调用的方法.
 */
@FunctionalInterface
public interface Consumer {
    // Delivery 的意思是 "投递", 这个方法预期是在每次服务器收到消息之后, 来调用.
    // 通过这个方法把消息推送给对应的消费者.
    // (注意! 这里的方法名和参数, 也都是参考 RabbitMQ 展开的)
    void handleDelivery(String consumerTag, BasicProperties basicProperties, byte[] body) throws MqException, IOException;

}
