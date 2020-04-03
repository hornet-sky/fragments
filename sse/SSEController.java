package com.example.demo;

import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sse")
public class SSEController {
    private int retry = 3000; // 这个参数好像没什么效果，即使服务器端超过3秒没响应仍然正常，没有触发浏览器端的重连
    @GetMapping("/push_msg")
    public void pushMsg(HttpServletRequest req, HttpServletResponse resp) {
        String lastEventID = req.getHeader("Last-Event-ID");
        /*
         Content-Type: text/event-stream
         Cache-Control: no-cache
         Connection: keep-alive
         */
        resp.setContentType("text/event-stream;charset=utf-8");
        resp.setHeader("Cache-Control", "no-cache");
        resp.setHeader("Connection", "keep-alive");
        int currentId = lastEventID == null
                ? 0 // 重新开始
                : Integer.parseInt(lastEventID) + 1; // 断线重连（即使后台服务宕机了，浏览器端仍然不断地尝试重连）
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try {
            PrintWriter writer = resp.getWriter();
            while (currentId < 4) {
                Thread.sleep(120000);
                System.out.println("currentId " + ++currentId);
                String data = df.format(new Date());
                sendMessage (data, currentId, writer);
                if (writer.checkError()) { // 可能是浏览器端手动调用了eventSource.close()，也可能是直接关闭了浏览器。
                    System.out.println("浏览器端断开了连接");
                    return;
                }
            }
            // writer.close(); 服务器端主动关闭通道后，浏览器端还是会自动重连，
            // 只能是给浏览器端发送一个自定义的关闭事件，浏览器端接收到这个事件后调用eventSource.close()，这样才有效果。
            sendCloseCommand (writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void sendMessage (String data, int id, PrintWriter writer) {
        String content = generateContent(data, id);
        writer.write(content);
        writer.flush();
    }
    
    private void sendCloseCommand (PrintWriter writer) {
        String content = generateContent("bye", -1, "closenow"); // 自定义的 close now 事件
        writer.write(content);
        writer.flush();
    }
    
    private String generateContent (String data, int id) {
        return generateContent(data, id, "message", retry);
    }
    
    private String generateContent (String data, int id, String event) {
        return generateContent(data, id, event, retry);
    }
    
    private String generateContent (String data, int id, String event, int retry) {
        // :this is a test stream\n\n  以冒号开头的行，表示注释。通常，服务器每隔一段时间就会向浏览器发送一个注释，保持连接不中断。
        // data: another message\n
        // data: with two lines \n\n 数据data可以有多行，最后一行用两个换行“\n\n”表示结束
        String contentTemplate = "retry:{0}\nevent:{1}\nid:{2}\ndata:{3}\n\n";
        return MessageFormat.format(contentTemplate, retry, event, id, data);
    }
    /* 甚至可以发送JSON格式的数据：
     data: {\n
     data: "foo": "bar",\n
     data: "baz", 555\n
     data: }\n\n
     */
}

// https://www.cnblogs.com/xiongzaiqiren/archive/2017/05/18/6874283.html
