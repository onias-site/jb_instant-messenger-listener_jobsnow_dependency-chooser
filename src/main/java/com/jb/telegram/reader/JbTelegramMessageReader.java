package com.jb.telegram.reader;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.ccp.constants.CcpOtherConstants;
import com.ccp.decorators.CcpJsonRepresentation;
import com.ccp.decorators.CcpJsonRepresentation.CcpJsonFieldName;
import com.ccp.especifications.http.CcpHttpHandler;
import com.ccp.especifications.http.CcpHttpMethods;
import com.ccp.especifications.http.CcpHttpResponseType;
import com.ccp.especifications.http.CcpHttpTooManyRequests;
import com.ccp.especifications.instant.messenger.CcpErrorInstantMessageThisBotWasBlockedByThisUser;
import com.ccp.process.CcpFunctionThrowException;
import com.jn.business.messages.JnBusinessSendInstantMessage;
import com.jn.business.messages.JnBusinessSendInstantMessage.JnBotType;
import com.jn.utils.JnSystemProperties;

/**
 * Lê as mensagens recebidas pelo bot de suporte no Telegram através do recurso {@code getUpdates}.
 * O token do bot e a url da api são obtidos do bot de suporte via {@link JnSystemProperties},
 * e a comunicação http segue o mesmo padrão do módulo {@code ccp_instant-messenger_telegram}:
 * um {@link CcpHttpHandler} com os fluxos mapeados por status (403 bot bloqueado, 404 bot inexistente,
 * 401 bot inativo, 429 excesso de requisições e 200 sucesso).
 */
public class JbTelegramMessageReader {

	public static enum JsonFieldNames implements CcpJsonFieldName{
		ok, result, update_id, message, message_id, text, chat, id, from, username, date,
		offset, timeout,
		botName, chatId, typedValue, updateId, userName, sentAt
	}

	public static final JbTelegramMessageReader INSTANCE = new JbTelegramMessageReader();

	private final AtomicLong offset = new AtomicLong(0L);

	private JbTelegramMessageReader() {}

	/**
	 * Devolve o token do bot de suporte lido das propriedades do sistema.
	 * @return token do bot de suporte
	 */
	public String getSupportBotToken() {
		String botToken = JnSystemProperties.INSTANCE.getSystemInnerProperty(JnBusinessSendInstantMessage.Fields.bots, JnBotType.support);
		return botToken;
	}

	/**
	 * Executa o {@code getUpdates} da api do Telegram e devolve a resposta crua.
	 * @param offset identificador da primeira atualização a ser devolvida
	 * @param timeout tempo em segundos do long polling (zero para consulta imediata)
	 * @return json devolvido pela api do Telegram
	 */
	public CcpJsonRepresentation getUpdates(Long offset, Integer timeout) {

		CcpHttpHandler httpHandler = this.getHttpHandler("/getUpdates");

		CcpJsonRepresentation body = CcpOtherConstants.EMPTY_JSON
				.put(JsonFieldNames.offset, offset)
				.put(JsonFieldNames.timeout, timeout)
				;

		CcpJsonRepresentation response = httpHandler.executeHttpRequest("readInstantMessages", CcpHttpMethods.POST, CcpOtherConstants.EMPTY_JSON, body, CcpHttpResponseType.singleRecord);

		return response;
	}

	/**
	 * Lê as mensagens a partir do offset informado, sem alterar o offset interno.
	 * @param offset identificador da primeira atualização a ser devolvida
	 * @param timeout tempo em segundos do long polling (zero para consulta imediata)
	 * @return lista de mensagens simplificadas
	 */
	public List<CcpJsonRepresentation> readMessages(Long offset, Integer timeout) {
		CcpJsonRepresentation updates = this.getUpdates(offset, timeout);
		List<CcpJsonRepresentation> messages = this.extractMessages(updates, new AtomicLong(offset));
		return messages;
	}

	/**
	 * Lê somente as mensagens ainda não lidas, avançando o offset interno para que a próxima
	 * chamada não devolva as mesmas mensagens.
	 * @param timeout tempo em segundos do long polling (zero para consulta imediata)
	 * @return lista de mensagens simplificadas
	 */
	public List<CcpJsonRepresentation> readNewMessages(Integer timeout) {
		CcpJsonRepresentation updates = this.getUpdates(this.offset.get(), timeout);
		List<CcpJsonRepresentation> messages = this.extractMessages(updates, this.offset);
		return messages;
	}

	/**
	 * Lê as mensagens ainda não lidas sem esperar por novas mensagens.
	 * @return lista de mensagens simplificadas
	 */
	public List<CcpJsonRepresentation> readNewMessages() {
		List<CcpJsonRepresentation> messages = this.readNewMessages(0);
		return messages;
	}

	private List<CcpJsonRepresentation> extractMessages(CcpJsonRepresentation updates, AtomicLong offsetToUpdate) {

		Boolean ok = updates.getOrDefault(JsonFieldNames.ok, () -> false);

		boolean requestWasNotOk = false == ok;

		if(requestWasNotOk) {
			throw new JbErrorUnableToReadInstantMessages(updates);
		}

		List<CcpJsonRepresentation> result = updates.getAsJsonList(JsonFieldNames.result);

		List<CcpJsonRepresentation> messages = new ArrayList<>();

		for (CcpJsonRepresentation update : result) {

			Long updateId = update.getAsLongNumber(JsonFieldNames.update_id);

			offsetToUpdate.set(updateId + 1);

			boolean thereIsNoMessage = false == update.containsAllFields(JsonFieldNames.message);

			if(thereIsNoMessage) {
				continue;
			}

			CcpJsonRepresentation message = this.extractMessage(update, updateId);

			messages.add(message);
		}

		return messages;
	}

	private CcpJsonRepresentation extractMessage(CcpJsonRepresentation update, Long updateId) {

		Double chatId = update.getValueFromPath(0d, JsonFieldNames.message, JsonFieldNames.chat, JsonFieldNames.id);
		Double messageId = update.getValueFromPath(0d, JsonFieldNames.message, JsonFieldNames.message_id);
		Double sentAt = update.getValueFromPath(0d, JsonFieldNames.message, JsonFieldNames.date);
		String typedValue = update.getValueFromPath("", JsonFieldNames.message, JsonFieldNames.text);
		String userName = update.getValueFromPath("", JsonFieldNames.message, JsonFieldNames.from, JsonFieldNames.username);

		CcpJsonRepresentation message = CcpOtherConstants.EMPTY_JSON
				.put(JsonFieldNames.botName, JnBotType.support.name())
				.put(JsonFieldNames.chatId, chatId.longValue())
				.put(JsonFieldNames.message_id, messageId.longValue())
				.put(JsonFieldNames.sentAt, sentAt.longValue())
				.put(JsonFieldNames.updateId, updateId)
				.put(JsonFieldNames.userName, userName)
				.put(JsonFieldNames.typedValue, typedValue)
				;

		return message;
	}

	private CcpHttpHandler getHttpHandler(String resource) {

		String botName = JnBotType.support.name();
		String botToken = this.getSupportBotToken();
		String botUrl = JnSystemProperties.INSTANCE.urlInstantMessengerKey();
		String url = botUrl + botToken + resource;

		CcpJsonRepresentation handlers = CcpOtherConstants.EMPTY_JSON
				.addJsonTransformer(403, new CcpFunctionThrowException(new CcpErrorInstantMessageThisBotWasBlockedByThisUser(botName)))
				.addJsonTransformer(404, new CcpFunctionThrowException(new RuntimeException("The bot '" + botName + "' was not found")))
				.addJsonTransformer(401, new CcpFunctionThrowException(new RuntimeException("The bot '" + botName + "' is inactive")))
				.addJsonTransformer(429, new CcpFunctionThrowException(new CcpHttpTooManyRequests()))
				.addJsonTransformer(200, CcpOtherConstants.DO_NOTHING)
				;

		CcpHttpHandler httpHandler = new CcpHttpHandler(handlers, url);

		return httpHandler;
	}

	@SuppressWarnings("serial")
	public static class JbErrorUnableToReadInstantMessages extends RuntimeException {
		private JbErrorUnableToReadInstantMessages(CcpJsonRepresentation json) {
			super("It was not possible to read the instant messages. Details: " + json);
		}
	}

}
