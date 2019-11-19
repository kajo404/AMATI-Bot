package main.java.quiz.states;


import main.java.quiz.QuizHandler;
import de.tum.jk.application.model.presenter.MultiShotQuestion;
import de.tum.jk.application.model.presenter.SingleShotQuestion;

public class IdleState extends QuizState{
    public IdleState(QuizHandler quizhandler) {
        super(quizhandler);
    }

    @Override
    public void startQuiz() {
        //not supported
    }

    @Override
    public void registerQuiz(boolean single) {
        if(single){
            this.quizhandler.setTeacherQuestion(new SingleShotQuestion());
        } else {
            this.quizhandler.setTeacherQuestion((new MultiShotQuestion()));
        }
        this.quizhandler.setState(new RegisterState(quizhandler));
    }

    @Override
    public void addQuestion(String question) {
    }

    @Override
    public void addAnswer(String answer) {
    }

    @Override
    public void addVote(String user, String vote) {
        //not supported
    }

    @Override
    public void stopQuiz() {
        //not supported
    }

    @Override
    public void abort() {
        //does nothing
    }

    @Override
    public String getResults() {
        //does nothing
        return null;
    }
    @Override
    public String getQuiz() {
        return null;
    }
}