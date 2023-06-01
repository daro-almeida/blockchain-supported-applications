package app.toolbox;

import org.apache.commons.math3.distribution.AbstractIntegerDistribution;

import java.util.Set;

public record DiscretePollValues(String description, Set<String> values, Poll.Authorization authorization,
                                 AbstractIntegerDistribution voteDistribution) {
}