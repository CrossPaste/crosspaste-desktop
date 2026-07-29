/**
 * Bridge module for the Kotlin/JS core library (ESM).
 * Kotlin singletons are accessed via getInstance().
 */

import {
  CrossPasteJson as KtJson,
  CrossPasteHash as KtHash,
  CrossPasteCrypto as KtCrypto,
  createPairingV3Initiator as ktCreatePairingV3Initiator,
  JsPairingV3Initiator,
} from "@crosspaste/core";

/** JSON parsing utilities */
export const CrossPasteJson = KtJson.getInstance();

/** Hashing utilities (MurmurHash3, Base64) */
export const CrossPasteHash = KtHash.getInstance();

/** Crypto utilities — all async (WebCrypto backend) */
export const CrossPasteCrypto = KtCrypto.getInstance();

/**
 * Pairing v3 (SPAKE2 + PIN) initiator session. One instance per pairing
 * attempt; all protocol messages cross as JSON strings.
 */
export const createPairingV3Initiator = ktCreatePairingV3Initiator;
export type PairingV3Initiator = JsPairingV3Initiator;
