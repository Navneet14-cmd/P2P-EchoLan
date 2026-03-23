package com.robustians.echolan.model;

public class Message {
    private int type;
    private String content;
    private String peerId;

    public Message(int type, String content) {
        this(type, content, "");
    }

    public Message(int type, String content, String peerId) {
        this.type = type;
        this.content = content;
        this.peerId = peerId;
    }

    public int getType() {
        return type;
    }

    public String getContent() {
        return content;
    }

    public String getPeerId() {
        return peerId;
    }
}
