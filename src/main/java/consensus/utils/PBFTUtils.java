package consensus.utils;

import consensus.messages.PrePrepareMessage;
import consensus.messages.ViewChangeMessage;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class PBFTUtils {

    public static Set<PrePrepareMessage> newPrePrepareMessages(int newViewNumber, Set<ViewChangeMessage> viewChanges, int f) {
        // min seq of operation at least 2f+1 replicas executed ((f+1)th element of array in ascending order)
        int minSeq = (int) viewChanges.stream().map(ViewChangeMessage::getLastExecuted).sorted(Integer::compareTo).toArray()[f];
        // max prepared
        int maxSeq = viewChanges.stream().map(ViewChangeMessage::maxPrepared).max(Integer::compareTo).orElse(-1);

        Set<PrePrepareMessage> prePrepares = new HashSet<>();
        for (int seq = minSeq + 1; seq <= maxSeq; seq++) {
            int finalSeq = seq; //dumb java moment
            var preparedProof = viewChanges.stream().map(vc -> vc.getPreparedProof(finalSeq)).filter(Objects::nonNull)
                    .findFirst().orElse(null);
            PrePrepareMessage newPrePrepare;
            if (preparedProof == null) {
                newPrePrepare = new PrePrepareMessage(newViewNumber, seq);
            } else {
                var prePrepare = preparedProof.getPrePrepare();
                newPrePrepare = new PrePrepareMessage(newViewNumber, seq, prePrepare.getDigest());
            }
            prePrepares.add(newPrePrepare);
        }
        return prePrepares;
    }
}
