package main.java.quiz.states;

import main.java.quiz.QuizHandler;

public class FinishedState extends QuizState {

    public FinishedState(QuizHandler quizhandler) {
        super(quizhandler);
    }

    @Override
    public void registerQuiz(boolean single) {
        //not supported
    }

    @Override
    public void addQuestion(String question) {
        //not supported
    }

    @Override
    public void addAnswer(String answer) {
        // not supported
    }

    @Override
    public void startQuiz() {
        //not supported
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
        this.quizhandler.setState(new IdleState(quizhandler));
        return this.quizhandler.getTeacherQuestion().showResults();
    }

    @Override
    public String getQuiz() {
        return this.quizhandler.getTeacherQuestion().showQuiz();
    }
}
