package com.example.hldsn.mesh.model;

public class RelayDecision {
    public enum Action {
        IGNORE,
        DELIVER,
        FORWARD
    }

    private final Action action;
    private final MeshMessage message;
    private final String clearText;

    private RelayDecision(Action action, MeshMessage message, String clearText) {
        this.action = action;
        this.message = message;
        this.clearText = clearText;
    }

    public static RelayDecision ignore(MeshMessage message) {
        return new RelayDecision(Action.IGNORE, message, null);
    }

    public static RelayDecision deliver(MeshMessage message, String clearText) {
        return new RelayDecision(Action.DELIVER, message, clearText);
    }

    public static RelayDecision forward(MeshMessage message) {
        return new RelayDecision(Action.FORWARD, message, null);
    }

    public Action getAction() {
        return action;
    }

    public MeshMessage getMessage() {
        return message;
    }

    public String getClearText() {
        return clearText;
    }
}

