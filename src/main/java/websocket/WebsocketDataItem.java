package main.java.websocket;

public class WebsocketDataItem {

    public static final String STUDENT_QUESTION = "student-question";
    public static final String TEACHER_QUESTION = "teacher-question";


    String type;
    Object content;

    public WebsocketDataItem() {

    }

    public WebsocketDataItem(String type, Object content) {
        super();
        this.type = type;
        this.content = content;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Object getContent() {
        return content;
    }

    public void setContent(Object content) {
        this.content = content;
    }


}
