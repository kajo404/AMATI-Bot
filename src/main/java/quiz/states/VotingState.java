package main.java.quiz.states;

import main.java.quiz.QuizHandler;

public class VotingState extends QuizState{

    public VotingState(QuizHandler quizhandler) {
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
        //not supported
    }

    @Override
    public void startQuiz() {
        //not supported
    }

    @Override
    public void addVote(String user, String vote) {
        this.quizhandler.getTeacherQuestion().updateVoteByUser(user,vote);
    }

    @Override
    public void stopQuiz() {
        this.quizhandler.setState(new FinishedState(this.quizhandler));
    }

    @Override
    public String getResults() {
        return null;
    }

    @Override
    public String getQuiz() {
        return this.quizhandler.getTeacherQuestion().showQuiz();
    }
}
