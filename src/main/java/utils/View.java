package utils;

import java.util.*;
import java.util.function.Consumer;

public class View implements Iterable<Node> {

    private int viewNumber;
    private Node primary;
    private final Map<Integer, Node> nodes;

    public View(List<Node> nodeList, Node primary) {
        this.viewNumber = 0;
        this.primary = primary;
        this.nodes = new HashMap<>();
        for (Node node : nodeList) {
            nodes.put(node.id(), node);
        }
    }

    public View(View view) {
        this.viewNumber = view.viewNumber;
        this.primary = view.primary;
        this.nodes = new HashMap<>(view.nodes);
    }

    public int getViewNumber() {
        return viewNumber;
    }

    public void updateView(int viewNumber, List<Node> nodeList, Node primary) {
        assert viewNumber == this.viewNumber + 1;

        this.viewNumber = viewNumber;
        for (Node node : nodeList) {
            nodes.put(node.id(), node);
        }
        this.primary = primary;
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
