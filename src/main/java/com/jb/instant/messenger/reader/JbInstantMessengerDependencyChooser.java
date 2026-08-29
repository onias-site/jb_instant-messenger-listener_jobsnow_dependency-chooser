package com.jb.instant.messenger.reader;

import com.ccp.dependency.injection.CcpDependencyInjection;
import com.ccp.implementations.db.crud.elasticsearch.CcpElasticSearchCrud;
import com.ccp.implementations.db.utils.elasticsearch.CcpElasticSearchDbRequest;
import com.ccp.implementations.http.apache.mime.CcpApacheMimeHttp;
import com.ccp.implementations.instant.messenger.telegram.CcpTelegramInstantMessenger;
import com.ccp.implementations.json.gson.CcpGsonJsonHandler;
import com.ccp.local.testings.implementations.cache.CcpLocalCacheInstances;

/**
 * Escolhe as implementações necessárias para que {@link JbInstantMessengerMessageReader} funcione:
 * json via Gson, http via Apache Mime, cache nulo, banco de dados via Elasticsearch (necessário para
 * gravar e recuperar o offset de cada bot) e mensageria instantânea via Telegram.
 */
public class JbInstantMessengerDependencyChooser {

	/**
	 * Registra no {@code CcpDependencyInjection} as implementações usadas pela leitura de mensagens.
	 */
	public static void chooseDependencies() {
		CcpDependencyInjection.loadAllDependencies(
				new CcpGsonJsonHandler(),
				new CcpApacheMimeHttp(),
				CcpLocalCacheInstances.mock,
				new CcpElasticSearchDbRequest(),
				new CcpElasticSearchCrud(),
				new CcpTelegramInstantMessenger()
		);
	}

}
