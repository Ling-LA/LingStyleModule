package io.github.ling.style.core;

public enum FaultKind {
    QUOTE("引用读取"), IMAGE("图片处理"), MODEL_NETWORK("模型网络"), MODEL_HTTP("模型 HTTP"),
    RESPONSE_JSON("接口 JSON"), RESPONSE_SCHEMA("接口响应结构"), DECISION_JSON("插话 JSON"),
    DECISION_SCHEMA("插话判断结构"), MODEL_OUTPUT("模型内容"), MODEL("模型生成"),
    WEB_TIMEOUT("网页超时"), WEB_LOAD("网页加载"), WEB_QUOTA("网页额度"), WEB_RESULT("网页结果"),
    QUEUE("任务排队"), SEND("QQ 发送"), RECEIVE("消息接收"), CANCELLED("配置变更取消"), OTHER("其他异常");
    public final String title;
    FaultKind(String title){this.title=title;}
    public static FaultKind fromMessage(String message){
        if(message.contains("配置已变化")||message.contains("账号已切换"))return CANCELLED;
        if(message.contains("队列已满")||message.contains("排队超时"))return QUEUE;
        if(message.contains("网页")||message.contains("WebView")){
            if(message.contains("超时"))return WEB_TIMEOUT;
            if(message.contains("额度"))return WEB_QUOTA;
            if(message.contains("加载")||message.contains("HTTP")||message.contains("渲染")||message.contains("WebView"))return WEB_LOAD;
            return WEB_RESULT;
        }
        if(message.contains("提交")||message.contains("发送"))return SEND;
        if(message.contains("接收"))return RECEIVE;
        return OTHER;
    }
}
