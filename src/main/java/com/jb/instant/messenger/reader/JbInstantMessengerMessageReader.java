package com.jb.instant.messenger.reader;

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
import com.jb.entities.JbEntityBotUpdateId;
import com.jn.business.messages.JnBusinessSendInstantMessage;
import com.jn.business.messages.JnBusinessSendInstantMessage.JnBotType;
import com.jn.utils.JnSystemProperties;

/**
 * Lê as mensagens recebidas no Telegram através do recurso {@code getUpdates}. Todas as operações
 * recebem o {@link JnBotType} do bot a ser lido, de forma que qualquer item do enum seja contemplado.
 * O token do bot e a url da api são obtidos via {@link JnSystemProperties}, e a comunicação http
 * segue o mesmo padrão do módulo {@code ccp_instant-messenger_telegram}: um {@link CcpHttpHandler}
 * com os fluxos mapeados por status (403 bot bloqueado, 404 bot inexistente, 401 bot inativo,
 * 429 excesso de requisições e 200 sucesso).
 *
 * <p>O offset, que é o identificador da primeira atualização a ser devolvida pelo {@code getUpdates},
 * é gravado na entidade {@link JbEntityBotUpdateId}, sempre incrementado em um a partir da última
 * atualização lida. Como o nome do bot é a chave primária dessa entidade, cada bot tem o seu próprio
 * offset, e as mensagens já lidas não voltam nem mesmo depois de reiniciar a aplicação.</p>
 */
public class JbInstantMessengerMessageReader {

	public static enum JsonFieldNames implements CcpJsonFieldName{
		ok, result, update_id, message, message_id, text, chat, id, from, username, date,
		offset, timeout,
		botName, chatId, typedValue, updateId, userName, sentAt
	}

	public static final JbInstantMessengerMessageReader INSTANCE = new JbInstantMessengerMessageReader();

	private static final Long FIRST_OFFSET = 0L;

	private JbInstantMessengerMessageReader() {}

	/**
	 * Recupera da entidade {@link JbEntityBotUpdateId} o offset salvo para o bot informado.
	 * Enquanto nenhuma atualização tiver sido lida, devolve zero, valor com o qual a api do
	 * Telegram devolve as atualizações mais antigas ainda disponíveis.
	 * @param botType bot cujo offset será recuperado
	 * @return offset salvo para o bot informado, ou zero caso ainda não exista registro
	 */
	public Long getOffset(JnBotType botType) {

		CcpJsonRepresentation parametersToSearch = this.getParametersToSearchOffset(botType);

		boolean thereIsNoSavedOffset = false == JbEntityBotUpdateId.ENTITY.exists(parametersToSearch);

		if(thereIsNoSavedOffset) {
			return FIRST_OFFSET;
		}

		CcpJsonRepresentation savedOffset = JbEntityBotUpdateId.ENTITY.getOneById(parametersToSearch);

		Long offset = savedOffset.getAsLongNumber(JbEntityBotUpdateId.Fields.updateId);

		return offset;
	}

	/**
	 * Salva na entidade {@link JbEntityBotUpdateId} o offset do bot informado, ou seja, o número
	 * da última atualização lida já incrementado, de forma que a próxima leitura comece na
	 * atualização seguinte.
	 * @param botType bot cujo offset será salvo
	 * @param offset offset a ser salvo
	 * @return o próprio offset salvo
	 */
	public Long saveOffset(JnBotType botType, Long offset) {

		CcpJsonRepresentation offsetToSave = this.getParametersToSearchOffset(botType)
				.put(JbEntityBotUpdateId.Fields.updateId, offset)
				;

		JbEntityBotUpdateId.ENTITY.save(offsetToSave);

		return offset;
	}

	private CcpJsonRepresentation getParametersToSearchOffset(JnBotType botType) {

		CcpJsonRepresentation parametersToSearch = CcpOtherConstants.EMPTY_JSON
				.put(JbEntityBotUpdateId.Fields.botName, botType.name())
				;

		return parametersToSearch;
	}

	/**
	 * Devolve o token do bot informado lido das propriedades do sistema.
	 * @param botType bot cujo token será lido
	 * @return token do bot informado
	 */
	public String getBotToken(JnBotType botType) {
		String botToken = JnSystemProperties.INSTANCE.getSystemInnerProperty(JnBusinessSendInstantMessage.Fields.bots, botType);
		return botToken;
	}

	/**
	 * Executa o {@code getUpdates} da api do Telegram e devolve a resposta crua.
	 * @param botType bot a ser lido
	 * @param offset identificador da primeira atualização a ser devolvida
	 * @param timeout tempo em segundos do long polling (zero para consulta imediata)
	 * @return json devolvido pela api do Telegram
	 */
	public CcpJsonRepresentation getUpdates(JnBotType botType, Long offset, Integer timeout) {

		CcpHttpHandler httpHandler = this.getHttpHandler(botType, "/getUpdates");

		CcpJsonRepresentation body = CcpOtherConstants.EMPTY_JSON
				.put(JsonFieldNames.offset, offset)
				.put(JsonFieldNames.timeout, timeout)
				;

		CcpJsonRepresentation response = httpHandler.executeHttpRequest("readInstantMessages", CcpHttpMethods.POST, CcpOtherConstants.EMPTY_JSON, body, CcpHttpResponseType.singleRecord);

		return response;
	}

	/**
	 * Lê as mensagens a partir do offset informado, sem alterar o offset salvo na entidade
	 * {@link JbEntityBotUpdateId}.
	 * @param botType bot a ser lido
	 * @param offset identificador da primeira atualização a ser devolvida
	 * @param timeout tempo em segundos do long polling (zero para consulta imediata)
	 * @return lista de mensagens simplificadas
	 */
	public List<CcpJsonRepresentation> readMessages(JnBotType botType, Long offset, Integer timeout) {
		CcpJsonRepresentation updates = this.getUpdates(botType, offset, timeout);
		List<CcpJsonRepresentation> messages = this.extractMessages(botType, updates, new AtomicLong(offset));
		return messages;
	}

	/**
	 * Lê somente as mensagens do bot informado que ainda não foram lidas, partindo do offset salvo
	 * na entidade {@link JbEntityBotUpdateId} e salvando o offset já incrementado, para que a
	 * próxima chamada não devolva as mesmas mensagens.
	 * @param botType bot a ser lido
	 * @param timeout tempo em segundos do long polling (zero para consulta imediata)
	 * @return lista de mensagens simplificadas
	 */
	public List<CcpJsonRepresentation> readNewMessages(JnBotType botType, Integer timeout) {

		Long offset = this.getOffset(botType);

		CcpJsonRepresentation updates = this.getUpdates(botType, offset, timeout);

		AtomicLong offsetToUpdate = new AtomicLong(offset);

		List<CcpJsonRepresentation> messages = this.extractMessages(botType, updates, offsetToUpdate);

		this.saveOffsetIfItWasIncremented(botType, offset, offsetToUpdate.get());

		return messages;
	}

	private void saveOffsetIfItWasIncremented(JnBotType botType, Long offset, Long incrementedOffset) {

		boolean offsetWasNotIncremented = offset.equals(incrementedOffset);

		if(offsetWasNotIncremented) {
			return;
		}

		this.saveOffset(botType, incrementedOffset);
	}

	/**
	 * Lê as mensagens do bot informado ainda não lidas, sem esperar por novas mensagens.
	 * @param botType bot a ser lido
	 * @return lista de mensagens simplificadas
	 */
	public List<CcpJsonRepresentation> readNewMessages(JnBotType botType) {
		List<CcpJsonRepresentation> messages = this.readNewMessages(botType, 0);
		return messages;
	}

	private List<CcpJsonRepresentation> extractMessages(JnBotType botType, CcpJsonRepresentation updates, AtomicLong offsetToUpdate) {

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

			CcpJsonRepresentation message = this.extractMessage(botType, update, updateId);

			messages.add(message);
		}

		return messages;
	}

	private CcpJsonRepresentation extractMessage(JnBotType botType, CcpJsonRepresentation update, Long updateId) {

		Double chatId = update.getValueFromPath(0d, JsonFieldNames.message, JsonFieldNames.chat, JsonFieldNames.id);
		Double messageId = update.getValueFromPath(0d, JsonFieldNames.message, JsonFieldNames.message_id);
		Double sentAt = update.getValueFromPath(0d, JsonFieldNames.message, JsonFieldNames.date);
		String typedValue = update.getValueFromPath("", JsonFieldNames.message, JsonFieldNames.text);
		String userName = update.getValueFromPath("", JsonFieldNames.message, JsonFieldNames.from, JsonFieldNames.username);

		CcpJsonRepresentation message = CcpOtherConstants.EMPTY_JSON
				.put(JsonFieldNames.botName, botType.name())
				.put(JsonFieldNames.chatId, chatId.longValue())
				.put(JsonFieldNames.message_id, messageId.longValue())
				.put(JsonFieldNames.sentAt, sentAt.longValue()) 
				.put(JsonFieldNames.updateId, updateId)
				.put(JsonFieldNames.userName, userName)
				.put(JsonFieldNames.message, typedValue)
				;

		return message;
	}

	private CcpHttpHandler getHttpHandler(JnBotType botType, String resource) {

		String botName = botType.name();
		String botToken = this.getBotToken(botType);
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
