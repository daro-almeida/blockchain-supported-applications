package app.toolbox;

import java.util.Set;

public record DiscretePollValues(String description, Set<String> values, Poll.Authorization authorization ) {
}