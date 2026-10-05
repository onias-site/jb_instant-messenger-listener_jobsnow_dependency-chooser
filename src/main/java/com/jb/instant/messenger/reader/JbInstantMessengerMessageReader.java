package com.jb.instant.messenger.reader;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import com.ccp.business.CcpBusiness;
import com.ccp.constants.CcpOtherConstants;
import com.ccp.decorators.CcpJsonFieldName;
import com.ccp.decorators.CcpJsonRepresentation;
import com.ccp.especifications.db.utils.entity.decorators.engine.CcpEntityMetaData;
import com.ccp.especifications.http.CcpHttpHandler;
import com.ccp.especifications.http.CcpHttpMethods;
import com.ccp.especifications.http.CcpHttpResponseType;
import com.ccp.especifications.http.CcpHttpTooManyRequests;
import com.ccp.especifications.instant.messenger.CcpErrorInstantMessageThisBotWasBlockedByThisUser;
import com.ccp.process.CcpFunctionThrowException;
import com.jb.entities.JbEntityBotUpdateId;
import com.jn.business.messages.JnMessageType;
import com.jn.json.fields.validation.JnJsonCommonsFields;
import com.jn.json.fields.validation.JnJsonInstantMessengerFields;
import com.jn.utils.JnSystemProperties;

import com.ccp.json.fields.validation.CcpJsonCommonsFields;

/**
 * Reads the messages received by a bot through the {@code getUpdates} resource of Telegram. The token of the bot and the
 * URL of the API come from {@code JnSystemProperties}; the HTTP flows are mapped by status as in the
 * {@code ccp_instant-messenger_telegram} module (403 bot blocked, 404 bot not found, 401 bot inactive, 429 too many
 * requests and 200 success).
 * <p>The offset, the id of the first update returned by {@code getUpdates}, is saved in {@code JbEntityBotUpdateId},
 * always one more than the last update read. The bot name is the primary key of that entity, so each bot has its own
 * offset, and the messages already read do not come back even after the application restarts (see finding: only updates
 * that carry a message move the offset).</p>
 */
public class JbInstantMessengerMessageReader {

	/** Fields of the Telegram updates and of the simplified messages. */
	public static enum JsonFieldNames implements CcpJsonFieldName{
		/** The id of the update. */
		update_id,
		/** The message of the update. */
		message,
		/** The id of the message. */
		message_id,
		/** The chat of the message. */
		chat,
		/** The sender of the message. */
		from,
		/** The user name of the sender. */
		username,
		/** The first update to return. */
		offset,
		/** The long polling time, in seconds. */
		timeout,
		/** The bot. */
		botName,
		/** The chat id. */
		chatId,
		/** The id of the update, in the simplified message. */
		updateId,
		/** The user name, in the simplified message. */
		userName,
		/** When the message was sent, in the simplified message. */
		sentAt
	}

	/** The single instance. */
	public static final JbInstantMessengerMessageReader INSTANCE = new JbInstantMessengerMessageReader();

	/** The offset before any update was read: Telegram returns the oldest updates still available. */
	private static final Long FIRST_OFFSET = 0L;

	/** Singleton; use {@link #INSTANCE}. */
	private JbInstantMessengerMessageReader() {}

	/**
	 * Returns the offset saved for the bot, or zero while no update was read.
	 * @param botType the bot
	 * @return the offset
	 */
	public Long getOffset(String botType) {

		CcpJsonRepresentation parametersToSearch = this.getParametersToSearchOffset(botType);
		
		CcpEntityMetaData entityMetaData = JbEntityBotUpdateId.ENTITY.getEntityMetaData();
		
		CcpJsonRepresentation savedOffset = entityMetaData.getOneByIdOrHandleItIfThisIdWasNotFound(parametersToSearch, json -> CcpOtherConstants.EMPTY_JSON.put(JbEntityBotUpdateId.Fields.updateId, FIRST_OFFSET));
		
		Long offset = savedOffset.getAsLongNumber(JbEntityBotUpdateId.Fields.updateId) ;

		return offset;
	}

	/**
	 * Saves the offset of the bot: one more than the highest update id among the messages, never less than the saved one.
	 * @param botType the bot
	 * @param savedOffset the offset saved before the reading
	 * @param messages the messages read
	 * @return the offset saved, or the previous one when there was no message
	 */
	public Long saveOffset(String botType, long savedOffset, List<CcpJsonRepresentation> messages) {
		
		boolean hasNoMessages = messages.isEmpty();
		if(hasNoMessages) {
			return  savedOffset;
		}
		
		messages.sort((a, b) -> (int)(b.getAsLongNumber(JbEntityBotUpdateId.Fields.updateId) - a.getAsLongNumber(JbEntityBotUpdateId.Fields.updateId)));
		Stream<CcpJsonRepresentation> stream = messages.stream();
		var streamMap = stream.map(a -> a.getAsLongNumber(JbEntityBotUpdateId.Fields.updateId) + 1);
		var findFirst = streamMap.findFirst();

		Long lastOffset = findFirst.get();
		
		long offset = Math.max(savedOffset, lastOffset);
		CcpJsonRepresentation parametersToSearchOffset = this.getParametersToSearchOffset(botType);

		CcpJsonRepresentation offsetToSave = parametersToSearchOffset
				.put(JbEntityBotUpdateId.Fields.updateId, offset)
				;

		JbEntityBotUpdateId.ENTITY.save(offsetToSave);

		return offset;
	}

	/**
	 * Returns the key of the offset of the bot.
	 * @param botType the bot
	 * @return the key
	 */
	private CcpJsonRepresentation getParametersToSearchOffset(String botType) {

		CcpJsonRepresentation parametersToSearch = CcpOtherConstants.EMPTY_JSON
				.put(JnJsonInstantMessengerFields.botName, botType)
				;

		return parametersToSearch;
	}

	/**
	 * Returns the token of the bot from the system properties.
	 * @param botType the bot
	 * @return the token
	 */
	public String getBotToken(String botType) {
		String botToken = JnSystemProperties.INSTANCE.getSystemInnerProperty(JnMessageType.InstantMessengerApiFields.bots, () -> botType);
		return botToken;
	}

	/**
	 * Calls {@code getUpdates} and returns the raw answer.
	 * @param botType the bot
	 * @param offset the first update to return
	 * @param timeout the long polling time, in seconds (zero for an immediate answer)
	 * @return the answer of Telegram
	 */
	public CcpJsonRepresentation getUpdates(String botType, Long offset, Integer timeout) {
		String updatesResource = "/getUpdates?offset=" + offset;

		CcpHttpHandler httpHandler = this.getHttpHandler(botType, updatesResource);
		CcpJsonRepresentation put = CcpOtherConstants.EMPTY_JSON
				.put(JsonFieldNames.offset, offset);

				CcpJsonRepresentation body = put
				.put(JsonFieldNames.timeout, timeout)
				;

		CcpJsonRepresentation response = httpHandler.executeHttpRequest("readInstantMessages", CcpHttpMethods.POST, CcpOtherConstants.EMPTY_JSON, body, CcpHttpResponseType.singleRecord);

		return response;
	}

	/**
	 * Reads the messages from the offset, without changing the saved offset.
	 * @param botType the bot
	 * @param offset the first update to return
	 * @param timeout the long polling time, in seconds
	 * @return the simplified messages
	 */
	public List<CcpJsonRepresentation> readMessages(String botType, Long offset, Integer timeout) {
		CcpJsonRepresentation updates = this.getUpdates(botType, offset, timeout);
		AtomicLong atomicLong = new AtomicLong(offset);
		List<CcpJsonRepresentation> messages = this.extractMessages(botType, updates, atomicLong);
		return messages;
	}

	/**
	 * Reads the messages not read yet (from the saved offset), hands each one to the bot and saves the new offset.
	 * @param timeout the long polling time, in seconds
	 * @param messageReader the bot; its name is the bot type
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


	/**
	 * Simplifies the updates that carry a message.
	 * @param botType the bot
	 * @param updates the answer of Telegram
	 * @param offsetToUpdate receives the offset after the last update (unused by the callers)
	 * @return the simplified messages
	 * @throws JbErrorUnableToReadInstantMessages when the answer is not {@code ok}
	 */
	private List<CcpJsonRepresentation> extractMessages(String botType, CcpJsonRepresentation updates, AtomicLong offsetToUpdate) {

		Boolean ok = updates.getOrDefault(CcpJsonCommonsFields.ok, () -> false);

		boolean requestWasNotOk = false == ok;

		if(requestWasNotOk) {
			JbErrorUnableToReadInstantMessages jbErrorUnableToReadInstantMessages = new JbErrorUnableToReadInstantMessages(updates);
			throw jbErrorUnableToReadInstantMessages;
		}

		List<CcpJsonRepresentation> result = updates.getAsJsonList(CcpJsonCommonsFields.result);

		List<CcpJsonRepresentation> messages = new ArrayList<>();

		for (CcpJsonRepresentation update : result) {

			Long updateId = update.getAsLongNumber(JsonFieldNames.update_id);
			Long nextUpdateId = updateId + 1;

			offsetToUpdate.set(nextUpdateId);
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

	/**
	 * Simplifies an update: bot, chat id, message id, send time, update id, user name and text.
	 * @param botType the bot
	 * @param update the update
	 * @param updateId the id of the update
	 * @return the simplified message
	 */
	private CcpJsonRepresentation extractMessage(String botType, CcpJsonRepresentation update, Long updateId) {

		Double chatId = update.getValueFromPath(0d, JnJsonInstantMessengerFields.message, JsonFieldNames.chat, JnJsonCommonsFields.id);
		Double messageId = update.getValueFromPath(0d, JnJsonInstantMessengerFields.message, CcpJsonCommonsFields.message_id);
		Double sentAt = update.getValueFromPath(0d, JnJsonInstantMessengerFields.message, JnJsonCommonsFields.date);
		String typedValue = update.getValueFromPath("", JnJsonInstantMessengerFields.message, CcpJsonCommonsFields.text);
		String userName = update.getValueFromPath("", JnJsonInstantMessengerFields.message, JsonFieldNames.from, JsonFieldNames.username);
		CcpJsonRepresentation put2 = CcpOtherConstants.EMPTY_JSON
				.put(JnJsonInstantMessengerFields.botName, botType);
				long longValue = chatId.longValue();
				CcpJsonRepresentation put3 = put2
				.put(JnJsonInstantMessengerFields.chatId, longValue);
				long longValue2 = messageId.longValue();
				CcpJsonRepresentation put4 = put3
				.put(CcpJsonCommonsFields.message_id, longValue2);
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

	/**
	 * Builds the HTTP handler of a resource of the bot, with the flows by status.
	 * @param botType the bot
	 * @param resource the resource, with its query string
	 * @return the handler
	 */
	private CcpHttpHandler getHttpHandler(String botType, String resource) {

		String botToken = this.getBotToken(botType);
		String botUrl = JnSystemProperties.INSTANCE.urlInstantMessengerKey();
		String botUrlWithToken = botUrl + botToken;
		String url = botUrlWithToken + resource;
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


	/** Raised when the messenger answers 404 for the bot: the bot does not exist. */
	@SuppressWarnings("serial")
	public static class JbErrorInstantMessengerBotNotFound extends RuntimeException {
		/**
		 * Names the bot not found.
		 * @param botType the bot
		 */
		private JbErrorInstantMessengerBotNotFound(String botType) {
			super("The bot '" + botType + "' was not found");
		}
	}

	/** Raised when the messenger answers 401 for the bot: the bot exists but is inactive. */
	@SuppressWarnings("serial")
	public static class JbErrorInstantMessengerBotIsInactive extends RuntimeException {
		/**
		 * Names the inactive bot.
		 * @param botType the bot
		 */
		private JbErrorInstantMessengerBotIsInactive(String botType) {
			super("The bot '" + botType + "' is inactive");
		}
	}

}
