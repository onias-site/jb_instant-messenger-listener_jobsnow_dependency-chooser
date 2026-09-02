package com.jb.instant.messenger.reader;

import com.ccp.decorators.CcpJsonRepresentation;

@SuppressWarnings("serial")
public class JbErrorUnableToReadInstantMessages extends RuntimeException {
	JbErrorUnableToReadInstantMessages(CcpJsonRepresentation json) {
		super("It was not possible to read the instant messages. Details: " + json);
	}
}
