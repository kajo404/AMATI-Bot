package main.java.storage;

import de.tum.jk.application.model.students.Reply;
import de.tum.jk.application.model.students.StudentQuestion;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;

public class SlackStorage {

    private ArrayList<StudentQuestion> possibleQuestions;
    private HashMap<String, String> smsMapping;
    private HashMap<String, String> anonMapping;

    @Setter
    private boolean dev;

    public SlackStorage(){
        possibleQuestions = new ArrayList<>();
        smsMapping =  new HashMap<>();
        anonMapping = new HashMap<>();
    }

    public void addPossibleQuestion(StudentQuestion q){
        int size = possibleQuestions.size();
        int threshold = 100;
        if(dev){
            threshold = 3;
        }
        if(size>threshold){
            possibleQuestions.remove(0);
        }
        possibleQuestions.add(q);
    }

    public StudentQuestion retrieveFromBacklog(String slackID){
        StudentQuestion find = null;
        for(StudentQuestion q : possibleQuestions){
            if(q.getInputSourceId().equals(slackID)){
                find = q;
                possibleQuestions.remove(q);
                break;
            }
        }
        return find;
    }

    public void addAnonMapping(String userID, String questionText){
        this.anonMapping.put(questionText,userID);
    }

    public void mapAnon(StudentQuestion question){
        if(this.anonMapping.containsKey(question.getContent())){
            question.setAnonymousAuthor(this.anonMapping.get(question.getContent()));
            this.anonMapping.remove(question.getContent());
        }
    }

    public void addSMSMapping(String questionText, String author){
        smsMapping.put(questionText,author);
    }

    public String getAuthorForSMSQuestion(String questionText){
        String s = smsMapping.get(questionText);
        if(s!=null){
            smsMapping.remove(questionText);
        }
        return s;
    }

    public boolean addReply(String thread, Reply reply) {
        for(StudentQuestion qs : possibleQuestions){
            if(qs.getInputSourceId().equals(thread)){
                if(qs.getReplies() == null){
                    qs.setReplies(new ArrayList<>());
                }
                qs.addReply(reply);
                return true;
            }
        }
        return false;
    }


}
