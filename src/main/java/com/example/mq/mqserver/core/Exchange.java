package com.example.mq.mqserver.core;

import java.util.HashMap;
import java.util.Map;

//交换机
public class Exchange {
    private String name;  //此处使用 name 来作为交换机的身份标识。（唯一的）
    private ExchangeType type=ExchangeType.DIRECT;   //交换机类型 DIRECT,FANOUT,TOPIC
    private boolean durable =false;    //表示该交换机是否要持久化存储。true表示需要持久化，false表示不需要持久化

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ExchangeType getType() {
        return type;
    }

    public void setType(ExchangeType type) {
        this.type = type;
    }

    public boolean isDurable() {
        return durable;
    }

    public void setDurable(boolean durable) {
        this.durable = durable;
    }
}
