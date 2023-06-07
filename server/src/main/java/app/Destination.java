package app;

import pt.unl.fct.di.novasys.network.data.Host;

public record Destination(Host host, short sourceProto) {
}
