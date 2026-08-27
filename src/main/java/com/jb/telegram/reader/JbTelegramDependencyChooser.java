package com.jb.telegram.reader;

import com.ccp.dependency.injection.CcpDependencyInjection;
import com.ccp.implementations.http.apache.mime.CcpApacheMimeHttp;
import com.ccp.implementations.instant.messenger.telegram.CcpTelegramInstantMessenger;
import com.ccp.implementations.json.gson.CcpGsonJsonHandler;

/**
 * Escolhe as implementações necessárias para que {@link JbTelegramMessageReader} funcione:
 * json via Gson, http via Apache Mime e mensageria instantânea via Telegram.
 */
public class JbTelegramDependencyChooser {

	/**
	 * Registra no {@code CcpDependencyInjection} as implementações usadas pela leitura de mensagens.
	 */
	public static void chooseDependencies() {
		CcpDependencyInjection.loadAllDependencies(
				new CcpGsonJsonHandler(),
				new CcpApacheMimeHttp(),
				new CcpTelegramInstantMessenger()
		);
	}

}
