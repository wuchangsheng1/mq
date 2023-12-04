package com.example.mq.mqserver.datacenter;

import com.example.mq.MqApplication;
import com.example.mq.mqserver.core.Binding;
import com.example.mq.mqserver.core.Exchange;
import com.example.mq.mqserver.core.ExchangeType;
import com.example.mq.mqserver.core.MSGQueue;
import com.example.mq.mqserver.mapper.MetaMapper;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.util.List;

/*
 * 通过这个类, 来整合数据库操作.
 */
public class DataBaseManager {
    // 要做的是从 Spring 中拿到现成的对象
    private MetaMapper metaMapper;

    // 针对数据库进行初始化
    public void init() {
        // 手动的获取到 MetaMapper
        metaMapper = MqApplication.context.getBean(MetaMapper.class);

        if (!checkDBExists()) {
            // 数据库不存在, 就进行建建库表操作
            // 先创建一个 data 目录
            File dataDir = new File("./data");
            dataDir.mkdirs();
            // 创建数据表
            createTable();
            // 插入默认数据
            createDefaultData();
            System.out.println("[DataBaseManager] 数据库初始化完成!");
        } else {
            // 数据库已经存在了, 啥都不必做即可
            System.out.println("[DataBaseManager] 数据库已经存在!");
        }
    }

    public void deleteDB() {
        File file = new File("./data/meta.db");
        boolean ret = file.delete();
        if (ret) {
            System.out.println("[DataBaseManager] 删除数据库文件成功!");
        } else {
            System.out.println("[DataBaseManager] 删除数据库文件失败!");
        }

        File dataDir = new File("./data");
        // 使用 delete 删除目录的时候, 需要保证目录是空的.
        ret = dataDir.delete();
        if (ret) {
            System.out.println("[DataBaseManager] 删除数据库目录成功!");
        } else {
            System.out.println("[DataBaseManager] 删除数据库目录失败!");
        }
    }

    private boolean checkDBExists() {
        File file = new File("./data/meta.db");
        if (file.exists()) {
            return true;
        }
        return false;
    }

    // 这个方法用来建表.
    // 建库操作并不需要手动执行. (不需要手动创建 meta.db 文件)
    // 首次执行这里的数据库操作的时候, 就会自动的创建出 meta.db 文件来 (MyBatis 帮我们完成的)
    private void createTable() {
        metaMapper.createExchangeTable();
        metaMapper.createQueueTable();
        metaMapper.createBindingTable();
        System.out.println("[DataBaseManager] 创建表完成!");
    }

    // 给数据库表中, 添加默认的数据.
    // 此处主要是添加一个默认的交换机.
    // RabbitMQ 里有一个这样的设定: 带有一个 匿名 的交换机, 类型是 DIRECT.
    private void createDefaultData() {
        // 构造一个默认的交换机.
        Exchange exchange = new Exchange();
        exchange.setName("");
        exchange.setType(ExchangeType.DIRECT);
        exchange.setDurable(true);
        metaMapper.insertExchange(exchange);
        System.out.println("[DataBaseManager] 创建初始数据完成!");
    }

    // 把其他的数据库的操作, 也在这个类中封装一下.
    public void insertExchange(Exchange exchange) {
        metaMapper.insertExchange(exchange);
    }

    public List<Exchange> selectAllExchanges() {
        return metaMapper.selectAllExchanges();
    }

    public void deleteExchange(String exchangeName) {
        metaMapper.deleteExchange(exchangeName);
    }

    public void insertQueue(MSGQueue queue) {
        metaMapper.insertQueue(queue);
    }

    public List<MSGQueue> selectAllQueues() {
        return metaMapper.selectAllQueues();
    }

    public void deleteQueue(String queueName) {
        metaMapper.deleteQueue(queueName);
    }

    public void insertBinding(Binding binding) {
        metaMapper.insertBinding(binding);
    }

    public List<Binding> selectAllBindings() {
        return metaMapper.selectAllBindings();
    }

    public void deleteBinding(Binding binding) {
        metaMapper.deleteBinding(binding);
    }
}
