package com.jb.instant.messenger.reader;

import com.ccp.decorators.CcpJsonRepresentation;

/** Raised when the messenger answers {@code getUpdates} without {@code ok}. */
@SuppressWarnings("serial")
public class JbErrorUnableToReadInstantMessages extends RuntimeException {
	/**
	 * Builds the error with the answer.
	 * @param json the answer of the messenger
	 */
	JbErrorUnableToReadInstantMessages(CcpJsonRepresentation json) {
		super("It was not possible to read the instant messages. Details: " + json);
	}
}
