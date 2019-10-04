package com.bass2000.tlgrm.bot.bot;

import com.bass2000.tlgrm.bot.model.User;
import com.bass2000.tlgrm.bot.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;

@Component
@PropertySource("classpath:telegram.properties")
public class ChatBot extends TelegramLongPollingBot {

    private static final Logger LOGGER = LogManager.getLogger(ChatBot.class);
    private static final String BROADCAST = "broadcast ";
    private static final String LIST_USERS = "users";
    private final UserService userService;
    @Value("${bot.name}")
    private String botName;
    @Value("${bot.token}")
    private String botToken;

    public ChatBot(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText())
            return;
        final String text = update.getMessage().getText();
        final long chatId = update.getMessage().getChatId();
        User user = userService.findChatId(chatId);

        if (checkIfAdminCommand(user, text))
            return;

        BotContext context;
        BotState state;

        if (user == null) {
            state = BotState.getInitialState();
            user = new User(chatId, state.ordinal());
            userService.addUser(user);

            context = BotContext.of(this, user, text);
            state.enter(context);
            LOGGER.info("New user registered: " + chatId);
        } else {
            context = BotContext.of(this, user, text);
            state = BotState.byId(user.getStateId());
            LOGGER.info("Update received for user in state: " + state);
        }

        state.handleInput(context);

        do {
            state = state.nextState();
            state.enter(context);
        } while (!state.isInputNeeded());

        user.setStateId(state.ordinal());
        userService.updateUser(user);
    }

    @Override
    public String getBotUsername() {
        return botName;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    private boolean checkIfAdminCommand(User user, String text) {
        if (user == null || !user.getAdmin())
            return false;
        if (text.startsWith(BROADCAST)) {
            LOGGER.info("Admin command received: " + BROADCAST);

            text = text.substring(BROADCAST.length());
            broadcast(text);
            return true;
        } else if (text.equals(LIST_USERS)) {
            LOGGER.info("Admin command received: " + LIST_USERS);
            listUsers(user);
            return true;
        }
        return false;
    }

    private void sendMessage(long chatId, String text) {
        SendMessage message = new SendMessage()
                .setChatId(chatId)
                .setText(text);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void listUsers(User admin) {
        StringBuilder sb = new StringBuilder("All users list:\r\n");
        List<User> users = userService.findAllUsers();

        users.forEach(user -> sb.append(user.getId())
                .append(' ')
                .append(user.getPhone())
                .append(' ')
                .append(user.getEmail())
                .append(' ')
                .append("\r\n"));
        sendMessage(admin.getChatId(), sb.toString());
    }

    private void broadcast(String text) {
        List<User> users = userService.findAllUsers();
        users.forEach(user -> sendMessage(user.getChatId(), text));
    }
}