package com.example.mq.common;

/*
 * 自定义一个异常类. 如果是我们的 mq 的业务逻辑中, 出现的异常, 就抛出这个异常对象, 同时在构造方法中指定出现异常的原因信息
 */
public class MqException extends Exception {
    public MqException(String reason) {
        super(reason);
    }
}
