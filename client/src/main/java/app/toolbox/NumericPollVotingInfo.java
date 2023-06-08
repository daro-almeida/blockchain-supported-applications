package app.toolbox;

import org.apache.commons.math3.distribution.AbstractRealDistribution;
import org.apache.commons.math3.distribution.LaplaceDistribution;

public record NumericPollVotingInfo(AbstractRealDistribution voteDistribution, LaplaceDistribution noiseDistribution) {
}
