package app.toolbox;

import org.apache.commons.math3.distribution.AbstractIntegerDistribution;

public record DiscretePollVotingInfo(AbstractIntegerDistribution voteDistribution, double truthProbability) {
}
