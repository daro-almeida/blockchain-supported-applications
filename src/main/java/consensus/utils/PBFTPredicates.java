package consensus.utils;

import consensus.messages.CommitMessage;
import consensus.messages.PrePrepareMessage;
import consensus.messages.PrepareMessage;

import java.util.Arrays;
import java.util.Set;

public class PBFTPredicates {

    /*
     * We define the predicate prepared(m,v,n,i) to be true if and only if replica i has inserted in its log: the
     * request m, a pre-prepare for m in view v with sequence number n, and 2f prepares from different backups that
     * match the pre-prepare.
     */
    public static boolean prepared(int f, PrePrepareMessage prePrepare, Set<PrepareMessage> prepares) {
        if (prePrepare == null || prepares == null)
            return false;

        var viewNumber = prePrepare.getViewNumber();
        var seqNum = prePrepare.getSeq();

        int matchingPrepares = 0;
        for (var prepare : prepares) {
            if (prepare.getViewNumber() == viewNumber && prepare.getSeq() == seqNum && Arrays.equals(prepare.getDigest(), prePrepare.getDigest())) {
                matchingPrepares++;
                // 2f + 1 because it includes the pre-prepare from the primary
                if (matchingPrepares >= 2 * f + 1) {
                    return true;
                }
            }
        }
        return false;
    }

    /*
     * true if and only if prepared(m,v,n,i) is true and has accepted 2f + 1 commits (possibly including its own) from
     *  different replicas that match the pre-prepare for m; a commit matches a pre-prepare if they have the same view,
     *  sequence number, and digest.
     */
    public static boolean committed(int f, PrePrepareMessage prePrepare, Set<PrepareMessage> prepares, Set<CommitMessage> commits) {
        if (prePrepare == null || prepares == null || commits == null)
            return false;

        var viewNumber = prePrepare.getViewNumber();
        var seqNum = prePrepare.getSeq();

        int matchingCommits = 0;
        for (var commit : commits) {
            if (commit.getViewNumber() == viewNumber && commit.getSeq() == seqNum && Arrays.equals(commit.getDigest(),
                    prePrepare.getDigest())) {
                matchingCommits++;
                if (matchingCommits >= 2 * f + 1) {
                    return prepared(f, prePrepare, prepares);
                }
            }
        }
        return false;
    }
}
