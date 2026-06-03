package com.robustians.echolan.db;

import java.util.List;
import com.robustians.echolan.model.Message;

public interface MessageDao {
    void initSchema();
    void insert(Message message);
    List<Message> getRecent(String peerId, int limit);
    int count(String peerId);
    void deleteOldest(String peerId, int excess);
    void close();
}
