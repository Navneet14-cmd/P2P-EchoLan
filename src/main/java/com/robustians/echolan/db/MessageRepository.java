package com.robustians.echolan.db;

import java.util.List;
import com.robustians.echolan.model.Message;

public class MessageRepository {
    private final MessageDao dao;
    private final int maxMessages;

    public MessageRepository(MessageDao dao, int maxMessages) {
        this.dao = dao;
        this.maxMessages = maxMessages;
    }

    public void init() {
        dao.initSchema();
    }

    public void save(Message message) {
        dao.insert(message);
        enforceLimit(message.getPeerId());
    }

    public List<Message> getRecentMessages(String peerId) {
        return dao.getRecent(peerId, maxMessages);
    }

    private void enforceLimit(String peerId) {
        int total = dao.count(peerId);
        if (total > maxMessages) {
            dao.deleteOldest(peerId, total - maxMessages);
        }
    }

    public void close() {
        dao.close();
    }
}
