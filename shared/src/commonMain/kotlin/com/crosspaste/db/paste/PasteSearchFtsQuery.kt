package com.crosspaste.db.paste

/**
 * The FTS5 MATCH expression for a paste search: every term must prefix-match the
 * search content. Each term is quoted as an FTS5 string literal so punctuation
 * (".", "'", "@", "+", "-") is matched as text instead of being parsed as query syntax.
 */
fun pasteSearchFtsQuery(searchTerms: List<String>): String =
    "pasteSearchContent:(${searchTerms.joinToString(" AND ") { "\"${it.replace("\"", "\"\"")}\"*" }})"
