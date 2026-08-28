package com.jb.instant.messenger.reader;

import java.util.List;

import com.ccp.decorators.CcpJsonRepresentation;

/**
 * Executa o {@code getUpdates} do Telegram contra o bot de suporte e imprime as mensagens recebidas.
 * Serve para testar o bot manualmente: rode esta classe, mande uma mensagem para o bot de suporte no
 * Telegram e a mensagem aparece no console.
 *
 * <p>Argumentos (todos opcionais):</p>
 * <ol>
 * <li>timeout em segundos do long polling, ou seja, quanto tempo cada leitura espera por mensagens
 * novas antes de desistir. Padrão 10.</li>
 * <li>quantidade de leituras. Zero significa ler indefinidamente. Padrão 1.</li>
 * </ol>
 */
public class JbInstantMessengerReaderStarter {

	private static final int TIMEOUT_PADRAO = 10;

	private static final int LEITURAS_PADRAO = 1;

	public static void main(String[] args) {

		JbInstantMessengerDependencyChooser.chooseDependencies();

		Integer timeout = getArgument(args, 0, TIMEOUT_PADRAO);
		Integer leituras = getArgument(args, 1, LEITURAS_PADRAO);

		boolean lerIndefinidamente = 0 == leituras;

		System.out.println("Lendo as mensagens do bot de suporte. Timeout de " + timeout + "s por leitura.");

		for (int volta = 1; lerIndefinidamente || volta <= leituras; volta++) {

			List<CcpJsonRepresentation> mensagens = JbInstantMessengerMessageReader.INSTANCE.readNewMessages(timeout);

			System.out.println("--- leitura " + volta + ": " + mensagens.size() + " mensagem(ns) ---");

			for (CcpJsonRepresentation mensagem : mensagens) {
				System.out.println(mensagem);
			}
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
