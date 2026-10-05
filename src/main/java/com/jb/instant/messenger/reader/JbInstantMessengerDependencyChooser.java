package com.jb.instant.messenger.reader;

import com.ccp.dependency.injection.CcpDependencyInjection;
import com.ccp.implementations.db.bulk.elasticsearch.CcpElasticSerchDbBulk;
import com.ccp.implementations.db.crud.elasticsearch.CcpElasticSearchCrud;
import com.ccp.implementations.db.query.elasticsearch.CcpElasticSearchQueryExecutor;
import com.ccp.implementations.db.utils.elasticsearch.CcpElasticSearchDbRequest;
import com.ccp.implementations.http.apache.mime.CcpApacheMimeHttp;
import com.ccp.implementations.instant.messenger.telegram.CcpTelegramInstantMessenger;
import com.ccp.implementations.json.gson.CcpGsonJsonHandler;
import com.ccp.implementations.password.mindrot.CcpMindrotPasswordHandler;
import com.ccp.local.testings.implementations.CcpLocalInstances;
import com.ccp.local.testings.implementations.cache.CcpLocalCacheInstances;

/**
 * Chooses the implementations {@link JbInstantMessengerMessageReader} needs: JSON through Gson, HTTP through Apache
 * Mime, a mock cache, the database through Elasticsearch (to save and read the offset of each bot) and the instant
 * messenger through Telegram.
 */
public class JbInstantMessengerDependencyChooser {

	/** Registers in {@code CcpDependencyInjection} the implementations used to read the messages. */
	public static void chooseDependencies() {
		CcpGsonJsonHandler ccpGsonJsonHandler = new CcpGsonJsonHandler();
		CcpElasticSerchDbBulk ccpElasticSerchDbBulk = new CcpElasticSerchDbBulk();
		CcpApacheMimeHttp ccpApacheMimeHttp = new CcpApacheMimeHttp();
		CcpElasticSearchDbRequest ccpElasticSearchDbRequest = new CcpElasticSearchDbRequest();
		CcpElasticSearchCrud ccpElasticSearchCrud = new CcpElasticSearchCrud();
		CcpTelegramInstantMessenger ccpTelegramInstantMessenger = new CcpTelegramInstantMessenger();
		// the queries (e.g. the orphan items of a skill hierarchy fix request, the purge of a versionable record's history) need it
		CcpElasticSearchQueryExecutor ccpElasticSearchQueryExecutor = new CcpElasticSearchQueryExecutor();
		CcpDependencyInjection.loadAllDependencies(
				ccpGsonJsonHandler,
				CcpLocalInstances.email,
				new CcpMindrotPasswordHandler(),
				ccpElasticSerchDbBulk, 
				ccpApacheMimeHttp,
				CcpLocalInstances.syncMensageriaListener,
				CcpLocalCacheInstances.mock,
				ccpElasticSearchDbRequest,
				ccpElasticSearchCrud,
				ccpElasticSearchQueryExecutor,
				ccpTelegramInstantMessenger
		);
	}

}
