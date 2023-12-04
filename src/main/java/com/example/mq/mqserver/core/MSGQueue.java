package com.example.mq.mqserver.core;

import com.example.mq.common.ConsumerEnv;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

//MSG -> Message
//表示一个存储消息的队列
public class MSGQueue {
    private String name;   // 表示队列的身份标识.

    private boolean durable = false; // 表示队列是否持久化, true 表示持久化保存, false 表示不持久化.

    // 当前队列都有哪些消费者订阅了.
    private List<ConsumerEnv> consumerEnvList = new ArrayList<>();
    // 记录当前取到了第几个消费者. 方便实现轮询策略.
    private AtomicInteger consumerSeq = new AtomicInteger(0);

    // 添加一个新的订阅者
    public void addConsumerEnv(ConsumerEnv consumerEnv) {
        consumerEnvList.add(consumerEnv);
    }

    // 订阅者的删除暂时先不考虑.
    // 挑选一个订阅者, 用来处理当前的消息. (按照轮询的方式)
    public ConsumerEnv chooseConsumer() {
        if (consumerEnvList.size() == 0) {
            // 该队列没有人订阅的
            return null;
        }
        // 计算一下当前要取的元素的下标.
        int index = consumerSeq.get() % consumerEnvList.size();
        consumerSeq.getAndIncrement();
        return consumerEnvList.get(index);
    }
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isDurable() {
        return durable;
    }

    public void setDurable(boolean durable) {
        this.durable = durable;
    }
}
