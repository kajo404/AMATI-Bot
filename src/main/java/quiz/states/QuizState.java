package main.java.quiz.states;

import de.tum.jk.application.model.presenter.MultiShotQuestion;
import main.java.quiz.QuizHandler;

public abstract class QuizState {

    protected QuizHandler quizhandler;

    public QuizState(QuizHandler quizhandler){
        this.quizhandler = quizhandler;
    }

    public abstract void registerQuiz(boolean single);

    public abstract void addQuestion(String question);

    public abstract void addAnswer(String answer);

    public abstract void startQuiz();

    public abstract void addVote(String user, String vote);

    public abstract void stopQuiz();

    public void abort(){
        this.quizhandler.setTeacherQuestion(new MultiShotQuestion()); //clear data
        this.quizhandler.setState(new IdleState(quizhandler)); //set to inital state
    }

    public abstract String getResults();

    public abstract String getQuiz();

}
