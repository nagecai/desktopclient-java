/*
 *  Kontalk Java client
 *  Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kontalk.model;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kontalk.system.Database;

/**
 * Messages of chat.
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class ChatMessages {
    private static final Logger LOGGER = Logger.getLogger(ChatMessages.class.getName());

    private final Chat mChat;
    private final NavigableSet<KonMessage> mSet =
        Collections.synchronizedNavigableSet(new TreeSet<KonMessage>());

    private boolean mLoaded = false;

    public ChatMessages(Chat chat) {
        mChat = chat;
    }

    private void ensureLoaded() {
        if (mLoaded)
            return;

        this.loadMessages();
        mLoaded = true;
    }

    private void loadMessages() {
        Database db = Database.getInstance();

        try (ResultSet messageRS = db.execSelectWhereInsecure(KonMessage.TABLE,
                KonMessage.COL_CHAT_ID + " == " + mChat.getID())) {
            while (messageRS.next()) {
                this.addSilent(KonMessage.load(messageRS, mChat));
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "can't load messages from db", ex);
        }
    }

    /**
     * Add message to chat without notifying other components.
     */
    boolean add(KonMessage message) {
        this.ensureLoaded();

        return this.addSilent(message);
    }

    private boolean addSilent(KonMessage message) {
        // see KonMessage.equals()
        if (mSet.contains(message)) {
            LOGGER.warning("message already in chat: " + message);
            return false;
        }
        boolean added = mSet.add(message);
        return added;
    }

    public NavigableSet<KonMessage> getAll() {
        this.ensureLoaded();

        return mSet;
    }

    /**
     * Get all outgoing messages with status "PENDING" for this chat.
     */
    public SortedSet<OutMessage> getPending() {
        this.ensureLoaded();

        SortedSet<OutMessage> s = new TreeSet<>();
        // TODO performance, probably additional map needed
        // TODO use lambda in near future
        for (KonMessage m : mSet) {
            if (m.getStatus() == KonMessage.Status.PENDING &&
                    m instanceof OutMessage) {
                s.add((OutMessage) m);
            }
        }
        return s;
    }

    /**
     * Get the newest (ie last received) outgoing message.
     */
    public Optional<OutMessage> getLast(String xmppID) {
        this.ensureLoaded();

        // TODO performance
        OutMessage message = null;
        for (KonMessage m: mSet.descendingSet()) {
            if (m.getXMPPID().equals(xmppID) && m instanceof OutMessage) {
                message = (OutMessage) m;
            }
        }

        if (message == null) {
            return Optional.empty();
        }

        return Optional.of(message);
    }
}
