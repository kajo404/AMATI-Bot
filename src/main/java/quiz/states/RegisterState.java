package main.java.quiz.states;

import main.java.quiz.QuizHandler;
import de.tum.jk.application.model.presenter.QuizFullException;

public class RegisterState extends QuizState {
    public RegisterState(QuizHandler quizhandler) {
        super(quizhandler);
    }

    @Override
    public void startQuiz() {
        this.quizhandler.setState(new VotingState(quizhandler));
    }

    @Override
    public void registerQuiz(boolean single) {
        //not supported
    }

    @Override
    public void addQuestion(String question) {
        this.quizhandler.getTeacherQuestion().setContent(question);
    }

    @Override
    public void addAnswer(String answer) {
        try {
            this.quizhandler.getTeacherQuestion().addAnswer(answer);
        } catch (QuizFullException e) {
        }
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
    public String getResults() {
        //not supported
        return null;
    }
    @Override
    public String getQuiz() {
        return this.quizhandler.getTeacherQuestion().showQuiz();
    }
}
