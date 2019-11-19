package main.java.websocket;


import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ullink.slack.simpleslackapi.SlackPreparedMessage;
import com.ullink.slack.simpleslackapi.SlackSession;
import main.java.storage.SlackStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.ClientEndpoint;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import java.io.IOException;
import java.util.LinkedHashMap;

@ClientEndpoint(encoders = DataEncoder.class, decoders = DataDecoder.class)
public class WebSocketClient {


    private final Logger logger = LoggerFactory.getLogger("WebSocketClient.class");
    public Session s;
    private SlackSession session;
    private String channel;
    private SlackStorage storage;
    public WebSocketClient() {
    }

    public WebSocketClient(SlackSession session, String channel, SlackStorage storage) {
        this.session = session;
        this.channel = channel;
        this.storage = storage;
    }

    @OnOpen
    public void onOpen(Session p) {
        s = p;
        logger.debug("session opened");

    }


    @OnMessage
    public void onMessage(String question) throws IOException {
        logger.debug("received message via websocket");
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        WebsocketDataItem q = mapper.readValue(question, WebsocketDataItem.class);
        String messageType = q.getType();
        switch(messageType) {
            case "admin-answer":
                handleAdminAnswer(q);
                break;
            case "student-question":
                handleQuestion(q);
                break;
            default: logger.warn("Websocket message not handled of type: " + messageType + " with content: " + q.getContent().toString());
        }
    }

    private void handleAdminAnswer(WebsocketDataItem q){
        //todo handle admin answer via websocket
        String preText = "Admin Answer:\n";
        LinkedHashMap<String, String> sq = (LinkedHashMap) q.content;
        String slackChannel = sq.get("slackChannel");
        String answer = sq.get("answer");
        if(answer.startsWith("MARKANSWER:")){
            String replyInputSourceId = answer.split(":")[1];
                session.addReactionToMessage(session.findChannelById(slackChannel),replyInputSourceId,"white_check_mark");
        } else if(answer.startsWith("ADMINNOTE:")){
            String note = answer.substring(10);
            SlackPreparedMessage preparedMessage = new SlackPreparedMessage.Builder()
                    .withUnfurl(false)
                    .withMessage(note)
                    .withThreadTimestamp(sq.get("questionInputSourceId"))
                    .build();
            session.sendMessage(session.findChannelById(slackChannel),preparedMessage);
        }

        else {
            SlackPreparedMessage preparedMessage = new SlackPreparedMessage.Builder()
                    .withUnfurl(false)
                    .withMessage(preText + answer)
                    .withThreadTimestamp(sq.get("questionInputSourceId"))
                    .build();
            session.sendMessage(session.findChannelById(slackChannel),preparedMessage);
        }

    }

    private void handleQuestion(WebsocketDataItem q){
        LinkedHashMap<String, String> sq = (LinkedHashMap) q.content;
        String message = sq.get("content");
        if (!message.contains("?")) {
            message += "?";
        }
        String ask = sq.get("displayName") + ": " + message;
        storage.addSMSMapping(ask, sq.get("author"));
        session.sendMessage(session.findChannelByName(channel), ask);
    }
}