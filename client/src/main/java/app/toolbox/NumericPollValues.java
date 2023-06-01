package app.toolbox;

import org.apache.commons.math3.distribution.AbstractRealDistribution;

public record NumericPollValues(String description, double min, double max, Poll.Authorization authorization,
                                AbstractRealDistribution voteDistribution) {
}
