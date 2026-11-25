package com.lumina.engine.team;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

public final class TeamCrdt {
    public static final class Op {
        public String id;
        public String k;
        public String p;
        public String c;
        public long t;

        public Op() {}

        public Op(String id, String k, String p, String c) {
            this(id, k, p, c, 0L);
        }

        public Op(String id, String k, String p, String c, long t) {
            this.id = id;
            this.k = k;
            this.p = p;
            this.c = c;
            this.t = t;
        }
    }

    private static final class Node {
        final String id;
        final String parent;
        final char ch;
        final long t;
        boolean deleted;
        boolean added;

        Node(String id, String parent, char ch, long t) {
            this.id = id;
            this.parent = parent;
            this.ch = ch;
            this.t = t;
        }
    }

    private final String site;
    private final Map<String, Node> nodes = new HashMap<>();
    private final Map<String, List<String>> children = new HashMap<>();
    private final Set<String> pendingDeletes = new HashSet<>();
    private int counter;
    private String text = "";
    private boolean dirty = true;

    public TeamCrdt(String site) {
        this.site = site;
    }

    public String site() {
        return site;
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    public String text() {
        if (dirty) rebuild();
        return text;
    }

    public List<Op> insert(int index, String value) {
        if (value == null || value.isEmpty()) return Collections.emptyList();
        List<String> visible = visibleIds();
        int at = Math.max(0, Math.min(index, visible.size()));
        String anchor = at == 0 ? "" : visible.get(at - 1);
        String base = site + ":" + (++counter);
        String parent = anchor;
        long now = System.currentTimeMillis();
        for (int i = 0; i < value.length(); i++) {
            String id = i == 0 ? base : base + "." + i;
            addNode(new Node(id, parent, value.charAt(i), now));
            parent = id;
        }
        dirty = true;
        return Collections.singletonList(new Op(base, "i", anchor.isEmpty() ? null : anchor, value, now));
    }

    public List<Op> delete(int start, int end) {
        List<String> visible = visibleIds();
        int from = Math.max(0, Math.min(start, visible.size()));
        int to = Math.max(from, Math.min(end, visible.size()));
        if (from == to) return Collections.emptyList();
        List<Op> out = new ArrayList<>(to - from);
        for (int i = from; i < to; i++) {
            String id = visible.get(i);
            Node node = nodes.get(id);
            if (node == null || node.deleted) continue;
            node.deleted = true;
            out.add(new Op(id, "d", null, null));
        }
        if (!out.isEmpty()) dirty = true;
        return out;
    }

    public boolean apply(Op op) {
        if (op == null || op.id == null) return false;
        if ("i".equals(op.k)) return applyInsert(op);
        if ("d".equals(op.k)) return applyDelete(op);
        return false;
    }

    public void applyAll(Collection<Op> ops) {
        if (ops == null) return;
        for (Op op : ops) apply(op);
    }

    private boolean applyInsert(Op op) {
        String value = op.c == null ? "" : op.c;
        String parent = op.p == null ? "" : op.p;
        if (site != null && op.id != null && op.id.startsWith(site + ":")) {
            counter = Math.max(counter, (int) sequence(op.id.substring(site.length() + 1)));
        }
        boolean changed = false;
        for (int i = 0; i < value.length(); i++) {
            String id = i == 0 ? op.id : op.id + "." + i;
            if (nodes.containsKey(id)) {
                parent = id;
                continue;
            }
            Node node = new Node(id, parent, value.charAt(i), op.t);
            if (pendingDeletes.contains(id)) node.deleted = true;
            addNode(node);
            parent = id;
            changed = true;
        }
        if (changed) dirty = true;
        return changed;
    }

    private boolean applyDelete(Op op) {
        Node node = nodes.get(op.id);
        if (node == null) {
            pendingDeletes.add(op.id);
            return false;
        }
        if (node.deleted) return false;
        node.deleted = true;
        dirty = true;
        return true;
    }

    private void addNode(Node node) {
        if (nodes.put(node.id, node) != null) return;
        List<String> list = children.get(node.parent);
        if (list == null) {
            list = new ArrayList<>();
            children.put(node.parent, list);
        }
        int at = Collections.binarySearch(list, node.id, this::compareNodes);
        if (at < 0) at = -at - 1;
        list.add(at, node.id);
    }

    /**
     * Siblings are visited newest first, so an insertion at a position appears immediately
     * after its anchor instead of after the text that was already there.
     */
    private int compareNodes(String a, String b) {
        Node na = nodes.get(a);
        Node nb = nodes.get(b);
        long at = na == null ? 0L : na.t;
        long bt = nb == null ? 0L : nb.t;
        if (at != bt) return Long.compare(bt, at);
        return compareIds(a, b);
    }

    private static int compareIds(String a, String b) {
        String as = a == null ? "" : a;
        String bs = b == null ? "" : b;
        int ac = as.indexOf(':');
        int bc = bs.indexOf(':');
        String asite = ac < 0 ? "" : as.substring(0, ac);
        String bsite = bc < 0 ? "" : bs.substring(0, bc);
        if (!asite.equals(bsite)) return bsite.compareTo(asite);
        long an = sequence(ac < 0 ? as : as.substring(ac + 1));
        long bn = sequence(bc < 0 ? bs : bs.substring(bc + 1));
        if (an != bn) return Long.compare(bn, an);
        return bs.compareTo(as);
    }

    private static long sequence(String value) {
        int dot = value.indexOf('.');
        String head = dot < 0 ? value : value.substring(0, dot);
        try {
            return Long.parseLong(head);
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private List<String> visibleIds() {
        List<String> out = new ArrayList<>();
        Deque<String> stack = new ArrayDeque<>();
        pushChildren(stack, "");
        while (!stack.isEmpty()) {
            String id = stack.pop();
            Node node = nodes.get(id);
            if (node == null) continue;
            if (!node.deleted) out.add(id);
            pushChildren(stack, id);
        }
        return out;
    }

    private void pushChildren(Deque<String> stack, String parent) {
        List<String> list = children.get(parent);
        if (list == null) return;
        for (int i = list.size() - 1; i >= 0; i--) stack.push(list.get(i));
    }

    private void rebuild() {
        StringBuilder sb = new StringBuilder(Math.max(16, nodes.size()));
        Deque<String> stack = new ArrayDeque<>();
        pushChildren(stack, "");
        while (!stack.isEmpty()) {
            String id = stack.pop();
            Node node = nodes.get(id);
            if (node == null) continue;
            if (!node.deleted) sb.append(node.ch);
            pushChildren(stack, id);
        }
        text = sb.toString();
        dirty = false;
    }
}
