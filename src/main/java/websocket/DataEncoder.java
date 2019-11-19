package main.java.websocket;


import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.jk.application.model.tweetwall.WebsocketDataItem;

import javax.websocket.EncodeException;
import javax.websocket.Encoder;
import javax.websocket.EndpointConfig;
import java.io.IOException;
import java.io.Writer;


public class DataEncoder implements Encoder.TextStream<WebsocketDataItem> {

    public void destroy() {
        // TODO Auto-generated method stub

    }


    public void init(EndpointConfig arg0) {
        // TODO Auto-generated method stub

    }

    public void encode(WebsocketDataItem object, Writer writer) throws IOException {
        ObjectMapper x = new ObjectMapper();
        String a = x.writeValueAsString(object);
        writer.write(a);

    }


}
