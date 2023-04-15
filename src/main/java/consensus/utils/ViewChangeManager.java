package consensus.utils;

import consensus.messages.NewViewMessage;
import consensus.messages.PrePrepareMessage;
import consensus.messages.ViewChangeMessage;
import consensus.requests.SuspectLeader;
import utils.Crypto;
import utils.Node;
import utils.View;

import java.security.PrivateKey;
import java.util.*;

public class ViewChangeManager {

    private final Node self;
    private final View view;
    private final PrivateKey key;
    private final Map<Integer, Set<ViewChangeMessage>> viewChangesLog = new HashMap<>();

    private Integer askedViewChange = null;
    private Integer pendingViewChange = null;

    public ViewChangeManager(Node self, View view, PrivateKey key) {
        this.self = self;
        this.view = view;
        this.key = key;
    }
    public boolean canSendViewChange(SuspectLeader req) {
        boolean canSend = ((req.getCurrentView() < view.getViewNumber()) || // ignore unneeded requests
                (askedViewChange != null && pendingViewChange == null) || // already asked and not pending
                (askedViewChange != null && askedViewChange > pendingViewChange)); // already asked for new pending

        if (canSend && pendingViewChange != null && (askedViewChange == null || askedViewChange < pendingViewChange)) {
            askedViewChange = pendingViewChange;
            return false;
        } else
            return canSend;
    }

    public int newViewNumber() {
        int newViewNumber;
        if (askedViewChange == null && pendingViewChange == null) {
            newViewNumber = view.getViewNumber() + 1;
        } else { // asked == pending
            newViewNumber = pendingViewChange + 1;
        }

        askedViewChange = newViewNumber;
        assert pendingViewChange == null || askedViewChange > pendingViewChange;

        return newViewNumber;
    }

    public NewViewMessage processViewChangeMessage(ViewChangeMessage msg, int f) {
        viewChangesLog.computeIfAbsent(msg.getNewViewNumber(), k -> new HashSet<>()).add(msg);
        var viewChanges = viewChangesLog.get(msg.getNewViewNumber());

        if (viewChanges.size() >= 2*f + 1) {
            pendingViewChange = msg.getNewViewNumber();
            if (view.leaderInView(msg.getNewViewNumber()).equals(self)) {
                Set<PrePrepareMessage> prePrepares = ViewChangeManager.newPrePrepareMessages(msg.getNewViewNumber(), viewChanges, f);
                prePrepares.forEach(prePrepare -> Crypto.signMessage(prePrepare, key));

                return new NewViewMessage(msg.getNewViewNumber(), viewChanges, prePrepares);
            }
        }
        return null;
    }

    public void newView(int newViewNumber) {
        viewChangesLog.remove(newViewNumber);
        askedViewChange = null;
        pendingViewChange = null;
    }

    public boolean rejectMessage(int viewNumber) {
        return (viewNumber != view.getViewNumber() && pendingViewChange == null) ||
                (pendingViewChange != null && viewNumber != pendingViewChange);
    }

    // min seq of operation at least 2f+1 replicas executed ((f+1)th element of array in ascending order)
    public static int minSeq(Set<ViewChangeMessage> viewChanges, int f) {
        return (int) viewChanges.stream().map(ViewChangeMessage::getLastExecuted).sorted(Integer::compareTo).toArray()[f];
    }

    // max prepared
    public static int maxSeq(Set<ViewChangeMessage> viewChanges) {
        return viewChanges.stream().map(ViewChangeMessage::maxPrepared).max(Integer::compareTo).orElse(-1);
    }

    public static Set<PrePrepareMessage> newPrePrepareMessages(int newViewNumber, Set<ViewChangeMessage> viewChanges, int f) {
        Set<PrePrepareMessage> prePrepares = new HashSet<>();
        for (int seq = minSeq(viewChanges, f) + 1; seq <= maxSeq(viewChanges); seq++) {
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
