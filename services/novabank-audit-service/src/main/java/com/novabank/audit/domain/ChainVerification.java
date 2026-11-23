package com.novabank.audit.domain;

/**
 * Result of walking the chain from the first entry to the head.
 *
 * @param headHash hex; copy (headSeq, headHash) somewhere else now and then to anchor the chain
 * @param brokenAtSeq the first entry that doesn't check out, or {@code null} if all do
 * @param problem what is wrong at that entry
 */
public record ChainVerification(
    boolean valid,
    long checkedEntries,
    long headSeq,
    String headHash,
    Long brokenAtSeq,
    String problem) {

  public static ChainVerification valid(long checked, long headSeq, String headHash) {
    return new ChainVerification(true, checked, headSeq, headHash, null, null);
  }

  public static ChainVerification broken(
      long checked, long headSeq, String headHash, long seq, String problem) {
    return new ChainVerification(false, checked, headSeq, headHash, seq, problem);
  }
}
