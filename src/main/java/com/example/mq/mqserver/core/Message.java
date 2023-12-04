package com.example.mq.mqserver.core;

import java.io.Serializable;
import java.util.UUID;

//表示一个要传递的消息
//此处的 Message 对象, 需要能够在网络上传输, 并且也需要能写入到文件中.
// 此时就需要针对 Message 进行序列化和反序列化. 此处使用标准库自带的 序列化/反序列化 操作.
public class Message implements Serializable {
    private BasicProperties basicProperties = new BasicProperties();  //message的基本属性
    private byte[] body;  //消息主体

    // 持久化的情况下,消息在文件中存储的位置使用下列的两个偏移量来进行表示. [offsetBeg, offsetEnd)
    // 这俩属性并不需要被序列化保存到文件中,因为消息一旦被写入文件之后,所在的位置就固定了,并不需要单独存储.
    // 这俩属性可以让内存中的 Message 对象, 能够快速找到对应的硬盘上的 Message 的位置.
    private transient long offsetBeg = 0;  // 消息数据的开头距离文件开头的位置偏移(字节)  transient 可以让此变量不被序列化
    private transient long offsetEnd = 0;  // 消息数据的结尾距离文件开头的位置偏移(字节)  transient 可以让此变量不被序列化
    private byte isValid = 0x1; // 使用这个属性表示该消息在文件中是否是有效消息. (针对文件中的消息, 如果删除, 使用逻辑删除的方式)
    // 0x1 表示有效. 0x0 表示无效.

    // 创建一个工厂方法, 让工厂方法帮我们封装一下创建 Message 对象的过程.
    // 这个方法中创建的 Message 对象, 会自动生成唯一的 MessageId
    // 万一 routingKey 和 basicProperties 里的 routingKey 冲突, 以外面的为主.
    public static Message createMessageWithId(String routingKey, BasicProperties basicProperties, byte[] body) {
        Message message = new Message();
        if (basicProperties != null) {
            message.setBasicProperties(basicProperties);
        }
        // 此处生成的 MessageId 以 M- 作为前缀.
        message.setMessageId("M-" + UUID.randomUUID());
        message.setRoutingKey(routingKey);
        message.body = body;
        // 此处是把 body 和 basicProperties 先设置出来. 他们是 Message 的核心内容.
        // 而 offsetBeg, offsetEnd, isValid, 则是消息持久化的时候才会用到. 在把消息写入文件之前再进行设定.
        // 此处只是在内存中创建一个 Message 对象.
        return message;
    }

    public int getDeliverMode() {
        return basicProperties.getDeliverMode();
    }

    public void setDeliverMode(int mode) {
        basicProperties.setDeliverMode(mode);
    }
    public String getMessageId() {
        return basicProperties.getMessageId();
    }

    public void setMessageId(String messageId) {
        basicProperties.setMessageId(messageId);
    }

    public String getRoutingKey() {
        return basicProperties.getRoutingKey();
    }

    public void setRoutingKey(String routingKey) {
        basicProperties.setRoutingKey(routingKey);
    }
    public BasicProperties getBasicProperties() {
        return basicProperties;
    }

    public void setBasicProperties(BasicProperties basicProperties) {
        this.basicProperties = basicProperties;
    }

    public byte[] getBody() {
        return body;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }

    public long getOffsetBeg() {
        return offsetBeg;
    }

    public void setOffsetBeg(long offsetBeg) {
        this.offsetBeg = offsetBeg;
    }

    public long getOffsetEnd() {
        return offsetEnd;
    }

    public void setOffsetEnd(long offsetEnd) {
        this.offsetEnd = offsetEnd;
    }

    public byte getIsValid() {
        return isValid;
    }

    public void setIsValid(byte isValid) {
        this.isValid = isValid;
    }
}
