package com.jb.instant.messenger.reader;

import com.ccp.business.CcpBusiness;
import com.ccp.decorators.CcpTimeDecorator;
import com.jb.business.bots.engine.JbBotType;
import com.jn.business.messages.JnMessageType;
import com.jn.business.messages.JnMessageType.JnBotType;

/**
 * Reads the messages of a bot and hands them to it, to run the bot by hand: start this class, send a message to the bot
 * in Telegram and the bot answers.
 * <p>Arguments (all optional):</p>
 * <ol>
 * <li>the long polling time in seconds, that is, how long each reading waits for new messages. Default 10.</li>
 * <li>how many readings. Zero means reading forever. Default 0.</li>
 * <li>the bot, an item of {@code JnBotType}. Default {@code support}.</li>
 * </ol>
 */
public class JbInstantMessengerReaderStarter {

	/** The default long polling time, in seconds. */
	private static final int DEFAULT_TIMEOUT = 10;

	/** The default number of readings: zero, reading forever. */
	private static final int DEFAULT_READS = 0;

	/** The default bot. */
	private static final JnMessageType.JnBotType DEFAULT_BOT = JnMessageType.JnBotType.support;

	/**
	 * Reads the messages of the bot, 3 seconds apart.
	 * @param args the long polling time, the number of readings and the bot
	 */
	public static void main(String[] args) {

		JbInstantMessengerDependencyChooser.chooseDependencies();

		Integer timeout = getArgument(args, 0, DEFAULT_TIMEOUT);
		Integer reads = getArgument(args, 1, DEFAULT_READS);
		JnBotType botType = getBotType(args, 2, DEFAULT_BOT);

		boolean readForever = 0 == reads;
		CcpTimeDecorator ctd = new CcpTimeDecorator();
		String botTypeName = botType.name();

		JbBotType valueOf = JbBotType.valueOf(botTypeName);
		CcpBusiness bot = valueOf.getBot();

		for (int round = 1; readForever || round <= reads; round++) {
			JbInstantMessengerMessageReader.INSTANCE.readNewMessages(timeout, bot);
			ctd.sleep(3000);
		}
	}

	/**
	 * Reads the bot from the arguments.
	 * @param args the arguments
	 * @param position the position of the argument
	 * @param defaultValue the value when the argument is absent or invalid
	 * @return the bot
	 */
	private static JnBotType getBotType(String[] args, int position, JnBotType defaultValue) {

		boolean argumentIsMissing = position >= args.length;

		if (argumentIsMissing) {
			return defaultValue;
		}

		try {
			String argsTrim = args[position].trim();
			JnBotType botType = JnBotType.valueOf(argsTrim);
			return botType;
		} catch (IllegalArgumentException e) {
			return defaultValue;
		}
	}

	/**
	 * Reads a number from the arguments.
	 * @param args the arguments
	 * @param position the position of the argument
	 * @param defaultValue the value when the argument is absent or invalid
	 * @return the number
	 */
	private static Integer getArgument(String[] args, int position, int defaultValue) {

		boolean argumentIsMissing = position >= args.length;

		if (argumentIsMissing) {
			return defaultValue;
		}

		try {
			String argsTrim2 = args[position].trim();
			Integer value = Integer.valueOf(argsTrim2);
			return value;
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}

}
