package com.mikunaigen.backend.telegram;

import com.mikunaigen.backend.service.RegistroTelegramService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Contact;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@ConditionalOnProperty(name = "telegram.bot.token")
public class MikunaigenTelegramBot extends TelegramLongPollingBot {

    private final RegistroTelegramService registroTelegramService;
    private final String botUsername;

    public MikunaigenTelegramBot(
            @Value("${telegram.bot.token}") String botToken,
            @Value("${telegram.bot.username}") String botUsername,
            RegistroTelegramService registroTelegramService
    ) {
        super(botToken);
        this.botUsername = botUsername;
        this.registroTelegramService = registroTelegramService;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update == null || !update.hasMessage()) {
            return;
        }
        Message message = update.getMessage();
        if (message == null || message.getFrom() == null) {
            return;
        }

        String telegramUserId = String.valueOf(message.getFrom().getId());
        Long chatId = message.getChatId();

        if (message.hasContact()) {
            Contact contact = message.getContact();
            String phone = contact != null ? contact.getPhoneNumber() : null;
            registroTelegramService.procesarContactoTelegram(telegramUserId, phone, chatId, this);
            return;
        }

        if (!message.hasText()) {
            return;
        }

        String text = message.getText().trim();
        if (!text.startsWith("/start")) {
            return;
        }

        // En telegrambots 6.8 el teléfono solo llega al compartir contacto (Contact), no en User.
        registroTelegramService.procesarActivacionTelegram(
                text, telegramUserId, null, chatId, this);
    }
}
