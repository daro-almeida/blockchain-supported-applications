package utils;

import java.security.PublicKey;
import java.util.*;
import java.util.function.Consumer;

public class View implements Iterable<Node> {

    private int viewNumber;
    private Node primary;
    private final int firstPrimaryId;
    private final Map<Integer, Node> nodes;

    public View(List<Node> nodeList, Node primary) {
        this.viewNumber = 0;
        this.primary = primary;
        this.firstPrimaryId = primary.id();
        this.nodes = new HashMap<>();
        for (Node node : nodeList) {
            nodes.put(node.id(), node);
        }
    }

    public View(View view) {
        this.viewNumber = view.viewNumber;
        this.primary = view.primary;
        this.firstPrimaryId = view.firstPrimaryId;
        this.nodes = new HashMap<>(view.nodes);
    }

    public int getViewNumber() {
        return viewNumber;
    }

    public void updateView(int viewNumber) {
        assert viewNumber > this.viewNumber;

        this.viewNumber = viewNumber;
        this.primary = leaderInView(viewNumber);
    }

    public Node getPrimary() {
        assert primary != null;
        return primary;
    }

    public Node getNode(int id) {
        var node = nodes.get(id);
        assert node != null;
        return node;
    }

    public Map<Integer, PublicKey> publicKeys() {
        Map<Integer, PublicKey> publicKeys = new HashMap<>();
        for (Node node : nodes.values()) {
            publicKeys.put(node.id(), node.publicKey());
        }
        return publicKeys;
    }

    public Node leaderInView(int viewNumber) {
        assert viewNumber >= 0;
        var nodeId = (viewNumber % nodes.size()) + firstPrimaryId;
        return nodes.get(nodeId);
    }

    public int size() {
        return nodes.size();
    }

    @Override
    public Iterator<Node> iterator() {
        return nodes.values().iterator();
    }

    @Override
    public void forEach(Consumer<? super Node> action) {
        nodes.values().forEach(action);
    }
}
