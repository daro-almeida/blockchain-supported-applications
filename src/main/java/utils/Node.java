package utils;

import pt.unl.fct.di.novasys.network.data.Host;

import java.security.PublicKey;
import java.util.Objects;

public record Node(int id, Host host, PublicKey publicKey) {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Node node = (Node) o;
        return id == node.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
