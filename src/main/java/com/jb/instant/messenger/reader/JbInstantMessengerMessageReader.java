package com.jb.instant.messenger.reader;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.ccp.business.CcpBusiness;
import com.ccp.constants.CcpOtherConstants;
import com.ccp.decorators.CcpJsonRepresentation;
import com.ccp.decorators.CcpJsonFieldName;
import com.ccp.especifications.db.utils.entity.decorators.engine.CcpEntityMetaData;
import com.ccp.especifications.http.CcpHttpHandler;
import com.ccp.especifications.http.CcpHttpMethods;
import com.ccp.especifications.http.CcpHttpResponseType;
import com.ccp.especifications.http.CcpHttpTooManyRequests;
import com.ccp.especifications.instant.messenger.CcpErrorInstantMessageThisBotWasBlockedByThisUser;
import com.ccp.process.CcpFunctionThrowException;
import com.jb.entities.JbEntityBotUpdateId;
import com.jn.business.messages.JnBusinessSendInstantMessage;
import com.jn.utils.JnSystemProperties;
import java.util.stream.Stream;

import com.jn.json.fields.validation.JnJsonInstantMessengerFields;
import com.jn.json.fields.validation.JnJsonCommonsFields;

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
		ok, result, update_id, message, message_id, text, chat, from, username,
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
	public Long getOffset(String botType) {

		CcpJsonRepresentation parametersToSearch = this.getParametersToSearchOffset(botType);
		
		CcpEntityMetaData entityMetaData = JbEntityBotUpdateId.ENTITY.getEntityMetaData();
		
		CcpJsonRepresentation savedOffset = entityMetaData.getOneByIdOrHandleItIfThisIdWasNotFound(parametersToSearch, json -> CcpOtherConstants.EMPTY_JSON.put(JbEntityBotUpdateId.Fields.updateId, FIRST_OFFSET));
		
		Long offset = savedOffset.getAsLongNumber(JbEntityBotUpdateId.Fields.updateId) ;

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
	public Long saveOffset(String botType, long savedOffset, List<CcpJsonRepresentation> messages) {
		
		
		messages.sort((a, b) -> (int)(b.getAsLongNumber(JbEntityBotUpdateId.Fields.updateId) - a.getAsLongNumber(JbEntityBotUpdateId.Fields.updateId)));
		Stream<CcpJsonRepresentation> stream = messages.stream();
		var streamMap = stream.map(a -> a.getAsLongNumber(JbEntityBotUpdateId.Fields.updateId) + 1);
		var findFirst = streamMap.findFirst();

		Long lastOffset = findFirst.orElseGet(() -> FIRST_OFFSET);
		
		long offset = Math.max(savedOffset, lastOffset);
		CcpJsonRepresentation parametersToSearchOffset = this.getParametersToSearchOffset(botType);

		CcpJsonRepresentation offsetToSave = parametersToSearchOffset
				.put(JbEntityBotUpdateId.Fields.updateId, offset)
				;

		JbEntityBotUpdateId.ENTITY.save(offsetToSave);

		return offset;
	}

	private CcpJsonRepresentation getParametersToSearchOffset(String botType) {

		CcpJsonRepresentation parametersToSearch = CcpOtherConstants.EMPTY_JSON
				.put(JnJsonInstantMessengerFields.botName, botType)
				;

		return parametersToSearch;
	}

	/**
	 * Devolve o token do bot informado lido das propriedades do sistema.
	 * @param botType bot cujo token será lido
	 * @return token do bot informado
	 */
	public String getBotToken(String botType) {
		String botToken = JnSystemProperties.INSTANCE.getSystemInnerProperty(JnBusinessSendInstantMessage.Fields.bots, () -> botType);
		return botToken;
	}

	/**
	 * Executa o {@code getUpdates} da api do Telegram e devolve a resposta crua.
	 * @param botType bot a ser lido
	 * @param offset identificador da primeira atualização a ser devolvida
	 * @param timeout tempo em segundos do long polling (zero para consulta imediata)
	 * @return json devolvido pela api do Telegram
	 */
	public CcpJsonRepresentation getUpdates(String botType, Long offset, Integer timeout) {
		String valorMais = "/getUpdates?offset=" + offset;

		CcpHttpHandler httpHandler = this.getHttpHandler(botType, valorMais);
		CcpJsonRepresentation put = CcpOtherConstants.EMPTY_JSON
				.put(JsonFieldNames.offset, offset);

				CcpJsonRepresentation body = put
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
	public List<CcpJsonRepresentation> readMessages(String botType, Long offset, Integer timeout) {
		CcpJsonRepresentation updates = this.getUpdates(botType, offset, timeout);
		AtomicLong atomicLong = new AtomicLong(offset);
		List<CcpJsonRepresentation> messages = this.extractMessages(botType, updates, atomicLong);
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
	public void readNewMessages(Integer timeout, CcpBusiness messageReader) {
		String botType = messageReader.name();
		
		Long offset = this.getOffset(botType);

		CcpJsonRepresentation updates = this.getUpdates(botType, offset, timeout);

		AtomicLong offsetToUpdate = new AtomicLong(offset);

		List<CcpJsonRepresentation> messages = this.extractMessages(botType, updates, offsetToUpdate);
		
		for (CcpJsonRepresentation message : messages) {
			messageReader.execute(message);
		}

		offset = this.saveOffset(botType, offset, messages);
	}


	private List<CcpJsonRepresentation> extractMessages(String botType, CcpJsonRepresentation updates, AtomicLong offsetToUpdate) {

		Boolean ok = updates.getOrDefault(JsonFieldNames.ok, () -> false);

		boolean requestWasNotOk = false == ok;

		if(requestWasNotOk) {
			JbErrorUnableToReadInstantMessages jbErrorUnableToReadInstantMessages = new JbErrorUnableToReadInstantMessages(updates);
			throw jbErrorUnableToReadInstantMessages;
		}

		List<CcpJsonRepresentation> result = updates.getAsJsonList(JsonFieldNames.result);

		List<CcpJsonRepresentation> messages = new ArrayList<>();

		for (CcpJsonRepresentation update : result) {

			Long updateId = update.getAsLongNumber(JsonFieldNames.update_id);
			Long updateIdMais = updateId + 1;

			offsetToUpdate.set(updateIdMais);
			boolean containsAllFields = update.containsAllFields(JnJsonInstantMessengerFields.message);

			boolean thereIsNoMessage = false == containsAllFields;

			if(thereIsNoMessage) {
				continue;
			}

			CcpJsonRepresentation message = this.extractMessage(botType, update, updateId);

			messages.add(message);
		}

		return messages;
	}

	private CcpJsonRepresentation extractMessage(String botType, CcpJsonRepresentation update, Long updateId) {

		Double chatId = update.getValueFromPath(0d, JnJsonInstantMessengerFields.message, JsonFieldNames.chat, JnJsonCommonsFields.id);
		Double messageId = update.getValueFromPath(0d, JnJsonInstantMessengerFields.message, JsonFieldNames.message_id);
		Double sentAt = update.getValueFromPath(0d, JnJsonInstantMessengerFields.message, JnJsonCommonsFields.date);
		String typedValue = update.getValueFromPath("", JnJsonInstantMessengerFields.message, JsonFieldNames.text);
		String userName = update.getValueFromPath("", JnJsonInstantMessengerFields.message, JsonFieldNames.from, JsonFieldNames.username);
		CcpJsonRepresentation put2 = CcpOtherConstants.EMPTY_JSON
				.put(JnJsonInstantMessengerFields.botName, botType);
				long longValue = chatId.longValue();
				CcpJsonRepresentation put3 = put2
				.put(JnJsonInstantMessengerFields.chatId, longValue);
				long longValue2 = messageId.longValue();
				CcpJsonRepresentation put4 = put3
				.put(JsonFieldNames.message_id, longValue2);
				long longValue3 = sentAt.longValue();
				CcpJsonRepresentation put5 = put4
				.put(JsonFieldNames.sentAt, longValue3);
				CcpJsonRepresentation put6 = put5 
				.put(JsonFieldNames.updateId, updateId);
				CcpJsonRepresentation put7 = put6
				.put(JsonFieldNames.userName, userName);

				CcpJsonRepresentation message = put7
				.put(JnJsonInstantMessengerFields.message, typedValue)
				;

		return message;
	}

	private CcpHttpHandler getHttpHandler(String botType, String resource) {

		String botToken = this.getBotToken(botType);
		String botUrl = JnSystemProperties.INSTANCE.urlInstantMessengerKey();
		String botUrlMais = botUrl + botToken;
		String url = botUrlMais + resource;
		CcpErrorInstantMessageThisBotWasBlockedByThisUser ccpErrorInstantMessageThisBotWasBlockedByThisUser = new CcpErrorInstantMessageThisBotWasBlockedByThisUser(botType);
		CcpFunctionThrowException ccpFunctionThrowException = new CcpFunctionThrowException(ccpErrorInstantMessageThisBotWasBlockedByThisUser);
		CcpJsonRepresentation addJsonTransformer = CcpOtherConstants.EMPTY_JSON
				.addJsonTransformer(403, ccpFunctionThrowException);
				JbErrorInstantMessengerBotNotFound jbErrorInstantMessengerBotNotFound = new JbErrorInstantMessengerBotNotFound(botType);
				CcpFunctionThrowException ccpFunctionThrowException2 = new CcpFunctionThrowException(jbErrorInstantMessengerBotNotFound);
				CcpJsonRepresentation addJsonTransformer2 = addJsonTransformer
				.addJsonTransformer(404, ccpFunctionThrowException2);
				JbErrorInstantMessengerBotIsInactive jbErrorInstantMessengerBotIsInactive = new JbErrorInstantMessengerBotIsInactive(botType);
				CcpFunctionThrowException ccpFunctionThrowException3 = new CcpFunctionThrowException(jbErrorInstantMessengerBotIsInactive);
				CcpJsonRepresentation addJsonTransformer3 = addJsonTransformer2
				.addJsonTransformer(401, ccpFunctionThrowException3);
				CcpHttpTooManyRequests ccpHttpTooManyRequests = new CcpHttpTooManyRequests();
				CcpFunctionThrowException ccpFunctionThrowException4 = new CcpFunctionThrowException(ccpHttpTooManyRequests);
				CcpJsonRepresentation addJsonTransformer4 = addJsonTransformer3
				.addJsonTransformer(429, ccpFunctionThrowException4);

				CcpJsonRepresentation handlers = addJsonTransformer4
				.addJsonTransformer(200, CcpOtherConstants.DO_NOTHING)
				;

		CcpHttpHandler httpHandler = new CcpHttpHandler(handlers, url);

		return httpHandler;
	}


	/**
	 * Exceção lançada quando o mensageiro responde 404 para o bot informado, ou seja, o bot não existe.
	 */
	@SuppressWarnings("serial")
	public static class JbErrorInstantMessengerBotNotFound extends RuntimeException {
		/**
		 * Monta a mensagem informando qual bot não foi encontrado.
		 * @param botType o tipo do bot procurado
		 */
		private JbErrorInstantMessengerBotNotFound(String botType) {
			super("The bot '" + botType + "' was not found");
		}
	}

	/**
	 * Exceção lançada quando o mensageiro responde 401 para o bot informado, ou seja, o bot existe mas está inativo.
	 */
	@SuppressWarnings("serial")
	public static class JbErrorInstantMessengerBotIsInactive extends RuntimeException {
		/**
		 * Monta a mensagem informando qual bot está inativo.
		 * @param botType o tipo do bot inativo
		 */
		private JbErrorInstantMessengerBotIsInactive(String botType) {
			super("The bot '" + botType + "' is inactive");
		}
	}

}
