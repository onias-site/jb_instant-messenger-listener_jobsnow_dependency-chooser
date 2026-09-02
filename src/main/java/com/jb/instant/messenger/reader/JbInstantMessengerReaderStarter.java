package com.jb.instant.messenger.reader;

import com.ccp.business.CcpBusiness;
import com.ccp.decorators.CcpTimeDecorator;
import com.jb.business.bots.engine.JbBotType;
import com.jn.business.messages.JnBusinessSendInstantMessage.JnBotType;

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
		String botTypeName = botType.name();

		JbBotType valueOf = JbBotType.valueOf(botTypeName);
		CcpBusiness bot = valueOf.getBot();

		for (int volta = 1; lerIndefinidamente || volta <= leituras; volta++) {
			JbInstantMessengerMessageReader.INSTANCE.readNewMessages(timeout, bot);
			ctd.sleep(3000);
		}
	}

	private static JnBotType getBotType(String[] args, int posicao, JnBotType valorPadrao) {

		boolean argumentoAusente = posicao >= args.length;

		if (argumentoAusente) {
			return valorPadrao;
		}

		try {
			String argsTrim = args[posicao].trim();
			JnBotType botType = JnBotType.valueOf(argsTrim);
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
			String argsTrim2 = args[posicao].trim();
			Integer valor = Integer.valueOf(argsTrim2);
			return valor;
		} catch (NumberFormatException e) {
			return valorPadrao;
		}
	}

}
