package com.example.mq.mqserver.mapper;

import com.example.mq.mqserver.core.Binding;
import com.example.mq.mqserver.core.Exchange;
import com.example.mq.mqserver.core.MSGQueue;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface MetaMapper {
    // 提供三个核心建表方法
    void createExchangeTable();
    void createQueueTable();
    void createBindingTable();

    // 插入 删除 和 查找
    void insertExchange(Exchange exchange);
    void deleteExchange(String exchangeName);
    List<Exchange> selectAllExchanges();
    void insertQueue(MSGQueue queue);
    void deleteQueue(String queueName);
    List<MSGQueue> selectAllQueues();
    void insertBinding(Binding binding);
    void deleteBinding(Binding binding);
    List<Binding> selectAllBindings();
}
