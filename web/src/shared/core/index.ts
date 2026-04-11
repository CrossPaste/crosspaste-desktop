/**
 * Bridge module for the Kotlin/JS core library (ESM).
 * Kotlin singletons are accessed via getInstance().
 */

import {
  CrossPasteJson as KtJson,
  CrossPasteHash as KtHash,
  CrossPasteCrypto as KtCrypto,
} from "@crosspaste/core";

/** JSON parsing utilities */
export const CrossPasteJson = KtJson.getInstance();

/** Hashing utilities (MurmurHash3, Base64) */
export const CrossPasteHash = KtHash.getInstance();

/** Crypto utilities — all async (WebCrypto backend) */
export const CrossPasteCrypto = KtCrypto.getInstance();
