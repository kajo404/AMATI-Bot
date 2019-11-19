package main.java.quiz;

import de.tum.jk.application.model.presenter.SingleShotQuestion;
import de.tum.jk.application.model.presenter.TeacherQuestion;
import lombok.Getter;
import lombok.Setter;
import main.java.quiz.states.IdleState;
import main.java.quiz.states.QuizState;

import java.util.ArrayList;
import java.util.Map;


public class QuizHandler {

    @Getter
    @Setter
    private TeacherQuestion teacherQuestion;

    @Getter
    @Setter
    private QuizState state;

    public QuizHandler() {
        this.teacherQuestion = new SingleShotQuestion();
        this.state = new IdleState(this);
    }

    public void reqisterQuiz(boolean single) {
        state.registerQuiz(single);
    }

    public void addQuestion(String question) {
        state.addQuestion(question);
    }

    public void addAnswer(String answer) {
        state.addAnswer(answer);
    }

    public void startQuiz() {
        state.startQuiz();
    }

    public void addVote(String user, String vote) {
        state.addVote(user, vote);
    }

    public void stopQuiz() {
        state.stopQuiz();
    }

    public String getResults() {
        return state.getResults();
    }

    public String getQuiz() {
        return state.getQuiz();
    }

    public void abort() {
        this.state.abort();
    }

    public ArrayList<String> getWinners() {
        Map<String, String> votes = this.teacherQuestion.getVotes();
        ArrayList<String> correctUsers = new ArrayList<>();
        //create copy to avoid manipulating original dataset
        votes.forEach((key, value) -> {
            if (!value.equals(teacherQuestion.getCorrectAnswerKey())) {
                correctUsers.add(key);
            }
        });
        return correctUsers;
    }
}
