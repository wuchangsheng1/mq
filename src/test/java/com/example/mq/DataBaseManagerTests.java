package com.example.mq;

import com.example.mq.mqserver.core.Binding;
import com.example.mq.mqserver.core.Exchange;
import com.example.mq.mqserver.core.ExchangeType;
import com.example.mq.mqserver.core.MSGQueue;
import com.example.mq.mqserver.datacenter.DataBaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

// 加上这个注解之后, 改类就会被识别为单元测试类.
@SpringBootTest
public class DataBaseManagerTests {
    private DataBaseManager dataBaseManager = new DataBaseManager();

    // 接下来下面这里需要编写多个 方法 . 每个方法都是一个/一组单元测试用例.
    // 还需要做一个准备工作. 需要写两个方法, 分别用于进行 "准备工作" 和 "收尾工作"

    // 使用这个方法, 来执行准备工作. 每个用例执行前, 都要调用这个方法.
    @BeforeEach
    public void setUp() {
        // 由于在 init 中, 需要通过 context 对象拿到 metaMapper 实例的.
        // 所以就需要先把 context 对象给搞出来.
        MqApplication.context = SpringApplication.run(MqApplication.class);
        dataBaseManager.init();
    }

    // 使用这个方法, 来执行收尾工作. 每个用例执行后, 都要调用这个方法.
    @AfterEach
    public void tearDown() {
        // 这里要进行的操作, 就是把数据库给清空~~ (把数据库文件, meta.db 直接删了就行了)
        // 注意, 此处不能直接就删除, 而需要先关闭上述 context 对象!!
        // 此处的 context 对象, 持有了 MetaMapper 的实例, MetaMapper 实例又打开了 meta.db 数据库文件.
        // 如果 meta.db 被别人打开了, 此时的删除文件操作是不会成功的 (Windows 系统的限制, Linux 则没这个问题).
        // 另一方面, 获取 context 操作, 会占用 8080 端口. 此处的 close 也是释放 8080.
        MqApplication.context.close();
        dataBaseManager.deleteDB();
    }

    @Test
    public void testInitTable() {
        // 由于 init 方法, 已经在上面 setUp 中调用过了. 直接在测试用例代码中, 检查当前的数据库状态即可.
        // 直接从数据库中查询. 看数据是否符合预期.
        // 查交换机表, 里面应该有一个数据(匿名的 exchange); 查队列表, 没有数据; 查绑定表, 没有数据.
        List<Exchange> exchangeList = dataBaseManager.selectAllExchanges();
        List<MSGQueue> queueList = dataBaseManager.selectAllQueues();
        List<Binding> bindingList = dataBaseManager.selectAllBindings();

        // 直接打印结果, 通过肉眼来检查结果, 固然也可以. 但是不优雅, 不方便.
        // 更好的办法是使用断言.
        // System.out.println(exchangeList.size());
        // assertEquals 判定结果是不是相等.
        // 注意这俩参数的顺序. 虽然比较相等, 谁在前谁在后, 无所谓.
        // 但是 assertEquals 的形参, 第一个形参叫做 expected (预期的), 第二个形参叫做 actual (实际的)
        Assertions.assertEquals(1, exchangeList.size());
        Assertions.assertEquals("", exchangeList.get(0).getName());
        Assertions.assertEquals(ExchangeType.DIRECT, exchangeList.get(0).getType());
        Assertions.assertEquals(0, queueList.size());
        Assertions.assertEquals(0, bindingList.size());
    }

    private Exchange createTestExchange(String exchangeName) {
        Exchange exchange = new Exchange();
        exchange.setName(exchangeName);
        exchange.setType(ExchangeType.FANOUT);
        exchange.setDurable(true);
        return exchange;
    }

    @Test
    public void testInsertExchange() {
        // 构造一个 Exchange 对象, 插入到数据库中. 再查询出来, 看结果是否符合预期.
        Exchange exchange = createTestExchange("testExchange");
        dataBaseManager.insertExchange(exchange);
        // 插入完毕之后, 查询结果
        List<Exchange> exchangeList = dataBaseManager.selectAllExchanges();
        Assertions.assertEquals(2, exchangeList.size());
        Exchange newExchange = exchangeList.get(1);
        Assertions.assertEquals("testExchange", newExchange.getName());
        Assertions.assertEquals(ExchangeType.FANOUT, newExchange.getType());
        Assertions.assertEquals(true, newExchange.isDurable());
    }

    @Test
    public void testDeleteExchange() {
        // 先构造一个交换机, 插入数据库; 然后再按照名字删除即可!
        Exchange exchange = createTestExchange("testExchange");
        dataBaseManager.insertExchange(exchange);
        List<Exchange> exchangeList = dataBaseManager.selectAllExchanges();
        Assertions.assertEquals(2, exchangeList.size());
        Assertions.assertEquals("testExchange", exchangeList.get(1).getName());

        // 进行删除操作
        dataBaseManager.deleteExchange("testExchange");
        // 再次查询
        exchangeList = dataBaseManager.selectAllExchanges();
        Assertions.assertEquals(1, exchangeList.size());
        Assertions.assertEquals("", exchangeList.get(0).getName());
    }

    private MSGQueue createTestQueue(String queueName) {
        MSGQueue queue = new MSGQueue();
        queue.setName(queueName);
        queue.setDurable(true);
        return queue;
    }

    @Test
    public void testInsertQueue() {
        MSGQueue queue = createTestQueue("testQueue");
        dataBaseManager.insertQueue(queue);

        List<MSGQueue> queueList = dataBaseManager.selectAllQueues();

        Assertions.assertEquals(1, queueList.size());
        MSGQueue newQueue = queueList.get(0);
        Assertions.assertEquals("testQueue", newQueue.getName());
        Assertions.assertEquals(true, newQueue.isDurable());
    }

    @Test
    public void testDeleteQueue() {
        MSGQueue queue = createTestQueue("testQueue");
        dataBaseManager.insertQueue(queue);
        List<MSGQueue> queueList = dataBaseManager.selectAllQueues();
        Assertions.assertEquals(1, queueList.size());
        // 进行删除
        dataBaseManager.deleteQueue("testQueue");
        queueList = dataBaseManager.selectAllQueues();
        Assertions.assertEquals(0, queueList.size());
    }

    private Binding createTestBinding(String exchangeName, String queueName) {
        Binding binding = new Binding();
        binding.setExchangeName(exchangeName);
        binding.setQueueName(queueName);
        binding.setBindingKey("testBindingKey");
        return binding;
    }

    @Test
    public void testInsertBinding() {
        Binding binding = createTestBinding("testExchange", "testQueue");
        dataBaseManager.insertBinding(binding);

        List<Binding> bindingList = dataBaseManager.selectAllBindings();
        Assertions.assertEquals(1, bindingList.size());
        Assertions.assertEquals("testExchange", bindingList.get(0).getExchangeName());
        Assertions.assertEquals("testQueue", bindingList.get(0).getQueueName());
        Assertions.assertEquals("testBindingKey", bindingList.get(0).getBindingKey());
    }

    @Test
    public void testDeleteBinding() {
        Binding binding = createTestBinding("testExchange", "testQueue");
        dataBaseManager.insertBinding(binding);
        List<Binding> bindingList = dataBaseManager.selectAllBindings();
        Assertions.assertEquals(1, bindingList.size());

        // 删除
        Binding toDeleteBinding = createTestBinding("testExchange", "testQueue");
        dataBaseManager.deleteBinding(toDeleteBinding);
        bindingList = dataBaseManager.selectAllBindings();
        Assertions.assertEquals(0, bindingList.size());
    }
}
