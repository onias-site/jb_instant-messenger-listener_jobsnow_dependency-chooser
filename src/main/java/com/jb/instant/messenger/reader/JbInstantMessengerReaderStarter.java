package com.jb.instant.messenger.reader;

import java.util.List;

import com.ccp.business.CcpBusiness;
import com.ccp.decorators.CcpJsonRepresentation;
import com.ccp.decorators.CcpTimeDecorator;
import com.jb.business.bots.engine.JbBotEngine;
import com.jb.business.bots.engine.JbBotEngine.JbBotType;
import com.jn.business.messages.JnBusinessSendInstantMessage;
import com.jn.business.messages.JnBusinessSendInstantMessage.JnBotType;
import com.jn.business.messages.JnBusinessSendInstantMessage.JnInstantMessageType;

/**
 * Executa o {@code getUpdates} do Telegram contra o bot informado e imprime as mensagens recebidas.
 * Serve para testar o bot manualmente: rode esta classe, mande uma mensagem para o bot no
 * Telegram e a mensagem aparece no console.
 *
 * <p>Argumentos (todos opcionais):</p>
 * <ol>
 * <li>timeout em segundos do long polling, ou seja, quanto tempo cada leitura espera por mensagens
 * novas antes de desistir. Padrão 10.</li>
 * <li>quantidade de leituras. Zero significa ler indefinidamente. Padrão 1.</li>
 * <li>nome do bot a ser lido, um dos itens de {@link JnBotType}. Padrão {@code support}.</li>
 * </ol>
 */
public class JbInstantMessengerReaderStarter {

	private static final int TIMEOUT_PADRAO = 10;

	private static final int LEITURAS_PADRAO = 0;

	private static final JnBotType BOT_PADRAO = JnBotType.support;

	public static void main(String[] args) {

		JbInstantMessengerDependencyChooser.chooseDependencies();

		Integer timeout = getArgument(args, 0, TIMEOUT_PADRAO);
		Integer leituras = getArgument(args, 1, LEITURAS_PADRAO);
		JnBotType botType = getBotType(args, 2, BOT_PADRAO);

		boolean lerIndefinidamente = 0 == leituras;
		CcpTimeDecorator ctd = new CcpTimeDecorator();

		for (int volta = 1; lerIndefinidamente || volta <= leituras; volta++) {

			List<CcpJsonRepresentation> mensagens = JbInstantMessengerMessageReader.INSTANCE.readNewMessages(botType, timeout);

			for (CcpJsonRepresentation message : mensagens) {
				CcpJsonRepresentation put = message
						.put(JnBusinessSendInstantMessage.JnJsonValidator.botName, botType.name())
						//TODO PARAMETRIZAR ESSE TEXT
						.put(JnBusinessSendInstantMessage.JnJsonValidator.instantMessageType, JnInstantMessageType.text)
						.renameField(JbInstantMessengerMessageReader.JsonFieldNames.message_id, JnBusinessSendInstantMessage.Fields.replyTo)
						;
				JbBotType valueOf = JbBotEngine.JbBotType.valueOf(botType.name());
				CcpBusiness bot = valueOf.getBot();
				bot.execute(put);
			}
			ctd.sleep(1);
		}
	}

	private static JnBotType getBotType(String[] args, int posicao, JnBotType valorPadrao) {

		boolean argumentoAusente = posicao >= args.length;

		if (argumentoAusente) {
			return valorPadrao;
		}

		try {
			JnBotType botType = JnBotType.valueOf(args[posicao].trim());
			return botType;
		} catch (IllegalArgumentException e) {
			return valorPadrao;
		}
	}

	private static Integer getArgument(String[] args, int posicao, int valorPadrao) {

		boolean argumentoAusente = posicao >= args.length;

		if (argumentoAusente) {
			return valorPadrao;
		}

		try {
			Integer valor = Integer.valueOf(args[posicao].trim());
			return valor;
		} catch (NumberFormatException e) {
			return valorPadrao;
		}
	}

}
