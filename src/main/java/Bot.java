package main.java;

import com.ullink.slack.simpleslackapi.*;
import com.ullink.slack.simpleslackapi.events.SlackMessagePosted;
import com.ullink.slack.simpleslackapi.impl.SlackSessionFactory;
import de.tum.jk.application.model.presenter.TeacherQuestion;
import de.tum.jk.application.model.presenterTool.ActiveSlide;
import de.tum.jk.application.model.students.DataItem;
import de.tum.jk.application.model.students.Feedback;
import de.tum.jk.application.model.students.Reply;
import de.tum.jk.application.model.students.StudentQuestion;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.DefaultApi;
import io.swagger.client.model.Score;
import main.java.quiz.QuizHandler;
import main.java.storage.SlackStorage;
import main.java.websocket.WebSocketClient;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.WebSocketContainer;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Bot {

    private String token;
    private String contentQuestions;
    private String generalQuestions;
    private String exerciseQuestions;
    private String quiz;
    private ArrayList<String> admins;
    private String serverURL;
    private String socketURL;
    private SlackStorage storage;
    private String apiUser;
    private String apiPassword;
    private SlackSession session;
    private DefaultApi api;

    //variable for dev mode
    private boolean dev;

    //variables for quiz mode
    private QuizHandler quizhandler;


    private final Logger logger = LoggerFactory.getLogger("Bot.class");
    private ArrayList<String> tutors;

    public Bot() {
        config();
        this.storage = new SlackStorage();
        api = new DefaultApi();
        String apiPath = serverURL + "api";

        ApiClient c = new ApiClient();
        c.setBasePath(apiPath);
        c.setUsername(apiUser);
        c.setPassword(apiPassword);

        this.api.setApiClient(c);
        quizhandler = new QuizHandler();
        this.session = SlackSessionFactory.createWebSocketSlackSession(token);
        try {
            session.connect();
        } catch (IOException e) {
            e.printStackTrace();
        }
        setupWebsocket(this.socketURL);
    }

    private void setupWebsocket(String url) {
        if (url == null || url.equals("")) {
            return;
        }
        //final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        container.setDefaultMaxSessionIdleTimeout(0);

        WebSocketClient client = new WebSocketClient(session, contentQuestions, storage);
        try {
            container.connectToServer(client, URI.create(url));

        } catch (DeploymentException | IOException e) {

            logger.debug("websocket not connected");
        }

    }

    public void addQuestionModule() {
        addDirectMessageListener();
        addReactionListeners();
        if (generalQuestions != null && generalQuestions.length() != 0) {
            addQuestionsChannelListener(generalQuestions);
        }
        if (contentQuestions != null && contentQuestions.length() != 0) {
            addQuestionsChannelListener(contentQuestions);
        }
        if (exerciseQuestions != null && exerciseQuestions.length() != 0) {
            addQuestionsChannelListener(exerciseQuestions);
        }


    }

    private void addQuestionsChannelListener(String channel) {
        session.addMessagePostedListener((SlackMessagePosted event, SlackSession session1) -> {
            // if I'm only interested on a certain contentQuestions
            // I can filter out messages coming from other channels
            SlackChannel theChannel = session1.findChannelByName(channel);
            //only listen to specified contentQuestions
            if (!theChannel.getId().equals(event.getChannel().getId())) {
                return;
            }
            //ignore own message -> Could be anon q
            boolean self = session1.sessionPersona().getId().equals(event.getSender().getId());
            String messageContent = event.getMessageContent();
            //check for thread update
            if (event.getMsgSubType().getCode().equals("MESSAGE_REPLIED") || event.getMsgSubType().getCode().equals("FILE_MENTION")) {
                return;
            }
            //check for threaded message
            String thread = event.getThreadTimestamp();
            ActiveSlide context = buildContext(messageContent, thread);
            if (thread != null) {
                logger.debug("Found threaded message with id " + event.getTimestamp());
                //preparation for slide references
                if (!self) {
                    String message;
                    if (context != null) {
                        Reply replyToDelete = null;
                        SlackAttachment screenshot;
                        ArrayList<SlackAttachment> att = new ArrayList<>();
                        if (messageContent.contains("!ref")) {
                            try {
                                replyToDelete = api.getReplyForQuestionByContent(thread, "Reference found");
                            } catch (ApiException e) {
                                e.printStackTrace();
                            }
                            try {
                                StudentQuestion q;
                                q = api.getQuestionByInputSourceId(thread);
                                if (q != null) {
                                    q.setRefURL(context.getActiveSlide());
                                    q.setRefSlide(context.getSlideNumber());
                                    q.setRefSlideset(context.getPresentationNumber());
                                    api.postQuestion(q);
                                }
                            } catch (ApiException e) {
                                logger.debug("Error while setting reference for question " + thread);
                                e.printStackTrace();
                            }
                            screenshot = new SlackAttachment("Reference", new Date().toString(), "SlideSet:" + context.getPresentationNumber() + " Slide:" + context.getSlideNumber(), "");
                            screenshot.setImageUrl(serverURL + context.getActiveSlide());
                            message = "Reference found";
                        } else {
                            try {
                                replyToDelete = api.getReplyForQuestionByContent(thread, "Question ID");
                            } catch (ApiException e) {
                                e.printStackTrace();
                            }
                            try {
                                StudentQuestion q = api.getQuestionByInputSourceId(thread);
                                if (q != null) {
                                    q.setSlideSet(context.getPresentationNumber());
                                    q.setSlide(context.getSlideNumber());
                                    q.setSlideURL(context.getActiveSlide());
                                    api.postQuestion(q);
                                }
                            } catch (ApiException e) {
                                logger.debug("Error while setting context for question " + thread);
                                e.printStackTrace();
                            }

                            screenshot = new SlackAttachment("Context", new Date().toString(), "SlideSet:" + context.getPresentationNumber() + " Slide:" + context.getSlideNumber(), "");
                            screenshot.setImageUrl(serverURL + context.getActiveSlide());
                            message = "Question ID:" + thread;
                        }

                        if (replyToDelete != null) {
                            logger.debug("trying to delete reply with id " + replyToDelete.getInputSourceId());
                            String id = replyToDelete.getInputSourceId();
                            try {
                                api.deleteReply(id);
                                session1.deleteMessage(id, event.getChannel());
                            } catch (ApiException e) {
                                logger.debug("Reply with id: " + id + " in Question: " + thread + " could not be deleted");
                                e.printStackTrace();
                            }
                        }
                        att.add(screenshot);
                        SlackPreparedMessage preparedMessage = new SlackPreparedMessage.Builder()
                                .withUnfurl(false)
                                .withMessage(message)
                                .withThreadTimestamp(thread)
                                .withAttachments(att)
                                .build();
                        session1.sendMessage(event.getChannel(), preparedMessage);
                        Reply reply = new Reply(messageContent, event.getSender().getId(), event.getTimestamp(), DataItem.INPUTSOURCE_SLACK, event.getChannel().getId());
                        reply.setDisplayName(event.getSender().getUserName());
                        reply.setInputSource(DataItem.INPUTSOURCE_SLACK);
                        //try to save possible question
                        boolean possible = storage.addReply(event.getThreadTimestamp(), reply);
                        if (!possible) {
                            try {
                                api.postReply(thread, reply);
                            } catch (ApiException e) {
                                e.printStackTrace();
                            }
                        }
                        return;
                    }
                }
                //no commands matched so we save a generic reply, also works for possible Questions
                Reply reply = new Reply(messageContent, event.getSender().getId(), event.getTimestamp(), DataItem.INPUTSOURCE_SLACK, event.getChannel().getId());
                if (self) {
                    reply.setBotUser(true);
                }
                reply.setDisplayName(event.getSender().getUserName());
                boolean possible = storage.addReply(event.getThreadTimestamp(), reply);
                if (!possible) {
                    try {
                        api.postReply(thread, reply);
                    } catch (ApiException e) {
                        e.printStackTrace();
                    }
                }
                if (self & messageContent.startsWith("Admin Answer")) {
                    session1.addReactionToMessage(event.getChannel(),event.getTimeStamp(),"white_check_mark");
                }
                return;
            }
            //highscore command
            if (messageContent.toLowerCase().contains("!score")) {
                Score score = null;
                try {
                    score = api.getScoreforInputSource(DataItem.INPUTSOURCE_SLACK);
                } catch (ApiException e) {
                    e.printStackTrace();
                }
                Collection<SlackUser> users = session1.getUsers();
                Iterator<SlackUser> iterator = users.iterator();
                Map<String, String> idToDisplayName = new HashMap<>();
                while (iterator.hasNext()) {
                    SlackUser user = iterator.next();
                    idToDisplayName.put(user.getId(), user.getUserName());
                }
                ArrayList<String> skip = new ArrayList<>(admins);
                skip.addAll(tutors);
                StringBuilder sb = new StringBuilder("Here are the current scores. Keep up the good work :smile:\n");
                sb.append(":white_check_mark:*Best Answers:*\n");
                sb.append(orderedMapToString((LinkedHashMap) score.getBestAnswersCount(), 5, idToDisplayName, skip));
                sb.append(":question:*Most Questions asked:*\n");
                sb.append(orderedMapToString((LinkedHashMap) score.getQuestionsCount(), 5, idToDisplayName, skip));
                sb.append(":+1:*Most Upvotes:*\n");
                sb.append(orderedMapToString((LinkedHashMap) score.getUpvotesCount(), 5, idToDisplayName, skip));
                String answertimeString = "Our average answer time is: ";
                List<Long> answerTime = score.getAnswerTime();
                long sum = 0;
                for (int i = 0; i < answerTime.size(); i++) {
                    sum += answerTime.get(i);
                }
                long avg = sum / answerTime.size();
                avg = avg / 1000 / 60;
                answertimeString += avg + " Minutes.\n";
                sb.append(answertimeString);
                sb.append("Total questions answered: " + answerTime.size() + "\n");
                session1.sendMessage(event.getChannel(), sb.toString());
                return;
            }
            if (messageContent.toLowerCase().contains("!help")) {
                String reply = "https://gist.github.com/kajo404/fe41a18c7e04a72e990f7930cc089c23"; //todo should not be hardcoded
                session1.sendMessage(event.getChannel(), reply);
                return;
            }
            //mapping
            if (messageContent.toLowerCase().contains("!lectures")) {

                try {
                    StringBuilder sb = new StringBuilder();
                    Map<String, String> presentations = api.getPresentations();
                    presentations.forEach((k, v) -> sb.append(k).append(":").append("\t").append(v).append("\n"));
                    session1.sendMessage(event.getChannel(), sb.toString());
                } catch (ApiException e) {
                    logger.debug("Error while fetching presentation mapping from database");
                    e.printStackTrace();
                }
                return;
            }
            if (messageContent.toLowerCase().contains("!exercises")) {
                try {
                    StringBuilder sb = new StringBuilder();
                    Map<String, String> presentations = api.getExercises();
                    presentations.forEach((k, v) -> sb.append(k).append(":").append("\t").append(v).append("\n"));
                    session1.sendMessage(event.getChannel(), sb.toString());
                } catch (ApiException e) {
                    logger.debug("Error while fetching exercise mapping from database");
                    e.printStackTrace();
                }
                return;
            }
            //recognize question

            String id = event.getTimestamp();
            StudentQuestion question = new StudentQuestion(messageContent, event.getSender().getId(), new DateTime(), id, DataItem.INPUTSOURCE_SLACK, event.getChannel().getId());
            question.setDisplayName(event.getSender().getUserName());
            if (channel.equals(exerciseQuestions)) {
                question.setQuestionType(StudentQuestion.QUESTIONTYPE_EXERCISE);
            } else if (channel.equals(contentQuestions)) {
                question.setQuestionType(StudentQuestion.QUESTIONTYPE_CONTENT);
            } else {
                question.setQuestionType(StudentQuestion.QUESTIONTYPE_GENERAL);
            }
            if (self) {
                question.setBotUser(true);
            }
            if (messageContent.contains("?")) {
                logger.debug("recognized question");
                //register the question
                String url = "";
                int slideSet = 0;
                int slide = 0;
                if (channel.equals(generalQuestions)) {
                    url = "/assets/img/general-question.jpg";
                } else {
                    try {
                        ActiveSlide presenterContext = api.getActiveSlides();
                        if (context == null && !channel.equals(generalQuestions)) {
                            url = presenterContext.getActiveSlide();
                            slideSet = presenterContext.getPresentationNumber();
                            slide = presenterContext.getSlideNumber();
                        }
                        if (context != null) {
                            slide = context.getSlideNumber();
                            slideSet = context.getPresentationNumber();
                            url = context.getActiveSlide();
                        }
                    } catch (ApiException e) {
                        logger.debug("Error while parsing context");
                        e.printStackTrace();
                    }
                }
                question.setSlideSet(slideSet);
                question.setSlide(slide);
                question.setInputSource(DataItem.INPUTSOURCE_SLACK);
                ArrayList<SlackAttachment> att = new ArrayList<>();
                if (url != null) {
                    question.setSlideURL(url);
                    String text;
                    SlackPreparedMessage preparedMessage;
                    String message = "Question ID: " + id;
                    if (channel.equals(generalQuestions)) {
                        preparedMessage = new SlackPreparedMessage.Builder()
                                .withUnfurl(false)
                                .withMessage(message)
                                .withThreadTimestamp(id)
                                .build();
                    } else {
                        text = "\n Change the slide by replying with \"L:5 S:25\" (E: for exercises).";
                        SlackAttachment screenshot = new SlackAttachment("Context", new Date().toString(), text, "");
                        screenshot.setImageUrl(serverURL + url);
                        att.add(screenshot);
                        preparedMessage = new SlackPreparedMessage.Builder()
                                .withUnfurl(false)
                                .withMessage(message)
                                .withThreadTimestamp(id)
                                .withAttachments(att)
                                .build();
                    }
                    session1.sendMessage(event.getChannel(), preparedMessage);
                } else {
                    String message = "Question ID:" + id + "\n Add a slide by replying with \"L:5 S:25\" (E: for exercises).";
                    SlackPreparedMessage preparedMessage = new SlackPreparedMessage.Builder()
                            .withMessage(message)
                            .withUnfurl(false)
                            .withThreadTimestamp(id)
                            .build();
                    session1.sendMessage(event.getChannel(), preparedMessage);
                }
                if (self) {
                    String prefix = messageContent.substring(0, messageContent.indexOf(':'));
                    if (!prefix.equals("ANONYMOUS")) {
                        question.setDisplayName(prefix);
                        question.setAuthor(storage.getAuthorForSMSQuestion(messageContent));
                        String inputSource;
                        switch (prefix) {
                            case "WHATSAPP":
                                inputSource = DataItem.INPUTSOURCE_WHATSAPP;
                                break;
                            case "SMS":
                                inputSource = DataItem.INPUTSOURCE_SMS;
                                break;
                            default:
                                inputSource = DataItem.INPUTSOURCE_SLACK;
                        }
                        question.setInputSource(inputSource);
                    }
                }
                storage.mapAnon(question);
                try {
                    api.postQuestion(question);
                } catch (ApiException e) {
                    logger.debug("Error while saving question to database");
                    e.printStackTrace();
                }
                return;
            }
            //Add message to backlog
            storage.addPossibleQuestion(question);
        });
    }

    private void addReactionListeners() {
        session.addReactionAddedListener((event, session1) -> {
            boolean self = session1.sessionPersona().getId().equals(event.getUser().getId());
            Set<String> channels = new HashSet<>();
            if (generalQuestions != null && generalQuestions.length() != 0) {
                channels.add(session1.findChannelByName(generalQuestions).getId());
            }
            if (contentQuestions != null && contentQuestions.length() != 0) {
                channels.add(session1.findChannelByName(contentQuestions).getId());
            }
            if (exerciseQuestions != null && exerciseQuestions.length() != 0) {
                channels.add(session1.findChannelByName(exerciseQuestions).getId());
            }
            //only listen to specified channel
            if (!channels.contains(event.getChannel().getId())) {
                return;
            }
            String message_ts = event.getMessageID();
            String eventtype = event.getEmojiName();
            if (eventtype.equals("+1") || eventtype.equals("point_up")) {
                try {
                    api.upvote(message_ts, event.getUser().getId());
                } catch (ApiException e) {
                    e.printStackTrace();
                }
                return;
            }
            if ((eventtype.equals("white_check_mark") && admins.contains(event.getUser().getUserName())) || self) {
                StudentQuestion studentQuestion = null;
                try {
                    studentQuestion = api.getQuestionByReplyInputSourceId(message_ts);
                } catch (ApiException e) {
                    e.printStackTrace();
                }
                logger.debug("Final answer detected. Proceeding to notify user");
                try {
                    api.postBestAnswer(message_ts);
                } catch (ApiException e) {
                    logger.debug("couldn't close question");
                    e.printStackTrace();
                }

                if (studentQuestion != null && studentQuestion.getInputSource().equals(DataItem.INPUTSOURCE_SLACK)) {
                    String author = studentQuestion.getAuthor();
                    StringBuilder sb = new StringBuilder("Your Question was:\n");
                    sb.append(studentQuestion.getContent());
                    sb.append("\nThe answer was:\n");
                    studentQuestion.getReplies().forEach((reply) -> {
                        if (reply.getInputSourceId().equals(message_ts)) {
                            sb.append(reply.getContent());
                        }
                    });
                    SlackAttachment screenshot = null;
                    if (studentQuestion.getSlideSet() != 0) {
                        screenshot = new SlackAttachment("\nRegarding this slide: ", new Date().toString(), "SlideSet:" + studentQuestion.getSlideSet() + " Slide:" + studentQuestion.getSlide(), "");
                        screenshot.setImageUrl(serverURL + studentQuestion.getSlideURL());
                    }
                    session1.sendMessageToUser(session1.findUserById(author), sb.toString(), screenshot);
                    if (studentQuestion.getRefURL() != null) {
                        SlackAttachment ref;
                        ref = new SlackAttachment("\nReference: ", new Date().toString(), "SlideSet:" + studentQuestion.getSlideSet() + " Slide:" + studentQuestion.getSlide(), "");
                        ref.setImageUrl(serverURL + studentQuestion.getRefURL());
                        session1.sendMessageToUser(session1.findUserById(author), "", ref);
                    }
                }
                return;
            }
            if (eventtype.equals("question")) {
                StudentQuestion question = storage.retrieveFromBacklog(message_ts);
                if (question == null) {
                    return;
                }
                String message = "Question ID:" + message_ts + "\n Add a slide by replying with \"L:5 S:25\".";
                SlackPreparedMessage preparedMessage = new SlackPreparedMessage.Builder()
                        .withMessage(message)
                        .withUnfurl(false)
                        .withThreadTimestamp(message_ts)
                        .build();

                try {
                    api.postQuestion(question);
                    session1.sendMessage(event.getChannel(), preparedMessage);
                } catch (ApiException e) {
                    logger.debug("Couldn't restore question");
                    session1.sendMessage(event.getChannel(), "Couldn't restore question, please ask again");
                    e.printStackTrace();
                }
            }
        });

        session.addReactionRemovedListener((event, session1) -> {
            Set<SlackChannel> channels = new HashSet<>();
            channels.add(session1.findChannelByName(generalQuestions));
            channels.add(session1.findChannelByName(contentQuestions));
            channels.add(session1.findChannelByName(exerciseQuestions));
            //only listen to specified channel
            if (!channels.contains(event.getChannel().getId())) {
                return;
            }
            String message_ts = event.getMessageID();
            String eventtype = event.getEmojiName();
            if (eventtype.equals("+1") || eventtype.equals("point_up")) {
                try {
                    api.downvote(message_ts, event.getUser().getId());
                } catch (ApiException e) {
                    logger.debug("couldn't upvote DataItem " + message_ts);
                    e.printStackTrace();
                }
                return;
            }
            if (eventtype.equals("white_check_mark") && admins.contains(event.getUser().getUserName())) {
                try {
                    api.deleteBestAnswer(message_ts);
                } catch (ApiException e) {
                    logger.debug("Couldn't remove bestAnswer with id " + message_ts);
                    e.printStackTrace();
                }
            }
        });
    }

    private void addDirectMessageListener() {
        //listener for personal messages
        session.addMessagePostedListener((event, session1) -> {
            if (!event.getChannel().isDirect()) {
                return;
            }
            //ignore own message
            if (session1.sessionPersona().getId().equals(event.getSender().getId())) {
                return;
            }
            String message = event.getMessageContent();
            if (message.toLowerCase().startsWith("!ageneral ")) {
                askAnonQuestion(event, session1, generalQuestions);
                return;
            }
            if (message.toLowerCase().startsWith("!acontent ")) {
                askAnonQuestion(event, session1, contentQuestions);
                return;
            }
            if (message.toLowerCase().startsWith("!aexercise ")) {
                askAnonQuestion(event, session1, exerciseQuestions);
                return;
            }
            if (message.toLowerCase().startsWith("!ask ")) {
                String summary = "Command depreacted, please refer to https://gist.github.com/kajo404/fe41a18c7e04a72e990f7930cc089c23";
                session1.sendMessage(event.getChannel(), summary);
                return;
            }
            if (message.startsWith("!feedback")) {
                String reply = "Your feedback has been recorded. Thank you for making the world a better place.";
                Feedback feedback = new Feedback(message.substring(9), new DateTime(), event.getTimestamp(), DataItem.INPUTSOURCE_SLACK, event.getChannel().getId());
                feedback.setInputSource(DataItem.INPUTSOURCE_SLACK);
                try {
                    api.postFeedback(feedback);
                } catch (ApiException e) {
                    logger.debug("Couldn't save feedback, proceeding to notify user");
                    session1.sendMessageToUser(session1.findUserByUserName(admins.get(0)), feedback.getContent(), null);
                    e.printStackTrace();
                }
                session1.sendMessage(event.getChannel(), reply);
                return;
            }
            if (message.contains("!rootme") && dev) {
                String reply = "Root privileges granted until termination.";
                if (admins.contains(event.getUser().getUserName())) {
                    reply = "You are already an admin. There is nothing more to give you.";
                } else {
                    admins.add(event.getUser().getUserName());
                }
                session1.sendMessageToUser(event.getUser(), reply, null);
            }
        });
    }

    private void askAnonQuestion(SlackMessagePosted event, SlackSession session1, String channel) {
        String message = event.getMessageContent();
        if (!message.contains("?")) {
            message += "?";
        }
        String question = message.substring(message.indexOf(' '));
        String ask = "ANONYMOUS: " + question;
        String reply = "Your Question has been asked in #" + channel + ".";
        storage.addAnonMapping(event.getSender().getId(), ask);
        session1.sendMessage(session1.findChannelByName(channel), ask);
        session1.sendMessage(event.getChannel(), reply);
    }

    public void addQuizModule() {
        //listeners for quiz mode
        //contentQuestions listener (currently none because we don't react to contentQuestions messages
        //pm listener
        session.addMessagePostedListener((event, session1) -> {
            //check for direct message
            if (!event.getChannel().isDirect()) {
                return;
            }
            //ignore own message
            if (session1.sessionPersona().getId().equals(event.getSender().getId())) {
                return;
            }
            //only available to admin
            String message = event.getMessageContent();
            //it's a vote
            if (message.length() == 1) {
                this.quizhandler.addVote(event.getSender().getId(), message.toUpperCase());
            }
            if (!admins.contains(event.getSender().getUserName())) {
                return;
            }

            if (message.toLowerCase().startsWith("!multi_attempt ") || message.toLowerCase().startsWith("!ma ")) {
                //start registering quiz session and register question
                String question = message.substring(message.indexOf(" ") + 1);
                this.quizhandler.reqisterQuiz(false);
                this.quizhandler.addQuestion(question);
                String reply = "Question (Multi Attempt) text set. Continue with the following commands: \n*!correct_answer* (!c) For the correct answer \n*!wrong_answer* (!w) For wrong answers \n*!abort* If you mess up\n!start to start the quiz\n  . Provided Order will be kept";
                session1.sendMessage(event.getChannel(), reply);
                return;
            }
            if (message.toLowerCase().startsWith("!single_attempt ") || message.toLowerCase().startsWith("!sa ")) {
                //start registering quiz session and register question
                String question = message.substring(message.indexOf(" ") + 1);
                this.quizhandler.reqisterQuiz(true);
                this.quizhandler.addQuestion(question);
                String reply = "Question (Single Attempt) text set. Continue with the following commands: \n`!correct_answer` or `!c` \tFor the correct answer \n`!wrong_answer` or `!w` \tFor wrong answers \n`!abort` \tIf you mess up\n!start to start the quiz\n  . Provided Order will be kept";
                session1.sendMessage(event.getChannel(), reply);
                return;
            }
            if (message.toLowerCase().startsWith("!wrong_answer ") || message.toLowerCase().startsWith("!w ")) {
                //register a wrong answer
                String answer = message.substring(message.indexOf(" ") + 1);
                this.quizhandler.addAnswer(answer);
                return;
            }
            if (message.toLowerCase().startsWith("!correct_answer ") || message.toLowerCase().startsWith("!c ")) {
                //register a correct answer
                String answer = message.substring(message.indexOf(" ") + 1);
                this.quizhandler.addAnswer(answer);
                this.quizhandler.getTeacherQuestion().lastAnswerasCorrect();
                return;
            }
            if (message.toLowerCase().contains("!start")) {
                //capture context
                ActiveSlide activeSlides = null;
                try {
                    activeSlides = api.getActiveSlides();
                } catch (ApiException e) {
                    e.printStackTrace();
                }
                TeacherQuestion qq = this.quizhandler.getTeacherQuestion();
                assert activeSlides != null;
                qq.setSlideURL(activeSlides.getActiveSlide());
                qq.setSlideSet(activeSlides.getPresentationNumber());
                qq.setSlide(activeSlides.getSlideNumber());
                //send quiz to quizchannel and start gathering votes
                this.quizhandler.startQuiz();
                String reply = "Starting Quiz in " + quiz + " channel. \n Conclude the voting process with !stop";
                String quiztype = "Quiz";
                if (quizhandler.getTeacherQuestion().getName().toLowerCase().contains("single")) {
                    quiztype = "*Single-Attempt*";
                }
                if (quizhandler.getTeacherQuestion().getName().toLowerCase().contains("multi")) {
                    quiztype = "*Multi-Attempt*";
                }
                String quizText = quiztype + ", Vote by messaging me (the bot) with a *personal message*. Messages in this channel are not counted!" + "\n";
                quizText += quizhandler.getQuiz();
                session1.sendMessage(event.getChannel(), reply);
                session1.sendMessage(session1.findChannelByName(this.quiz), quizText);
                return;
            }
            if (message.toLowerCase().contains("!stop")) {
                //stop gathering answers
                this.quizhandler.stopQuiz();
                String reply = "Voting has concluded. Please stop spamming, bots have feelings, too";
                reply += "\n" + this.quizhandler.getResults();
                TeacherQuestion quizQuestion = this.quizhandler.getTeacherQuestion();
                quizQuestion.setDate(new DateTime());
                quizQuestion.setId(event.getTimestamp());
                try {
                    TeacherQuestion teacherQuestion = api.putQuiz(quizQuestion);
                } catch (ApiException e) {
                    e.printStackTrace();
                }
                session1.sendMessage(session1.findChannelByName(this.quiz), reply);
                return;
            }
            if (message.toLowerCase().contains("!winners")) {
                ArrayList<String> winners = this.quizhandler.getWinners();
                if (winners == null || winners.size() == 0) {
                    return;
                }
                StringBuilder winnerReply = new StringBuilder();
                winners.forEach((user) -> {
                    winnerReply.append(user).append("\n");
                });
                String reply = winnerReply.toString();
                session1.sendFile(event.getChannel(), reply.getBytes(), "all winners");
                return;
            }
            if (message.toLowerCase().contains("!abort")) {
                //abort current quiz session and reset
                this.quizhandler.abort();
                String reply = "Quiz session aborted and reset. start with !q to set a Question";
                session1.sendMessage(event.getChannel(), reply);
                session1.sendMessage(session1.findChannelByName(this.quiz), "The question has been *cancelled*. You can stop voting");
                return;
            }

            if (message.toLowerCase().contains("!view")) {
                String reply = "This is what the quiz currently looks like: \n";
                reply += this.quizhandler.getQuiz();
                session1.sendMessage(event.getChannel(), reply);
            }
        });
    }

    private void config() {
        Properties prop = new Properties();
        InputStream input = null;
        token = "";
        try {
            input = new FileInputStream("config.properties");

            // load a properties file
            prop.load(input);

            // get the property value and print it out
            token = prop.getProperty("token");
            contentQuestions = prop.getProperty("contentChannel");
            generalQuestions = prop.getProperty("generalChannel");
            exerciseQuestions = prop.getProperty("exerciseChannel");
            quiz = prop.getProperty("quizchannel");
            String[] a = prop.getProperty("admins").split(";");
            admins = new ArrayList<>();
            Collections.addAll(admins, a);
            dev = prop.getProperty("devmode").equals("true");
            serverURL = prop.getProperty("serverURL");
            socketURL = prop.getProperty("socketURL");
            apiUser = prop.getProperty("apiUser");
            apiPassword = prop.getProperty("apiPassword");
            tutors = new ArrayList<>();
            Collections.addAll(tutors, prop.getProperty("tutors").split(";"));
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private ActiveSlide buildContext(String message, String thread) {
        //check for context in message
        Map<String, Integer> messageContext = analyzeContext(message);
        int slide = messageContext.get("slide");
        int lecture = messageContext.get("slideSet");
        int exercise = messageContext.get("exercise");
        if (slide == 0) {
            return null;
        }
        if (lecture != 0) {
            //check for invalid input (all 3 values set)
            if (exercise != 0) {
                return null;
            }
            try {
                return buildLectureContext(lecture, slide);
            } catch (ApiException e) {
                logger.debug("Error while fetching slideURL for l" + lecture + "s" + slide);
                e.printStackTrace();
                return null;
            }
        } else {
            if (exercise == 0) {
                StudentQuestion question = null;
                try {
                    question = api.getQuestionByInputSourceId(thread);
                } catch (ApiException e) {
                    logger.debug("error while fetching question");
                    e.printStackTrace();
                    //fetch presenter information
                }
                if (question == null) {
                    int presenterSlideSet = 0;
                    try {
                        presenterSlideSet = api.getActiveSlides().getPresentationNumber();
                    } catch (ApiException e) {
                        logger.debug("Error while fetching active Presentation");
                        e.printStackTrace();
                    }
                    if (presenterSlideSet != 0) {
                        lecture = presenterSlideSet;
                    }
                    if (lecture == 0) {
                        return null;
                    }
                } else {
                    if (question.getQuestionType().equals(StudentQuestion.QUESTIONTYPE_CONTENT)) {
                        lecture = question.getSlideSet();
                    } else if (question.getQuestionType().equals(StudentQuestion.QUESTIONTYPE_EXERCISE)) {
                        exercise = question.getSlideSet();
                        try {
                            return buildLectureContext(exercise, slide);
                        } catch (ApiException e) {
                            e.printStackTrace();
                            return null;
                        }
                    }
                }
                try {
                    return buildLectureContext(lecture, slide);
                } catch (ApiException e) {
                    logger.debug("Error fetching URL");
                    e.printStackTrace();
                }
                return null;
            } else {
                try {
                    return buildExerciseContext(exercise, slide);
                } catch (ApiException e) {
                    logger.debug("Error while fetching slideURL for e" + exercise + "s" + slide);
                    e.printStackTrace();
                    return null;
                }
            }
        }
    }

    private ActiveSlide buildLectureContext(int lecture, int slide) throws ApiException {
        String url = api.getSlideForPresentationNumberAndSlideNumber(lecture, slide);
        ActiveSlide context = new ActiveSlide(url, "");
        context.setPresentationNumber(lecture);
        context.setSlideNumber(slide);
        return context;
    }

    private ActiveSlide buildExerciseContext(int exercise, int slide) throws ApiException {
        String url = api.getSlideForExerciseNumberAndSlideNumber(exercise, slide);
        ActiveSlide context = new ActiveSlide(url, "");
        context.setPresentationNumber(exercise);
        context.setSlideNumber(slide);
        return context;
    }

    private Map<String, Integer> analyzeContext(String messageContent) {
        int exercise = 0;
        int slideSet = 0;
        int slide = 0;
        String slideREGEX = "[Ss]:\\d+";
        String slideSetREGEX = "[Ll]:\\d+";
        String exerciseREGEX = "[Ee]:\\d+";
        if (messageContent.toLowerCase().contains("l:")) {
            logger.debug("found slideset information");
            Pattern slideSetPattern = Pattern.compile(slideSetREGEX);
            Matcher slideSetMatcher = slideSetPattern.matcher(messageContent);
            if (slideSetMatcher.find()) {
                String match = slideSetMatcher.group();
                slideSet = Integer.parseInt(match.substring(2));
            }
        }
        if (messageContent.toLowerCase().contains("e:")) {
            logger.debug("found exercise information");
            Pattern exercisePattern = Pattern.compile(exerciseREGEX);
            Matcher exerciseMatcher = exercisePattern.matcher(messageContent);
            if (exerciseMatcher.find()) {
                String match = exerciseMatcher.group();
                exercise = Integer.parseInt(match.substring(2));
            }
        }
        if (messageContent.toLowerCase().contains("s:")) {
            logger.debug("setting slide number");
            Pattern slidePattern = Pattern.compile(slideREGEX);
            Matcher slideMatcher = slidePattern.matcher(messageContent);
            if (slideMatcher.find()) {
                String match = slideMatcher.group();
                slide = Integer.parseInt(match.substring(2));
            }
        }
        Map<String, Integer> context = new HashMap<>();
        context.put("exercise", exercise);
        context.put("slideSet", slideSet);
        context.put("slide", slide);
        return context;
    }

    private String orderedMapToString(LinkedHashMap<String, Integer> map, int num, Map<String, String> userIDMapping, ArrayList<String> skip) {
        StringBuilder sb = new StringBuilder();
        Iterator<Map.Entry<String, Integer>> entryIterator = map.entrySet().iterator();
        for (int i = 0; i < num; i++) {
            if (entryIterator.hasNext()) {
                Map.Entry<String, Integer> entry = entryIterator.next();
                if (skip.contains(userIDMapping.get(entry.getKey()))) {
                    continue;
                }
                sb.append("[[").append(entry.getValue()).append("]]\t").append(userIDMapping.get(entry.getKey())).append("\n");
            } else {
                break;
            }
        }
        return sb.toString();
    }

}
