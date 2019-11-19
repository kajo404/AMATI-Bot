package main.java.websocket;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.jk.application.model.tweetwall.WebsocketDataItem;

import javax.websocket.DecodeException;
import javax.websocket.Decoder;
import javax.websocket.EndpointConfig;
import java.io.IOException;
import java.io.Reader;

public class DataDecoder implements Decoder.TextStream<WebsocketDataItem> {

    public void destroy() {
        // TODO Auto-generated method stub

    }

    public void init(EndpointConfig arg0) {
        // TODO Auto-generated method stub

    }

    public WebsocketDataItem decode(Reader arg0) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(arg0, new TypeReference<Object>() {
        });
    }

}
