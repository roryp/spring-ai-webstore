package com.contoso.store;

import java.util.List;

/** The agent's answer, and the tools it called to get there, in order. */
record Answer(String text, List<String> toolCalls) {
}
